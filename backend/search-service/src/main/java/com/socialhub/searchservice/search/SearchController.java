package com.socialhub.searchservice.search;

import com.socialhub.common.web.ApiResponse;
import com.socialhub.searchservice.search.SearchService.Page;
import com.socialhub.searchservice.search.SearchService.Suggestion;
import com.socialhub.searchservice.search.SearchService.TagCount;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public search API (anonymous GET allowed at the gateway). */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    /** Combined results page: top hits of each kind. */
    @GetMapping
    public ApiResponse<Map<String, Page>> all(@RequestParam String q, @RequestParam(defaultValue = "5") int perType) {
        return ApiResponse.ok(search.all(q, Math.min(Math.max(perType, 1), 20)));
    }

    /**
     * /api/search/videos?q=spring&category=TECH&tag=java&minDuration=60&maxDuration=1200
     *                   &uploadedWithin=hour|day|week|month|year&sort=relevance|views|date&page=0&size=20
     */
    @GetMapping("/videos")
    public ApiResponse<Page> videos(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) String tag,
                                    @RequestParam(required = false) Integer minDuration,
                                    @RequestParam(required = false) Integer maxDuration,
                                    @RequestParam(required = false) String uploadedWithin,
                                    @RequestParam(defaultValue = "relevance") String sort,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(search.videos(q, category, tag, minDuration, maxDuration, uploadedWithin, sort, page, size));
    }

    @GetMapping("/videos/{id}/related")
    public ApiResponse<Page> related(@PathVariable long id, @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(search.relatedVideos(id, size));
    }

    @GetMapping("/posts")
    public ApiResponse<Page> posts(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) String hashtag,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(defaultValue = "relevance") String sort,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(search.posts(q, hashtag, type, sort, page, size));
    }

    @GetMapping("/users")
    public ApiResponse<Page> users(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(search.users(q, page, size));
    }

    @GetMapping("/channels")
    public ApiResponse<Page> channels(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(search.channels(q, page, size));
    }

    @GetMapping("/autocomplete")
    public ApiResponse<List<Suggestion>> autocomplete(@RequestParam String q, @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.ok(search.autocomplete(q, Math.min(Math.max(limit, 1), 10)));
    }

    @GetMapping("/hashtags")
    public ApiResponse<List<TagCount>> hashtags(@RequestParam(defaultValue = "") String q,
                                                @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(search.hashtags(q, Math.min(Math.max(limit, 1), 50)));
    }
}
