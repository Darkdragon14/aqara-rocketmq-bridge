package darkdragon.aqara.bridge.mq;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RocketMqHealth {

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicReference<Instant> lastMessageAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    public boolean isStarted() {
        return started.get();
    }

    public void markStarted() {
        started.set(true);
        lastError.set(null);
    }

    public void markStopped() {
        started.set(false);
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
        lastError.set(exception.getMessage());
    }
}
