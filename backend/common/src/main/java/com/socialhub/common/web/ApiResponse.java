package com.socialhub.common.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Uniform JSON envelope returned by every SocialHub endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
