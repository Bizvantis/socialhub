package com.socialhub.mediaservice.entity;

import java.util.Map;
import java.util.Set;

/**
 * What an upload is for. Each purpose fixes the allowed content types and the size limit,
 * so validation happens before a pre-signed URL is ever issued.
 */
public enum MediaPurpose {

    AVATAR(MediaType.IMAGE, mb(5)),
    BANNER(MediaType.IMAGE, mb(10)),
    POST_IMAGE(MediaType.IMAGE, mb(15)),
    STORY_IMAGE(MediaType.IMAGE, mb(10)),
    THUMBNAIL(MediaType.IMAGE, mb(5)),
    REEL(MediaType.VIDEO, mb(200)),
    STORY_VIDEO(MediaType.VIDEO, mb(100)),
    VIDEO(MediaType.VIDEO, mb(2048));

    private static final Map<MediaType, Map<String, String>> EXTENSIONS = Map.of(
            MediaType.IMAGE, Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp"),
            MediaType.VIDEO, Map.of("video/mp4", "mp4", "video/quicktime", "mov", "video/webm", "webm"));

    private final MediaType mediaType;
    private final long maxBytes;

    MediaPurpose(MediaType mediaType, long maxBytes) {
        this.mediaType = mediaType;
        this.maxBytes = maxBytes;
    }

    private static long mb(int n) {
        return n * 1024L * 1024L;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public Set<String> allowedContentTypes() {
        return EXTENSIONS.get(mediaType).keySet();
    }

    public String extensionFor(String contentType) {
        return EXTENSIONS.get(mediaType).get(contentType);
    }

    /** Videos need transcoding (thumbnail + HLS) before they are playable. */
    public boolean needsProcessing() {
        return mediaType == MediaType.VIDEO;
    }
}
