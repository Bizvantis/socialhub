package com.socialhub.userservice.controller;

import com.socialhub.userservice.dto.*;
import com.socialhub.userservice.service.ProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profileService;

    // GET /api/users/me
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMe(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Username") String username) {

        ProfileResponse profile = profileService.getMe(userId, username);

        return ResponseEntity.ok(
                ApiResponse.success("Profile fetched successfully", profile));
    }

    // PUT /api/users/me
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Username") String username,
            @Valid @RequestBody UpdateProfileRequest request) {

        ProfileResponse profile = profileService.update(userId, username, request);

        return ResponseEntity.ok(
                ApiResponse.success("Profile updated successfully", profile));
    }

    // GET /api/users/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long viewerId) {

        ProfileResponse profile = profileService.getById(id, viewerId);

        return ResponseEntity.ok(
                ApiResponse.success("Profile fetched successfully", profile));
    }

    // POST /api/users/{id}/follow
    @PostMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long followerId,
            @RequestHeader("X-Username") String username) {

        profileService.follow(followerId, username, id);

        return ResponseEntity.ok(
                ApiResponse.<Void>success("Followed successfully", null));
    }

    // DELETE /api/users/{id}/follow
    @DeleteMapping("/{id}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long followerId) {

        profileService.unfollow(followerId, id);

        return ResponseEntity.ok(
                ApiResponse.<Void>success("Unfollowed successfully", null));
    }

    // GET /api/users/{id}/followers
    @GetMapping("/{id}/followers")
    public ResponseEntity<ApiResponse<PageResponse<ProfileResponse>>> followers(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long viewerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<ProfileResponse> result =
                profileService.followers(id, viewerId, page, size);

        return ResponseEntity.ok(
                ApiResponse.success("Followers fetched successfully", result));
    }

    // GET /api/users/{id}/following
    @GetMapping("/{id}/following")
    public ResponseEntity<ApiResponse<PageResponse<ProfileResponse>>> following(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long viewerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<ProfileResponse> result =
                profileService.following(id, viewerId, page, size);

        return ResponseEntity.ok(
                ApiResponse.success("Following fetched successfully", result));
    }
}