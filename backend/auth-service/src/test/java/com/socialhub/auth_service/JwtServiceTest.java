
package com.socialhub.authservice.security;

import com.socialhub.authservice.entity.Role;
import com.socialhub.authservice.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    @Test
    void shouldGenerateAndParseAccessToken() {

        JwtService jwtService = new JwtService(
            "unit_test_secret_unit_test_secret_1234567890",
            15
        );

        User user = new User();
        user.setId(42L);
        user.setUsername("sid");

        Role role = new Role();
        role.setName("USER");

        user.setRoles(Set.of(role));

        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.parse(token);

        assertEquals("42", claims.getSubject());
        assertEquals("sid", claims.get("username"));
    }

    @Test
    void shouldRejectInvalidToken() {

        JwtService jwtService = new JwtService(
            "unit_test_secret_unit_test_secret_1234567890",
            15
        );

        User user = new User();
        user.setId(42L);
        user.setUsername("sid");

        Role role = new Role();
        role.setName("USER");

        user.setRoles(Set.of(role));

        String token = jwtService.generateAccessToken(user);

        assertThrows(
            Exception.class,
            () -> jwtService.parse(token + "x")
        );
    }
}
