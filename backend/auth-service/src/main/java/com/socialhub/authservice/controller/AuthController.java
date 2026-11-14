
package com.socialhub.authservice.controller;

import com.socialhub.authservice.dto.*;
import com.socialhub.authservice.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisteredUser>> register(
            @Valid @RequestBody RegisterRequest r) {

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(auth.register(r)));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest r) {

        return ApiResponse.ok(auth.login(r));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest r) {

        return ApiResponse.ok(auth.refresh(r));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @Valid @RequestBody RefreshRequest r) {

        auth.logout(r);

        return ApiResponse.ok(null);
    }
}
