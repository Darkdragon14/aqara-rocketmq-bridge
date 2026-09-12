package darkdragon.aqara.bridge.model;

import java.time.Instant;
import java.util.List;

public record HealthResponse(
        String status,
        boolean rocketmqEnabled,
        boolean rocketmqStarted,
        boolean consumerRegistered,
        int assignedQueueCount,
        Instant lastMessageAt,
        String publicUrl,
        String nameserver,
        String lastError,
        List<String> capabilities,
        long heartbeatIntervalSeconds
) {
}
