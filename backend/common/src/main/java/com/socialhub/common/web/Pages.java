package com.socialhub.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Clamps client-supplied paging so nobody can request a page of a million rows. */
public final class Pages {

    public static final int MAX_SIZE = 50;

    private Pages() {}

    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(page, 0), clampSize(size));
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), clampSize(size), sort);
    }

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }
}
