package com.socialhub.postservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.postservice.dto.PostDtos.*;
import com.socialhub.postservice.service.StoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService stories;

    public StoryController(StoryService stories) {
        this.stories = stories;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoryResponse>> create(CurrentUser user, @Valid @RequestBody CreateStoryRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(stories.create(user, req)));
    }

    /** The stories tray at the top of the home screen. */
    @GetMapping("/feed")
    public ApiResponse<List<StoryRing>> tray(CurrentUser user) {
        return ApiResponse.ok(stories.tray(user));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<StoryResponse>> ofUser(CurrentUser user, @PathVariable Long userId) {
        return ApiResponse.ok(stories.ofUser(user, userId));
    }

    @PostMapping("/{id}/view")
    public ApiResponse<Void> view(CurrentUser user, @PathVariable Long id) {
        stories.view(user, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/viewers")
    public ApiResponse<List<StoryViewer>> viewers(CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(stories.viewers(user, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable Long id) {
        stories.delete(user, id);
        return ApiResponse.ok("Deleted", null);
    }
}
