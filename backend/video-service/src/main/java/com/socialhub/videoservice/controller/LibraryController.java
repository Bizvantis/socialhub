package com.socialhub.videoservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.AuthUser;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.PageResponse;
import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.service.LibraryService;
import com.socialhub.videoservice.service.PlaylistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Playlists, watch history and watch later (Step 23). */
@RestController
public class LibraryController {

    private final PlaylistService playlists;
    private final LibraryService library;

    public LibraryController(PlaylistService playlists, LibraryService library) {
        this.playlists = playlists;
        this.library = library;
    }

    // ---- playlists -----------------------------------------------------

    @PostMapping("/api/playlists")
    public ResponseEntity<ApiResponse<PlaylistResponse>> create(CurrentUser user,
                                                                @Valid @RequestBody CreatePlaylistRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(playlists.create(user, req)));
    }

    @GetMapping("/api/playlists/me")
    public ApiResponse<PageResponse<PlaylistResponse>> mine(CurrentUser user,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(playlists.mine(user, page, size));
    }

    @GetMapping("/api/playlists/{id}")
    public ApiResponse<PlaylistDetail> get(@AuthUser(required = false) CurrentUser viewer, @PathVariable Long id,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(playlists.get(viewer, id, page, size));
    }

    @PatchMapping("/api/playlists/{id}")
    public ApiResponse<PlaylistResponse> update(CurrentUser user, @PathVariable Long id,
                                                @Valid @RequestBody UpdatePlaylistRequest req) {
        return ApiResponse.ok(playlists.update(user, id, req));
    }

    @DeleteMapping("/api/playlists/{id}")
    public ApiResponse<Void> delete(CurrentUser user, @PathVariable Long id) {
        playlists.delete(user, id);
        return ApiResponse.ok("Deleted", null);
    }

    @PostMapping("/api/playlists/{id}/videos")
    public ApiResponse<PlaylistResponse> add(CurrentUser user, @PathVariable Long id,
                                             @Valid @RequestBody AddToPlaylistRequest req) {
        return ApiResponse.ok(playlists.add(user, id, req.videoId()));
    }

    @DeleteMapping("/api/playlists/{id}/videos/{videoId}")
    public ApiResponse<PlaylistResponse> remove(CurrentUser user, @PathVariable Long id, @PathVariable Long videoId) {
        return ApiResponse.ok(playlists.remove(user, id, videoId));
    }

    // ---- history -------------------------------------------------------

    @GetMapping("/api/history")
    public ApiResponse<List<HistoryItem>> history(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(library.history(user, page, size));
    }

    @DeleteMapping("/api/history/{videoId}")
    public ApiResponse<Void> removeFromHistory(CurrentUser user, @PathVariable Long videoId) {
        library.removeFromHistory(user, videoId);
        return ApiResponse.ok("Removed", null);
    }

    @DeleteMapping("/api/history")
    public ApiResponse<Map<String, Integer>> clearHistory(CurrentUser user) {
        return ApiResponse.ok(Map.of("removed", library.clearHistory(user)));
    }

    // ---- watch later ---------------------------------------------------

    @GetMapping("/api/watch-later")
    public ApiResponse<List<WatchLaterItem>> watchLater(CurrentUser user, @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(library.watchLater(user, page, size));
    }

    @PostMapping("/api/watch-later/{videoId}")
    public ApiResponse<Void> addWatchLater(CurrentUser user, @PathVariable Long videoId) {
        library.addWatchLater(user, videoId);
        return ApiResponse.ok("Saved", null);
    }

    @DeleteMapping("/api/watch-later/{videoId}")
    public ApiResponse<Void> removeWatchLater(CurrentUser user, @PathVariable Long videoId) {
        library.removeWatchLater(user, videoId);
        return ApiResponse.ok("Removed", null);
    }
}
