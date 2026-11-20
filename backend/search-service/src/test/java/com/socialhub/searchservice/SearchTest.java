package com.socialhub.searchservice;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.searchservice.es.EsClient;
import com.socialhub.searchservice.es.Indices;
import com.socialhub.searchservice.index.IndexingConsumer;
import com.socialhub.searchservice.search.SearchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchTest {

    final EventJson json = new EventJson(JsonMapper.builder().build());
    final EsClient es = mock(EsClient.class);
    final IndexingConsumer consumer = new IndexingConsumer(json, es, mock(Indices.class));

    private String event(String type, Map<String, Object> payload) {
        return json.write(EventEnvelope.of(type, 1, payload));
    }

    @Test
    void listedVideoIsIndexedWithItsVersion() {
        Map<String, Object> p = new HashMap<>(Map.of("videoId", 4, "title", "Spring Boot", "listed", true,
                "category", "TECH", "channelId", 2, "ownerId", 1, "mediaId", 9, "viewCount", 10, "version", 1234L));
        consumer.onMessage(event("VIDEO_UPSERTED", p));
        verify(es).index(eq(Indices.VIDEOS), eq("4"), argThat(d -> "Spring Boot".equals(d.get("title"))), eq(1234L));
    }

    @Test
    void videoThatStopsBeingPublicIsDeletedNotIndexed() {
        consumer.onMessage(event("VIDEO_UPSERTED", new HashMap<>(Map.of("videoId", 4, "listed", false, "version", 2000L))));
        verify(es).delete(Indices.VIDEOS, "4", 2000L);
        verify(es, never()).index(anyString(), anyString(), anyMap(), anyLong());
    }

    @Test
    void followersOnlyPostIsNeverSearchable() {
        consumer.onMessage(event("POST_UPSERTED", new HashMap<>(Map.of("postId", 7, "listed", false, "version", 5L))));
        verify(es).delete(Indices.POSTS, "7", 5L);
    }

    @Test
    void viewsAndFollowsAreIncrementalCounters() {
        consumer.onMessage(event("VIDEO_VIEWED", new HashMap<>(Map.of("videoId", 4))));
        consumer.onMessage(event("USER_FOLLOWED", new HashMap<>(Map.of("followerId", 1, "followeeId", 2))));
        verify(es).increment(Indices.VIDEOS, "4", "viewCount", 1);
        verify(es).increment(Indices.USERS, "2", "followerCount", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void videoSearchBuildsFilteredFuzzyQuery() {
        Map<String, Object> empty = Map.of("hits", Map.of("hits", List.of(), "total", Map.of("value", 0)));
        when(es.search(eq(Indices.VIDEOS), anyMap())).thenReturn(empty);
        var search = new SearchService(es);

        search.videos("sprng boot", "tech", "#Java", 60, 600, "week", "views", 0, 10);

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(es).search(eq(Indices.VIDEOS), body.capture());
        String q = body.getValue().toString();
        assertTrue(q.contains("fuzziness=AUTO"), q);
        assertTrue(q.contains("category=TECH"), q);       // normalised to the enum value
        assertTrue(q.contains("tags=java"), q);           // '#' stripped, lower-cased
        assertTrue(q.contains("durationSeconds={gte=60, lte=600}"), q);
        assertTrue(q.contains("publishedAt={gte="), q);
        assertTrue(q.contains("viewCount=desc"), q);      // sort=views
    }

    @Test
    void deepPagingBeyondTheResultWindowIsRefused() {
        var page = new SearchService(es).videos("x", null, null, null, null, null, null, 1000, 50);
        assertTrue(page.items().isEmpty());
        verifyNoInteractions(es);
    }
}
