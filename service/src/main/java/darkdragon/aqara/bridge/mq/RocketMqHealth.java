package darkdragon.aqara.bridge.mq;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RocketMqHealth {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final AtomicBoolean consumerRegistered = new AtomicBoolean(false);
    private final AtomicInteger assignedQueueCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastRawMessageAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastParsedMessageAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastPublishedEventAt = new AtomicReference<>();
    private final AtomicLong rawMessageCount = new AtomicLong();
    private final AtomicLong parsedMessageCount = new AtomicLong();
    private final AtomicLong publishedEventCount = new AtomicLong();
    private final AtomicLong ignoredMessageCount = new AtomicLong();
    private final AtomicLong processingErrorCount = new AtomicLong();
    private final AtomicReference<String> lastMessageType = new AtomicReference<>();
    private final AtomicReference<String> lastIgnoredReason = new AtomicReference<>();
    private final AtomicReference<String> connectionError = new AtomicReference<>();
    private final AtomicReference<String> processingError = new AtomicReference<>();

    public boolean isStarted() {
        return started.get();
    }

    public boolean updateReadiness(boolean registered, int queueCount) {
        consumerRegistered.set(registered);
        assignedQueueCount.set(queueCount);
        boolean ready = registered && queueCount > 0;
        boolean changed = started.getAndSet(ready) != ready;
        failed.set(false);
        connectionError.set(ready ? null : "RocketMQ consumer has no assigned queues");
        return changed;
    }

    public void markStopped() {
        started.set(false);
        consumerRegistered.set(false);
        assignedQueueCount.set(0);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public void markRetrying(String message) {
        started.set(false);
        consumerRegistered.set(false);
        assignedQueueCount.set(0);
        failed.set(false);
        connectionError.set(message);
    }

    public void markFailed(String message) {
        started.set(false);
        consumerRegistered.set(false);
        assignedQueueCount.set(0);
        failed.set(true);
        connectionError.set(message);
    }

    public void markRawMessageReceived() {
        rawMessageCount.incrementAndGet();
        lastRawMessageAt.set(Instant.now());
    }

    public void markMessageParsed(String messageType) {
        parsedMessageCount.incrementAndGet();
        lastParsedMessageAt.set(Instant.now());
        lastMessageType.set(messageType);
    }

    public void markEventsPublished(int eventCount) {
        publishedEventCount.addAndGet(eventCount);
        lastPublishedEventAt.set(Instant.now());
        processingError.set(null);
    }

    public void markMessageIgnored(String messageType, String reason) {
        ignoredMessageCount.incrementAndGet();
        lastMessageType.set(messageType);
        lastIgnoredReason.set(reason);
    }

    public Instant getLastMessageAt() {
        return lastPublishedEventAt.get();
    }

    public Instant getLastRawMessageAt() {
        return lastRawMessageAt.get();
    }

    public Instant getLastParsedMessageAt() {
        return lastParsedMessageAt.get();
    }

    public Instant getLastPublishedEventAt() {
        return lastPublishedEventAt.get();
    }

    public long getRawMessageCount() {
        return rawMessageCount.get();
    }

    public long getParsedMessageCount() {
        return parsedMessageCount.get();
    }

    public long getPublishedEventCount() {
        return publishedEventCount.get();
    }

    public long getIgnoredMessageCount() {
        return ignoredMessageCount.get();
    }

    public long getProcessingErrorCount() {
        return processingErrorCount.get();
    }

    public String getLastMessageType() {
        return lastMessageType.get();
    }

    public String getLastIgnoredReason() {
        return lastIgnoredReason.get();
    }

    public boolean isConsumerRegistered() {
        return consumerRegistered.get();
    }

    public int getAssignedQueueCount() {
        return assignedQueueCount.get();
    }

    public String getLastError() {
        String message = processingError.get();
        return message != null ? message : connectionError.get();
    }

    public void markError(Exception exception) {
        markError(exception.getMessage());
    }

    public void markError(String message) {
        processingErrorCount.incrementAndGet();
        processingError.set(message);
    }
}
