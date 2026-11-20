package com.socialhub.mediaservice.service;

import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.mediaservice.config.StorageProperties;
import com.socialhub.mediaservice.dto.MediaDtos.CreateUploadRequest;
import com.socialhub.mediaservice.entity.Media;
import com.socialhub.mediaservice.entity.MediaPurpose;
import com.socialhub.mediaservice.entity.MediaStatus;
import com.socialhub.mediaservice.entity.MediaType;
import com.socialhub.mediaservice.repository.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock MediaRepository repo;
    @Mock StorageService storage;
    @Mock OutboxWriter outbox;

    MediaService service;
    final CurrentUser alice = new CurrentUser(1L, "alice", Set.of("USER"));
    final CurrentUser bob = new CurrentUser(2L, "bob", Set.of("USER"));

    @BeforeEach
    void setUp() throws Exception {
        var props = new StorageProperties("bucket", null, null, null, null, null, true, null, null, null);
        service = new MediaService(repo, storage, props, outbox);
        lenient().when(storage.presignDownload(anyString())).thenReturn(URI.create("http://s3/get").toURL());
    }

    @Test
    void rejectsDisallowedContentType() {
        var ex = assertThrows(ApiException.class, () -> service.createUpload(alice,
                new CreateUploadRequest(MediaPurpose.AVATAR, "application/pdf", 1000, "cv.pdf")));
        assertTrue(ex.getMessage().contains("not allowed"));
        verifyNoInteractions(repo, storage);
    }

    @Test
    void rejectsOversizedFile() {
        assertThrows(ApiException.class, () -> service.createUpload(alice,
                new CreateUploadRequest(MediaPurpose.AVATAR, "image/png", 6L * 1024 * 1024, "big.png")));
        verifyNoInteractions(repo, storage);
    }

    @Test
    void createsPendingMediaAndPresignsWithDeclaredTypeAndSize() throws Exception {
        when(repo.save(any())).thenAnswer(inv -> { Media m = inv.getArgument(0); m.setId(10L); return m; });
        when(storage.presignUpload(anyString(), eq("image/png"), eq(1234L)))
                .thenReturn(URI.create("http://s3/upload").toURL());

        var ticket = service.createUpload(alice, new CreateUploadRequest(MediaPurpose.POST_IMAGE, "IMAGE/PNG", 1234, "a.png"));

        assertEquals(10L, ticket.mediaId());
        assertEquals("PUT", ticket.method());
        assertEquals("image/png", ticket.headers().get("Content-Type"));
    }

    @Test
    void completeRejectsSizeMismatchAndMarksFailed() {
        Media m = pending(MediaPurpose.POST_IMAGE, "image/png", 1000);
        when(repo.findById(5L)).thenReturn(Optional.of(m));
        when(storage.head(m.getObjectKey())).thenReturn(Optional.of(
                HeadObjectResponse.builder().contentLength(999_999L).contentType("image/png").build()));

        assertThrows(ApiException.class, () -> service.complete(alice, 5L));

        assertEquals(MediaStatus.FAILED, m.getStatus());
        verify(outbox).publish(anyString(), eq("MEDIA_FAILED"), eq(5L), anyMap());
    }

    @Test
    void completeImageBecomesReady() {
        Media m = pending(MediaPurpose.POST_IMAGE, "image/png", 1000);
        when(repo.findById(5L)).thenReturn(Optional.of(m));
        when(storage.head(m.getObjectKey())).thenReturn(Optional.of(
                HeadObjectResponse.builder().contentLength(1000L).contentType("image/png").build()));

        service.complete(alice, 5L);

        assertEquals(MediaStatus.READY, m.getStatus());
        verify(outbox).publish(anyString(), eq("MEDIA_READY"), eq(5L), anyMap());
    }

    @Test
    void completeVideoWaitsForProcessing() {
        Media m = pending(MediaPurpose.VIDEO, "video/mp4", 5000);
        when(repo.findById(5L)).thenReturn(Optional.of(m));
        when(storage.head(m.getObjectKey())).thenReturn(Optional.of(
                HeadObjectResponse.builder().contentLength(5000L).contentType("video/mp4").build()));

        service.complete(alice, 5L);

        assertEquals(MediaStatus.UPLOADED, m.getStatus());
        verify(outbox).publish(anyString(), eq("MEDIA_UPLOADED"), eq(5L), anyMap());
    }

    @Test
    void onlyOwnerCanComplete() {
        when(repo.findById(5L)).thenReturn(Optional.of(pending(MediaPurpose.POST_IMAGE, "image/png", 10)));
        assertThrows(ApiException.class, () -> service.complete(bob, 5L));
        verifyNoInteractions(storage);
    }

    @Test
    void restrictedMediaIsHiddenFromOthers() {
        Media m = pending(MediaPurpose.VIDEO, "video/mp4", 10);
        m.setStatus(MediaStatus.READY);
        m.setRestricted(true);
        when(repo.findById(5L)).thenReturn(Optional.of(m));

        var ex = assertThrows(ApiException.class, () -> service.get(bob, 5L));
        assertEquals(404, ex.getStatus().value());
        assertThrows(ApiException.class, () -> service.get(null, 5L));
    }

    private Media pending(MediaPurpose purpose, String type, long size) {
        Media m = new Media();
        m.setId(5L);
        m.setOwnerId(1L);
        m.setPurpose(purpose);
        m.setMediaType(purpose.mediaType());
        m.setContentType(type);
        m.setSizeBytes(size);
        m.setStatus(MediaStatus.PENDING);
        m.setObjectKey("u/1/x/uuid/original.bin");
        return m;
    }
}
