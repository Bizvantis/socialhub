
package com.socialhub.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import org.springframework.context.annotation.Primary;

@Configuration
public class RateLimitConfig {

    @Primary
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest()
                        .getRemoteAddress() != null
                        ? exchange.getRequest()
                            .getRemoteAddress()
                            .getAddress()
                            .getHostAddress()
                        : "unknown"
        );
    }

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");

            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            return Mono.just("ip:" +
                    (exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress()
                        .getAddress().getHostAddress()
                    : "unknown"));
        };
    }
}
