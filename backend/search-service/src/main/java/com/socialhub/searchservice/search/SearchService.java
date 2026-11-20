package com.socialhub.searchservice.search;

import com.socialhub.common.web.PageSize;
import com.socialhub.searchservice.es.EsClient;
import com.socialhub.searchservice.es.Indices;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Search queries (Step 27). Everything is built as the plain Elasticsearch query DSL.
 *
 * Relevance: multi_match over weighted fields (title^3 > tags^2 = channel^2 > description) with
 * fuzziness AUTO for typos, plus a small popularity boost (log of views) so equally relevant
 * results favour what people actually watch.
 */
@Service
public class SearchService {

    public record Hit(String type, double score, Map<String, Object> doc, Map<String, Object> highlight) {}

    public record Page(List<Hit> items, long total, int page, int size) {}

    public record Suggestion(String type, String text, Object id) {}

    public record TagCount(String tag, long count) {}

    private static final int MAX_WINDOW = 10_000; // ES from+size limit

    private final EsClient es;

    public SearchService(EsClient es) {
        this.es = es;
    }

    // ---- videos -------------------------------------------------------

    public Page videos(String q, String category, String tag, Integer minDuration, Integer maxDuration,
                       String uploadedWithin, String sort, int page, int size) {
        List<Object> filters = new ArrayList<>();
        if (notBlank(category)) filters.add(term("category", category.toUpperCase(Locale.ROOT)));
        if (notBlank(tag)) filters.add(term("tags", tag.toLowerCase(Locale.ROOT).replace("#", "")));
        if (minDuration != null || maxDuration != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            if (minDuration != null) r.put("gte", minDuration);
            if (maxDuration != null) r.put("lte", maxDuration);
            filters.add(Map.of("range", Map.of("durationSeconds", r)));
        }
        Instant since = since(uploadedWithin);
        if (since != null) filters.add(Map.of("range", Map.of("publishedAt", Map.of("gte", since.toString()))));

        Object match = notBlank(q)
                ? Map.of("multi_match", Map.of("query", q, "type", "best_fields", "fuzziness", "AUTO",
                        "fields", List.of("title^3", "title.auto", "tags^2", "channelName^2", "description")))
                : Map.of("match_all", Map.of());
        // Popularity boost: + log1p(views) * 0.3 on top of text relevance.
        Map<String, Object> scored = Map.of("function_score", Map.of(
                "query", Map.of("bool", Map.of("must", match, "filter", filters)),
                "field_value_factor", Map.of("field", "viewCount", "modifier", "log1p", "factor", 0.3, "missing", 0),
                "boost_mode", "sum"));

        List<Object> sorts = switch (sort == null ? "relevance" : sort) {
            case "views" -> List.of(Map.of("viewCount", "desc"), Map.of("publishedAt", "desc"));
            case "date" -> List.of(Map.of("publishedAt", "desc"));
            default -> List.of("_score", Map.of("publishedAt", "desc"));
        };
        return run(Indices.VIDEOS, "VIDEO", scored, sorts, List.of("title", "description"), page, size);
    }

    /** "More like this" over title/description/tags: relevance-ranked related videos. */
    public Page relatedVideos(long videoId, int size) {
        Map<String, Object> mlt = Map.of("bool", Map.of(
                "must", Map.of("more_like_this", Map.of(
                        "fields", List.of("title", "description", "tags"),
                        "like", List.of(Map.of("_index", Indices.VIDEOS, "_id", String.valueOf(videoId))),
                        "min_term_freq", 1, "min_doc_freq", 1, "max_query_terms", 25)),
                "must_not", term("videoId", videoId)));
        return run(Indices.VIDEOS, "VIDEO", mlt, List.of("_score"), List.of(), 0, size);
    }

    // ---- posts / users / channels --------------------------------------

    public Page posts(String q, String hashtag, String type, String sort, int page, int size) {
        List<Object> filters = new ArrayList<>();
        if (notBlank(hashtag)) filters.add(term("hashtags", hashtag.toLowerCase(Locale.ROOT).replace("#", "")));
        if (notBlank(type)) filters.add(term("type", type.toUpperCase(Locale.ROOT)));
        Object match = notBlank(q)
                ? Map.of("multi_match", Map.of("query", q, "fuzziness", "AUTO", "fields", List.of("caption", "hashtags^2")))
                : Map.of("match_all", Map.of());
        Map<String, Object> query = Map.of("bool", Map.of("must", match, "filter", filters));
        List<Object> sorts = "date".equals(sort) ? List.of(Map.of("createdAt", "desc"))
                : List.of("_score", Map.of("createdAt", "desc"));
        return run(Indices.POSTS, "POST", query, sorts, List.of("caption"), page, size);
    }

    public Page users(String q, int page, int size) {
        if (!notBlank(q)) return new Page(List.of(), 0, page, size);
        Map<String, Object> query = Map.of("bool", Map.of("should", List.of(
                Map.of("term", Map.of("username", Map.of("value", q.toLowerCase(Locale.ROOT), "boost", 10))),
                Map.of("match", Map.of("username.auto", Map.of("query", q, "boost", 3))),
                Map.of("match", Map.of("displayName.auto", Map.of("query", q, "boost", 2))),
                Map.of("match", Map.of("displayName", Map.of("query", q, "fuzziness", "AUTO"))),
                Map.of("match", Map.of("bio", q))), "minimum_should_match", 1));
        return run(Indices.USERS, "USER", query, List.of("_score", Map.of("followerCount", "desc")), List.of(), page, size);
    }

    public Page channels(String q, int page, int size) {
        if (!notBlank(q)) return new Page(List.of(), 0, page, size);
        Map<String, Object> query = Map.of("bool", Map.of("should", List.of(
                Map.of("term", Map.of("handle", Map.of("value", q.toLowerCase(Locale.ROOT).replace("@", ""), "boost", 10))),
                Map.of("match", Map.of("handle.auto", Map.of("query", q, "boost", 3))),
                Map.of("match", Map.of("name.auto", Map.of("query", q, "boost", 2))),
                Map.of("match", Map.of("name", Map.of("query", q, "fuzziness", "AUTO"))),
                Map.of("match", Map.of("description", q))), "minimum_should_match", 1));
        return run(Indices.CHANNELS, "CHANNEL", query, List.of("_score", Map.of("subscriberCount", "desc")), List.of(), page, size);
    }

    /** Top results of every kind for the combined results page. */
    public Map<String, Page> all(String q, int perType) {
        Map<String, Page> out = new LinkedHashMap<>();
        out.put("videos", videos(q, null, null, null, null, null, "relevance", 0, perType));
        out.put("channels", channels(q, 0, perType));
        out.put("users", users(q, 0, perType));
        out.put("posts", posts(q, null, null, "relevance", 0, perType));
        return out;
    }

    // ---- autocomplete & hashtags ---------------------------------------

    /** As-you-type suggestions: video titles and users (edge n-grams) and hashtags (prefix aggregation). */
    public List<Suggestion> autocomplete(String prefix, int limit) {
        if (!notBlank(prefix) || prefix.trim().length() < 2) return List.of();
        String p = prefix.trim();
        List<Suggestion> out = new ArrayList<>();
        if (p.startsWith("#")) {
            hashtags(p, limit).forEach(t -> out.add(new Suggestion("HASHTAG", "#" + t.tag(), t.tag())));
            return out;
        }
        Map<String, Object> vq = Map.of("size", limit, "_source", List.of("videoId", "title"),
                "query", Map.of("match", Map.of("title.auto", Map.of("query", p, "operator", "and"))),
                "sort", List.of("_score", Map.of("viewCount", "desc")));
        for (Map<String, Object> h : hits(es.search(Indices.VIDEOS, vq))) {
            Map<?, ?> src = (Map<?, ?>) h.get("_source");
            out.add(new Suggestion("VIDEO", String.valueOf(src.get("title")), src.get("videoId")));
        }
        Map<String, Object> uq = Map.of("size", limit, "_source", List.of("userId", "username"),
                "query", Map.of("match", Map.of("username.auto", p)),
                "sort", List.of("_score", Map.of("followerCount", "desc")));
        for (Map<String, Object> h : hits(es.search(Indices.USERS, uq))) {
            Map<?, ?> src = (Map<?, ?>) h.get("_source");
            out.add(new Suggestion("USER", "@" + src.get("username"), src.get("userId")));
        }
        hashtags(p, 3).forEach(t -> out.add(new Suggestion("HASHTAG", "#" + t.tag(), t.tag())));
        return out.stream().limit(limit * 2L).toList();
    }

    /**
     * Hashtag search with usage counts, computed by a terms aggregation over indexed posts and
     * video tags. No separate counter to keep in sync: counts are always exactly what is indexed.
     */
    public List<TagCount> hashtags(String prefix, int limit) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT).replace("#", "").trim();
        String include = p.isEmpty() ? ".*" : escapeRegex(p) + ".*";
        Map<String, Long> counts = new HashMap<>();
        for (String[] src : List.of(new String[]{Indices.POSTS, "hashtags"}, new String[]{Indices.VIDEOS, "tags"})) {
            Map<String, Object> q = Map.of("size", 0, "aggs", Map.of("t", Map.of("terms",
                    Map.of("field", src[1], "include", include, "size", Math.max(limit, 10)))));
            Map<?, ?> aggs = (Map<?, ?>) es.search(src[0], q).get("aggregations");
            if (aggs == null) continue;
            for (Object b : (List<?>) ((Map<?, ?>) aggs.get("t")).get("buckets")) {
                Map<?, ?> bucket = (Map<?, ?>) b;
                counts.merge(String.valueOf(bucket.get("key")), ((Number) bucket.get("doc_count")).longValue(), Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit).map(e -> new TagCount(e.getKey(), e.getValue())).toList();
    }

    // ---- helpers --------------------------------------------------------

    private Page run(String index, String type, Object query, List<Object> sort, List<String> highlightFields,
                     int page, int size) {
        int s = PageSize.clamp(size);
        int from = Math.max(page, 0) * s;
        if (from + s > MAX_WINDOW) return new Page(List.of(), 0, page, s); // deep paging: use filters instead
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from);
        body.put("size", s);
        body.put("query", query);
        body.put("sort", sort);
        body.put("track_total_hits", true);
        if (!highlightFields.isEmpty()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            highlightFields.forEach(f -> fields.put(f, Map.of()));
            body.put("highlight", Map.of("fields", fields, "pre_tags", List.of("<em>"), "post_tags", List.of("</em>")));
        }
        Map<String, Object> res = es.search(index, body);
        List<Hit> items = new ArrayList<>();
        for (Map<String, Object> h : hits(res)) {
            Object sc = h.get("_score");
            @SuppressWarnings("unchecked") Map<String, Object> src = (Map<String, Object>) h.get("_source");
            @SuppressWarnings("unchecked") Map<String, Object> hl = (Map<String, Object>) h.getOrDefault("highlight", Map.of());
            items.add(new Hit(type, sc instanceof Number n ? n.doubleValue() : 0, src, hl));
        }
        Map<?, ?> total = (Map<?, ?>) ((Map<?, ?>) res.get("hits")).get("total");
        return new Page(items, ((Number) total.get("value")).longValue(), Math.max(page, 0), s);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> hits(Map<String, Object> res) {
        return (List<Map<String, Object>>) ((Map<String, Object>) res.get("hits")).get("hits");
    }

    private static Map<String, Object> term(String field, Object value) {
        return Map.of("term", Map.of(field, value));
    }

    private static Instant since(String within) {
        if (within == null) return null;
        return switch (within) {
            case "hour" -> Instant.now().minus(Duration.ofHours(1));
            case "day" -> Instant.now().minus(Duration.ofDays(1));
            case "week" -> Instant.now().minus(Duration.ofDays(7));
            case "month" -> Instant.now().minus(Duration.ofDays(30));
            case "year" -> Instant.now().minus(Duration.ofDays(365));
            default -> null;
        };
    }

    /** Lucene regex (used by "include") reserves these characters. */
    private static String escapeRegex(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (".?+*|{}[]()\"\\#@&<>~".indexOf(c) >= 0) b.append('\\');
            b.append(c);
        }
        return b.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
