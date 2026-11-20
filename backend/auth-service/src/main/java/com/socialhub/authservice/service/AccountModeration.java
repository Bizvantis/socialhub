package com.socialhub.authservice.service;

import com.socialhub.authservice.repository.RefreshTokenRepository;
import com.socialhub.authservice.repository.RoleRepository;
import com.socialhub.authservice.repository.UserRepository;
import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Account-level moderation and role bootstrap.
 *
 * Suspension (USER_SUSPENDED from moderation-service) has to stop a user immediately, but access
 * tokens are stateless and stay valid for up to 15 minutes. So three things happen:
 *   1. status = SUSPENDED: login and refresh are refused;
 *   2. every refresh token is revoked;
 *   3. auth:suspended:{id} is set in Redis for the access-token lifetime, and the gateway rejects
 *      requests from that user id at once (a small, bounded denylist instead of per-request DB checks).
 */
@Component
public class AccountModeration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountModeration.class);
    private static final String CONSUMER = "auth-service.moderation";

    private final EventJson json;
    private final Idempotency idempotency;
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final StringRedisTemplate redis;
    private final TransactionTemplate tx;
    private final Set<String> adminEmails;
    private final Duration denylistTtl;

    public AccountModeration(EventJson json, Idempotency idempotency, UserRepository users, RoleRepository roles,
                             RefreshTokenRepository refreshTokens, StringRedisTemplate redis, TransactionTemplate tx,
                             @Value("${app.bootstrap-admins:}") String adminEmails,
                             @Value("${app.jwt.access-ttl-minutes:15}") long accessTtlMinutes) {
        this.json = json;
        this.idempotency = idempotency;
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.redis = redis;
        this.tx = tx;
        this.adminEmails = Arrays.stream(adminEmails.split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.denylistTtl = Duration.ofMinutes(accessTtlMinutes + 1);
    }

    @KafkaListener(topics = Topics.MODERATION_EVENTS, groupId = "auth-service")
    @Transactional
    public void onModeration(String message) {
        EventEnvelope e = json.read(message);
        if (!"USER_SUSPENDED".equals(e.type()) && !"USER_REINSTATED".equals(e.type())) return;
        if (!idempotency.firstTime(CONSUMER, e.eventId())) return;
        long userId = e.longField("userId");
        boolean suspend = "USER_SUSPENDED".equals(e.type());
        users.findById(userId).ifPresent(u -> {
            u.setStatus(suspend ? "SUSPENDED" : "ACTIVE");
            if (suspend) {
                refreshTokens.revokeAllForUser(userId);
                redis.opsForValue().set("auth:suspended:" + userId, "1", denylistTtl);
            } else {
                redis.delete("auth:suspended:" + userId);
            }
            log.info("User {} {}", userId, suspend ? "suspended" : "reinstated");
        });
    }

    /** Grant ADMIN to the configured emails (BOOTSTRAP_ADMINS) at startup. */
    @Override
    public void run(ApplicationArguments args) {
        if (adminEmails.isEmpty()) return;
        tx.executeWithoutResult(s -> adminEmails.forEach(this::grantAdminIfListed));
    }

    public boolean grantAdminIfListed(String email) {
        if (!adminEmails.contains(email.toLowerCase(Locale.ROOT))) return false;
        return users.findByEmail(email.toLowerCase(Locale.ROOT)).map(u -> {
            roles.findByName("ADMIN").ifPresent(r -> u.getRoles().add(r));
            return true;
        }).orElse(false);
    }
}
