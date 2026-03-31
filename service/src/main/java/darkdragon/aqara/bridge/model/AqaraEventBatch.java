package darkdragon.aqara.bridge.model;

import java.util.List;

public record AqaraEventBatch(
        String type,
        long cursor,
        List<AqaraEvent> events
) {
}
