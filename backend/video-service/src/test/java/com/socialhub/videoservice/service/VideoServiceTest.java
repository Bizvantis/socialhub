package com.socialhub.videoservice.service;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.videoservice.client.MediaClient;
import com.socialhub.videoservice.dto.VideoDtos.CreateVideoRequest;
import com.socialhub.videoservice.entity.Channel;
import com.socialhub.videoservice.entity.Enums.Category;
import com.socialhub.videoservice.entity.Enums.VideoStatus;
import com.socialhub.videoservice.entity.Enums.Visibility;
import com.socialhub.videoservice.entity.Video;
import com.socialhub.videoservice.repository.ChannelRepository;
import com.socialhub.videoservice.repository.RelationsDao;
import com.socialhub.videoservice.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock VideoRepository videos;
    @Mock ChannelRepository channels;
    @Mock RelationsDao relations;
    @Mock MediaClient media;
    @Mock VideoEvents events;
    @Mock ViewCounter views;

    VideoService service;
    ChannelService channelService;
    final CurrentUser alice = new CurrentUser(1L, "alice", Set.of("USER"));
    final CurrentUser bob = new CurrentUser(2L, "bob", Set.of("USER"));
    Channel channel;

    @BeforeEach
    void setUp() {
        service = new VideoService(videos, channels, relations, media, events, views);
        channelService = new ChannelService(channels, relations, media, events);
        channel = new Channel();
        channel.setId(7L);
        channel.setOwnerId(1L);
        channel.setHandle("alice");
        channel.setName("Alice");
    }

    private CreateVideoRequest req(Visibility vis) {
        return new CreateVideoRequest(100L, " My video ", "desc", Set.of("Java", "spring"), Category.TECH, vis, null);
    }

    @Test
    void uploadRequiresAChannel() {
        when(channels.findByOwnerId(1L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.create(alice, req(Visibility.PUBLIC)));
        verifyNoInteractions(media);
    }

    @Test
    void readyPublicVideoIsPublishedImmediately() {
        when(channels.findByOwnerId(1L)).thenReturn(Optional.of(channel));
        when(media.requireAttachable(100L, 1L, "VIDEO"))
                .thenReturn(new MediaClient.MediaInfo(100L, 1L, "VIDEO", "VIDEO", "READY", 42));
        when(videos.findByMediaId(100L)).thenReturn(Optional.empty());

        var r = service.create(alice, req(Visibility.PUBLIC));

        assertEquals(VideoStatus.READY, r.status());
        assertNotNull(r.publishedAt());
        assertEquals("My video", r.title());
        assertTrue(r.tags().containsAll(Set.of("java", "spring")));
        verify(events).published(any());
        verify(events).upserted(any());
        verify(channels).adjustVideos(7L, 1);
    }

    @Test
    void processingVideoIsNotPublishedUntilMediaIsReady() {
        when(channels.findByOwnerId(1L)).thenReturn(Optional.of(channel));
        when(media.requireAttachable(100L, 1L, "VIDEO"))
                .thenReturn(new MediaClient.MediaInfo(100L, 1L, "VIDEO", "VIDEO", "UPLOADED", null));
        when(videos.findByMediaId(100L)).thenReturn(Optional.empty());

        var r = service.create(alice, req(Visibility.PUBLIC));

        assertEquals(VideoStatus.PROCESSING, r.status());
        assertNull(r.publishedAt());
        verify(events, never()).published(any());
    }

    @Test
    void mediaReadyEventPublishesOnce() {
        Video v = new Video();
        v.setId(5L);
        v.setChannel(channel);
        v.setOwnerId(1L);
        v.setVisibility(Visibility.PUBLIC);
        v.setStatus(VideoStatus.PROCESSING);
        v.setCategory(Category.TECH);
        when(videos.findByMediaId(100L)).thenReturn(Optional.of(v));

        service.onMediaProcessed(100L, true, 30);
        service.onMediaProcessed(100L, true, 30); // duplicate / late event

        assertEquals(VideoStatus.READY, v.getStatus());
        assertEquals(30, v.getDurationSeconds());
        verify(events, times(1)).published(v);
    }

    @Test
    void privateVideoIsHiddenFromOthers() {
        Video v = new Video();
        v.setId(5L);
        v.setChannel(channel);
        v.setOwnerId(1L);
        v.setVisibility(Visibility.PRIVATE);
        v.setStatus(VideoStatus.READY);
        v.setCategory(Category.TECH);
        when(videos.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(v));

        var ex = assertThrows(ApiException.class, () -> service.get(bob, 5L));
        assertEquals(404, ex.getStatus().value());
        assertDoesNotThrow(() -> service.get(alice, 5L));
    }

    @Test
    void cannotSubscribeToOwnChannel() {
        when(channels.findById(7L)).thenReturn(Optional.of(channel));
        assertThrows(ApiException.class, () -> channelService.subscribe(alice, 7L));
        verifyNoInteractions(relations);
    }

    @Test
    void duplicateSubscribeDoesNotMoveCounter() {
        when(channels.findById(7L)).thenReturn(Optional.of(channel));
        when(relations.subscribe(2L, 7L)).thenReturn(true, false);

        var first = channelService.subscribe(bob, 7L);
        var second = channelService.subscribe(bob, 7L);

        assertEquals(1, first.subscriberCount());
        assertEquals(0, second.subscriberCount()); // no change on the duplicate
        verify(channels, times(1)).adjustSubscribers(7L, 1);
        verify(events, times(1)).subscribed(channel, 2L, true);
    }
}
