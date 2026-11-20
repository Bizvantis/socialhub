package com.socialhub.authservice.service;

import com.socialhub.authservice.exception.ApiException;
import com.socialhub.authservice.repository.RefreshTokenRepository;
import com.socialhub.authservice.repository.UserRepository;
import com.socialhub.common.queue.TaskQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Secure password reset (Step 31):
 *  - "forgot" always answers 200, whether or not the email exists (no account enumeration);
 *  - the token is 256 random bits, emailed once; only its SHA-256 is stored;
 *  - it expires after 30 minutes, is single-use, and a new request invalidates older tokens;
 *  - a successful reset revokes every refresh token (signs the user out everywhere).
 * The email goes to RabbitMQ (not Kafka): a secret must not sit in a replayable event log.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final Duration ttl;
    private final String webBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                                JdbcTemplate jdbc, RabbitTemplate rabbit, ObjectMapper mapper,
                                @Value("${app.password-reset.ttl:30m}") Duration ttl,
                                @Value("${app.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.ttl = ttl;
        this.webBaseUrl = webBaseUrl;
    }

    @Transactional
    public void requestReset(String email) {
        users.findByEmail(email.trim().toLowerCase()).filter(u -> "ACTIVE".equals(u.getStatus())).ifPresent(u -> {
            jdbc.update("UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP(3) WHERE user_id = ? AND used_at IS NULL",
                    u.getId());
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                    u.getId(), sha256(token), Timestamp.from(Instant.now().plus(ttl)));

            String link = webBaseUrl + "/reset-password?token=" + token;
            String text = "Hi " + u.getUsername() + ",\n\nSomeone asked to reset your SocialHub password.\n"
                    + "If it was you, open this link within " + ttl.toMinutes() + " minutes:\n\n  " + link
                    + "\n\nIf it was not you, ignore this email: your password stays the same.\n";
            afterCommit(() -> sendEmail(u.getEmail(), "Reset your SocialHub password", text));
        });
    }

    @Transactional
    public void reset(String token, String newPassword) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, user_id FROM password_reset_tokens
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP(3) FOR UPDATE""", sha256(token));
        if (rows.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Reset link is invalid or has expired");
        long tokenId = ((Number) rows.get(0).get("id")).longValue();
        long userId = ((Number) rows.get(0).get("user_id")).longValue();

        var u = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid token"));
        u.setPasswordHash(encoder.encode(newPassword));
        jdbc.update("UPDATE password_reset_tokens SET used_at = CURRENT_TIMESTAMP(3) WHERE id = ?", tokenId);
        refreshTokens.revokeAllForUser(userId);
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            byte[] body = mapper.writeValueAsString(Map.of("to", to, "subject", subject, "text", text))
                    .getBytes(StandardCharsets.UTF_8);
            rabbit.invoke(ops -> {
                ops.send(TaskQueues.NOTIFY_TASKS, TaskQueues.EMAIL_KEY, MessageBuilder.withBody(body)
                        .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT).build());
                ops.waitForConfirmsOrDie(10_000);
                return null;
            });
        } catch (Exception e) {
            // The user can simply request another link; never fail the request (or reveal anything).
            log.warn("Could not queue password-reset email: {}", e.getMessage());
        }
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
