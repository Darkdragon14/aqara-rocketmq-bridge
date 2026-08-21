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
    }
}
