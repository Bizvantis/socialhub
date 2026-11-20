package com.socialhub.userservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.client.InternalApi;
import com.socialhub.common.web.ApiResponse;
import com.socialhub.userservice.exception.ApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Asks media-service whether an uploaded image may become this user's profile photo. */
@Component
public class MediaClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaInfo(Long id, Long ownerId, String purpose, String status) {}

    private static final ParameterizedTypeReference<ApiResponse<MediaInfo>> TYPE = new ParameterizedTypeReference<>() {};

    private final InternalApi api;

    public MediaClient(InternalApi api) {
        this.api = api;
    }

    public void requireAvatar(Long mediaId, Long ownerId) {
        MediaInfo m = api.get("http://media-service/internal/media/{id}", TYPE, mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Media " + mediaId + " not found"));
        if (!ownerId.equals(m.ownerId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Media " + mediaId + " is not yours");
        }
        if (!"AVATAR".equals(m.purpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Media " + mediaId + " is not a profile photo upload");
        }
        if (!"READY".equals(m.status()) && !"UPLOADED".equals(m.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Media " + mediaId + " is not uploaded (status " + m.status() + ")");
        }
    }
}
