package com.socialhub.postservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.postservice.dto.PostDtos.PostInfo;
import com.socialhub.postservice.service.PostService;
import org.springframework.web.bind.annotation.*;

/** Service-to-service API (Eureka only; blocked at the gateway). */
@RestController
@RequestMapping("/internal/posts")
public class InternalPostController {

    private final PostService posts;

    public InternalPostController(PostService posts) {
        this.posts = posts;
    }

    @GetMapping("/{id}")
    public ApiResponse<PostInfo> info(@PathVariable Long id) {
        return ApiResponse.ok(posts.info(id));
    }

    @PostMapping("/{id}/removed")
    public ApiResponse<Void> setRemoved(@PathVariable Long id, @RequestParam boolean value) {
        posts.setRemoved(id, value);
        return ApiResponse.ok(null);
    }
}
