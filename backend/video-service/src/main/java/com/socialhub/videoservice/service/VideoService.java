package com.socialhub.videoservice.service;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.PageResponse;
import com.socialhub.common.web.Pages;
import com.socialhub.videoservice.client.MediaClient;
import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.entity.Channel;
import com.socialhub.videoservice.entity.Enums.Category;
import com.socialhub.videoservice.entity.Enums.VideoStatus;
import com.socialhub.videoservice.entity.Video;
import com.socialhub.videoservice.repository.ChannelRepository;
import com.socialhub.videoservice.repository.RelationsDao;
import com.socialhub.videoservice.repository.VideoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class VideoService {

    private final VideoRepository videos;
    private final ChannelRepository channels;
    private final RelationsDao relations;
    private final MediaClient media;
    private final VideoEvents events;
    private final ViewCounter views;

    public VideoService(VideoRepository videos, ChannelRepository channels, RelationsDao relations,
                        MediaClient media, VideoEvents events, ViewCounter views) {
        this.videos = videos;
        this.channels = channels;
        this.relations = relations;
        this.media = media;
        this.events = events;
        this.views = views;
    }

    // ---- write side -----------------------------------------------------

    @Transactional
    public VideoResponse create(CurrentUser user, CreateVideoRequest req) {
        Channel channel = channels.findByOwnerId(user.id())
                .orElseThrow(() -> ApiException.badRequest("Create a channel before uploading videos"));
        MediaClient.MediaInfo file = media.requireAttachable(req.mediaId(), user.id(), "VIDEO");
        if (req.thumbnailMediaId() != null) {
            media.requireAttachable(req.thumbnailMediaId(), user.id(), "THUMBNAIL");
        }
        if (videos.findByMediaId(req.mediaId()).isPresent()) {
            throw ApiException.conflict("This upload is already attached to a video");
        }

        Video v = new Video();
        v.setChannel(channel);
        v.setOwnerId(user.id());
        v.setMediaId(req.mediaId());
        v.setThumbnailMediaId(req.thumbnailMediaId());
        v.setTitle(req.title().trim());
        v.setDescription(req.description());
        v.setTags(normalizeTags(req.tags()));
        v.setCategory(req.category());
        v.setVisibility(req.visibility());
        // The file may already be processed (fast worker) or still transcoding.
        if (file.ready()) {
            v.setStatus(VideoStatus.READY);
            v.setDurationSeconds(file.durationSeconds());
        } else {
            v.setStatus(VideoStatus.PROCESSING);
        }
        maybePublish(v);
        videos.saveAndFlush(v);
        channels.adjustVideos(channel.getId(), 1);

        if (v.getPublishedAt() != null) events.published(v);
        events.upserted(v);
        return VideoMapper.full(v, 0, null, false);
    }

    @Transactional
    public VideoResponse update(CurrentUser user, Long id, UpdateVideoRequest req) {
        Video v = load(id);
        user.requireOwnerOrAdmin(v.getOwnerId());
        if (req.title() != null) v.setTitle(req.title().trim());
        if (req.description() != null) v.setDescription(req.description());
        if (req.tags() != null) v.setTags(normalizeTags(req.tags()));
        if (req.category() != null) v.setCategory(req.category());
        if (req.visibility() != null) v.setVisibility(req.visibility());
        if (req.thumbnailMediaId() != null) {
            media.requireAttachable(req.thumbnailMediaId(), v.getOwnerId(), "THUMBNAIL");
            v.setThumbnailMediaId(req.thumbnailMediaId());
        }
        boolean firstPublish = maybePublish(v);
        if (firstPublish) events.published(v);
        events.upserted(v);
        return VideoMapper.full(v, views.pending(v.getId()), null, null);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Video v = load(id);
        user.requireOwnerOrAdmin(v.getOwnerId());
        v.setDeleted(true);
        channels.adjustVideos(v.getChannel().getId(), -1);
        events.deleted(v);
    }

    /**
     * Keeps the video's status in step with the uploaded file (media-service events).
     * Idempotency is handled by the caller (MediaEventsConsumer).
     */
    @Transactional
    public void onMediaProcessed(Long mediaId, boolean ready, Integer durationSeconds) {
        videos.findByMediaId(mediaId).ifPresent(v -> {
            if (v.getStatus() != VideoStatus.PROCESSING) return;
            v.setStatus(ready ? VideoStatus.READY : VideoStatus.FAILED);
            if (ready) v.setDurationSeconds(durationSeconds);
            boolean firstPublish = maybePublish(v);
            if (firstPublish) events.published(v);
            events.upserted(v);
        });
    }

    /** Moderation takedown / restore (Step 32). */
    @Transactional
    public void setRemoved(Long id, boolean removed) {
        videos.findById(id).ifPresent(v -> {
            v.setRemoved(removed);
            events.upserted(v);
        });
    }

    /** publishedAt is set exactly once: the first moment the video is publicly listed. */
    private boolean maybePublish(Video v) {
        if (v.getPublishedAt() == null && v.isListed()) {
            v.setPublishedAt(Instant.now());
            return true;
        }
        return false;
    }

    // ---- read side ------------------------------------------------------

    @Transactional(readOnly = true)
    public VideoResponse get(CurrentUser viewer, Long id) {
        Video v = load(id);
        boolean owner = viewer != null && (viewer.id().equals(v.getOwnerId()) || viewer.isAdmin());
        if (!owner && !v.isWatchableByOthers()) {
            throw ApiException.notFound("Video"); // 404 for private/removed: do not reveal existence
        }
        Integer resume = viewer == null ? null : relations.progress(viewer.id(), v.getId());
        Boolean later = viewer == null ? null : relations.inWatchLater(viewer.id(), v.getId());
        return VideoMapper.full(v, views.pending(v.getId()), resume, later);
    }

    @Transactional(readOnly = true)
    public PageResponse<VideoCard> list(Category category, String sort, int page, int size) {
        Sort order = "popular".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "viewCount").and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by(Sort.Direction.DESC, "publishedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        return cards(videos.findListed(category, Pages.of(page, size, order)));
    }

    @Transactional(readOnly = true)
    public PageResponse<VideoCard> channelVideos(CurrentUser viewer, Long channelId, int page, int size) {
        Channel c = channels.findById(channelId).orElseThrow(() -> ApiException.notFound("Channel"));
        boolean owner = viewer != null && (viewer.id().equals(c.getOwnerId()) || viewer.isAdmin());
        Sort order = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        return cards(videos.findByChannel(channelId, owner, Pages.of(page, size, order)));
    }

    @Transactional(readOnly = true)
    public List<VideoCard> related(Long id, int limit) {
        Video v = load(id);
        Collection<String> tags = v.getTags().isEmpty() ? List.of("\u0000") : v.getTags();
        List<Video> found = videos.findRelated(v.getId(), v.getCategory(), tags,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 30)));
        Map<Long, Long> pending = views.pending(found.stream().map(Video::getId).toList());
        return found.stream().map(x -> VideoMapper.card(x, pending.getOrDefault(x.getId(), 0L))).toList();
    }

    /** Ordered cards for a list of ids, hiding videos the viewer may no longer see. */
    @Transactional(readOnly = true)
    public List<VideoCard> cardsFor(CurrentUser viewer, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Video> byId = videos.findByIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Video::getId, Function.identity()));
        Map<Long, Long> pending = views.pending(byId.keySet());
        List<VideoCard> out = new ArrayList<>();
        for (Long id : ids) {
            Video v = byId.get(id);
            if (v == null) continue;
            boolean owner = viewer != null && viewer.id().equals(v.getOwnerId());
            if (owner || v.isWatchableByOthers()) out.add(VideoMapper.card(v, pending.getOrDefault(id, 0L)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public VideoInfo info(Long id) {
        Video v = videos.findById(id).orElseThrow(() -> ApiException.notFound("Video"));
        return new VideoInfo(v.getId(), v.getOwnerId(), v.getChannel().getId(), v.getTitle(), v.getVisibility(),
                v.getStatus(), v.isDeleted(), v.isRemoved());
    }

    /** Count a view and, for signed-in viewers, update watch history / resume position. */
    @Transactional
    public boolean recordView(CurrentUser viewer, String anonKey, Long id, Integer watchedSeconds) {
        Video v = load(id);
        boolean owner = viewer != null && viewer.id().equals(v.getOwnerId());
        if (!owner && !v.isWatchableByOthers()) throw ApiException.notFound("Video");
        if (v.getStatus() != VideoStatus.READY) return false;

        if (viewer != null) {
            relations.recordWatch(viewer.id(), v.getId(), watchedSeconds == null ? 0 : watchedSeconds);
        }
        String viewerKey = viewer != null ? "u" + viewer.id() : "a" + anonKey;
        return views.record(v.getId(), viewerKey, viewer == null ? null : viewer.id(), v.getChannel().getId());
    }

    // ---- helpers --------------------------------------------------------

    private PageResponse<VideoCard> cards(Page<Video> page) {
        Map<Long, Long> pending = views.pending(page.getContent().stream().map(Video::getId).toList());
        return PageResponse.from(page, v -> VideoMapper.card(v, pending.getOrDefault(v.getId(), 0L)));
    }

    private Video load(Long id) {
        return videos.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound("Video"));
    }

    private static Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) return new LinkedHashSet<>();
        return tags.stream().map(t -> t.trim().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
