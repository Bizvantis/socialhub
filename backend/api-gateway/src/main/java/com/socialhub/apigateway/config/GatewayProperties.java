package com.socialhub.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * app.gateway.* settings. A record bound with @ConfigurationProperties (not @Value)
 * because @Value cannot bind YAML lists.
 *
 * @param publicGetPrefixes path prefixes where GET works without a token
 */
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties(List<String> publicGetPrefixes) {

    public GatewayProperties {
        publicGetPrefixes = publicGetPrefixes == null ? List.of() : List.copyOf(publicGetPrefixes);
    }
}
