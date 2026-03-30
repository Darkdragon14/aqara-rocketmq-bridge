package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.config.BridgeProperties;
import darkdragon.aqara.bridge.model.HealthResponse;
import darkdragon.aqara.bridge.mq.RocketMqHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final BridgeProperties bridgeProperties;
    private final RocketMqHealth rocketMqHealth;

    public HealthController(BridgeProperties bridgeProperties, RocketMqHealth rocketMqHealth) {
        this.bridgeProperties = bridgeProperties;
        this.rocketMqHealth = rocketMqHealth;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        boolean started = bridgeProperties.isRocketmqEnabled() && rocketMqHealth.isStarted();
        String status = bridgeProperties.isRocketmqEnabled()
                ? (started ? "up" : "starting")
                : "degraded";

        return new HealthResponse(
                status,
                bridgeProperties.isRocketmqEnabled(),
                started,
                rocketMqHealth.getLastMessageAt(),
                bridgeProperties.getBridgePublicUrl(),
                bridgeProperties.getMqNamesrvAddr(),
                rocketMqHealth.getLastError()
        );
    }
}
