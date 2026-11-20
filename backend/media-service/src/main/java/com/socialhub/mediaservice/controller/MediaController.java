package com.socialhub.mediaservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.AuthUser;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.mediaservice.dto.MediaDtos.*;
import com.socialhub.mediaservice.service.MediaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    /** Start an upload: returns a pre-signed PUT URL valid for 15 minutes. */
    @PostMapping("/uploads")
    public ResponseEntity<ApiResponse<UploadTicket>> createUpload(CurrentUser user,
                                                                   @Valid @RequestBody CreateUploadRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(media.createUpload(user, req)));
    }

    /** Finish an upload after the PUT to S3 succeeded. */
    @PostMapping("/{id}/complete")
    public ApiResponse<MediaResponse> complete(CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(media.complete(user, id));
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaResponse> get(@AuthUser(required = false) CurrentUser viewer, @PathVariable Long id) {
        return ApiResponse.ok(media.get(viewer, id));
    }

    /** Resolve many media ids to URLs in one call (feeds, carousels, profile grids). */
    @PostMapping("/batch")
    public ApiResponse<List<MediaResponse>> batch(@AuthUser(required = false) CurrentUser viewer,
                                                  @Valid @RequestBody BatchRequest req) {
        return ApiResponse.ok(media.batch(viewer, req.ids()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable Long id) {
        media.delete(user, id);
        return ApiResponse.ok("Deleted", null);
    }

    /** HLS playlists (master and variants) with segment URLs signed on the fly. */
    @GetMapping(value = "/{id}/hls/**", produces = "application/vnd.apple.mpegurl")
    public ResponseEntity<String> hls(@AuthUser(required = false) CurrentUser viewer, @PathVariable Long id,
                                      HttpServletRequest request) {
        String prefix = "/api/media/" + id + "/hls/";
        String file = request.getRequestURI().substring(request.getRequestURI().indexOf(prefix) + prefix.length());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                .header("Cache-Control", "private, max-age=60")
                .body(media.playlist(viewer, id, file));
    }
}
