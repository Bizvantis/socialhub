package com.socialhub.mediaservice.controller;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.mediaservice.dto.MediaDtos.MediaInfo;
import com.socialhub.mediaservice.service.MediaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API (called through Eureka, never through the gateway, which blocks
 * every /internal/** path). Other services use it to check that a media id they are asked
 * to attach exists, belongs to the caller and finished uploading.
 */
@RestController
@RequestMapping("/internal/media")
public class InternalMediaController {

    private final MediaService media;

    public InternalMediaController(MediaService media) {
        this.media = media;
    }

    @GetMapping("/{id}")
    public ApiResponse<MediaInfo> info(@PathVariable Long id) {
        return ApiResponse.ok(media.info(id));
    }
}
