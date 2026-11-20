package com.socialhub.videoservice.dto;

import com.socialhub.videoservice.entity.Enums.Category;
import com.socialhub.videoservice.entity.Enums.VideoStatus;
import com.socialhub.videoservice.entity.Enums.Visibility;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class VideoDtos {

    private VideoDtos() {}

    // ---- channels ------------------------------------------------------

    public record CreateChannelRequest(
            @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_.]{3,30}$", message = "3-30 letters, digits, _ or .") String handle,
            @NotBlank @Size(max = 80) String name,
            @Size(max = 1000) String description) {}

    public record UpdateChannelRequest(
            @Size(min = 1, max = 80) String name,
            @Size(max = 1000) String description,
            Long avatarMediaId,
            Long bannerMediaId) {}

    public record ChannelSummary(Long id, Long ownerId, String handle, String name, Long avatarMediaId,
                                 int subscriberCount) {}

    public record ChannelResponse(Long id, Long ownerId, String handle, String name, String description,
                                  Long avatarMediaId, Long bannerMediaId, int subscriberCount, int videoCount,
                                  boolean subscribed, Instant createdAt) {}

    // ---- videos --------------------------------------------------------

    public record CreateVideoRequest(
            @NotNull Long mediaId,
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String description,
            @Size(max = 15) Set<@Pattern(regexp = "^[\\p{L}\\p{N}_-]{1,40}$", message = "invalid tag") String> tags,
            @NotNull Category category,
            @NotNull Visibility visibility,
            Long thumbnailMediaId) {}

    public record UpdateVideoRequest(
            @Size(min = 1, max = 150) String title,
            @Size(max = 5000) String description,
            @Size(max = 15) Set<@Pattern(regexp = "^[\\p{L}\\p{N}_-]{1,40}$", message = "invalid tag") String> tags,
            Category category,
            Visibility visibility,
            Long thumbnailMediaId) {}

    public record VideoResponse(
            Long id,
            ChannelSummary channel,
            String title,
            String description,
            List<String> tags,
            Category category,
            Visibility visibility,
            VideoStatus status,
            Long mediaId,
            Long thumbnailMediaId,
            Integer durationSeconds,
            long viewCount,
            Instant publishedAt,
            Instant createdAt,
            Integer resumeAtSeconds,
            Boolean inWatchLater) {}

    /** Card for grids and lists (no description, no tags). */
    public record VideoCard(
            Long id,
            ChannelSummary channel,
            String title,
            VideoStatus status,
            Visibility visibility,
            Long mediaId,
            Long thumbnailMediaId,
            Integer durationSeconds,
            long viewCount,
            Instant publishedAt) {}

    public record ViewRequest(@PositiveOrZero Integer watchedSeconds) {}

    /** Service-to-service view of a video (engagement, moderation, notifications). */
    public record VideoInfo(Long id, Long ownerId, Long channelId, String title, Visibility visibility,
                            VideoStatus status, boolean deleted, boolean removed) {}

    // ---- playlists & library -------------------------------------------

    public record CreatePlaylistRequest(
            @NotBlank @Size(max = 100) String title,
            @Size(max = 1000) String description,
            @NotNull Visibility visibility) {}

    public record UpdatePlaylistRequest(
            @Size(min = 1, max = 100) String title,
            @Size(max = 1000) String description,
            Visibility visibility) {}

    public record AddToPlaylistRequest(@NotNull Long videoId) {}

    public record PlaylistResponse(Long id, Long ownerId, String title, String description, Visibility visibility,
                                   int videoCount, Instant updatedAt) {}

    public record PlaylistDetail(PlaylistResponse playlist, List<VideoCard> videos) {}

    public record HistoryItem(VideoCard video, int progressSeconds, Instant watchedAt) {}

    public record WatchLaterItem(VideoCard video, Instant addedAt) {}
}
