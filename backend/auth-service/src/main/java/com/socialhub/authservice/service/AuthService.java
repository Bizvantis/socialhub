
package com.socialhub.authservice.service;

import com.socialhub.authservice.dto.*;
import com.socialhub.authservice.entity.*;
import com.socialhub.authservice.exception.ApiException;
import com.socialhub.authservice.repository.*;
import com.socialhub.authservice.security.JwtService;
import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final long refreshTtlDays;
    private final OutboxWriter outbox;
    private final AccountModeration accounts;

    private final SecureRandom random = new SecureRandom();
    private static final java.time.Duration REUSE_GRACE = java.time.Duration.ofSeconds(30);

    public AuthService(
            UserRepository users,
            RoleRepository roles,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays,
            OutboxWriter outbox,
            AccountModeration accounts) {

        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtlDays = refreshTtlDays;
        this.outbox = outbox;
        this.accounts = accounts;
    }

    // REGISTER
    @Transactional
    public RegisteredUser register(RegisterRequest req) {

        String email = req.email().trim().toLowerCase();
        String username = req.username().trim();

        if (users.existsByEmail(email))
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Email already registered"
            );

        if (users.existsByUsername(username))
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Username already taken"
            );

        User u = new User();

        u.setEmail(email);
        u.setUsername(username);

        // Hash the password
        u.setPasswordHash(encoder.encode(req.password()));

        // Assign default USER role
        u.getRoles().add(
            roles.findByName("USER").orElseThrow()
        );

        users.save(u);
        accounts.grantAdminIfListed(email);

        // Other services (notifications: email delivery) learn about the account from this event,
        // written in the same transaction as the user row (transactional outbox).
        outbox.publish(Topics.ACCOUNT_EVENTS, "USER_REGISTERED", u.getId(),
                Map.of("userId", u.getId(), "email", u.getEmail(), "username", u.getUsername()));

        return new RegisteredUser(
            u.getId(),
            u.getUsername(),
            u.getEmail()
        );
    }

    // LOGIN
    @Transactional
    public AuthResponse login(LoginRequest req) {

        String id = req.emailOrUsername().trim();

        User u = users.findByEmail(id.toLowerCase())
            .or(() -> users.findByUsername(id))
            .orElse(null);

        if (u == null ||
            !encoder.matches(req.password(), u.getPasswordHash())) {

            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials"
            );
        }

        if (!"ACTIVE".equals(u.getStatus())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Account is not active"
            );
        }

        return issueTokens(u, UUID.randomUUID().toString());
    }

    // REFRESH TOKEN
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(RefreshRequest req) {

        RefreshToken old = refreshTokens
            .findByTokenHash(hash(req.refreshToken()))
            .orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Invalid refresh token"
            ));

        if (old.isRevoked()) {
            // Reuse grace window: a token rotated a few seconds ago is most likely a benign retry
            // (the browser navigated away before storing the new token, two tabs refreshed at once).
            // Only if the family is still alive, so a logout or theft response is never undone.
            boolean benignRetry = old.getReplacedAt() != null
                    && old.getReplacedAt().isAfter(Instant.now().minus(REUSE_GRACE))
                    && refreshTokens.existsByFamilyIdAndRevokedFalse(old.getFamilyId());
            if (!benignRetry) {
                // Real reuse of an old token = likely theft: kill the whole session family.
                refreshTokens.revokeFamily(old.getFamilyId());
                throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token reuse detected"
                );
            }
        }

        if (old.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token expired"
            );
        }

        User u = users.findById(old.getUserId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Invalid refresh token"
            ));

        if (!"ACTIVE".equals(u.getStatus())) {
            throw new ApiException(
                HttpStatus.FORBIDDEN,
                "Account is not active"
            );
        }

        // Revoke old token, remembering it was ROTATED (not logged out) for the grace window
        if (!old.isRevoked()) {
            old.setRevoked(true);
            old.setReplacedAt(Instant.now());
        }

        // Generate new tokens in the same family
        return issueTokens(u, old.getFamilyId());
    }

    // LOGOUT
    @Transactional
    public void logout(RefreshRequest req) {

        refreshTokens.findByTokenHash(hash(req.refreshToken()))
            .ifPresent(t ->
                refreshTokens.revokeFamily(t.getFamilyId())
            );
    }

    // GENERATE TOKENS
    private AuthResponse issueTokens(User u, String familyId) {

        byte[] bytes = new byte[32];

        random.nextBytes(bytes);

        String raw = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);

        RefreshToken rt = new RefreshToken();

        rt.setUserId(u.getId());
        rt.setTokenHash(hash(raw));
        rt.setFamilyId(familyId);

        rt.setExpiresAt(
            Instant.now().plus(refreshTtlDays, ChronoUnit.DAYS)
        );

        refreshTokens.save(rt);

        return new AuthResponse(
            jwt.generateAccessToken(u),
            raw,
            "Bearer",
            jwt.accessTtlSeconds()
        );
    }

    // SHA-256 HASHING
    private static String hash(String raw) {

        try {

            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8))
            );

        } catch (Exception e) {

            throw new IllegalStateException(e);
        }
    }
}
