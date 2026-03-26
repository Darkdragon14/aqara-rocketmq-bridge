package darkdragon.aqara.bridge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import darkdragon.aqara.bridge.model.AqaraEvent;
import darkdragon.aqara.bridge.mq.RocketMqMessageParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RocketMqMessageParserTest {

    private final RocketMqMessageParser parser = new RocketMqMessageParser(new ObjectMapper());

    @Test
    void parsesResourceReportPayload() throws Exception {
        String payload = """
                {
                  \"msgId\": \"abc\",
                  \"openId\": \"open\",
                  \"msgType\": \"resource_report\",
                  \"data\": [
                    {
                      \"subjectId\": \"lumi.xxx\",
                      \"resourceId\": \"3.51.85\",
                      \"value\": \"1\",
                      \"time\": \"1710000000000\",
                      \"statusCode\": 0,
                      \"triggerSource\": {
                        \"type\": 21,
                        \"time\": \"1710000000\",
                        \"id\": \"AL.xxx\"
                      }
                    }
                  ]
                }
                """;

        List<AqaraEvent> events = parser.parse(payload);

        assertThat(events).hasSize(1);
        AqaraEvent event = events.get(0);
        assertThat(event.type()).isEqualTo("resource_report");
        assertThat(event.subjectId()).isEqualTo("lumi.xxx");
        assertThat(event.resourceId()).isEqualTo("3.51.85");
        assertThat(event.value()).isEqualTo("1");
        assertThat(event.time()).isEqualTo(1710000000000L);
        assertThat(event.statusCode()).isZero();
        assertThat(event.msgId()).isEqualTo("abc");
        assertThat(event.openId()).isEqualTo("open");
        assertThat(event.triggerSource()).isNotNull();
        assertThat(event.triggerSource().type()).isEqualTo(21);
    }
}
