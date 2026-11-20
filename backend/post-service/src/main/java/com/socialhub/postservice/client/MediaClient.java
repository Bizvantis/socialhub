package com.socialhub.postservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.client.InternalApi;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * media-service's internal API, used synchronously only where the answer is needed to accept
 * a request: "does this media exist, belong to you, and has it finished uploading?"
 */
@Component
public class MediaClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaInfo(Long id, Long ownerId, String purpose, String mediaType, String status,
                            Integer durationSeconds) {
        public boolean ready() { return "READY".equals(status); }
        public boolean failed() { return "FAILED".equals(status) || "DELETED".equals(status); }
    }

    private static final ParameterizedTypeReference<ApiResponse<MediaInfo>> TYPE = new ParameterizedTypeReference<>() {};

    private final InternalApi api;

    public MediaClient(InternalApi api) {
        this.api = api;
    }

    public MediaInfo get(Long mediaId) {
        return api.get("http://media-service/internal/media/{id}", TYPE, mediaId)
                .orElseThrow(() -> ApiException.badRequest("Media " + mediaId + " not found"));
    }

    /** Validates that a media id may be attached by this owner for the given purpose(s). */
    public MediaInfo requireAttachable(Long mediaId, Long ownerId, String... purposes) {
        MediaInfo m = get(mediaId);
        if (!m.ownerId().equals(ownerId)) throw ApiException.forbidden("Media " + mediaId + " is not yours");
        if (purposes.length > 0 && !Arrays.asList(purposes).contains(m.purpose())) {
            throw ApiException.badRequest("Media " + mediaId + " has purpose " + m.purpose());
        }
        if ("PENDING".equals(m.status()) || m.failed()) {
            throw ApiException.badRequest("Media " + mediaId + " is not uploaded (status " + m.status() + ")");
        }
        return m;
    }
}
