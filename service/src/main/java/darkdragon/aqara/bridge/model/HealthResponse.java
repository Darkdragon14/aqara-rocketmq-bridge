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
        Instant lastRawMessageAt,
        Instant lastParsedMessageAt,
        Instant lastPublishedEventAt,
        long rawMessageCount,
        long parsedMessageCount,
        long publishedEventCount,
        long ignoredMessageCount,
        long processingErrorCount,
        String lastMessageType,
        String lastIgnoredReason,
        String publicUrl,
        String nameserver,
        String lastError,
        List<String> capabilities,
        long heartbeatIntervalSeconds
) {
}
