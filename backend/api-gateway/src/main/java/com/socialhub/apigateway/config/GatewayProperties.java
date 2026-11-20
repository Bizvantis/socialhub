package com.socialhub.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * app.gateway.* settings. A record bound with @ConfigurationProperties (not @Value)
 * because @Value cannot bind YAML lists.
 *
 * @param publicGetPrefixes      path prefixes where GET works without a token
 * @param optionalAuthPostPaths  path patterns (e.g. /api/videos/{id}/views) where POST works
 *                               without a token; a token, if sent, is still validated
 */
@ConfigurationProperties(prefix = "app.gateway")
public record GatewayProperties(List<String> publicGetPrefixes, List<String> optionalAuthPostPaths) {

    public GatewayProperties {
        publicGetPrefixes = publicGetPrefixes == null ? List.of() : List.copyOf(publicGetPrefixes);
        optionalAuthPostPaths = optionalAuthPostPaths == null ? List.of() : List.copyOf(optionalAuthPostPaths);
    }
}
