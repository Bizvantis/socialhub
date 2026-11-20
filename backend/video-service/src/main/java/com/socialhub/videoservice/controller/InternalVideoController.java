package com.socialhub.videoservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.videoservice.dto.VideoDtos.VideoInfo;
import com.socialhub.videoservice.repository.RelationsDao;
import com.socialhub.videoservice.service.VideoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Service-to-service API (Eureka only; blocked at the gateway). */
@RestController
@RequestMapping("/internal")
public class InternalVideoController {

    private final VideoService videos;
    private final RelationsDao relations;

    public InternalVideoController(VideoService videos, RelationsDao relations) {
        this.videos = videos;
        this.relations = relations;
    }

    /** Target validation for comments/reactions/reports and owner lookup for notifications. */
    @GetMapping("/videos/{id}")
    public ApiResponse<VideoInfo> video(@PathVariable Long id) {
        return ApiResponse.ok(videos.info(id));
    }

    /** Keyset-paginated subscriber ids for fan-out (feed, notifications). */
    @GetMapping("/channels/{id}/subscribers")
    public ApiResponse<List<Long>> subscribers(@PathVariable Long id,
                                               @RequestParam(defaultValue = "0") long after,
                                               @RequestParam(defaultValue = "1000") int limit) {
        return ApiResponse.ok(relations.subscriberIds(id, after, Math.min(Math.max(limit, 1), 5000)));
    }

    /** Moderation takedown / restore (Step 32 also does this through events). */
    @PostMapping("/videos/{id}/removed")
    public ApiResponse<Void> setRemoved(@PathVariable Long id, @RequestParam boolean value) {
        videos.setRemoved(id, value);
        return ApiResponse.ok(null);
    }
}
