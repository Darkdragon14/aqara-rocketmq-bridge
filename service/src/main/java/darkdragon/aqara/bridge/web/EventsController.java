package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.config.BridgeProperties;
import darkdragon.aqara.bridge.model.AqaraEventBatch;
import darkdragon.aqara.bridge.stream.EventBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class EventsController {

    private final EventBroadcaster eventBroadcaster;
    private final BridgeProperties bridgeProperties;

    public EventsController(EventBroadcaster eventBroadcaster, BridgeProperties bridgeProperties) {
        this.eventBroadcaster = eventBroadcaster;
        this.bridgeProperties = bridgeProperties;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> streamEvents() {
        AqaraEventBatch snapshot = eventBroadcaster.snapshot();
        AtomicLong lastCursor = new AtomicLong(snapshot.cursor());

        Flux<ServerSentEvent<Object>> initial = Flux.just(toBatchMessage(snapshot));

        Flux<ServerSentEvent<Object>> events = Flux.interval(Duration.ofMillis(bridgeProperties.getBatchIntervalMs()))
                .<ServerSentEvent<Object>>handle((sequence, sink) -> eventBroadcaster.batchSince(lastCursor.get()).ifPresent(batch -> {
                    lastCursor.set(batch.cursor());
                    sink.next(toBatchMessage(batch));
                }));

        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(
                        Duration.ofSeconds(bridgeProperties.getHeartbeatIntervalSeconds())
                )
                .map(sequence -> ServerSentEvent.builder((Object) null)
                        .event("heartbeat")
                        .comment("keepalive")
                        .build());

        return Flux.concat(initial, Flux.merge(events, heartbeat));
    }

    private ServerSentEvent<Object> toBatchMessage(AqaraEventBatch batch) {
        return ServerSentEvent.builder((Object) batch)
                .event(batch.type())
                .id(Long.toString(batch.cursor()))
                .build();
    }
}
