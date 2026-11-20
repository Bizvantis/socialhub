package com.socialhub.moderationservice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.client.InternalApi;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

/** Finds the owner of reported/moderated content through the owning service's internal API. */
@Component
public class TargetLookup {

    public enum TargetType { VIDEO, POST, COMMENT, USER }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoInfo(Long id, Long ownerId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostInfo(Long id, Long authorId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommentInfo(Long id, Long authorId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserSummary(Long userId, String username) {}

    private final InternalApi api;

    public TargetLookup(InternalApi api) {
        this.api = api;
    }

    public long ownerOf(TargetType type, long id) {
        Long owner = switch (type) {
            case VIDEO -> api.get("http://video-service/internal/videos/{id}",
                    new ParameterizedTypeReference<ApiResponse<VideoInfo>>() {}, id).map(VideoInfo::ownerId).orElse(null);
            case POST -> api.get("http://post-service/internal/posts/{id}",
                    new ParameterizedTypeReference<ApiResponse<PostInfo>>() {}, id).map(PostInfo::authorId).orElse(null);
            case COMMENT -> api.get("http://engagement-service/internal/comments/{id}",
                    new ParameterizedTypeReference<ApiResponse<CommentInfo>>() {}, id).map(CommentInfo::authorId).orElse(null);
            case USER -> api.get("http://user-service/internal/users/batch?ids={id}",
                    new ParameterizedTypeReference<ApiResponse<List<UserSummary>>>() {}, id)
                    .filter(l -> !l.isEmpty()).map(l -> l.get(0).userId()).orElse(null);
        };
        if (owner == null) throw ApiException.notFound(type.name().charAt(0) + type.name().substring(1).toLowerCase());
        return owner;
    }
}
