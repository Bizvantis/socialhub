package com.socialhub.engagementservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.common.web.Pages;
import com.socialhub.engagementservice.domain.Comment;
import com.socialhub.engagementservice.domain.Types.ReactionKind;
import com.socialhub.engagementservice.domain.Types.ShareChannel;
import com.socialhub.engagementservice.domain.Types.TargetType;
import com.socialhub.engagementservice.dto.EngagementDtos.*;
import com.socialhub.engagementservice.repository.CommentRepository;
import com.socialhub.engagementservice.repository.EngagementDao;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Likes/dislikes, bookmarks, shares and the per-target summary used by every card. */
@Service
public class ReactionService {

    private final EngagementDao dao;
    private final CommentRepository comments;
    private final TargetResolver targets;
    private final OutboxWriter outbox;

    public ReactionService(EngagementDao dao, CommentRepository comments, TargetResolver targets, OutboxWriter outbox) {
        this.dao = dao;
        this.comments = comments;
        this.targets = targets;
        this.outbox = outbox;
    }

    /**
     * Set (or switch) the viewer's reaction. Idempotent: liking twice changes nothing.
     * Switching LIKE -> DISLIKE moves one count from likes to dislikes atomically.
     */
    @Transactional
    public Summary set(CurrentUser user, ReactionRequest req) {
        if (req.kind() == ReactionKind.DISLIKE && req.targetType() != TargetType.VIDEO) {
            throw ApiException.badRequest("Dislike is only available on videos");
        }
        TargetResolver.Target target = targets.require(req.targetType(), req.targetId(), user);

        Optional<ReactionKind> previous = dao.reactionOf(user.id(), req.targetType(), req.targetId());
        if (previous.isPresent() && previous.get() == req.kind()) {
            return summary(user, req.targetType(), req.targetId());
        }
        try {
            if (previous.isEmpty()) dao.insertReaction(user.id(), req.targetType(), req.targetId(), req.kind());
            else dao.changeReaction(user.id(), req.targetType(), req.targetId(), req.kind());
        } catch (DuplicateKeyException race) {
            // A concurrent request from the same user inserted first: treat as done.
            return summary(user, req.targetType(), req.targetId());
        }
        previous.ifPresent(old -> count(req.targetType(), req.targetId(), old, -1));
        count(req.targetType(), req.targetId(), req.kind(), 1);

        Map<String, Object> p = payload(user, target);
        p.put("kind", req.kind().name());
        p.put("previous", previous.map(Enum::name).orElse(null));
        outbox.publish(Topics.ENGAGEMENT_EVENTS, "REACTION_SET", req.targetId(), p);
        return summary(user, req.targetType(), req.targetId());
    }

    @Transactional
    public Summary remove(CurrentUser user, TargetType type, Long targetId) {
        Optional<ReactionKind> previous = dao.reactionOf(user.id(), type, targetId);
        if (previous.isPresent() && dao.deleteReaction(user.id(), type, targetId)) {
            count(type, targetId, previous.get(), -1);
            TargetResolver.Target t = targets.resolve(type, targetId);
            Map<String, Object> p = t == null ? new HashMap<>() : payload(user, t);
            p.put("targetType", type.name());
            p.put("targetId", targetId);
            p.put("kind", previous.get().name());
            outbox.publish(Topics.ENGAGEMENT_EVENTS, "REACTION_REMOVED", targetId, p);
        }
        return summary(user, type, targetId);
    }

    private void count(TargetType type, long targetId, ReactionKind kind, int delta) {
        if (type == TargetType.COMMENT) {
            if (kind == ReactionKind.LIKE) comments.adjustLikes(targetId, delta);
        } else {
            dao.bump(type, targetId, kind == ReactionKind.LIKE ? "likes" : "dislikes", delta);
        }
    }

    // ---- bookmarks & shares ----------------------------------------------

    @Transactional
    public void bookmark(CurrentUser user, BookmarkRequest req) {
        if (!req.targetType().bookmarkable()) throw ApiException.badRequest("Cannot save " + req.targetType());
        targets.require(req.targetType(), req.targetId(), user);
        dao.addBookmark(user.id(), req.targetType(), req.targetId());
    }

    @Transactional
    public void unbookmark(CurrentUser user, TargetType type, Long targetId) {
        dao.removeBookmark(user.id(), type, targetId);
    }

    /** Saved items, newest first; cursor = epoch millis of the last item's save time. */
    @Transactional(readOnly = true)
    public CursorPage<BookmarkItem> bookmarks(CurrentUser user, TargetType type, String cursor, int size) {
        int s = Pages.clampSize(size);
        Instant before = cursor == null ? Instant.now().plusSeconds(60) : Instant.ofEpochMilli(Long.parseLong(cursor));
        var rows = dao.bookmarks(user.id(), type, before, s);
        String next = rows.size() == s ? String.valueOf(rows.get(rows.size() - 1).savedAt().toEpochMilli()) : null;
        return CursorPage.of(rows.stream().map(r -> new BookmarkItem(r.type(), r.targetId(), r.savedAt())).toList(), next);
    }

    @Transactional
    public Summary share(CurrentUser user, ShareRequest req) {
        if (req.targetType() == TargetType.COMMENT) throw ApiException.badRequest("Cannot share a comment");
        TargetResolver.Target target = targets.require(req.targetType(), req.targetId(), user);
        String channel = (req.channel() == null ? ShareChannel.LINK : req.channel()).name();
        dao.insertShare(user.id(), req.targetType(), req.targetId(), channel);
        dao.bump(req.targetType(), req.targetId(), "shares", 1);
        Map<String, Object> p = payload(user, target);
        p.put("channel", channel);
        outbox.publish(Topics.ENGAGEMENT_EVENTS, "SHARED", req.targetId(), p);
        return summary(user, req.targetType(), req.targetId());
    }

    // ---- summaries -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Summary> summaries(CurrentUser viewer, TargetType type, List<Long> ids) {
        List<Long> unique = ids.stream().distinct().limit(100).toList();
        if (type == TargetType.COMMENT) {
            Map<Long, Comment> byId = new HashMap<>();
            comments.findAllById(unique).forEach(c -> byId.put(c.getId(), c));
            Map<Long, ReactionKind> mine = viewer == null ? Map.of() : dao.myReactions(viewer.id(), type, unique);
            return unique.stream().filter(byId::containsKey).map(id -> new Summary(type, id, byId.get(id).getLikeCount(),
                    0, byId.get(id).getReplyCount(), 0, mine.get(id), false)).toList();
        }
        Map<Long, EngagementDao.Stats> stats = dao.stats(type, unique);
        Map<Long, ReactionKind> mine = viewer == null ? Map.of() : dao.myReactions(viewer.id(), type, unique);
        Set<Long> saved = viewer == null ? Set.of() : dao.bookmarked(viewer.id(), type, unique);
        return unique.stream().map(id -> {
            EngagementDao.Stats s = stats.get(id);
            return new Summary(type, id, s == null ? 0 : s.likes(), s == null ? 0 : s.dislikes(),
                    s == null ? 0 : s.comments(), s == null ? 0 : s.shares(), mine.get(id), saved.contains(id));
        }).toList();
    }

    private Summary summary(CurrentUser user, TargetType type, Long id) {
        return summaries(user, type, List.of(id)).stream().findFirst()
                .orElse(new Summary(type, id, 0, 0, 0, 0, null, false));
    }

    private static Map<String, Object> payload(CurrentUser user, TargetResolver.Target t) {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", user.id());
        p.put("targetType", t.type().name());
        p.put("targetId", t.id());
        p.put("targetOwnerId", t.ownerId());
        return p;
    }
}
