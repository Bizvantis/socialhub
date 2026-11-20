package com.socialhub.videoservice.service;

import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.entity.Channel;
import com.socialhub.videoservice.entity.Playlist;
import com.socialhub.videoservice.entity.Video;

import java.util.ArrayList;

final class VideoMapper {

    private VideoMapper() {}

    static ChannelSummary summary(Channel c) {
        return new ChannelSummary(c.getId(), c.getOwnerId(), c.getHandle(), c.getName(), c.getAvatarMediaId(),
                c.getSubscriberCount());
    }

    static ChannelResponse channel(Channel c, boolean subscribed) {
        return channel(c, subscribed, c.getSubscriberCount());
    }

    static ChannelResponse channel(Channel c, boolean subscribed, int subscriberCount) {
        return new ChannelResponse(c.getId(), c.getOwnerId(), c.getHandle(), c.getName(), c.getDescription(),
                c.getAvatarMediaId(), c.getBannerMediaId(), subscriberCount, c.getVideoCount(), subscribed,
                c.getCreatedAt());
    }

    static VideoCard card(Video v, long pendingViews) {
        return new VideoCard(v.getId(), summary(v.getChannel()), v.getTitle(), v.getStatus(), v.getVisibility(),
                v.getMediaId(), v.getThumbnailMediaId(), v.getDurationSeconds(), v.getViewCount() + pendingViews,
                v.getPublishedAt());
    }

    static VideoResponse full(Video v, long pendingViews, Integer resumeAt, Boolean inWatchLater) {
        return new VideoResponse(v.getId(), summary(v.getChannel()), v.getTitle(), v.getDescription(),
                new ArrayList<>(v.getTags()), v.getCategory(), v.getVisibility(), v.getStatus(), v.getMediaId(),
                v.getThumbnailMediaId(), v.getDurationSeconds(), v.getViewCount() + pendingViews,
                v.getPublishedAt(), v.getCreatedAt(), resumeAt, inWatchLater);
    }

    static PlaylistResponse playlist(Playlist p) {
        return new PlaylistResponse(p.getId(), p.getOwnerId(), p.getTitle(), p.getDescription(), p.getVisibility(),
                p.getVideoCount(), p.getUpdatedAt());
    }
}
