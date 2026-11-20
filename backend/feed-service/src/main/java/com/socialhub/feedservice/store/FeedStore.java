package com.socialhub.feedservice.store;

import com.socialhub.feedservice.FeedProperties;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

/**
 * All Redis data structures of the feed, in one place.
 *
 * <pre>
 * g:followers:{user}   SET   who follows the user          (from USER_FOLLOWED/UNFOLLOWED)
 * g:following:{user}   SET   whom the user follows
 * g:subs:{channel}     SET   channel subscribers           (from CHANNEL_SUBSCRIBED/UNSUBSCRIBED)
 * g:chans:{user}       SET   channels the user subscribes to
 * tl:u:{author}        ZSET  the author's posts            score = created time (ms)
 * tl:c:{channel}       ZSET  the channel's videos
 * tl:global            ZSET  every public item (cold start for users who follow nobody)
 * feed:{user}          ZSET  fanned-out items for the user (fan-out on write)
 * item:{P:id|V:id}     STRING JSON snapshot used to render the card (TTL)
 * stats:{item}         HASH  likes / comments / shares (ranking signal)
 * aff:{user}           ZSET  authors the user interacts with (affinity signal)
 * evt:{eventId}        STRING idempotency marker for consumed events (TTL 7 days)
 * </pre>
 */
@Component
public class FeedStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final FeedProperties props;

    public FeedStore(StringRedisTemplate redis, ObjectMapper mapper, FeedProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.props = props;
    }

    // ---- idempotency ---------------------------------------------------

    /** SET NX with TTL: true the first time an event id is seen. */
    public boolean firstTime(String eventId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("evt:" + eventId, "1", Duration.ofDays(7)));
    }

    // ---- graph ---------------------------------------------------------

    public void follow(long follower, long followee) {
        redis.opsForSet().add("g:followers:" + followee, String.valueOf(follower));
        redis.opsForSet().add("g:following:" + follower, String.valueOf(followee));
    }

    public void unfollow(long follower, long followee) {
        redis.opsForSet().remove("g:followers:" + followee, String.valueOf(follower));
        redis.opsForSet().remove("g:following:" + follower, String.valueOf(followee));
    }

    public void subscribe(long user, long channel) {
        redis.opsForSet().add("g:subs:" + channel, String.valueOf(user));
        redis.opsForSet().add("g:chans:" + user, String.valueOf(channel));
    }

    public void unsubscribe(long user, long channel) {
        redis.opsForSet().remove("g:subs:" + channel, String.valueOf(user));
        redis.opsForSet().remove("g:chans:" + user, String.valueOf(channel));
    }

    public Set<String> followers(long user) { return members("g:followers:" + user); }
    public Set<String> following(long user) { return members("g:following:" + user); }
    public Set<String> subscribers(long channel) { return members("g:subs:" + channel); }
    public Set<String> channelsOf(long user) { return members("g:chans:" + user); }

    public long followerCount(long user) { return size("g:followers:" + user); }
    public long subscriberCount(long channel) { return size("g:subs:" + channel); }

    // ---- items & timelines ---------------------------------------------

    public void putItem(String key, Map<String, Object> snapshot) {
        redis.opsForValue().set("item:" + key, mapper.writeValueAsString(snapshot), props.itemTtl());
    }

    public void deleteItem(String key) {
        redis.delete(List.of("item:" + key, "stats:" + key));
    }

    public Map<String, Map<String, Object>> items(Collection<String> keys) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (keys.isEmpty()) return out;
        List<String> list = new ArrayList<>(keys);
        List<String> raw = redis.opsForValue().multiGet(list.stream().map(k -> "item:" + k).toList());
        for (int i = 0; i < list.size(); i++) {
            String json = raw == null ? null : raw.get(i);
            if (json != null) out.put(list.get(i), mapper.readValue(json, new TypeReference<Map<String, Object>>() {}));
        }
        return out;
    }

    public void addToTimeline(String timeline, String item, double score) {
        redis.opsForZSet().add(timeline, item, score);
        redis.opsForZSet().removeRange(timeline, 0, -(props.timelineSize() + 1));
    }

    public void removeFromTimeline(String timeline, String item) {
        redis.opsForZSet().remove(timeline, item);
    }

    /** Newest-first slice of a sorted set strictly older than {@code beforeScore}. */
    public List<TypedTuple<String>> newest(String zset, double beforeScore, int limit) {
        Set<TypedTuple<String>> s = redis.opsForZSet()
                .reverseRangeByScoreWithScores(zset, Double.NEGATIVE_INFINITY, beforeScore, 0, limit);
        return s == null ? List.of() : new ArrayList<>(s);
    }

    // ---- fan-out ---------------------------------------------------------

    /** Pipelined ZADD into many feeds, each trimmed to max size: one round trip per batch of 500. */
    public void fanOut(Collection<String> userIds, String item, double score) {
        if (userIds.isEmpty()) return;
        int max = props.maxFeedSize();
        List<String> ids = new ArrayList<>(userIds);
        for (int from = 0; from < ids.size(); from += 500) {
            List<String> batch = ids.subList(from, Math.min(ids.size(), from + 500));
            pipelined(ops -> {
                for (String u : batch) {
                    ops.opsForZSet().add("feed:" + u, item, score);
                    ops.opsForZSet().removeRange("feed:" + u, 0, -(max + 1));
                }
            });
        }
    }

    public void unFanOut(Collection<String> userIds, Collection<String> items) {
        if (userIds.isEmpty() || items.isEmpty()) return;
        Object[] arr = items.toArray();
        pipelined(ops -> {
            for (String u : userIds) ops.opsForZSet().remove("feed:" + u, arr);
        });
    }

    /**
     * Runs template operations inside one Redis pipeline (commands are queued and sent together).
     * SessionCallback is the version-independent way to do this with StringRedisTemplate.
     */
    private List<Object> pipelined(java.util.function.Consumer<RedisOperations<String, String>> body) {
        return redis.executePipelined(new SessionCallback<Object>() {
            @Override
            @SuppressWarnings("unchecked")
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                body.accept((RedisOperations<String, String>) operations);
                return null;
            }
        });
    }

    public void copyInto(String userId, List<TypedTuple<String>> entries) {
        if (entries.isEmpty()) return;
        Set<TypedTuple<String>> set = new HashSet<>(entries);
        redis.opsForZSet().add("feed:" + userId, set);
        redis.opsForZSet().removeRange("feed:" + userId, 0, -(props.maxFeedSize() + 1));
    }

    public List<String> timelineItems(String timeline, int limit) {
        Set<String> s = redis.opsForZSet().reverseRange(timeline, 0, limit - 1);
        return s == null ? List.of() : new ArrayList<>(s);
    }

    // ---- ranking signals -------------------------------------------------

    public void bumpStat(String item, String field, long delta) {
        redis.opsForHash().increment("stats:" + item, field, delta);
        redis.expire("stats:" + item, props.itemTtl());
    }

    public Map<String, Map<Object, Object>> stats(Collection<String> items) {
        List<String> list = new ArrayList<>(items);
        if (list.isEmpty()) return new HashMap<>();
        List<Object> raw = pipelined(ops -> {
            for (String i : list) ops.opsForHash().entries("stats:" + i);
        });
        Map<String, Map<Object, Object>> out = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            @SuppressWarnings("unchecked") Map<Object, Object> m = (Map<Object, Object>) raw.get(i);
            out.put(list.get(i), m == null ? Map.of() : m);
        }
        return out;
    }

    public void bumpAffinity(long user, long author, double delta) {
        if (user == author) return;
        redis.opsForZSet().incrementScore("aff:" + user, String.valueOf(author), delta);
        redis.expire("aff:" + user, Duration.ofDays(30));
    }

    public Map<String, Double> affinity(long user) {
        Set<TypedTuple<String>> s = redis.opsForZSet().reverseRangeWithScores("aff:" + user, 0, 199);
        Map<String, Double> out = new HashMap<>();
        if (s != null) s.forEach(t -> out.put(t.getValue(), t.getScore()));
        return out;
    }

    private Set<String> members(String key) {
        Set<String> s = redis.opsForSet().members(key);
        return s == null ? Set.of() : s;
    }

    private long size(String key) {
        Long n = redis.opsForSet().size(key);
        return n == null ? 0 : n;
    }
}
