package com.socialhub.videoservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.AuthUser;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.PageResponse;
import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.entity.Enums.Category;
import com.socialhub.videoservice.service.VideoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videos;

    public VideoController(VideoService videos) {
        this.videos = videos;
    }

    /** Publish an uploaded file as a video (upload goes to media-service first). */
    @PostMapping
    public ResponseEntity<ApiResponse<VideoResponse>> create(CurrentUser user,
                                                             @Valid @RequestBody CreateVideoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(videos.create(user, req)));
    }

    /** Public catalogue: ?category=MUSIC&sort=latest|popular */
    @GetMapping
    public ApiResponse<PageResponse<VideoCard>> list(@RequestParam(required = false) Category category,
                                                     @RequestParam(defaultValue = "latest") String sort,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(videos.list(category, sort, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<VideoResponse> get(@AuthUser(required = false) CurrentUser viewer, @PathVariable Long id) {
        return ApiResponse.ok(videos.get(viewer, id));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<VideoCard>> related(@PathVariable Long id, @RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.ok(videos.related(id, limit));
    }

    @PatchMapping("/{id}")
    public ApiResponse<VideoResponse> update(CurrentUser user, @PathVariable Long id,
                                             @Valid @RequestBody UpdateVideoRequest req) {
        return ApiResponse.ok(videos.update(user, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable Long id) {
        videos.delete(user, id);
        return ApiResponse.ok("Deleted", null);
    }

    /**
     * Called by the player after a few seconds of playback (and periodically with the
     * position, which powers "resume where you left off"). Anonymous viewers are deduplicated
     * by a hash of IP + User-Agent; the raw IP is never stored.
     */
    @PostMapping("/{id}/views")
    public ApiResponse<Map<String, Boolean>> view(@AuthUser(required = false) CurrentUser viewer,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody(required = false) ViewRequest req,
                                                  HttpServletRequest http) {
        boolean counted = videos.recordView(viewer, anonKey(http), id, req == null ? null : req.watchedSeconds());
        return ApiResponse.ok(Map.of("counted", counted));
    }

    private static String anonKey(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        String ip = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : http.getRemoteAddr();
        String ua = String.valueOf(http.getHeader("User-Agent"));
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((ip + "|" + ua).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h, 0, 12);
        } catch (Exception e) {
            return ip;
        }
    }
}
