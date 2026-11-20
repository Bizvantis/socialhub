package com.socialhub.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate-limit keys.
 *
 * Behind Nginx every request arrives from Nginx's address, so keying on the socket address
 * would make all users share ONE bucket. The real client IP is the last X-Forwarded-For entry,
 * which our own Nginx appends ($proxy_add_x_forwarded_for). maxTrustedIndex(1) trusts only that
 * entry, so a client cannot dodge the limit by sending a fake X-Forwarded-For header.
 * Without the header (direct calls in development) it falls back to the socket address.
 */
@Configuration
public class RateLimitConfig {

    private static final XForwardedRemoteAddressResolver CLIENT_IP = XForwardedRemoteAddressResolver.maxTrustedIndex(1);

    static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress addr = CLIENT_IP.resolve(exchange);
        if (addr == null) return "unknown";
        return addr.getAddress() != null ? addr.getAddress().getHostAddress() : addr.getHostString();
    }

    @Primary
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(clientIp(exchange));
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            return Mono.just("ip:" + clientIp(exchange));
        };
    }
}
