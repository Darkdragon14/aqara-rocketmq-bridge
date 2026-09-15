package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.config.BridgeProperties;
import darkdragon.aqara.bridge.mq.RocketMqHealth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void permanentRocketMqFailureHasErrorStatus() {
        BridgeProperties properties = new BridgeProperties();
        properties.setRocketmqEnabled(true);
        RocketMqHealth health = new RocketMqHealth();
        health.markFailed("access denied");

        var response = new HealthController(properties, health).health();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.rocketmqStarted()).isFalse();
        assertThat(response.lastError()).isEqualTo("access denied");
        assertThat(response.capabilities()).containsExactly("resource_report", "spec_report");
        assertThat(response.heartbeatIntervalSeconds()).isEqualTo(15L);
    }

    @Test
    void readyConsumerIncludesQueueAssignmentDiagnostics() {
        BridgeProperties properties = new BridgeProperties();
        properties.setRocketmqEnabled(true);
        RocketMqHealth health = new RocketMqHealth();
        health.updateReadiness(true, 2);

        var response = new HealthController(properties, health).health();

        assertThat(response.status()).isEqualTo("up");
        assertThat(response.rocketmqStarted()).isTrue();
        assertThat(response.consumerRegistered()).isTrue();
        assertThat(response.assignedQueueCount()).isEqualTo(2);
    }

    @Test
    void includesMessagePipelineDiagnostics() {
        BridgeProperties properties = new BridgeProperties();
        properties.setRocketmqEnabled(true);
        RocketMqHealth health = new RocketMqHealth();
        health.updateReadiness(true, 2);
        health.markRawMessageReceived();
        health.markMessageIgnored("device_online", "unsupported_msg_type");
        health.markRawMessageReceived();
        health.markMessageParsed("spec_report");
        health.markEventsPublished(2);

        var response = new HealthController(properties, health).health();

        assertThat(response.rawMessageCount()).isEqualTo(2);
        assertThat(response.parsedMessageCount()).isOne();
        assertThat(response.publishedEventCount()).isEqualTo(2);
        assertThat(response.ignoredMessageCount()).isOne();
        assertThat(response.lastRawMessageAt()).isNotNull();
        assertThat(response.lastParsedMessageAt()).isNotNull();
        assertThat(response.lastPublishedEventAt()).isNotNull();
        assertThat(response.lastMessageAt()).isEqualTo(response.lastPublishedEventAt());
        assertThat(response.lastMessageType()).isEqualTo("spec_report");
        assertThat(response.lastIgnoredReason()).isEqualTo("unsupported_msg_type");
    }

    @Test
    void readinessRecoveryDoesNotEraseProcessingError() {
        BridgeProperties properties = new BridgeProperties();
        properties.setRocketmqEnabled(true);
        RocketMqHealth health = new RocketMqHealth();
        health.markError("invalid payload");
        health.updateReadiness(true, 2);

        var response = new HealthController(properties, health).health();

        assertThat(response.lastError()).isEqualTo("invalid payload");
        assertThat(response.processingErrorCount()).isOne();
    }
}
