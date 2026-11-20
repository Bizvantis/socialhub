package com.socialhub.notificationservice;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.common.web.PageSize;
import com.socialhub.notificationservice.realtime.RealtimeHub;
import com.socialhub.notificationservice.store.NotificationDao;
import com.socialhub.notificationservice.store.NotificationDao.Notification;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@EnableScheduling
public class NotificationController {

    public record Preferences(boolean emailEnabled) {}

    private final NotificationDao dao;
    private final RealtimeHub realtime;

    public NotificationController(NotificationDao dao, RealtimeHub realtime) {
        this.dao = dao;
        this.realtime = realtime;
    }

    /** Inbox, newest first; ?unreadOnly=true; cursor = last id seen. */
    @GetMapping
    public ApiResponse<CursorPage<Notification>> inbox(CurrentUser user,
                                                       @RequestParam(required = false) Long cursor,
                                                       @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                       @RequestParam(defaultValue = "20") int size) {
        int s = PageSize.clamp(size);
        List<Notification> items = dao.inbox(user.id(), cursor == null ? Long.MAX_VALUE : cursor, unreadOnly, s);
        String next = items.size() == s ? String.valueOf(items.get(items.size() - 1).id()) : null;
        return ApiResponse.ok(CursorPage.of(items, next));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unread(CurrentUser user) {
        return ApiResponse.ok(Map.of("unread", dao.unreadCount(user.id())));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(CurrentUser user, @PathVariable long id) {
        dao.markRead(user.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> readAll(CurrentUser user) {
        return ApiResponse.ok(Map.of("marked", dao.markAllRead(user.id())));
    }

    @PutMapping("/preferences")
    public ApiResponse<Preferences> preferences(CurrentUser user, @RequestBody Preferences prefs) {
        dao.setEmailEnabled(user.id(), prefs.emailEnabled());
        return ApiResponse.ok(prefs);
    }

    /** Real-time stream (Server-Sent Events): event "notification" carries the JSON of a new item. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(CurrentUser user) {
        return realtime.connect(user.id());
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        dao.purgeOld();
    }
}
