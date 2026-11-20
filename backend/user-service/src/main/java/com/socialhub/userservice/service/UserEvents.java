package com.socialhub.userservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.userservice.entity.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * user-service domain events, written through the transactional outbox.
 * Follow events are keyed by the follower so one user's follow/unfollow sequence stays ordered;
 * the feed-service builds its follower graph from them and notification-service tells the
 * followee "X followed you".
 */
@Component
public class UserEvents {

    private final OutboxWriter outbox;

    public UserEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void followed(long follower, long followee, boolean followed) {
        Map<String, Object> p = new HashMap<>();
        p.put("followerId", follower);
        p.put("followeeId", followee);
        outbox.publish(Topics.USER_EVENTS, followed ? "USER_FOLLOWED" : "USER_UNFOLLOWED", follower, p);
    }

    /** Snapshot for read models (search index of users). */
    public void profileUpdated(Profile p) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", p.getUserId());
        m.put("username", p.getUsername());
        m.put("displayName", p.getDisplayName());
        m.put("bio", p.getBio());
        m.put("avatarMediaId", p.getAvatarMediaId());
        m.put("privateAccount", p.isPrivateAccount());
        m.put("followerCount", p.getFollowerCount());
        m.put("version", System.currentTimeMillis());
        outbox.publish(Topics.USER_EVENTS, "PROFILE_UPDATED", p.getUserId(), m);
    }
}
