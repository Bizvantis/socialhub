package com.socialhub.videoservice.service;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.PageResponse;
import com.socialhub.common.web.Pages;
import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.entity.Enums.Visibility;
import com.socialhub.videoservice.entity.Playlist;
import com.socialhub.videoservice.repository.PlaylistRepository;
import com.socialhub.videoservice.repository.RelationsDao;
import com.socialhub.videoservice.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlaylistService {

    private static final int MAX_ITEMS = 5000;

    private final PlaylistRepository playlists;
    private final RelationsDao relations;
    private final VideoRepository videos;
    private final VideoService videoService;

    public PlaylistService(PlaylistRepository playlists, RelationsDao relations, VideoRepository videos,
                           VideoService videoService) {
        this.playlists = playlists;
        this.relations = relations;
        this.videos = videos;
        this.videoService = videoService;
    }

    @Transactional
    public PlaylistResponse create(CurrentUser user, CreatePlaylistRequest req) {
        Playlist p = new Playlist();
        p.setOwnerId(user.id());
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        p.setVisibility(req.visibility());
        playlists.saveAndFlush(p);
        return VideoMapper.playlist(p);
    }

    @Transactional
    public PlaylistResponse update(CurrentUser user, Long id, UpdatePlaylistRequest req) {
        Playlist p = owned(user, id);
        if (req.title() != null) p.setTitle(req.title().trim());
        if (req.description() != null) p.setDescription(req.description());
        if (req.visibility() != null) p.setVisibility(req.visibility());
        return VideoMapper.playlist(p);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        playlists.delete(owned(user, id));
    }

    @Transactional(readOnly = true)
    public PlaylistDetail get(CurrentUser viewer, Long id, int page, int size) {
        Playlist p = playlists.findById(id).orElseThrow(() -> ApiException.notFound("Playlist"));
        boolean owner = viewer != null && viewer.id().equals(p.getOwnerId());
        if (p.getVisibility() == Visibility.PRIVATE && !owner) throw ApiException.notFound("Playlist");
        int s = Pages.clampSize(size);
        List<Long> ids = relations.playlistVideoIds(p.getId(), s, Math.max(page, 0) * s);
        return new PlaylistDetail(VideoMapper.playlist(p), videoService.cardsFor(viewer, ids));
    }

    @Transactional(readOnly = true)
    public PageResponse<PlaylistResponse> mine(CurrentUser user, int page, int size) {
        return PageResponse.from(playlists.findByOwnerIdOrderByUpdatedAtDesc(user.id(), Pages.of(page, size)),
                VideoMapper::playlist);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlaylistResponse> publicOf(Long ownerId, int page, int size) {
        return PageResponse.from(playlists.findPublicByOwner(ownerId, Pages.of(page, size)), VideoMapper::playlist);
    }

    /** Idempotent: adding the same video twice keeps one entry (composite primary key). */
    @Transactional
    public PlaylistResponse add(CurrentUser user, Long id, Long videoId) {
        Playlist p = owned(user, id);
        var v = videos.findByIdAndDeletedFalse(videoId).orElseThrow(() -> ApiException.notFound("Video"));
        if (!v.isWatchableByOthers() && !v.getOwnerId().equals(user.id())) throw ApiException.notFound("Video");
        if (p.getVideoCount() >= MAX_ITEMS) throw ApiException.badRequest("Playlist is full");
        if (relations.addToPlaylist(p.getId(), videoId)) {
            playlists.adjustCount(p.getId(), 1);
            return withCount(p, p.getVideoCount() + 1);
        }
        return VideoMapper.playlist(p);
    }

    @Transactional
    public PlaylistResponse remove(CurrentUser user, Long id, Long videoId) {
        Playlist p = owned(user, id);
        if (relations.removeFromPlaylist(p.getId(), videoId)) {
            playlists.adjustCount(p.getId(), -1);
            return withCount(p, Math.max(0, p.getVideoCount() - 1));
        }
        return VideoMapper.playlist(p);
    }

    private static PlaylistResponse withCount(Playlist p, int count) {
        return new PlaylistResponse(p.getId(), p.getOwnerId(), p.getTitle(), p.getDescription(), p.getVisibility(),
                count, p.getUpdatedAt());
    }

    private Playlist owned(CurrentUser user, Long id) {
        Playlist p = playlists.findById(id).orElseThrow(() -> ApiException.notFound("Playlist"));
        user.requireOwnerOrAdmin(p.getOwnerId());
        return p;
    }
}
