package com.socialhub.searchservice.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Minimal Elasticsearch REST client. Queries are plain JSON (the ES query DSL), which keeps
 * them readable and independent of client-library versions.
 *
 * Writes use external versioning: every event carries a monotonically increasing "version",
 * and ES itself rejects an index/delete whose version is not newer than the stored one (409).
 * That is how duplicate and out-of-order events are made harmless without any extra state.
 */
@Component
public class EsClient {

    private static final Logger log = LoggerFactory.getLogger(EsClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient http;

    public EsClient(@Value("${app.elasticsearch.url:http://localhost:9200}") String url) {
        this.http = RestClient.builder().baseUrl(url).build();
    }

    public boolean indexExists(String index) {
        try {
            http.head().uri("/{i}", index).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    public void createIndex(String index, String settingsAndMappingsJson) {
        http.put().uri("/{i}", index).contentType(MediaType.APPLICATION_JSON).body(settingsAndMappingsJson)
                .retrieve().toBodilessEntity();
        log.info("Created index {}", index);
    }

    /** @return false if ES rejected it as stale (an equal or newer version is already indexed). */
    public boolean index(String index, String id, Map<String, Object> doc, long version) {
        try {
            http.put().uri("/{i}/_doc/{id}?version={v}&version_type=external", index, id, version)
                    .contentType(MediaType.APPLICATION_JSON).body(doc).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.Conflict e) {
            log.debug("Stale event ignored for {}/{} v{}", index, id, version);
            return false;
        }
    }

    public void delete(String index, String id, long version) {
        try {
            http.delete().uri("/{i}/_doc/{id}?version={v}&version_type=external", index, id, version)
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict e) {
            // already gone, or a newer version exists: both fine
        }
    }

    /** Painless script update for counters (views, followers); missing docs are ignored. */
    public void increment(String index, String id, String field, long delta) {
        Map<String, Object> body = Map.of("script", Map.of(
                "source", "if (ctx._source." + field + " == null) { ctx._source." + field + " = 0 } ctx._source." + field + " += params.d",
                "lang", "painless", "params", Map.of("d", delta)));
        try {
            http.post().uri("/{i}/_update/{id}?retry_on_conflict=3", index, id)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            // not indexed (yet): the next snapshot event carries the absolute value
        }
    }

    public Map<String, Object> search(String index, Map<String, Object> query) {
        return http.post().uri("/{i}/_search", index).contentType(MediaType.APPLICATION_JSON).body(query)
                .retrieve().body(MAP);
    }

    public void refresh(String index) {
        http.post().uri("/{i}/_refresh", index).retrieve().toBodilessEntity();
    }
}
