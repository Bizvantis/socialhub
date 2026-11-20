package com.socialhub.postservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.postservice.client.MediaClient;
import com.socialhub.postservice.client.UserClient;
import com.socialhub.postservice.dto.PostDtos.*;
import com.socialhub.postservice.entity.Story;
import com.socialhub.postservice.repository.PostDao;
import com.socialhub.postservice.repository.StoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 24-hour stories.
 *
 * Expiry has two layers:
 *  - MySQL is the source of truth: every query filters on expires_at > now, so an expired
 *    story is invisible the moment it expires, even if a cleanup run is late.
 *  - Redis keeps the set of authors with an active story (stories:active, a sorted set scored by
 *    the latest expiry). Building the tray then touches only followees who actually have
 *    stories, instead of querying stories for all 1,000 people someone follows.
 *    ZREMRANGEBYSCORE drops authors whose stories have all expired.
 *  - A cleanup job hard-deletes stories 30 days after expiry (archive window).
 */
@Service
public class StoryService {

    private static final Logger log = LoggerFactory.getLogger(StoryService.class);
    private static final String ACTIVE = "stories:active";

    private final StoryRepository stories;
    private final PostDao dao;
    private final MediaClient media;
    private final UserClient users;
    private final StringRedisTemplate redis;
    private final OutboxWriter outbox;
    private final Duration ttl;

    public StoryService(StoryRepository stories, PostDao dao, MediaClient media, UserClient users,
                        StringRedisTemplate redis, OutboxWriter outbox,
                        @Value("${app.stories.ttl:24h}") Duration ttl) {
        this.stories = stories;
        this.dao = dao;
        this.media = media;
        this.users = users;
        this.redis = redis;
        this.outbox = outbox;
        this.ttl = ttl;
    }

    @Transactional
    public StoryResponse create(CurrentUser user, CreateStoryRequest req) {
        MediaClient.MediaInfo m = media.requireAttachable(req.mediaId(), user.id(), "STORY_IMAGE", "STORY_VIDEO");
        if (stories.findByMediaId(req.mediaId()).isPresent()) throw ApiException.conflict("Media already used in a story");

        Story s = new Story();
        s.setAuthorId(user.id());
        s.setMediaId(req.mediaId());
        s.setMediaType("STORY_VIDEO".equals(m.purpose()) ? "VIDEO" : "IMAGE");
        s.setCaption(req.caption());
        s.setReady(m.ready());
        s.setExpiresAt(Instant.now().plus(ttl));
        stories.saveAndFlush(s);
        if (s.isReady()) activate(s);
        return toResponse(s, false, 0);
    }

    /** Stories tray: my ring first, then followees with active stories, unseen rings first. */
    @Transactional(readOnly = true)
    public List<StoryRing> tray(CurrentUser user) {
        Instant now = Instant.now();
        redis.opsForZSet().removeRangeByScore(ACTIVE, 0, now.toEpochMilli());

        Set<Long> candidates = new LinkedHashSet<>();
        candidates.add(user.id());
        candidates.addAll(users.following(user.id()));

        // Keep only authors that Redis says have something active (cheap pipeline of ZSCOREs).
        List<Long> authorList = new ArrayList<>(candidates);
        List<Double> scores = redis.opsForZSet().score(ACTIVE, authorList.stream().map(String::valueOf).toArray());
        List<Long> active = new ArrayList<>();
        for (int i = 0; i < authorList.size(); i++) {
            if (scores != null && scores.get(i) != null) active.add(authorList.get(i));
        }
        if (active.isEmpty()) return List.of();

        List<Story> found = stories.findActiveByAuthors(active, now);
        Set<Long> seen = dao.seenStoryIds(user.id(), found.stream().map(Story::getId).toList());
        Map<Long, List<Story>> byAuthor = found.stream()
                .collect(Collectors.groupingBy(Story::getAuthorId, LinkedHashMap::new, Collectors.toList()));

        List<StoryRing> rings = new ArrayList<>();
        for (var e : byAuthor.entrySet()) {
            boolean mine = e.getKey().equals(user.id());
            List<StoryResponse> list = e.getValue().stream()
                    .map(s -> toResponse(s, seen.contains(s.getId()), mine ? s.getViewCount() : null)).toList();
            rings.add(new StoryRing(e.getKey(), list.stream().allMatch(StoryResponse::seen), list));
        }
        rings.sort(Comparator.comparing((StoryRing r) -> !r.authorId().equals(user.id()))
                .thenComparing(StoryRing::allSeen));
        return rings;
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> ofUser(CurrentUser viewer, Long authorId) {
        if (!viewer.id().equals(authorId) && !users.follows(viewer.id(), authorId)) {
            throw ApiException.forbidden("Follow this user to see their stories");
        }
        List<Story> found = stories.findActiveByAuthors(List.of(authorId), Instant.now());
        Set<Long> seen = dao.seenStoryIds(viewer.id(), found.stream().map(Story::getId).toList());
        boolean mine = viewer.id().equals(authorId);
        return found.stream().map(s -> toResponse(s, seen.contains(s.getId()), mine ? s.getViewCount() : null)).toList();
    }

    /** Idempotent: each viewer counts once per story; the author's own views are not counted. */
    @Transactional
    public void view(CurrentUser viewer, Long id) {
        Story s = stories.findById(id).orElseThrow(() -> ApiException.notFound("Story"));
        if (!s.isActive(Instant.now())) throw ApiException.notFound("Story");
        if (s.getAuthorId().equals(viewer.id())) return;
        if (!users.follows(viewer.id(), s.getAuthorId())) throw ApiException.notFound("Story");
        if (dao.markStoryViewed(s.getId(), viewer.id())) stories.incrementViews(s.getId());
    }

    @Transactional(readOnly = true)
    public List<StoryViewer> viewers(CurrentUser user, Long id) {
        Story s = stories.findById(id).orElseThrow(() -> ApiException.notFound("Story"));
        user.requireOwnerOrAdmin(s.getAuthorId());
        return dao.storyViewers(s.getId(), 500).stream().map(v -> new StoryViewer(v.viewerId(), v.viewedAt())).toList();
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Story s = stories.findById(id).orElseThrow(() -> ApiException.notFound("Story"));
        user.requireOwnerOrAdmin(s.getAuthorId());
        s.setDeleted(true);
    }

    @Transactional
    public void onMediaProcessed(Long mediaId, boolean ready) {
        stories.findByMediaId(mediaId).ifPresent(s -> {
            if (s.isReady() || s.isDeleted()) return;
            if (ready) {
                s.setReady(true);
                activate(s);
            } else {
                s.setDeleted(true);
            }
        });
    }

    private void activate(Story s) {
        // Score = the latest expiry among the author's stories (ZADD GT keeps the max).
        redis.opsForZSet().addIfAbsent(ACTIVE, String.valueOf(s.getAuthorId()), 0);
        Double current = redis.opsForZSet().score(ACTIVE, String.valueOf(s.getAuthorId()));
        double exp = s.getExpiresAt().toEpochMilli();
        if (current == null || current < exp) redis.opsForZSet().add(ACTIVE, String.valueOf(s.getAuthorId()), exp);

        Map<String, Object> p = new HashMap<>();
        p.put("storyId", s.getId());
        p.put("authorId", s.getAuthorId());
        p.put("expiresAt", s.getExpiresAt().toString());
        outbox.publish(Topics.POST_EVENTS, "STORY_CREATED", s.getId(), p);
    }

    @Scheduled(cron = "${app.stories.cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanup() {
        redis.opsForZSet().removeRangeByScore(ACTIVE, 0, Instant.now().toEpochMilli());
        int n = stories.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(30)));
        if (n > 0) log.info("Deleted {} archived stories", n);
    }

    private static StoryResponse toResponse(Story s, boolean seen, Integer viewCount) {
        return new StoryResponse(s.getId(), s.getAuthorId(), s.getMediaId(), s.getMediaType(), s.getCaption(),
                s.getCreatedAt(), s.getExpiresAt(), seen, viewCount);
    }
}
