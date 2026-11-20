package com.socialhub.engagementservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.client.InternalApi;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.engagementservice.domain.Comment;
import com.socialhub.engagementservice.domain.Types.TargetType;
import com.socialhub.engagementservice.repository.CommentRepository;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Answers "does this video/post/comment exist, who owns it, and may this viewer interact
 * with it?" by asking the owning service. Results are cached in Redis for 30 s because
 * likes are the hottest write path in the system and the answer rarely changes.
 */
@Component
public class TargetResolver {

    public record Target(TargetType type, long id, long ownerId, String visibility, boolean live) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VideoInfo(Long id, Long ownerId, String visibility, String status, boolean deleted, boolean removed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PostInfo(Long id, Long authorId, String visibility, String status, boolean deleted, boolean removed) {}

    private static final ParameterizedTypeReference<ApiResponse<VideoInfo>> VIDEO = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<PostInfo>> POST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOL = new ParameterizedTypeReference<>() {};

    private final InternalApi api;
    private final StringRedisTemplate redis;
    private final CommentRepository comments;

    public TargetResolver(InternalApi api, StringRedisTemplate redis, CommentRepository comments) {
        this.api = api;
        this.redis = redis;
        this.comments = comments;
    }

    /** Resolves a target the viewer is allowed to see, or 404 (never reveals private content). */
    public Target require(TargetType type, long id, CurrentUser viewer) {
        Target t = resolve(type, id);
        if (t == null || !canAccess(t, viewer)) throw ApiException.notFound(type.name().charAt(0)
                + type.name().substring(1).toLowerCase());
        return t;
    }

    public Target resolve(TargetType type, long id) {
        if (type == TargetType.COMMENT) {
            Comment c = comments.findById(id).orElse(null);
            return c == null ? null : new Target(type, id, c.getAuthorId(), "PUBLIC", c.isVisible());
        }
        String key = "target:" + type + ":" + id;
        String cached = redis.opsForValue().get(key);
        if (cached != null) return cached.isEmpty() ? null : decode(type, id, cached);

        Target t = switch (type) {
            case VIDEO -> api.get("http://video-service/internal/videos/{id}", VIDEO, id)
                    .map(v -> new Target(type, id, v.ownerId(), v.visibility(),
                            "READY".equals(v.status()) && !v.deleted() && !v.removed()))
                    .orElse(null);
            case POST -> api.get("http://post-service/internal/posts/{id}", POST, id)
                    .map(p -> new Target(type, id, p.authorId(), p.visibility(),
                            "READY".equals(p.status()) && !p.deleted() && !p.removed()))
                    .orElse(null);
            default -> null;
        };
        redis.opsForValue().set(key, t == null ? "" : t.ownerId() + "|" + t.visibility() + "|" + t.live(),
                Duration.ofSeconds(30));
        return t;
    }

    private boolean canAccess(Target t, CurrentUser viewer) {
        boolean owner = viewer != null && (viewer.id() == t.ownerId() || viewer.isAdmin());
        if (owner) return true;
        if (!t.live()) return false;
        return switch (t.visibility()) {
            case "PRIVATE" -> false;                                  // private video
            case "FOLLOWERS" -> viewer != null && follows(viewer.id(), t.ownerId());
            default -> true;                                          // PUBLIC, UNLISTED
        };
    }

    private boolean follows(long follower, long followee) {
        return api.get("http://user-service/internal/follows?follower={a}&followee={b}", BOOL, follower, followee)
                .orElse(false);
    }

    /** Called when an event says the target changed (e.g. made private, deleted). */
    public void evict(TargetType type, long id) {
        redis.delete("target:" + type + ":" + id);
    }

    private static Target decode(TargetType type, long id, String s) {
        String[] p = s.split("\\|");
        return new Target(type, id, Long.parseLong(p[0]), p[1], Boolean.parseBoolean(p[2]));
    }
}
