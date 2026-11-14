
package com.socialhub.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final SecretKey signingKey;

    public JwtAuthFilter(
            @Value("${app.jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        // Remove identity headers supplied by clients.
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-Username");
                    headers.remove("X-User-Roles");
                });

        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // Public endpoints.
        boolean publicRoute =
                method == HttpMethod.OPTIONS
                || (path.startsWith("/api/auth/")
                    && (method == HttpMethod.POST
                        || method == HttpMethod.GET))
                || path.equals("/actuator/health");

        if (publicRoute) {
            return chain.filter(
                    exchange.mutate()
                            .request(requestBuilder.build())
                            .build()
            );
        }

        // Protected endpoints require a Bearer token.
        String authHeader = request.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid token");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);

            Object rolesClaim = claims.get("roles");

            final String roles;

            if (rolesClaim instanceof List<?> roleList) {
                roles = String.join(",", roleList.stream()
                        .map(Object::toString)
                        .toList());
            } else {
                roles = "";
            }

            if (userId == null || username == null) {
                return unauthorized(exchange, "Invalid token claims");
            }

            requestBuilder.headers(headers -> {
                headers.set("X-User-Id", userId);
                headers.set("X-Username", username);
                headers.set("X-User-Roles", roles);
            });

            return chain.filter(
                    exchange.mutate()
                            .request(requestBuilder.build())
                            .build()
            );

        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> unauthorized(
            ServerWebExchange exchange,
            String message) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"success":false,"message":"%s"}
                """.formatted(message);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8)))
        );
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
