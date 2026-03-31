package darkdragon.aqara.bridge.stream;

import darkdragon.aqara.bridge.model.AqaraEventBatch;
import darkdragon.aqara.bridge.model.AqaraEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EventBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBroadcaster.class);

    private final Object stateLock = new Object();
    private final AtomicLong cursor = new AtomicLong();
    private final ConcurrentMap<EventKey, VersionedEvent> latestEvents = new ConcurrentHashMap<>();

    public void publish(AqaraEvent event) {
        if (isBlank(event.subjectId()) || isBlank(event.resourceId())) {
            LOGGER.debug(
                    "Ignoring Aqara event without routing key subjectId={} resourceId={} msgId={}",
                    event.subjectId(),
                    event.resourceId(),
                    event.msgId()
            );
            return;
        }

        synchronized (stateLock) {
            long nextCursor = cursor.incrementAndGet();
            latestEvents.put(
                    new EventKey(event.subjectId(), event.resourceId()),
                    new VersionedEvent(nextCursor, event)
            );
        }
    }

    public AqaraEventBatch snapshot() {
        synchronized (stateLock) {
            long snapshotCursor = cursor.get();
            List<AqaraEvent> events = latestEvents.values().stream()
                    .filter(versionedEvent -> versionedEvent.cursor() <= snapshotCursor)
                    .sorted(Comparator.comparingLong(VersionedEvent::cursor))
                    .map(VersionedEvent::event)
                    .toList();
            return new AqaraEventBatch("snapshot", snapshotCursor, events);
        }
    }

    public Optional<AqaraEventBatch> batchSince(long lastCursor) {
        synchronized (stateLock) {
            long batchCursor = cursor.get();
            if (batchCursor <= lastCursor) {
                return Optional.empty();
            }

            List<AqaraEvent> events = latestEvents.values().stream()
                    .filter(versionedEvent -> versionedEvent.cursor() > lastCursor && versionedEvent.cursor() <= batchCursor)
                    .sorted(Comparator.comparingLong(VersionedEvent::cursor))
                    .map(VersionedEvent::event)
                    .toList();

            if (events.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new AqaraEventBatch("batch", batchCursor, events));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record EventKey(String subjectId, String resourceId) {
    }

    private record VersionedEvent(long cursor, AqaraEvent event) {
    }
}
