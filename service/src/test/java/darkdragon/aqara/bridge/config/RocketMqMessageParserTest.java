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

    @Test
    void parsesTraitReportPayloadWithTypedValue() throws Exception {
        String payload = """
                {
                  "msgId": "trait-abc",
                  "openId": "open",
                  "msgType": "spec_report",
                  "data": [
                    {
                      "deviceId": "matt.u200",
                      "endpointId": "2",
                      "functionCode": "DoorLock",
                      "traitCode": "DoorState",
                      "value": true,
                      "time": "1762430871166",
                      "statusCode": 0,
                      "attach": "ha_aqara_devices"
                    }
                  ]
                }
                """;

        List<AqaraEvent> events = parser.parse(payload);

        assertThat(events).hasSize(1);
        AqaraEvent event = events.get(0);
        assertThat(event.type()).isEqualTo("spec_report");
        assertThat(event.subjectId()).isEqualTo("matt.u200");
        assertThat(event.resourceId()).isEqualTo("2.DoorLock.DoorState");
        assertThat(event.value()).isEqualTo(true);
        assertThat(event.time()).isEqualTo(1762430871166L);
        assertThat(event.attach()).isEqualTo("ha_aqara_devices");
    }

    @Test
    void rejectsItemsWithoutAnIntegerStatusCode() throws Exception {
        String payload = """
                {
                  "msgType": "spec_report",
                  "data": [
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":1,"time":100},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":2,"time":101,"statusCode":null},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":3,"time":102,"statusCode":"0"},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":4,"time":103,"statusCode":{}},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":5,"time":104,"statusCode":0.5},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":6,"time":105,"statusCode":0}
                  ]
                }
                """;

        List<AqaraEvent> events = parser.parse(payload);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.value()).isEqualTo(6);
            assertThat(event.time()).isEqualTo(105L);
        });
    }

    @Test
    void skipsMalformedTimestampWithoutDroppingValidNeighbors() throws Exception {
        String payload = """
                {
                  "msgType": "spec_report",
                  "data": [
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":1,"time":"100","statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":2,"time":"not-a-time","statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":3,"time":"300","statusCode":0}
                  ]
                }
                """;

        List<AqaraEvent> events = parser.parse(payload);

        assertThat(events)
                .extracting(AqaraEvent::value, AqaraEvent::time)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 100L),
                        org.assertj.core.groups.Tuple.tuple(3, 300L)
                );
    }

    @Test
    void rejectsNegativeNumericAndTextTimestamps() throws Exception {
        String payload = """
                {
                  "msgType": "spec_report",
                  "data": [
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":1,"time":-1,"statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":2,"time":"-2","statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":3,"time":0,"statusCode":0}
                  ]
                }
                """;

        List<AqaraEvent> events = parser.parse(payload);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.value()).isEqualTo(3);
            assertThat(event.time()).isZero();
        });
    }

    @Test
    void normalizesAbsentAndNullTimestampsWithoutDroppingValidNeighbors() throws Exception {
        String payload = """
                {
                  "msgType": "spec_report",
                  "data": [
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":1,"time":100,"statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":2,"statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":3,"time":null,"statusCode":0},
                    {"deviceId":"device","endpointId":2,"functionCode":"DoorLock","traitCode":"LockState","value":4,"time":400,"statusCode":0}
                  ]
                }
                """;

        List<AqaraEvent> events = parser.parse(payload);

        assertThat(events)
                .extracting(AqaraEvent::value, AqaraEvent::time)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, 100L),
                        org.assertj.core.groups.Tuple.tuple(2, 0L),
                        org.assertj.core.groups.Tuple.tuple(3, 0L),
                        org.assertj.core.groups.Tuple.tuple(4, 400L)
                );
    }
}
