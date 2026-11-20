package com.socialhub.videoservice.entity;

/** Small enums of the video domain, grouped to keep the package readable. */
public final class Enums {

    private Enums() {}

    /** PUBLIC: listed everywhere. UNLISTED: anyone with the link. PRIVATE: owner only. */
    public enum Visibility { PUBLIC, UNLISTED, PRIVATE }

    /** Mirrors the media-service processing state of the uploaded file. */
    public enum VideoStatus { PROCESSING, READY, FAILED }

    public enum Category {
        MUSIC, GAMING, EDUCATION, ENTERTAINMENT, SPORTS, NEWS, TECH, TRAVEL, FOOD, COMEDY, LIFESTYLE, OTHER
    }
}
