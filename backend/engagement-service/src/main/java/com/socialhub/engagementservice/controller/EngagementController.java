package com.socialhub.engagementservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.AuthUser;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.engagementservice.domain.Types.TargetType;
import com.socialhub.engagementservice.dto.EngagementDtos.*;
import com.socialhub.engagementservice.service.CommentService;
import com.socialhub.engagementservice.service.ReactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class EngagementController {

    private final CommentService comments;
    private final ReactionService reactions;

    public EngagementController(CommentService comments, ReactionService reactions) {
        this.comments = comments;
        this.reactions = reactions;
    }

    // ---- comments (Step 19) ----------------------------------------------

    @PostMapping("/api/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> comment(CurrentUser user,
                                                                @Valid @RequestBody CreateCommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(comments.create(user, req)));
    }

    /** ?targetType=VIDEO&targetId=1&sort=new|top&cursor=... */
    @GetMapping("/api/comments")
    public ApiResponse<CursorPage<CommentResponse>> list(@AuthUser(required = false) CurrentUser viewer,
                                                         @RequestParam TargetType targetType,
                                                         @RequestParam Long targetId,
                                                         @RequestParam(defaultValue = "new") String sort,
                                                         @RequestParam(required = false) String cursor,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(comments.list(viewer, targetType, targetId, sort, cursor, size));
    }

    @GetMapping("/api/comments/{id}/replies")
    public ApiResponse<CursorPage<CommentResponse>> replies(@AuthUser(required = false) CurrentUser viewer,
                                                            @PathVariable Long id,
                                                            @RequestParam(required = false) String cursor,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(comments.replies(viewer, id, cursor, size));
    }

    @PatchMapping("/api/comments/{id}")
    public ApiResponse<CommentResponse> edit(CurrentUser user, @PathVariable Long id,
                                             @Valid @RequestBody EditCommentRequest req) {
        return ApiResponse.ok(comments.edit(user, id, req));
    }

    @DeleteMapping("/api/comments/{id}")
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable Long id) {
        comments.delete(user, id);
        return ApiResponse.ok("Deleted", null);
    }

    // ---- reactions (Step 20) ---------------------------------------------

    @PutMapping("/api/reactions")
    public ApiResponse<Summary> react(CurrentUser user, @Valid @RequestBody ReactionRequest req) {
        return ApiResponse.ok(reactions.set(user, req));
    }

    @DeleteMapping("/api/reactions")
    public ApiResponse<Summary> unreact(CurrentUser user, @RequestParam TargetType targetType,
                                        @RequestParam Long targetId) {
        return ApiResponse.ok(reactions.remove(user, targetType, targetId));
    }

    /** Counts + my reaction + bookmarked, for up to 100 targets: ?targetType=POST&ids=1,2,3 */
    @GetMapping("/api/reactions/summary")
    public ApiResponse<List<Summary>> summary(@AuthUser(required = false) CurrentUser viewer,
                                              @RequestParam TargetType targetType, @RequestParam List<Long> ids) {
        return ApiResponse.ok(reactions.summaries(viewer, targetType, ids));
    }

    // ---- bookmarks & shares ----------------------------------------------

    @PostMapping("/api/bookmarks")
    public ApiResponse<Void> bookmark(CurrentUser user, @Valid @RequestBody BookmarkRequest req) {
        reactions.bookmark(user, req);
        return ApiResponse.ok("Saved", null);
    }

    @DeleteMapping("/api/bookmarks/{targetType}/{targetId}")
    public ApiResponse<Void> unbookmark(CurrentUser user, @PathVariable TargetType targetType, @PathVariable Long targetId) {
        reactions.unbookmark(user, targetType, targetId);
        return ApiResponse.ok("Removed", null);
    }

    @GetMapping("/api/bookmarks")
    public ApiResponse<CursorPage<BookmarkItem>> bookmarks(CurrentUser user,
                                                           @RequestParam(required = false) TargetType type,
                                                           @RequestParam(required = false) String cursor,
                                                           @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(reactions.bookmarks(user, type, cursor, size));
    }

    @PostMapping("/api/shares")
    public ApiResponse<Summary> share(CurrentUser user, @Valid @RequestBody ShareRequest req) {
        return ApiResponse.ok(reactions.share(user, req));
    }
}
