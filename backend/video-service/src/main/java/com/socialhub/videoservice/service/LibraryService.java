package com.socialhub.videoservice.service;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.Pages;
import com.socialhub.videoservice.dto.VideoDtos.HistoryItem;
import com.socialhub.videoservice.dto.VideoDtos.VideoCard;
import com.socialhub.videoservice.dto.VideoDtos.WatchLaterItem;
import com.socialhub.videoservice.repository.RelationsDao;
import com.socialhub.videoservice.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The viewer's personal library: watch history (with resume position) and watch later. */
@Service
public class LibraryService {

    private final RelationsDao relations;
    private final VideoRepository videos;
    private final VideoService videoService;

    public LibraryService(RelationsDao relations, VideoRepository videos, VideoService videoService) {
        this.relations = relations;
        this.videos = videos;
        this.videoService = videoService;
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> history(CurrentUser user, int page, int size) {
        int s = Pages.clampSize(size);
        var rows = relations.history(user.id(), s, Math.max(page, 0) * s);
        Map<Long, VideoCard> cards = byId(user, rows.stream().map(RelationsDao.HistoryRow::videoId).toList());
        return rows.stream().filter(r -> cards.containsKey(r.videoId()))
                .map(r -> new HistoryItem(cards.get(r.videoId()), r.progressSeconds(), r.watchedAt())).toList();
    }

    @Transactional
    public void removeFromHistory(CurrentUser user, Long videoId) {
        relations.removeFromHistory(user.id(), videoId);
    }

    @Transactional
    public int clearHistory(CurrentUser user) {
        return relations.clearHistory(user.id());
    }

    @Transactional(readOnly = true)
    public List<WatchLaterItem> watchLater(CurrentUser user, int page, int size) {
        int s = Pages.clampSize(size);
        var rows = relations.watchLater(user.id(), s, Math.max(page, 0) * s);
        Map<Long, VideoCard> cards = byId(user, rows.stream().map(RelationsDao.LaterRow::videoId).toList());
        return rows.stream().filter(r -> cards.containsKey(r.videoId()))
                .map(r -> new WatchLaterItem(cards.get(r.videoId()), r.addedAt())).toList();
    }

    @Transactional
    public void addWatchLater(CurrentUser user, Long videoId) {
        var v = videos.findByIdAndDeletedFalse(videoId).orElseThrow(() -> ApiException.notFound("Video"));
        if (!v.isWatchableByOthers() && !v.getOwnerId().equals(user.id())) throw ApiException.notFound("Video");
        relations.addWatchLater(user.id(), videoId);
    }

    @Transactional
    public void removeWatchLater(CurrentUser user, Long videoId) {
        relations.removeWatchLater(user.id(), videoId);
    }

    private Map<Long, VideoCard> byId(CurrentUser user, List<Long> ids) {
        return videoService.cardsFor(user, ids).stream().collect(Collectors.toMap(VideoCard::id, Function.identity()));
    }
}
