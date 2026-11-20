package com.socialhub.mediaservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.mediaservice.config.StorageProperties;
import com.socialhub.mediaservice.dto.MediaDtos.*;
import com.socialhub.mediaservice.entity.Media;
import com.socialhub.mediaservice.entity.MediaPurpose;
import com.socialhub.mediaservice.entity.MediaStatus;
import com.socialhub.mediaservice.repository.MediaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.*;

@Service
public class MediaService {

    private final MediaRepository repo;
    private final StorageService storage;
    private final StorageProperties props;
    private final OutboxWriter outbox;

    public MediaService(MediaRepository repo, StorageService storage, StorageProperties props, OutboxWriter outbox) {
        this.repo = repo;
        this.storage = storage;
        this.props = props;
        this.outbox = outbox;
    }

    /** Step 1 of an upload: validate, reserve a key, return a pre-signed PUT URL. */
    @Transactional
    public UploadTicket createUpload(CurrentUser user, CreateUploadRequest req) {
        MediaPurpose purpose = req.purpose();
        String contentType = req.contentType().toLowerCase(Locale.ROOT).trim();
        if (!purpose.allowedContentTypes().contains(contentType)) {
            throw ApiException.badRequest("Content type " + contentType + " not allowed for " + purpose
                    + "; allowed: " + purpose.allowedContentTypes());
        }
        if (req.sizeBytes() > purpose.maxBytes()) {
            throw ApiException.badRequest("File too large: max " + purpose.maxBytes() / (1024 * 1024) + " MB for " + purpose);
        }

        Media m = new Media();
        m.setOwnerId(user.id());
        m.setPurpose(purpose);
        m.setMediaType(purpose.mediaType());
        m.setContentType(contentType);
        m.setSizeBytes(req.sizeBytes());
        m.setOriginalFilename(req.fileName());
        m.setStatus(MediaStatus.PENDING);
        // Unguessable, per-owner prefix; all derived files (thumbnail, HLS) go next to the original.
        m.setObjectKey("u/%d/%s/%s/original.%s".formatted(user.id(), purpose.name().toLowerCase(Locale.ROOT),
                UUID.randomUUID(), purpose.extensionFor(contentType)));
        repo.save(m);

        String url = storage.presignUpload(m.getObjectKey(), contentType, req.sizeBytes()).toString();
        return new UploadTicket(m.getId(), url, "PUT", Map.of("Content-Type", contentType),
                Instant.now().plus(props.uploadUrlTtl()));
    }

    /**
     * Step 2: the client says "done". Never trust that: HeadObject checks the file really
     * exists with the declared size and type before the media becomes usable.
     * noRollbackFor: a mismatch must still commit the FAILED status (and its event) before
     * the 400 reaches the client.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MediaResponse complete(CurrentUser user, Long id) {
        Media m = load(id);
        user.requireOwnerOrAdmin(m.getOwnerId());
        if (m.getStatus() != MediaStatus.PENDING) {
            return toResponse(m, user); // idempotent: completing twice is fine
        }

        HeadObjectResponse head = storage.head(m.getObjectKey())
                .orElseThrow(() -> ApiException.badRequest("File has not been uploaded yet"));

        String actualType = Optional.ofNullable(head.contentType()).orElse("").toLowerCase(Locale.ROOT);
        if (head.contentLength() != m.getSizeBytes() || !actualType.startsWith(m.getContentType())) {
            fail(m, "Uploaded file does not match declared size/type");
            afterCommit(() -> storage.deletePrefix(m.keyPrefix()));
            throw ApiException.badRequest("Uploaded file does not match the declared size or content type");
        }

        if (m.getPurpose().needsProcessing()) {
            m.setStatus(MediaStatus.UPLOADED);
            outbox.publish(Topics.MEDIA_EVENTS, "MEDIA_UPLOADED", m.getId(), eventPayload(m));
        } else {
            m.setStatus(MediaStatus.READY);
            outbox.publish(Topics.MEDIA_EVENTS, "MEDIA_READY", m.getId(), eventPayload(m));
        }
        return toResponse(m, user);
    }

    @Transactional(readOnly = true)
    public MediaResponse get(CurrentUser viewer, Long id) {
        Media m = load(id);
        if (m.isRestricted() && !canSeeRestricted(viewer, m)) {
            throw ApiException.notFound("Media"); // 404, not 403: do not reveal it exists
        }
        return toResponse(m, viewer);
    }

    @Transactional(readOnly = true)
    public List<MediaResponse> batch(CurrentUser viewer, List<Long> ids) {
        return repo.findByIdIn(new LinkedHashSet<>(ids)).stream()
                .filter(m -> m.getStatus() != MediaStatus.DELETED)
                .filter(m -> !m.isRestricted() || canSeeRestricted(viewer, m))
                .map(m -> toResponse(m, viewer))
                .toList();
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Media m = load(id);
        user.requireOwnerOrAdmin(m.getOwnerId());
        m.setStatus(MediaStatus.DELETED);
        outbox.publish(Topics.MEDIA_EVENTS, "MEDIA_DELETED", m.getId(), eventPayload(m));
        // Delete files only once the DB change is committed.
        afterCommit(() -> storage.deletePrefix(m.keyPrefix()));
    }

    @Transactional(readOnly = true)
    public MediaInfo info(Long id) {
        Media m = repo.findById(id).orElseThrow(() -> ApiException.notFound("Media"));
        return new MediaInfo(m.getId(), m.getOwnerId(), m.getPurpose(), m.getMediaType(), m.getStatus(),
                m.getDurationSeconds());
    }

    /** HLS playlists reference segments by relative name; rewrite them into signed URLs. */
    @Transactional(readOnly = true)
    public String playlist(CurrentUser viewer, Long id, String file) {
        Media m = load(id);
        if (m.getStatus() != MediaStatus.READY || m.getPlaybackKey() == null) {
            throw ApiException.notFound("Playback");
        }
        if (m.isRestricted() && !canSeeRestricted(viewer, m)) {
            throw ApiException.notFound("Playback");
        }
        if (!file.matches("[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)?\\.m3u8")) {
            throw ApiException.badRequest("Invalid playlist name");
        }
        String hlsPrefix = m.keyPrefix() + "hls/";
        String key = hlsPrefix + file;
        String dir = key.substring(0, key.lastIndexOf('/') + 1);
        String body = new String(storage.read(key), java.nio.charset.StandardCharsets.UTF_8);

        StringBuilder out = new StringBuilder();
        for (String line : body.split("\r?\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                out.append(line).append('\n');
            } else if (line.endsWith(".m3u8")) {
                // Variant playlist: keep it routed through this endpoint (relative to file's dir).
                String rel = (dir + line).substring(hlsPrefix.length());
                out.append("/api/media/").append(id).append("/hls/").append(rel).append('\n');
            } else {
                out.append(storage.presignDownload(dir + line)).append('\n');
            }
        }
        return out.toString();
    }

    /** Called by the video-worker result consumer (Step 29). */
    @Transactional
    public void markProcessing(Long id) {
        repo.findById(id).ifPresent(m -> {
            if (m.getStatus() == MediaStatus.UPLOADED) m.setStatus(MediaStatus.PROCESSING);
        });
    }

    @Transactional
    public void markProcessed(Long id, String thumbnailKey, String playbackKey, Integer duration,
                              Integer width, Integer height) {
        Media m = repo.findById(id).orElse(null);
        if (m == null || m.getStatus() == MediaStatus.DELETED || m.getStatus() == MediaStatus.READY) return;
        m.setThumbnailKey(thumbnailKey);
        m.setPlaybackKey(playbackKey);
        m.setDurationSeconds(duration);
        m.setWidth(width);
        m.setHeight(height);
        m.setStatus(MediaStatus.READY);
        outbox.publish(Topics.MEDIA_EVENTS, "MEDIA_READY", m.getId(), eventPayload(m));
    }

    @Transactional
    public void markFailed(Long id, String reason) {
        repo.findById(id).ifPresent(m -> {
            if (m.getStatus() == MediaStatus.DELETED || m.getStatus() == MediaStatus.READY) return;
            fail(m, reason);
        });
    }

    @Transactional
    public void setRestricted(Collection<Long> ids, boolean restricted) {
        repo.findByIdIn(ids).forEach(m -> m.setRestricted(restricted));
    }

    // ------------------------------------------------------------------

    private void fail(Media m, String reason) {
        m.setStatus(MediaStatus.FAILED);
        m.setFailureReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
        outbox.publish(Topics.MEDIA_EVENTS, "MEDIA_FAILED", m.getId(), eventPayload(m));
    }

    private Media load(Long id) {
        Media m = repo.findById(id).orElseThrow(() -> ApiException.notFound("Media"));
        if (m.getStatus() == MediaStatus.DELETED) throw ApiException.notFound("Media");
        return m;
    }

    private static boolean canSeeRestricted(CurrentUser viewer, Media m) {
        return viewer != null && (viewer.id().equals(m.getOwnerId()) || viewer.isAdmin());
    }

    private MediaResponse toResponse(Media m, CurrentUser viewer) {
        boolean owner = viewer != null && (viewer.id().equals(m.getOwnerId()) || viewer.isAdmin());
        String url = null;
        if (m.getStatus() == MediaStatus.READY && m.getPurpose().mediaType() == com.socialhub.mediaservice.entity.MediaType.IMAGE) {
            url = storage.presignDownload(m.getObjectKey()).toString();
        } else if (owner && m.getStatus() != MediaStatus.PENDING && m.getStatus() != MediaStatus.FAILED) {
            url = storage.presignDownload(m.getObjectKey()).toString(); // originals of videos: owner only
        }
        String thumb = m.getThumbnailKey() == null ? null : storage.presignDownload(m.getThumbnailKey()).toString();
        String playback = m.getStatus() == MediaStatus.READY && m.getPlaybackKey() != null
                ? "/api/media/" + m.getId() + "/hls/master.m3u8" : null;
        return new MediaResponse(m.getId(), m.getOwnerId(), m.getPurpose(), m.getMediaType(), m.getContentType(),
                m.getSizeBytes(), m.getStatus(), url, thumb, playback, m.getDurationSeconds(), m.getWidth(),
                m.getHeight(), m.getCreatedAt());
    }

    private static Map<String, Object> eventPayload(Media m) {
        Map<String, Object> p = new HashMap<>();
        p.put("mediaId", m.getId());
        p.put("ownerId", m.getOwnerId());
        p.put("purpose", m.getPurpose().name());
        p.put("mediaType", m.getMediaType().name());
        p.put("contentType", m.getContentType());
        p.put("sizeBytes", m.getSizeBytes());
        p.put("objectKey", m.getObjectKey());
        p.put("status", m.getStatus().name());
        p.put("thumbnailKey", m.getThumbnailKey());
        p.put("durationSeconds", m.getDurationSeconds());
        return p;
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
