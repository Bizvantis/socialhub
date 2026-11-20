package com.socialhub.common.event;

/**
 * Kafka topic names. One topic per producing domain; the event "type" field
 * distinguishes events inside a topic. See docs/events/README.md for the full catalogue.
 */
public final class Topics {

    private Topics() {}

    /** user-service: USER_FOLLOWED, USER_UNFOLLOWED, PROFILE_UPDATED */
    public static final String USER_EVENTS = "user-events";

    /** auth-service: USER_REGISTERED, USER_SUSPENDED, USER_REINSTATED */
    public static final String ACCOUNT_EVENTS = "account-events";

    /** media-service: MEDIA_UPLOADED, MEDIA_READY, MEDIA_FAILED, MEDIA_DELETED */
    public static final String MEDIA_EVENTS = "media-events";

    /** video-service: CHANNEL_*, VIDEO_PUBLISHED, VIDEO_UPDATED, VIDEO_DELETED */
    public static final String VIDEO_EVENTS = "video-events";

    /** video-service: VIDEO_VIEWED (high volume, analytics) */
    public static final String VIDEO_VIEWS = "video-views";

    /** post-service: POST_CREATED, POST_UPDATED, POST_DELETED, STORY_CREATED */
    public static final String POST_EVENTS = "post-events";

    /** engagement-service: COMMENT_CREATED, COMMENT_DELETED, REACTION_SET, REACTION_REMOVED, SHARED */
    public static final String ENGAGEMENT_EVENTS = "engagement-events";

    /** moderation-service: CONTENT_REMOVED, CONTENT_RESTORED */
    public static final String MODERATION_EVENTS = "moderation-events";
}
