package com.socialhub.postservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.AuthUser;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.postservice.dto.PostDtos.*;
import com.socialhub.postservice.entity.Post;
import com.socialhub.postservice.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PostController {

    private final PostService posts;

    public PostController(PostService posts) {
        this.posts = posts;
    }

    @PostMapping("/api/posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(CurrentUser user, @Valid @RequestBody CreatePostRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(posts.create(user, req)));
    }

    @GetMapping("/api/posts/{id}")
    public ApiResponse<PostResponse> get(CurrentUser viewer, @PathVariable Long id) {
        return ApiResponse.ok(posts.get(viewer, id));
    }

    @PatchMapping("/api/posts/{id}")
    public ApiResponse<PostResponse> update(CurrentUser user, @PathVariable Long id,
                                            @Valid @RequestBody UpdatePostRequest req) {
        return ApiResponse.ok(posts.update(user, id, req));
    }

    @DeleteMapping("/api/posts/{id}")
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable Long id) {
        posts.delete(user, id);
        return ApiResponse.ok("Deleted", null);
    }

    /** Profile grid; pass ?before={nextCursor} for the next page. */
    @GetMapping("/api/posts/user/{userId}")
    public ApiResponse<CursorPage<PostResponse>> byUser(CurrentUser viewer, @PathVariable Long userId,
                                                        @RequestParam(required = false) Long before,
                                                        @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(posts.byAuthor(viewer, userId, before, size));
    }

    /** Explore page: newest public posts; ?type=REEL for the reels tab. */
    @GetMapping("/api/posts/explore")
    public ApiResponse<CursorPage<PostResponse>> explore(@RequestParam(required = false) Post.Type type,
                                                         @RequestParam(required = false) Long before,
                                                         @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(posts.explore(type, before, size));
    }

    @GetMapping("/api/posts/mentions/me")
    public ApiResponse<CursorPage<PostResponse>> mentions(CurrentUser user, @RequestParam(required = false) Long before,
                                                          @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(posts.mentioning(user, before, size));
    }

    // ---- hashtags (public) ---------------------------------------------

    @GetMapping("/api/hashtags/trending")
    public ApiResponse<List<HashtagResponse>> trending(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(posts.trendingHashtags(limit));
    }

    @GetMapping("/api/hashtags/suggest")
    public ApiResponse<List<HashtagResponse>> suggest(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(posts.suggestHashtags(q, limit));
    }

    @GetMapping("/api/hashtags/{tag}/posts")
    public ApiResponse<CursorPage<PostResponse>> byTag(@AuthUser(required = false) CurrentUser viewer,
                                                       @PathVariable String tag,
                                                       @RequestParam(required = false) Long before,
                                                       @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(posts.byHashtag(tag, before, size));
    }
}
