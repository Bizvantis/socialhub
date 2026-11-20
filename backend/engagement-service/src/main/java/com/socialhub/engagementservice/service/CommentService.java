package com.socialhub.engagementservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.common.web.Pages;
import com.socialhub.engagementservice.domain.Comment;
import com.socialhub.engagementservice.domain.Types.ReactionKind;
import com.socialhub.engagementservice.domain.Types.TargetType;
import com.socialhub.engagementservice.dto.EngagementDtos.*;
import com.socialhub.engagementservice.repository.CommentRepository;
import com.socialhub.engagementservice.repository.EngagementDao;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class CommentService {

    private final CommentRepository comments;
    private final EngagementDao dao;
    private final TargetResolver targets;
    private final OutboxWriter outbox;

    public CommentService(CommentRepository comments, EngagementDao dao, TargetResolver targets, OutboxWriter outbox) {
        this.comments = comments;
        this.dao = dao;
        this.targets = targets;
        this.outbox = outbox;
    }

    /**
     * Replies are kept one level deep (like YouTube/Instagram): replying to a reply attaches the
     * new comment to the same top-level thread and records who was replied to.
     */
    @Transactional
    public CommentResponse create(CurrentUser user, CreateCommentRequest req) {
        if (!req.targetType().commentable()) throw ApiException.badRequest("Cannot comment on " + req.targetType());
        TargetResolver.Target target = targets.require(req.targetType(), req.targetId(), user);

        Comment c = new Comment();
        c.setTargetType(req.targetType());
        c.setTargetId(req.targetId());
        c.setTargetOwnerId(target.ownerId());
        c.setAuthorId(user.id());
        c.setBody(req.body().trim());

        Comment parent = null;
        if (req.parentId() != null) {
            parent = comments.findById(req.parentId()).filter(Comment::isVisible)
                    .orElseThrow(() -> ApiException.notFound("Parent comment"));
            if (!parent.getTargetType().equals(req.targetType()) || !parent.getTargetId().equals(req.targetId())) {
                throw ApiException.badRequest("Parent comment belongs to a different target");
            }
            Long rootId = parent.getParentId() != null ? parent.getParentId() : parent.getId();
            c.setParentId(rootId);
            c.setReplyToUserId(parent.getAuthorId());
        }
        comments.saveAndFlush(c);
        if (c.getParentId() != null) comments.adjustReplies(c.getParentId(), 1);
        dao.bump(c.getTargetType(), c.getTargetId(), "comments", 1);

        Map<String, Object> p = basePayload(c);
        p.put("parentAuthorId", parent == null ? null : parent.getAuthorId());
        p.put("bodyPreview", c.getBody().length() > 140 ? c.getBody().substring(0, 140) : c.getBody());
        outbox.publish(Topics.ENGAGEMENT_EVENTS, "COMMENT_CREATED", c.getTargetId(), p);
        return toResponse(c, false);
    }

    @Transactional
    public CommentResponse edit(CurrentUser user, Long id, EditCommentRequest req) {
        Comment c = load(id);
        if (!c.getAuthorId().equals(user.id())) throw ApiException.forbidden("Only the author can edit a comment");
        c.setBody(req.body().trim());
        c.setEditedAt(Instant.now());
        return toResponse(c, dao.myReactions(user.id(), TargetType.COMMENT, List.of(id)).containsKey(id));
    }

    /** The author, the creator of the video/post, or an admin may delete a comment. */
    @Transactional
    public void delete(CurrentUser user, Long id) {
        Comment c = load(id);
        boolean allowed = c.getAuthorId().equals(user.id()) || c.getTargetOwnerId().equals(user.id()) || user.isAdmin();
        if (!allowed) throw ApiException.forbidden("Not allowed");
        c.setDeleted(true);
        if (c.getParentId() != null) comments.adjustReplies(c.getParentId(), -1);
        dao.bump(c.getTargetType(), c.getTargetId(), "comments", -(1 + (c.getParentId() == null ? c.getReplyCount() : 0)));
        outbox.publish(Topics.ENGAGEMENT_EVENTS, "COMMENT_DELETED", c.getTargetId(), basePayload(c));
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> list(CurrentUser viewer, TargetType type, Long targetId, String sort,
                                            String cursor, int size) {
        targets.require(type, targetId, viewer);
        int s = Pages.clampSize(size);
        List<Comment> found;
        String next;
        if ("top".equalsIgnoreCase(sort)) {
            int page = cursor == null ? 0 : Integer.parseInt(cursor);
            found = comments.findTop(type, targetId, PageRequest.of(page, s));
            next = found.size() == s ? String.valueOf(page + 1) : null;
        } else {
            long before = cursor == null ? Long.MAX_VALUE : Long.parseLong(cursor);
            found = comments.findNewest(type, targetId, before, PageRequest.of(0, s));
            next = found.size() == s ? String.valueOf(found.get(found.size() - 1).getId()) : null;
        }
        return CursorPage.of(toResponses(viewer, found), next);
    }

    @Transactional(readOnly = true)
    public CursorPage<CommentResponse> replies(CurrentUser viewer, Long parentId, String cursor, int size) {
        Comment parent = load(parentId);
        targets.require(parent.getTargetType(), parent.getTargetId(), viewer);
        int s = Pages.clampSize(size);
        long after = cursor == null ? 0 : Long.parseLong(cursor);
        List<Comment> found = comments.findReplies(parentId, after, PageRequest.of(0, s));
        String next = found.size() == s ? String.valueOf(found.get(found.size() - 1).getId()) : null;
        return CursorPage.of(toResponses(viewer, found), next);
    }

    @Transactional
    public void setRemoved(Long id, boolean removed) {
        comments.findById(id).ifPresent(c -> c.setRemoved(removed));
    }

    // ------------------------------------------------------------------

    private Comment load(Long id) {
        return comments.findById(id).filter(Comment::isVisible).orElseThrow(() -> ApiException.notFound("Comment"));
    }

    private List<CommentResponse> toResponses(CurrentUser viewer, List<Comment> found) {
        Set<Long> liked = viewer == null ? Set.of()
                : dao.myReactions(viewer.id(), TargetType.COMMENT, found.stream().map(Comment::getId).toList())
                .entrySet().stream().filter(e -> e.getValue() == ReactionKind.LIKE).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        return found.stream().map(c -> toResponse(c, liked.contains(c.getId()))).toList();
    }

    private static CommentResponse toResponse(Comment c, boolean liked) {
        return new CommentResponse(c.getId(), c.getTargetType(), c.getTargetId(), c.getAuthorId(), c.getParentId(),
                c.getReplyToUserId(), c.getBody(), c.getLikeCount(), c.getReplyCount(), liked,
                c.getAuthorId().equals(c.getTargetOwnerId()), c.getEditedAt(), c.getCreatedAt());
    }

    private static Map<String, Object> basePayload(Comment c) {
        Map<String, Object> p = new HashMap<>();
        p.put("commentId", c.getId());
        p.put("targetType", c.getTargetType().name());
        p.put("targetId", c.getTargetId());
        p.put("targetOwnerId", c.getTargetOwnerId());
        p.put("authorId", c.getAuthorId());
        p.put("parentId", c.getParentId());
        p.put("replyToUserId", c.getReplyToUserId());
        return p;
    }
}
