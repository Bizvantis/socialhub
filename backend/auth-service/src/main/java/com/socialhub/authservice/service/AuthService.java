
package com.socialhub.authservice.service;

import com.socialhub.authservice.dto.*;
import com.socialhub.authservice.entity.*;
import com.socialhub.authservice.exception.ApiException;
import com.socialhub.authservice.repository.*;
import com.socialhub.authservice.security.JwtService;

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
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final long refreshTtlDays;

    private final SecureRandom random = new SecureRandom();

    public AuthService(
            UserRepository users,
            RoleRepository roles,
            RefreshTokenRepository refreshTokens,
            PasswordEncoder encoder,
            JwtService jwt,
            @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {

        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTtlDays = refreshTtlDays;
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

            refreshTokens.revokeFamily(old.getFamilyId());

            throw new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token reuse detected"
            );
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

        // Revoke old token
        old.setRevoked(true);

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
