package com.socialhub.engagementservice.service;

import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.engagementservice.domain.Comment;
import com.socialhub.engagementservice.domain.Types.ReactionKind;
import com.socialhub.engagementservice.domain.Types.TargetType;
import com.socialhub.engagementservice.dto.EngagementDtos.CreateCommentRequest;
import com.socialhub.engagementservice.dto.EngagementDtos.ReactionRequest;
import com.socialhub.engagementservice.repository.CommentRepository;
import com.socialhub.engagementservice.repository.EngagementDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EngagementServiceTest {

    @Mock EngagementDao dao;
    @Mock CommentRepository comments;
    @Mock TargetResolver targets;
    @Mock OutboxWriter outbox;

    ReactionService reactions;
    CommentService commentService;
    final CurrentUser alice = new CurrentUser(1L, "alice", Set.of("USER"));
    final CurrentUser bob = new CurrentUser(2L, "bob", Set.of("USER"));
    final CurrentUser carol = new CurrentUser(3L, "carol", Set.of("USER"));
    final TargetResolver.Target video = new TargetResolver.Target(TargetType.VIDEO, 9L, 1L, "PUBLIC", true);

    @BeforeEach
    void setUp() {
        reactions = new ReactionService(dao, comments, targets, outbox);
        commentService = new CommentService(comments, dao, targets, outbox);
    }

    @Test
    void dislikeOnlyAllowedOnVideos() {
        assertThrows(ApiException.class,
                () -> reactions.set(bob, new ReactionRequest(TargetType.POST, 5L, ReactionKind.DISLIKE)));
        verifyNoInteractions(targets, dao);
    }

    @Test
    void likingTwiceIsANoOp() {
        when(targets.require(TargetType.VIDEO, 9L, bob)).thenReturn(video);
        when(dao.reactionOf(2L, TargetType.VIDEO, 9L)).thenReturn(Optional.of(ReactionKind.LIKE));

        reactions.set(bob, new ReactionRequest(TargetType.VIDEO, 9L, ReactionKind.LIKE));

        verify(dao, never()).insertReaction(anyLong(), any(), anyLong(), any());
        verify(dao, never()).bump(any(), anyLong(), anyString(), anyInt());
        verifyNoInteractions(outbox);
    }

    @Test
    void switchingLikeToDislikeMovesOneCount() {
        when(targets.require(TargetType.VIDEO, 9L, bob)).thenReturn(video);
        when(dao.reactionOf(2L, TargetType.VIDEO, 9L)).thenReturn(Optional.of(ReactionKind.LIKE));

        reactions.set(bob, new ReactionRequest(TargetType.VIDEO, 9L, ReactionKind.DISLIKE));

        verify(dao).changeReaction(2L, TargetType.VIDEO, 9L, ReactionKind.DISLIKE);
        verify(dao).bump(TargetType.VIDEO, 9L, "likes", -1);
        verify(dao).bump(TargetType.VIDEO, 9L, "dislikes", 1);
        verify(outbox).publish(anyString(), eq("REACTION_SET"), eq(9L), anyMap());
    }

    @Test
    void replyToAReplyStaysInTheSameThread() {
        Comment root = comment(10L, null, 1L);
        Comment reply = comment(11L, 10L, 3L);
        when(targets.require(TargetType.VIDEO, 9L, bob)).thenReturn(video);
        when(comments.findById(11L)).thenReturn(Optional.of(reply));
        when(comments.saveAndFlush(any())).thenAnswer(inv -> { Comment c = inv.getArgument(0); c.setId(12L); return c; });

        var r = commentService.create(bob, new CreateCommentRequest(TargetType.VIDEO, 9L, "agree!", 11L));

        assertEquals(10L, r.parentId());        // attached to the root, not to the reply
        assertEquals(3L, r.replyToUserId());    // but records whom it answers
        verify(comments).adjustReplies(10L, 1);
        verify(dao).bump(TargetType.VIDEO, 9L, "comments", 1);
    }

    @Test
    void creatorCanDeleteCommentsOnTheirContentButStrangersCannot() {
        Comment c = comment(20L, null, 2L); // bob's comment on alice's video
        when(comments.findById(20L)).thenReturn(Optional.of(c));

        assertThrows(ApiException.class, () -> commentService.delete(carol, 20L));
        commentService.delete(alice, 20L); // alice owns the video

        assertTrue(c.isDeleted());
    }

    private Comment comment(Long id, Long parent, Long author) {
        Comment c = new Comment();
        c.setId(id);
        c.setTargetType(TargetType.VIDEO);
        c.setTargetId(9L);
        c.setTargetOwnerId(1L);
        c.setAuthorId(author);
        c.setParentId(parent);
        c.setBody("text");
        return c;
    }
}
