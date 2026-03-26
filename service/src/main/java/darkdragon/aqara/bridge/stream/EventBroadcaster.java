package darkdragon.aqara.bridge.stream;

import darkdragon.aqara.bridge.model.AqaraEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class EventBroadcaster {

    private final Sinks.Many<AqaraEvent> sink = Sinks.many().multicast().directBestEffort();

    public void publish(AqaraEvent event) {
        sink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
    }

    public Flux<AqaraEvent> events() {
        return sink.asFlux();
    }
}
