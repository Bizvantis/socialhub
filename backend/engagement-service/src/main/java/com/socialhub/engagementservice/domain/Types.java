package com.socialhub.engagementservice.domain;

public final class Types {

    private Types() {}

    public enum TargetType {
        VIDEO, POST, COMMENT;

        public boolean commentable() { return this == VIDEO || this == POST; }
        public boolean bookmarkable() { return this == VIDEO || this == POST; }
    }

    /** LIKE on everything; DISLIKE only on videos (YouTube-style). */
    public enum ReactionKind { LIKE, DISLIKE }

    public enum ShareChannel { LINK, DIRECT, EXTERNAL }
}
