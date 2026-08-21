package darkdragon.aqara.bridge.mq;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RocketMqHealth {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastMessageAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public boolean isStarted() {
        return started.get();
    }

    public void markStarted() {
        started.set(true);
        failed.set(false);
        lastError.set(null);
    }

    public void markStopped() {
        started.set(false);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public void markRetrying(String message) {
        started.set(false);
        failed.set(false);
        lastError.set(message);
    }

    public void markFailed(String message) {
        started.set(false);
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
