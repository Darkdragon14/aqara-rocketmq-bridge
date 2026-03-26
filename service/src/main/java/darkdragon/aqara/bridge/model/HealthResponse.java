package darkdragon.aqara.bridge.model;

import java.time.Instant;

public record HealthResponse(
        String status,
        boolean rocketmqEnabled,
        boolean rocketmqStarted,
        Instant lastMessageAt,
        String publicUrl,
        String topic,
        String nameserver,
        String lastError
) {
}
