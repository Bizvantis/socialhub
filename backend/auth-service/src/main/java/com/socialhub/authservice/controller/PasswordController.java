package com.socialhub.authservice.controller;

import com.socialhub.authservice.dto.ApiResponse;
import com.socialhub.authservice.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordController {

    public record ForgotRequest(@NotBlank @Email String email) {}

    public record ResetRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 72) String newPassword) {}

    private final PasswordResetService resets;

    public PasswordController(PasswordResetService resets) {
        this.resets = resets;
    }

    /** Always 200: the response never reveals whether an account exists for the email. */
    @PostMapping("/forgot")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotRequest req) {
        resets.requestReset(req.email());
        return ApiResponse.ok(null);
    }

    @PostMapping("/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetRequest req) {
        resets.reset(req.token(), req.newPassword());
        return ApiResponse.ok(null);
    }
}
