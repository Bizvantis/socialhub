package com.socialhub.postservice.service;

import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.postservice.client.MediaClient;
import com.socialhub.postservice.client.UserClient;
import com.socialhub.postservice.dto.PostDtos.CreatePostRequest;
import com.socialhub.postservice.entity.Post;
import com.socialhub.postservice.repository.PostDao;
import com.socialhub.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock PostRepository posts;
    @Mock PostDao dao;
    @Mock MediaClient media;
    @Mock UserClient users;
    @Mock OutboxWriter outbox;

    PostService service;
    final CurrentUser alice = new CurrentUser(1L, "alice", Set.of("USER"));
    final CurrentUser bob = new CurrentUser(2L, "bob", Set.of("USER"));

    @BeforeEach
    void setUp() {
        service = new PostService(posts, dao, media, users, outbox);
    }

    private MediaClient.MediaInfo img(long id, String status) {
        return new MediaClient.MediaInfo(id, 1L, "POST_IMAGE", "IMAGE", status, null);
    }

    @Test
    void carouselNeedsTwoToTenImages() {
        assertThrows(ApiException.class, () -> service.create(alice,
                new CreatePostRequest(Post.Type.CAROUSEL, List.of(1L), "x", null)));
        verifyNoInteractions(media);
    }

    @Test
    void imagePostNeedsExactlyOneMedia() {
        assertThrows(ApiException.class, () -> service.create(alice,
                new CreatePostRequest(Post.Type.IMAGE, List.of(1L, 2L), "x", null)));
    }

    @Test
    void readyImagePostIsAnnouncedAndMentionsResolved() {
        when(media.requireAttachable(10L, 1L, "POST_IMAGE")).thenReturn(img(10L, "READY"));
        when(posts.saveAndFlush(any())).thenAnswer(inv -> { Post p = inv.getArgument(0); p.setId(5L); return p; });
        when(users.lookup(Set.of("bob", "alice")))
                .thenReturn(List.of(new UserClient.UserSummary(2L, "bob", "Bob", null, false),
                        new UserClient.UserSummary(1L, "alice", "Alice", null, false)));

        var r = service.create(alice, new CreatePostRequest(Post.Type.IMAGE, List.of(10L),
                "Sunset with @bob and @alice #goa", null));

        assertEquals(Post.Status.READY, r.status());
        assertEquals(Post.Visibility.PUBLIC, r.visibility());
        assertEquals(List.of(2L), r.mentionedUserIds()); // self-mention dropped
        verify(dao).linkHashtags(5L, Set.of("goa"));
        verify(outbox).publish(anyString(), eq("POST_CREATED"), eq(5L), anyMap());
        verify(outbox).publish(anyString(), eq("USER_MENTIONED"), eq(5L), anyMap());
    }

    @Test
    void reelWaitsForTranscodingBeforeAnnouncing() {
        when(media.requireAttachable(10L, 1L, "REEL"))
                .thenReturn(new MediaClient.MediaInfo(10L, 1L, "REEL", "VIDEO", "UPLOADED", null));
        when(posts.saveAndFlush(any())).thenAnswer(inv -> { Post p = inv.getArgument(0); p.setId(6L); return p; });

        var r = service.create(alice, new CreatePostRequest(Post.Type.REEL, List.of(10L), "reel", null));

        assertEquals(Post.Status.PROCESSING, r.status());
        verify(outbox, never()).publish(anyString(), eq("POST_CREATED"), any(), anyMap());
    }

    @Test
    void followersOnlyPostHiddenFromNonFollowers() {
        Post p = new Post();
        p.setId(5L);
        p.setAuthorId(1L);
        p.setType(Post.Type.IMAGE);
        p.setVisibility(Post.Visibility.FOLLOWERS);
        p.setStatus(Post.Status.READY);
        when(posts.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(p));
        when(users.follows(2L, 1L)).thenReturn(false, true);

        assertThrows(ApiException.class, () -> service.get(bob, 5L));
        assertDoesNotThrow(() -> service.get(bob, 5L)); // after bob follows alice
    }
}
