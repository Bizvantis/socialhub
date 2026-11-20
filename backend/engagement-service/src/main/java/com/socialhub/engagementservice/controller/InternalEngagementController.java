package com.socialhub.engagementservice.controller;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.ApiResponse;
import com.socialhub.engagementservice.repository.CommentRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service API (Eureka only; blocked at the gateway). */
@RestController
@RequestMapping("/internal/comments")
public class InternalEngagementController {

    public record CommentInfo(Long id, Long authorId, String targetType, Long targetId, boolean deleted, boolean removed) {}

    private final CommentRepository comments;

    public InternalEngagementController(CommentRepository comments) {
        this.comments = comments;
    }

    @GetMapping("/{id}")
    public ApiResponse<CommentInfo> comment(@PathVariable Long id) {
        var c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Comment"));
        return ApiResponse.ok(new CommentInfo(c.getId(), c.getAuthorId(), c.getTargetType().name(), c.getTargetId(),
                c.isDeleted(), c.isRemoved()));
    }
}
