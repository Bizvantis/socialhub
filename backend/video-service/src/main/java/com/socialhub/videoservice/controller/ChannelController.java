package com.socialhub.videoservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.AuthUser;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.PageResponse;
import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.service.ChannelService;
import com.socialhub.videoservice.service.PlaylistService;
import com.socialhub.videoservice.service.VideoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channels;
    private final VideoService videos;
    private final PlaylistService playlists;

    public ChannelController(ChannelService channels, VideoService videos, PlaylistService playlists) {
        this.channels = channels;
        this.videos = videos;
        this.playlists = playlists;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ChannelResponse>> create(CurrentUser user,
                                                               @Valid @RequestBody CreateChannelRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(channels.create(user, req)));
    }

    @GetMapping("/me")
    public ApiResponse<ChannelResponse> mine(CurrentUser user) {
        return ApiResponse.ok(channels.mine(user));
    }

    @GetMapping("/me/subscriptions")
    public ApiResponse<PageResponse<ChannelSummary>> subscriptions(CurrentUser user,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(channels.subscriptions(user, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChannelResponse> get(@AuthUser(required = false) CurrentUser viewer, @PathVariable Long id) {
        return ApiResponse.ok(channels.get(viewer, id));
    }

    @GetMapping("/handle/{handle}")
    public ApiResponse<ChannelResponse> byHandle(@AuthUser(required = false) CurrentUser viewer,
                                                 @PathVariable String handle) {
        return ApiResponse.ok(channels.byHandle(viewer, handle));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ChannelResponse> update(CurrentUser user, @PathVariable Long id,
                                               @Valid @RequestBody UpdateChannelRequest req) {
        return ApiResponse.ok(channels.update(user, id, req));
    }

    @PostMapping("/{id}/subscribe")
    public ApiResponse<ChannelResponse> subscribe(CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(channels.subscribe(user, id));
    }

    @DeleteMapping("/{id}/subscribe")
    public ApiResponse<ChannelResponse> unsubscribe(CurrentUser user, @PathVariable Long id) {
        return ApiResponse.ok(channels.unsubscribe(user, id));
    }

    @GetMapping("/{id}/videos")
    public ApiResponse<PageResponse<VideoCard>> videos(@AuthUser(required = false) CurrentUser viewer,
                                                       @PathVariable Long id,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "24") int size) {
        return ApiResponse.ok(videos.channelVideos(viewer, id, page, size));
    }

    @GetMapping("/{id}/playlists")
    public ApiResponse<PageResponse<PlaylistResponse>> playlists(@PathVariable Long id,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        ChannelResponse c = channels.get(null, id);
        return ApiResponse.ok(playlists.publicOf(c.ownerId(), page, size));
    }
}
