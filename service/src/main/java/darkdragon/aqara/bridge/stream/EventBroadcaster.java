package darkdragon.aqara.bridge.stream;

import darkdragon.aqara.bridge.model.AqaraEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class EventBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventBroadcaster.class);

    private final Object emitLock = new Object();
    private final Sinks.Many<AqaraEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(AqaraEvent event) {
        synchronized (emitLock) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isSuccess() || result == Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                return;
            }

            LOGGER.warn(
                    "Dropping Aqara event subjectId={} resourceId={} msgId={} because sink emit failed: {}",
                    event.subjectId(),
                    event.resourceId(),
                    event.msgId(),
                    result
            );
        }
    }

    public Flux<AqaraEvent> events() {
        return sink.asFlux();
    }
}
