package com.socialhub.common.client;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Service-to-service calls through Eureka, e.g. {@code api.get("http://media-service/internal/media/{id}", ...)}.
 *
 * Unwraps the {@link ApiResponse} envelope and maps failures consistently:
 * 404 becomes an empty Optional, while network errors and 5xx become a 503 for our
 * own caller, so a dependency outage never surfaces as a confusing 500.
 *
 * Synchronous calls are used only where the answer is needed to accept a request
 * (validation). Everything else between services goes through Kafka events.
 */
public class InternalApi {

    private final RestClient http;

    public InternalApi(RestClient.Builder loadBalancedBuilder) {
        this.http = loadBalancedBuilder.build();
    }

    public <T> Optional<T> get(String uri, ParameterizedTypeReference<ApiResponse<T>> type, Object... vars) {
        try {
            ApiResponse<T> r = http.get().uri(uri, vars).retrieve().body(type);
            return Optional.ofNullable(r == null ? null : r.data());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw unavailable();
        } catch (IllegalStateException e) {
            // Spring Cloud LoadBalancer: "No instances available for <service>" (not registered yet / down).
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is unavailable, try again");
    }

    public <T> T post(String uri, Object body, ParameterizedTypeReference<ApiResponse<T>> type, Object... vars) {
        try {
            ApiResponse<T> r = http.post().uri(uri, vars).body(body).retrieve().body(type);
            return r == null ? null : r.data();
        } catch (ResourceAccessException | HttpServerErrorException | IllegalStateException e) {
            throw unavailable();
        }
    }
}
