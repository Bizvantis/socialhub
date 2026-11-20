package com.socialhub.feedservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.feed")
public record FeedProperties(int celebrityThreshold, int maxFeedSize, int timelineSize, int backfillOnFollow,
                             Duration itemTtl) {

    public FeedProperties {
        if (celebrityThreshold <= 0) celebrityThreshold = 1000;
        if (maxFeedSize <= 0) maxFeedSize = 800;
        if (timelineSize <= 0) timelineSize = 500;
        if (backfillOnFollow <= 0) backfillOnFollow = 50;
        if (itemTtl == null) itemTtl = Duration.ofDays(14);
    }
}
