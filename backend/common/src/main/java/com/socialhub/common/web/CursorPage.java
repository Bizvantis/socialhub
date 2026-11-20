package com.socialhub.common.web;

import java.util.List;

/**
 * Cursor (keyset) pagination: stable under inserts and never scans with OFFSET.
 * {@code nextCursor} is opaque to clients; null means there is no next page.
 */
public record CursorPage<T>(List<T> items, String nextCursor) {

    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, nextCursor);
    }
}
