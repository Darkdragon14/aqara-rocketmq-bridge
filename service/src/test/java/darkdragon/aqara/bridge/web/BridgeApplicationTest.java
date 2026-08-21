package darkdragon.aqara.bridge.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import darkdragon.aqara.bridge.stream.EventBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void rocketMqLoggingUsesSlf4jAtWarnLevel() {
        assertThat(System.getProperty("rocketmq.client.logUseSlf4j")).isEqualTo("true");
        for (String loggerName : List.of("RocketmqClient", "RocketmqCommon", "RocketmqRemoting")) {
            Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
            assertThat(logger.getEffectiveLevel()).as(loggerName).isEqualTo(Level.WARN);
        }
    }

    @Test
    void rocketMqClientLoggerSelectsSlf4jBackendInFreshJvm() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(
                java,
                "-cp",
                classpath,
                RocketMqLoggingProbe.class.getName()
        ).redirectErrorStream(true).start();

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).as(output).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        assertThat(output).contains("Slf4jLoggerFactory");
    }
}
