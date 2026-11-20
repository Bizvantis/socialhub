package com.socialhub.moderationservice;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.moderationservice.ModerationService.*;
import com.socialhub.moderationservice.TargetLookup.TargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ModerationController {

    public record ReportRequest(@NotNull TargetType targetType, @NotNull Long targetId, @NotNull Reason reason,
                                @Size(max = 1000) String details) {}

    public record ActionRequest(@NotNull TargetType targetType, @NotNull Long targetId, @Size(max = 500) String reason) {}

    public record UserActionRequest(@Size(max = 500) String reason) {}

    private final ModerationService moderation;

    public ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    // ---- any signed-in user ----------------------------------------------

    @PostMapping("/api/reports")
    public ResponseEntity<ApiResponse<Report>> report(CurrentUser user, @Valid @RequestBody ReportRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(moderation.report(user, r.targetType(), r.targetId(), r.reason(), r.details())));
    }

    @GetMapping("/api/reports/mine")
    public ApiResponse<CursorPage<Report>> mine(CurrentUser user, @RequestParam(required = false) Long cursor,
                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(moderation.mine(user, cursor, size));
    }

    // ---- ADMIN only (enforced in the service with requireRole) -------------

    @GetMapping("/api/admin/queue")
    public ApiResponse<List<QueueItem>> queue(CurrentUser admin, @RequestParam(required = false) String targetType,
                                              @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(moderation.queue(admin, targetType, limit));
    }

    @GetMapping("/api/admin/stats")
    public ApiResponse<Map<String, Object>> stats(CurrentUser admin) {
        return ApiResponse.ok(moderation.stats(admin));
    }

    @PostMapping("/api/admin/content/remove")
    public ApiResponse<Map<String, Object>> remove(CurrentUser admin, @Valid @RequestBody ActionRequest r) {
        return ApiResponse.ok(moderation.removeContent(admin, r.targetType(), r.targetId(), r.reason()));
    }

    @PostMapping("/api/admin/content/restore")
    public ApiResponse<Map<String, Object>> restore(CurrentUser admin, @Valid @RequestBody ActionRequest r) {
        return ApiResponse.ok(moderation.restoreContent(admin, r.targetType(), r.targetId(), r.reason()));
    }

    @PostMapping("/api/admin/reports/dismiss")
    public ApiResponse<Map<String, Object>> dismiss(CurrentUser admin, @Valid @RequestBody ActionRequest r) {
        return ApiResponse.ok(moderation.dismiss(admin, r.targetType(), r.targetId(), r.reason()));
    }

    @PostMapping("/api/admin/users/{id}/suspend")
    public ApiResponse<Map<String, Object>> suspend(CurrentUser admin, @PathVariable long id,
                                                    @Valid @RequestBody(required = false) UserActionRequest r) {
        return ApiResponse.ok(moderation.suspend(admin, id, r == null ? null : r.reason(), true));
    }

    @PostMapping("/api/admin/users/{id}/reinstate")
    public ApiResponse<Map<String, Object>> reinstate(CurrentUser admin, @PathVariable long id,
                                                      @Valid @RequestBody(required = false) UserActionRequest r) {
        return ApiResponse.ok(moderation.suspend(admin, id, r == null ? null : r.reason(), false));
    }

    @GetMapping("/api/admin/actions")
    public ApiResponse<CursorPage<Action>> actions(CurrentUser admin, @RequestParam(required = false) Long cursor,
                                                   @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(moderation.actions(admin, cursor, size));
    }
}
