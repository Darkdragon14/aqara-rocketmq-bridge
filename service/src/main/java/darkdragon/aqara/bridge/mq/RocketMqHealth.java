package darkdragon.aqara.bridge.mq;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RocketMqHealth {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final AtomicBoolean consumerRegistered = new AtomicBoolean(false);
    private final AtomicInteger assignedQueueCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastMessageAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public boolean isStarted() {
        return started.get();
    }

    public boolean updateReadiness(boolean registered, int queueCount) {
        consumerRegistered.set(registered);
        assignedQueueCount.set(queueCount);
        boolean ready = registered && queueCount > 0;
        boolean changed = started.getAndSet(ready) != ready;
        failed.set(false);
        lastError.set(ready ? null : "RocketMQ consumer has no assigned queues");
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
        lastError.set(message);
    }

    public void markFailed(String message) {
        started.set(false);
        consumerRegistered.set(false);
        assignedQueueCount.set(0);
        failed.set(true);
        lastError.set(message);
    }

    public void markMessageReceived() {
        lastMessageAt.set(Instant.now());
        lastError.set(null);
    }

    public Instant getLastMessageAt() {
        return lastMessageAt.get();
    }

    public boolean isConsumerRegistered() {
        return consumerRegistered.get();
    }

    public int getAssignedQueueCount() {
        return assignedQueueCount.get();
    }

    public String getLastError() {
        return lastError.get();
    }

    public void markError(Exception exception) {
        markError(exception.getMessage());
    }

    public void markError(String message) {
        lastError.set(message);
    }
}
