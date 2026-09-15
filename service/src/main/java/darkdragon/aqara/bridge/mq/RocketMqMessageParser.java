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
        return parseDetailed(payload).events();
    }

    public ParseResult parseDetailed(String payload) throws IOException {
        JsonNode root = objectMapper.readTree(payload);
        String msgType = text(root, "msgType");
        if (!"resource_report".equals(msgType) && !"spec_report".equals(msgType)) {
            return new ParseResult(msgType, List.of(), "unsupported_msg_type");
        }

        String msgId = text(root, "msgId");
        String openId = text(root, "openId");
        List<AqaraEvent> events = new ArrayList<>();

        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return new ParseResult(msgType, List.of(), "invalid_data");
        }

        for (JsonNode item : data) {
            Integer statusCode = parseStatusCode(item.path("statusCode"));
            Long eventTime = parseLong(item.path("time"));
            if (statusCode == null || eventTime == null) {
                continue;
            }
            String subjectId;
            String resourceId;
            if ("spec_report".equals(msgType)) {
                subjectId = text(item, "deviceId");
                resourceId = traitCodePath(item);
            } else {
                subjectId = text(item, "subjectId");
                resourceId = text(item, "resourceId");
            }
            events.add(new AqaraEvent(
                    msgType,
                    subjectId,
                    resourceId,
                    "spec_report".equals(msgType) ? value(item.path("value")) : text(item, "value"),
                    eventTime,
                    statusCode,
                    parseTriggerSource(item.path("triggerSource")),
                    text(item, "attach"),
                    msgId,
                    openId
            ));
        }

        return new ParseResult(
                msgType,
                List.copyOf(events),
                events.isEmpty() ? "no_valid_events" : null
        );
    }

    public record ParseResult(String messageType, List<AqaraEvent> events, String ignoredReason) {

        public boolean ignored() {
            return events.isEmpty();
        }
    }

    private String traitCodePath(JsonNode item) {
        String endpointId = text(item, "endpointId");
        String functionCode = text(item, "functionCode");
        String traitCode = text(item, "traitCode");
        if (endpointId == null || functionCode == null || traitCode == null) {
            return null;
        }
        return String.join(".", endpointId, functionCode, traitCode);
    }

    private Object value(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
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

    private Integer parseStatusCode(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            return null;
        }
        return node.intValue();
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0L;
        }
        if (node.isIntegralNumber()) {
            if (!node.canConvertToLong()) {
                return null;
            }
            long value = node.longValue();
            return value >= 0 ? value : null;
        }
        if (!node.isTextual()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
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
