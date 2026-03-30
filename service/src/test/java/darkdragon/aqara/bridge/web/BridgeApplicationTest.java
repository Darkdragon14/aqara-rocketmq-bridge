package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.stream.EventBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(properties = {
        "APP_ID=test-app",
        "KEY_ID=test-key",
        "APP_KEY=test-secret",
        "BRIDGE_TOKEN=test-token",
        "ROCKETMQ_ENABLED=false",
        "BRIDGE_PUBLIC_URL=https://aqara.darkdragon.fr"
})
@AutoConfigureWebTestClient
class BridgeApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private EventBroadcaster eventBroadcaster;

    @Test
    void healthIncludesPublicUrlWithoutTopic() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.publicUrl").isEqualTo("https://aqara.darkdragon.fr")
                .jsonPath("$.topic").doesNotExist()
                .jsonPath("$.rocketmqEnabled").isEqualTo(false);
    }

    @Test
    void eventsRequireBearerToken() {
        webTestClient.get()
                .uri("/events")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void eventsAcceptConfiguredBearerToken() {
        webTestClient.get()
                .uri("/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, "text/event-stream;charset=UTF-8");
    }
}
