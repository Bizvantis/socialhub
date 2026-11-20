package com.socialhub.feedservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.feedservice.FeedProperties;
import com.socialhub.feedservice.store.FeedStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedWriterTest {

    @Mock FeedStore store;
    FeedWriter writer;

    @BeforeEach
    void setUp() {
        writer = new FeedWriter(store, new FeedProperties(3, 800, 500, 50, Duration.ofDays(14)));
    }

    private EventEnvelope post(long id, long author) {
        Map<String, Object> p = new HashMap<>();
        p.put("postId", id);
        p.put("authorId", author);
        p.put("type", "IMAGE");
        p.put("visibility", "PUBLIC");
        p.put("createdAt", "2026-11-20T10:00:00Z");
        return EventEnvelope.of("POST_CREATED", id, p);
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalAuthorIsFannedOutToFollowersAndSelf() {
        when(store.followerCount(1L)).thenReturn(2L);
        when(store.followers(1L)).thenReturn(Set.of("7", "8"));

        writer.postCreated(post(5L, 1L));

        ArgumentCaptor<Collection<String>> targets = ArgumentCaptor.forClass(Collection.class);
        verify(store).fanOut(targets.capture(), eq("P:5"), anyDouble());
        assertEquals(Set.of("1", "7", "8"), new HashSet<>(targets.getValue()));
        verify(store).addToTimeline(eq("tl:u:1"), eq("P:5"), anyDouble());
        verify(store).addToTimeline(eq("tl:global"), eq("P:5"), anyDouble());
    }

    @Test
    @SuppressWarnings("unchecked")
    void celebrityIsNotFannedOutOnlyTimelined() {
        when(store.followerCount(1L)).thenReturn(10_000L); // above threshold

        writer.postCreated(post(5L, 1L));

        ArgumentCaptor<Collection<String>> targets = ArgumentCaptor.forClass(Collection.class);
        verify(store).fanOut(targets.capture(), eq("P:5"), anyDouble());
        assertEquals(Set.of("1"), new HashSet<>(targets.getValue())); // only the author's own feed
        verify(store, never()).followers(1L);
        verify(store).addToTimeline(eq("tl:u:1"), eq("P:5"), anyDouble());
    }

    @Test
    void followingBackfillsRecentItemsButNotFromCelebrities() {
        when(store.followerCount(2L)).thenReturn(1L);
        writer.followed(9L, 2L);
        verify(store).follow(9L, 2L);
        verify(store).copyInto(eq("9"), any());

        when(store.followerCount(3L)).thenReturn(50_000L);
        writer.followed(9L, 3L);
        verify(store, times(1)).copyInto(anyString(), any()); // no backfill for the celebrity
    }

    @Test
    void unlistedVideoUpdateRemovesItFromFeeds() {
        Map<String, Object> p = new HashMap<>();
        p.put("videoId", 4L);
        p.put("channelId", 6L);
        p.put("ownerId", 1L);
        p.put("listed", false);
        when(store.subscribers(6L)).thenReturn(Set.of("7"));

        writer.videoUpserted(EventEnvelope.of("VIDEO_UPSERTED", 4L, p));

        verify(store).unFanOut(argThat(c -> c.containsAll(List.of("7", "1"))), eq(List.of("V:4")));
        verify(store).deleteItem("V:4");
    }

    @Test
    void rankerPrefersEngagementAffinityAndFreshness() {
        long hour = 3_600_000L;
        double fresh = Ranker.score(0, 0, 0, 0, hour);
        double old = Ranker.score(0, 0, 0, 0, 48 * hour);
        double popular = Ranker.score(100, 10, 5, 0, hour);
        double friend = Ranker.score(0, 0, 0, 20, hour);
        assertTrue(fresh > old);
        assertTrue(popular > fresh);
        assertTrue(friend > fresh);
        assertTrue(Ranker.score(10_000, 0, 0, 0, hour) - Ranker.score(1_000, 0, 0, 0, hour) < 3); // diminishing returns
    }
}
