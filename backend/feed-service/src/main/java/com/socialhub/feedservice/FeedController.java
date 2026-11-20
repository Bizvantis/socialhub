package com.socialhub.feedservice;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.feedservice.service.FeedReader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedReader feeds;

    public FeedController(FeedReader feeds) {
        this.feeds = feeds;
    }

    /** Personalised, ranked home feed (posts + videos). */
    @GetMapping("/home")
    public ApiResponse<CursorPage<Map<String, Object>>> home(CurrentUser user, @RequestParam(required = false) String cursor,
                                                             @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(feeds.home(user.id(), cursor, size));
    }

    /** Strictly chronological feed of followed people and subscribed channels. */
    @GetMapping("/following")
    public ApiResponse<CursorPage<Map<String, Object>>> following(CurrentUser user,
                                                                  @RequestParam(required = false) String cursor,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(feeds.following(user.id(), cursor, size));
    }

    /** Creator content: newest videos from subscribed channels. */
    @GetMapping("/subscriptions")
    public ApiResponse<CursorPage<Map<String, Object>>> subscriptions(CurrentUser user,
                                                                      @RequestParam(required = false) String cursor,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(feeds.subscriptions(user.id(), cursor, size));
    }
}
