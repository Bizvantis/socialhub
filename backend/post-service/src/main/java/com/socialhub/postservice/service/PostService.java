package com.socialhub.postservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.common.web.Pages;
import com.socialhub.postservice.client.MediaClient;
import com.socialhub.postservice.client.UserClient;
import com.socialhub.postservice.dto.PostDtos.*;
import com.socialhub.postservice.entity.Post;
import com.socialhub.postservice.repository.PostDao;
import com.socialhub.postservice.repository.PostRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository posts;
    private final PostDao dao;
    private final MediaClient media;
    private final UserClient users;
    private final OutboxWriter outbox;

    public PostService(PostRepository posts, PostDao dao, MediaClient media, UserClient users, OutboxWriter outbox) {
        this.posts = posts;
        this.dao = dao;
        this.media = media;
        this.users = users;
        this.outbox = outbox;
    }

    // ---- write side -----------------------------------------------------

    @Transactional
    public PostResponse create(CurrentUser user, CreatePostRequest req) {
        List<Long> mediaIds = req.mediaIds().stream().distinct().toList();
        boolean allReady = validateMedia(user, req.type(), mediaIds);

        Post p = new Post();
        p.setAuthorId(user.id());
        p.setType(req.type());
        p.setCaption(req.caption());
        p.setVisibility(req.visibility() == null ? Post.Visibility.PUBLIC : req.visibility());
        p.setStatus(allReady ? Post.Status.READY : Post.Status.PROCESSING);
        p.setMediaIds(new ArrayList<>(mediaIds));
        posts.saveAndFlush(p);

        Set<String> tags = TagParser.hashtags(req.caption());
        dao.linkHashtags(p.getId(), tags);
        List<Long> mentioned = resolveMentions(user, req.caption());
        dao.replaceMentions(p.getId(), mentioned);

        if (p.getStatus() == Post.Status.READY) announce(p, mentioned);
        upserted(p, tags, mentioned);
        return toResponse(p, new ArrayList<>(tags), mentioned);
    }

    @Transactional
    public PostResponse update(CurrentUser user, Long id, UpdatePostRequest req) {
        Post p = load(id);
        user.requireOwnerOrAdmin(p.getAuthorId());
        List<Long> newMentions = List.of();
        if (req.caption() != null) {
            List<Long> before = dao.mentionsOf(p.getId());
            p.setCaption(req.caption());
            p.setEditedAt(Instant.now());
            dao.unlinkHashtags(p.getId());
            dao.linkHashtags(p.getId(), TagParser.hashtags(req.caption()));
            List<Long> mentioned = resolveMentions(user, req.caption());
            dao.replaceMentions(p.getId(), mentioned);
            newMentions = mentioned.stream().filter(m -> !before.contains(m)).toList();
        }
        if (req.visibility() != null) p.setVisibility(req.visibility());

        // Notify only people newly mentioned by the edit, never re-notify old mentions.
        if (p.getStatus() == Post.Status.READY) mentionEvents(p, newMentions);
        List<String> tags = dao.hashtagsOf(p.getId());
        List<Long> mentions = dao.mentionsOf(p.getId());
        upserted(p, new LinkedHashSet<>(tags), mentions);
        return toResponse(p, tags, mentions);
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        Post p = load(id);
        user.requireOwnerOrAdmin(p.getAuthorId());
        p.setDeleted(true);
        dao.unlinkHashtags(p.getId());
        outbox.publish(Topics.POST_EVENTS, "POST_DELETED", p.getId(), snapshot(p, List.of(), List.of()));
    }

    /** Reel transcoding finished (media-service event, looked up by the media id). */
    @Transactional
    public void onReelProcessed(Long mediaId, boolean ready) {
        dao.postIdByMedia(mediaId).flatMap(posts::findById).ifPresent(p -> {
            if (p.getStatus() != Post.Status.PROCESSING) return;
            p.setStatus(ready ? Post.Status.READY : Post.Status.FAILED);
            List<String> tags = dao.hashtagsOf(p.getId());
            List<Long> mentions = dao.mentionsOf(p.getId());
            if (ready) announce(p, mentions);
            upserted(p, new LinkedHashSet<>(tags), mentions);
        });
    }

    @Transactional
    public void setRemoved(Long id, boolean removed) {
        posts.findById(id).ifPresent(p -> {
            p.setRemoved(removed);
            upserted(p, new LinkedHashSet<>(dao.hashtagsOf(p.getId())), dao.mentionsOf(p.getId()));
        });
    }

    // ---- read side ------------------------------------------------------

    @Transactional(readOnly = true)
    public PostResponse get(CurrentUser viewer, Long id) {
        Post p = load(id);
        if (!canView(viewer, p)) throw ApiException.notFound("Post");
        return toResponse(p, dao.hashtagsOf(p.getId()), dao.mentionsOf(p.getId()));
    }

    /** Profile grid: FOLLOWERS-only posts appear only for followers and the author. */
    @Transactional(readOnly = true)
    public CursorPage<PostResponse> byAuthor(CurrentUser viewer, Long authorId, Long before, int size) {
        boolean owner = viewer != null && (viewer.id().equals(authorId) || viewer.isAdmin());
        boolean follower = owner || (viewer != null && users.follows(viewer.id(), authorId));
        int s = Pages.clampSize(size);
        List<Post> found = posts.findByAuthor(authorId, owner, follower, cursor(before), PageRequest.of(0, s));
        return page(found, s);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> explore(Post.Type type, Long before, int size) {
        int s = Pages.clampSize(size);
        return page(posts.findPublic(type, cursor(before), PageRequest.of(0, s)), s);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> byHashtag(String tag, Long before, int size) {
        int s = Pages.clampSize(size);
        Long tagId = dao.hashtagId(tag.toLowerCase(Locale.ROOT).replace("#", "")).orElse(null);
        if (tagId == null) return CursorPage.of(List.of(), null);
        return page(loadOrdered(dao.postIdsByHashtag(tagId, cursor(before), s)), s);
    }

    @Transactional(readOnly = true)
    public CursorPage<PostResponse> mentioning(CurrentUser user, Long before, int size) {
        int s = Pages.clampSize(size);
        List<Post> found = loadOrdered(dao.postIdsMentioning(user.id(), cursor(before), s)).stream()
                .filter(p -> canView(user, p)).toList();
        return page(found, s);
    }

    @Transactional(readOnly = true)
    public List<HashtagResponse> trendingHashtags(int limit) {
        return dao.topHashtags(Math.min(Math.max(limit, 1), 50)).stream()
                .map(t -> new HashtagResponse(t.tag(), t.postCount())).toList();
    }

    @Transactional(readOnly = true)
    public List<HashtagResponse> suggestHashtags(String prefix, int limit) {
        String p = prefix.toLowerCase(Locale.ROOT).replace("#", "").trim();
        if (p.isEmpty()) return List.of();
        return dao.hashtagsByPrefix(p, Math.min(Math.max(limit, 1), 20)).stream()
                .map(t -> new HashtagResponse(t.tag(), t.postCount())).toList();
    }

    @Transactional(readOnly = true)
    public PostInfo info(Long id) {
        Post p = posts.findById(id).orElseThrow(() -> ApiException.notFound("Post"));
        return new PostInfo(p.getId(), p.getAuthorId(), p.getType(), p.getVisibility(), p.getStatus(),
                p.isDeleted(), p.isRemoved());
    }

    // ---- rules ------------------------------------------------------------

    /**
     * IMAGE: exactly 1 POST_IMAGE. CAROUSEL: 2-10 POST_IMAGEs. REEL: exactly 1 REEL video.
     * Every media item must belong to the author and be uploaded.
     * @return true if all media are already READY (images are; reels may still be transcoding)
     */
    private boolean validateMedia(CurrentUser user, Post.Type type, List<Long> ids) {
        switch (type) {
            case IMAGE, REEL -> { if (ids.size() != 1) throw ApiException.badRequest(type + " needs exactly 1 media item"); }
            case CAROUSEL -> { if (ids.size() < 2 || ids.size() > 10) throw ApiException.badRequest("CAROUSEL needs 2-10 images"); }
        }
        String purpose = type == Post.Type.REEL ? "REEL" : "POST_IMAGE";
        boolean allReady = true;
        for (Long id : ids) {
            MediaClient.MediaInfo m = media.requireAttachable(id, user.id(), purpose);
            allReady &= m.ready();
        }
        return allReady;
    }

    private boolean canView(CurrentUser viewer, Post p) {
        boolean owner = viewer != null && (viewer.id().equals(p.getAuthorId()) || viewer.isAdmin());
        if (owner) return !p.isDeleted();
        if (!p.isVisibleState()) return false;
        if (p.getVisibility() == Post.Visibility.PUBLIC) return true;
        return viewer != null && users.follows(viewer.id(), p.getAuthorId());
    }

    private List<Long> resolveMentions(CurrentUser author, String caption) {
        Set<String> names = TagParser.mentions(caption);
        if (names.isEmpty()) return List.of();
        return users.lookup(names).stream().map(UserClient.UserSummary::userId)
                .filter(id -> !id.equals(author.id())).distinct().toList();
    }

    // ---- events -------------------------------------------------------------

    /** First time a post becomes visible: drives the feed (POST_CREATED) and mention notifications. */
    private void announce(Post p, List<Long> mentioned) {
        outbox.publish(Topics.POST_EVENTS, "POST_CREATED", p.getId(), snapshot(p, dao.hashtagsOf(p.getId()), mentioned));
        mentionEvents(p, mentioned);
    }

    private void mentionEvents(Post p, List<Long> mentioned) {
        for (Long userId : mentioned) {
            Map<String, Object> m = new HashMap<>();
            m.put("postId", p.getId());
            m.put("authorId", p.getAuthorId());
            m.put("mentionedUserId", userId);
            outbox.publish(Topics.POST_EVENTS, "USER_MENTIONED", p.getId(), m);
        }
    }

    private void upserted(Post p, Set<String> tags, List<Long> mentions) {
        outbox.publish(Topics.POST_EVENTS, "POST_UPSERTED", p.getId(), snapshot(p, new ArrayList<>(tags), mentions));
    }

    private static Map<String, Object> snapshot(Post p, List<String> tags, List<Long> mentions) {
        Map<String, Object> m = new HashMap<>();
        m.put("postId", p.getId());
        m.put("authorId", p.getAuthorId());
        m.put("type", p.getType().name());
        m.put("caption", p.getCaption());
        m.put("hashtags", tags);
        m.put("mentionedUserIds", mentions);
        m.put("mediaIds", new ArrayList<>(p.getMediaIds()));
        m.put("visibility", p.getVisibility().name());
        m.put("status", p.getStatus().name());
        m.put("listed", p.isVisibleState() && p.getVisibility() == Post.Visibility.PUBLIC);
        m.put("deleted", p.isDeleted());
        m.put("removed", p.isRemoved());
        m.put("createdAt", p.getCreatedAt() == null ? Instant.now().toString() : p.getCreatedAt().toString());
        m.put("version", System.currentTimeMillis());
        return m;
    }

    // ---- helpers ------------------------------------------------------------

    private Post load(Long id) {
        return posts.findByIdAndDeletedFalse(id).orElseThrow(() -> ApiException.notFound("Post"));
    }

    private List<Post> loadOrdered(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, Post> byId = posts.findByIdInAndDeletedFalse(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private CursorPage<PostResponse> page(List<Post> found, int size) {
        List<Long> ids = found.stream().map(Post::getId).toList();
        Map<Long, List<String>> tags = dao.hashtagsOf(ids);
        Map<Long, List<Long>> mentions = dao.mentionsOf(ids);
        List<PostResponse> items = found.stream()
                .map(p -> toResponse(p, tags.getOrDefault(p.getId(), List.of()), mentions.getOrDefault(p.getId(), List.of())))
                .toList();
        String next = found.size() == size ? String.valueOf(found.get(found.size() - 1).getId()) : null;
        return CursorPage.of(items, next);
    }

    private static long cursor(Long before) {
        return before == null || before <= 0 ? Long.MAX_VALUE : before;
    }

    static PostResponse toResponse(Post p, List<String> tags, List<Long> mentions) {
        return new PostResponse(p.getId(), p.getAuthorId(), p.getType(), p.getCaption(), new ArrayList<>(p.getMediaIds()),
                tags, mentions, p.getVisibility(), p.getStatus(), p.getEditedAt(), p.getCreatedAt());
    }
}
