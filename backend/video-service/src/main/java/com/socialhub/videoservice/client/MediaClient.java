package com.socialhub.videoservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.web.ApiException;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Calls media-service's internal API through Eureka ("lb://media-service").
 * Used synchronously only where the answer is needed to accept a request:
 * "does this media exist, belong to you and finish uploading?"
 */
@Component
public class MediaClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MediaInfo(Long id, Long ownerId, String purpose, String mediaType, String status,
                            Integer durationSeconds) {
        public boolean ready() { return "READY".equals(status); }
        public boolean failed() { return "FAILED".equals(status) || "DELETED".equals(status); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(boolean success, String message, MediaInfo data) {}

    private final RestClient http;

    public MediaClient(@LoadBalanced RestClient.Builder lbBuilder) {
        this.http = lbBuilder.baseUrl("http://media-service").build();
    }

    public MediaInfo get(Long mediaId) {
        try {
            Envelope e = http.get().uri("/internal/media/{id}", mediaId).retrieve()
                    .body(new ParameterizedTypeReference<Envelope>() {});
            if (e == null || e.data() == null) throw ApiException.badRequest("Media " + mediaId + " not found");
            return e.data();
        } catch (HttpClientErrorException.NotFound ex) {
            throw ApiException.badRequest("Media " + mediaId + " not found");
        } catch (ResourceAccessException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Media service unavailable, try again");
        }
    }

    /** Validates that a media id may be attached by this owner for the given purpose(s). */
    public MediaInfo requireAttachable(Long mediaId, Long ownerId, String... purposes) {
        MediaInfo m = get(mediaId);
        if (!m.ownerId().equals(ownerId)) throw ApiException.forbidden("Media " + mediaId + " is not yours");
        if (purposes.length > 0 && !java.util.Arrays.asList(purposes).contains(m.purpose())) {
            throw ApiException.badRequest("Media " + mediaId + " has purpose " + m.purpose());
        }
        if ("PENDING".equals(m.status()) || m.failed()) {
            throw ApiException.badRequest("Media " + mediaId + " is not uploaded (status " + m.status() + ")");
        }
        return m;
    }

    /**
     * Two builders on purpose:
     *  - @Primary plain builder: what everything else (including the Eureka client itself)
     *    gets when it asks for a RestClient.Builder. If the load-balanced one were the only
     *    builder, Eureka would try to resolve "localhost" as a service name and never register.
     *  - @LoadBalanced builder: resolves "http://media-service" through Eureka.
     */
    @Configuration
    static class LoadBalancedClientConfig {
        @Bean
        @Primary
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        @LoadBalanced
        RestClient.Builder loadBalancedRestClientBuilder() {
            return RestClient.builder();
        }
    }
}
