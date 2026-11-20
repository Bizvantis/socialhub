package com.socialhub.apigateway.filter;

import com.socialhub.apigateway.config.GatewayProperties;
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
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Edge authentication.
 *
 * 1. Strips any identity headers the client sent (they would otherwise be trusted downstream).
 * 2. Blocks /internal/**: service-to-service APIs are never reachable from outside.
 * 3. Validates the JWT and forwards X-User-Id / X-Username / X-User-Roles.
 *
 * Route classes:
 *  - public:        /api/auth/** (login, register, refresh) and health
 *  - optional-auth: GET on configured public prefixes (anonymous browsing; a token, if sent,
 *                   must still be valid)
 *  - protected:     everything else requires a valid token
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final SecretKey signingKey;
    private final List<String> publicGetPrefixes;

    private final List<PathPattern> optionalAuthPost;

    public JwtAuthFilter(@Value("${app.jwt.secret}") String secret, GatewayProperties props) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.publicGetPrefixes = props.publicGetPrefixes();
        PathPatternParser parser = new PathPatternParser();
        this.optionalAuthPost = props.optionalAuthPostPaths().stream().map(parser::parse).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        if (path.startsWith("/internal/") || path.contains("/internal/")) {
            return reject(exchange, HttpStatus.NOT_FOUND, "Not found");
        }

        ServerHttpRequest.Builder builder = request.mutate().headers(h -> {
            h.remove("X-User-Id");
            h.remove("X-Username");
            h.remove("X-User-Roles");
        });

        boolean publicRoute = method == HttpMethod.OPTIONS
                || (path.startsWith("/api/auth/") && (method == HttpMethod.POST || method == HttpMethod.GET))
                || path.equals("/actuator/health");
        if (publicRoute) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        boolean optionalAuth = (method == HttpMethod.GET && isPublicGet(path))
                || (method == HttpMethod.POST && optionalAuthPost.stream()
                        .anyMatch(p -> p.matches(PathContainer.parsePath(path))));

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if (optionalAuth) {
                return chain.filter(exchange.mutate().request(builder.build()).build());
            }
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Missing or invalid token");
        }

        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(authHeader.substring(7)).getPayload();

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String roles = claims.get("roles") instanceof List<?> list
                    ? String.join(",", list.stream().map(Object::toString).toList())
                    : "";
            if (userId == null || username == null) {
                return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid token claims");
            }

            builder.headers(h -> {
                h.set("X-User-Id", userId);
                h.set("X-Username", username);
                h.set("X-User-Roles", roles);
            });
            return chain.filter(exchange.mutate().request(builder.build()).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    /** "/api/videos/" matches /api/videos, /api/videos/ and /api/videos/42 but not /api/videosX. */
    private boolean isPublicGet(String path) {
        for (String p : publicGetPrefixes) {
            String base = p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
            if (path.equals(base) || path.startsWith(base + "/")) return true;
        }
        return false;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = """
                {"success":false,"message":"%s","data":null}""".formatted(message).getBytes(StandardCharsets.UTF_8);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
