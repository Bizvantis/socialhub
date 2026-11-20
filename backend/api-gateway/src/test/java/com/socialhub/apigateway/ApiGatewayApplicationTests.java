package com.socialhub.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Self-contained context test: no Config Server, Eureka or Redis needed, so it runs the same on a
 * laptop and in CI. (Redis connections are lazy; nothing connects during startup.)
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "app.jwt.secret=test_secret_test_secret_test_secret_test_secret_1234",
        "app.gateway.public-get-prefixes=/api/videos/",
        "app.gateway.optional-auth-post-paths=/api/videos/{id}/views"
})
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
