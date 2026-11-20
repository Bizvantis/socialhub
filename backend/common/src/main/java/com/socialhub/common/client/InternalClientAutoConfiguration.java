package com.socialhub.common.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * Two RestClient builders, on purpose:
 *  - @Primary plain builder: what everything else (including the Eureka client itself)
 *    receives when it asks for a RestClient.Builder. If the load-balanced builder were the
 *    only one, Eureka would try to resolve "localhost" as a service name and never register.
 *  - @LoadBalanced builder: resolves http://{service-name} through Eureka; used by InternalApi.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration")
@ConditionalOnClass({RestClient.class, LoadBalanced.class})
public class InternalClientAutoConfiguration {

    @Bean
    @Primary
    RestClient.Builder socialhubRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder socialhubLoadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    InternalApi internalApi(@Qualifier("socialhubLoadBalancedRestClientBuilder") RestClient.Builder lb) {
        return new InternalApi(lb);
    }
}
