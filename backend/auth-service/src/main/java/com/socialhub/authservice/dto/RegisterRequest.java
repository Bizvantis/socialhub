package com.socialhub.authservice.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$", message = "3-30 chars: letters, digits, underscore") String username,
    @NotBlank @Size(min = 8, max = 72) String password) {}