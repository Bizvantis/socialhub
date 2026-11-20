package com.socialhub.common.web;

/**
 * Page-size clamping with no dependencies, usable by services without Spring Data
 * (search-service). {@link Pages} builds Spring Data Pageables on top of it.
 */
public final class PageSize {

    public static final int MAX = 50;

    private PageSize() {}

    public static int clamp(int size) {
        return Math.min(Math.max(size, 1), MAX);
    }
}
