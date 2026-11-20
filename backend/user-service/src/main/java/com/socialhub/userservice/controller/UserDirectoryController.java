package com.socialhub.userservice.controller;

import com.socialhub.userservice.controller.InternalUserController.UserSummary;
import com.socialhub.userservice.dto.ApiResponse;
import com.socialhub.userservice.dto.ProfileResponse;
import com.socialhub.userservice.exception.ApiException;
import com.socialhub.userservice.repository.ProfileRepository;
import com.socialhub.userservice.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Directory lookups for the frontend: author cards in feeds and profile pages by @username. */
@RestController
@RequestMapping("/api/users")
public class UserDirectoryController {

    private final InternalUserController directory;
    private final ProfileRepository profiles;
    private final ProfileService profileService;

    public UserDirectoryController(InternalUserController directory, ProfileRepository profiles,
                                   ProfileService profileService) {
        this.directory = directory;
        this.profiles = profiles;
        this.profileService = profileService;
    }

    /** GET /api/users/batch?ids=1,2,3 (max 200): one call to render every author in a feed page. */
    @GetMapping("/batch")
    public ApiResponse<List<UserSummary>> batch(@RequestParam List<Long> ids) {
        return ApiResponse.success(directory.summaries(ids));
    }

    /** GET /api/users/by-username/{username}: profile page URLs use the username, not the id. */
    @GetMapping("/by-username/{username}")
    public ApiResponse<ProfileResponse> byUsername(@RequestHeader("X-User-Id") Long viewerId,
                                                   @PathVariable String username) {
        Long id = profiles.findIdByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Profile not found"));
        return ApiResponse.success(profileService.getById(viewerId, id));
    }
}
