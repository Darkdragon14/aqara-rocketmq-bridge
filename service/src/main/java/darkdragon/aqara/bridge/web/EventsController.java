package darkdragon.aqara.bridge.web;

import darkdragon.aqara.bridge.config.BridgeProperties;
import darkdragon.aqara.bridge.model.AqaraEvent;
import darkdragon.aqara.bridge.stream.EventBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

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
        Flux<ServerSentEvent<Object>> initial = Flux.just(ServerSentEvent.builder((Object) null)
                .event("ready")
                .comment("connected")
                .build());

        Flux<ServerSentEvent<Object>> events = eventBroadcaster.events()
                .map(this::toEventMessage);

        Flux<ServerSentEvent<Object>> heartbeat = Flux.interval(
                        Duration.ofSeconds(bridgeProperties.getHeartbeatIntervalSeconds())
                )
                .map(sequence -> ServerSentEvent.builder((Object) null)
                        .event("heartbeat")
                        .comment("keepalive")
                        .build());

        return Flux.concat(initial, Flux.merge(events, heartbeat));
    }

    private ServerSentEvent<Object> toEventMessage(AqaraEvent event) {
        return ServerSentEvent.builder((Object) event)
                .event(event.type())
                .id(event.msgId() != null ? event.msgId() : null)
                .build();
    }
}
