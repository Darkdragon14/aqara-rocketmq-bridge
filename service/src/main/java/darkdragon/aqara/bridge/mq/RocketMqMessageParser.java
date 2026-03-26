package darkdragon.aqara.bridge.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import darkdragon.aqara.bridge.model.AqaraEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class RocketMqMessageParser {

    private final ObjectMapper objectMapper;

    public RocketMqMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<AqaraEvent> parse(String payload) throws IOException {
        JsonNode root = objectMapper.readTree(payload);
        String msgType = text(root, "msgType");
        if (!"resource_report".equals(msgType)) {
            return List.of();
        }

        String msgId = text(root, "msgId");
        String openId = text(root, "openId");
        List<AqaraEvent> events = new ArrayList<>();

        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }

        for (JsonNode item : data) {
            events.add(new AqaraEvent(
                    msgType,
                    text(item, "subjectId"),
                    text(item, "resourceId"),
                    text(item, "value"),
                    parseLong(item.path("time")),
                    item.path("statusCode").asInt(),
                    parseTriggerSource(item.path("triggerSource")),
                    text(item, "attach"),
                    msgId,
                    openId
            ));
        }

        return events;
    }

    private AqaraEvent.TriggerSource parseTriggerSource(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        Integer type = node.hasNonNull("type") ? node.path("type").asInt() : null;
        Long time = node.hasNonNull("time") ? parseLong(node.path("time")) : null;
        String id = text(node, "id");
        return new AqaraEvent.TriggerSource(type, time, id);
    }

    private long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0L;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }
}
