package com.socialhub.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Offset pagination page. Feeds and comment threads use {@link CursorPage} instead. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.hasNext());
    }

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
