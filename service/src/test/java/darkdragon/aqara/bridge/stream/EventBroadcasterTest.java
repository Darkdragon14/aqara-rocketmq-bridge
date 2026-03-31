package darkdragon.aqara.bridge.stream;

import darkdragon.aqara.bridge.model.AqaraEvent;
import darkdragon.aqara.bridge.model.AqaraEventBatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventBroadcasterTest {

    private final EventBroadcaster broadcaster = new EventBroadcaster();

    @Test
    void snapshotKeepsOnlyLatestValuePerKey() {
        broadcaster.publish(event("lumi.one", "3.1.85", "0", "first"));
        broadcaster.publish(event("lumi.one", "3.1.85", "1", "second"));
        broadcaster.publish(event("lumi.one", "3.2.85", "5", "third"));

        AqaraEventBatch snapshot = broadcaster.snapshot();

        assertThat(snapshot.type()).isEqualTo("snapshot");
        assertThat(snapshot.events()).hasSize(2);
        assertThat(snapshot.events())
                .extracting(AqaraEvent::resourceId, AqaraEvent::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("3.1.85", "1"),
                        org.assertj.core.groups.Tuple.tuple("3.2.85", "5")
                );
    }

    @Test
    void batchSinceReturnsOnlyNewLatestUpdates() {
        broadcaster.publish(event("lumi.one", "3.1.85", "0", "first"));
        long cursor = broadcaster.snapshot().cursor();

        broadcaster.publish(event("lumi.one", "3.1.85", "1", "second"));
        broadcaster.publish(event("lumi.one", "3.1.85", "2", "third"));
        broadcaster.publish(event("lumi.one", "3.2.85", "5", "fourth"));

        AqaraEventBatch batch = broadcaster.batchSince(cursor).orElseThrow();

        assertThat(batch.type()).isEqualTo("batch");
        assertThat(batch.events())
                .extracting(AqaraEvent::resourceId, AqaraEvent::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("3.1.85", "2"),
                        org.assertj.core.groups.Tuple.tuple("3.2.85", "5")
                );
    }

    @Test
    void publishIgnoresEventsWithoutRoutingKey() {
        broadcaster.publish(event(null, "3.1.85", "1", "missing-subject"));
        broadcaster.publish(event("lumi.one", null, "1", "missing-resource"));

        assertThat(broadcaster.snapshot().events()).isEmpty();
    }

    private AqaraEvent event(String subjectId, String resourceId, String value, String msgId) {
        return new AqaraEvent(
                "resource_report",
                subjectId,
                resourceId,
                value,
                1710000000000L,
                0,
                null,
                null,
                msgId,
                "open"
        );
    }
}
