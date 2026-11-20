package com.socialhub.postservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.client.InternalApi;
import com.socialhub.common.web.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * user-service internal API: mentions (@username -> id), follow checks for FOLLOWERS-only
 * content, and the following list for the stories tray.
 *
 * Follow checks are cached in Redis for 60 s: a post page is read far more often than
 * someone follows or unfollows, and a minute of staleness is an acceptable trade-off.
 */
@Component
public class UserClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserSummary(Long userId, String username, String displayName, Long avatarMediaId,
                              boolean privateAccount) {}

    private static final ParameterizedTypeReference<ApiResponse<List<UserSummary>>> USERS = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOL = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiResponse<List<Long>>> IDS = new ParameterizedTypeReference<>() {};

    private final InternalApi api;
    private final StringRedisTemplate redis;

    public UserClient(InternalApi api, StringRedisTemplate redis) {
        this.api = api;
        this.redis = redis;
    }

    public List<UserSummary> lookup(Collection<String> usernames) {
        if (usernames.isEmpty()) return List.of();
        return api.get("http://user-service/internal/users/lookup?usernames={u}", USERS, String.join(",", usernames))
                .orElse(List.of());
    }

    public boolean follows(long follower, long followee) {
        if (follower == followee) return true;
        String key = "follows:" + follower + ":" + followee;
        String cached = redis.opsForValue().get(key);
        if (cached != null) return "1".equals(cached);
        boolean f = api.get("http://user-service/internal/follows?follower={a}&followee={b}", BOOL, follower, followee)
                .orElse(false);
        redis.opsForValue().set(key, f ? "1" : "0", Duration.ofSeconds(60));
        return f;
    }

    /** Everyone the user follows (paged internally, capped at 5,000). */
    public List<Long> following(long userId) {
        List<Long> all = new ArrayList<>();
        long after = 0;
        while (all.size() < 5000) {
            List<Long> page = api.get("http://user-service/internal/users/{id}/following?after={a}&limit=1000",
                    IDS, userId, after).orElse(List.of());
            all.addAll(page);
            if (page.size() < 1000) break;
            after = page.get(page.size() - 1);
        }
        return all;
    }
}
