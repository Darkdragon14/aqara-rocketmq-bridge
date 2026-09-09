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

    @Test
    void snapshotKeepsLatestTraitValuePerCodePath() {
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 1, "first"));
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 2, "second"));

        AqaraEventBatch snapshot = broadcaster.snapshot();

        assertThat(snapshot.events()).singleElement().satisfies(event -> {
            assertThat(event.subjectId()).isEqualTo("matt.u200");
            assertThat(event.resourceId()).isEqualTo("2.DoorLock.LockState");
            assertThat(event.type()).isEqualTo("spec_report");
            assertThat(event.value()).isEqualTo(2);
        });
    }

    @Test
    void staleTraitReportDoesNotReplaceNewerState() {
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 1, 200L, 0, "newer"));
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 2, 100L, 0, "older"));

        assertThat(broadcaster.snapshot().events()).singleElement().satisfies(event -> {
            assertThat(event.value()).isEqualTo(1);
            assertThat(event.time()).isEqualTo(200L);
        });
    }

    @Test
    void failedTraitReportDoesNotReplaceSuccessfulState() {
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 1, 100L, 0, "success"));
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 2, 200L, 1, "failed"));

        assertThat(broadcaster.snapshot().events()).singleElement().satisfies(event -> {
            assertThat(event.value()).isEqualTo(1);
            assertThat(event.statusCode()).isZero();
        });
    }

    @Test
    void unknownTraitTimestampDoesNotEraseLatestKnownTime() {
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 1, 200L, 0, "newer"));
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 2, 0L, 0, "unknown"));
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 3, 100L, 0, "older"));

        assertThat(broadcaster.snapshot().events()).singleElement().satisfies(event -> {
            assertThat(event.value()).isEqualTo(2);
            assertThat(event.time()).isZero();
        });
    }

    @Test
    void resourceOccurrencesAreNotRejectedByStateTimestampOrdering() {
        broadcaster.publish(event("doorbell", "4.1.85", "ring-newer", "newer"));
        broadcaster.publish(new AqaraEvent(
                "resource_report",
                "doorbell",
                "4.1.85",
                "ring-older",
                100L,
                0,
                null,
                null,
                "older",
                "open"
        ));

        assertThat(broadcaster.snapshot().events()).singleElement().satisfies(event ->
                assertThat(event.value()).isEqualTo("ring-older")
        );
    }

    @Test
    void resourceTimestampDoesNotSetTraitStalenessWatermark() {
        broadcaster.publish(new AqaraEvent(
                "resource_report",
                "matt.u200",
                "same.key",
                "resource",
                300L,
                0,
                null,
                null,
                "resource",
                "open"
        ));
        broadcaster.publish(traitEvent("matt.u200", "same.key", 1, 200L, 0, "trait"));

        assertThat(broadcaster.snapshot().events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("spec_report");
            assertThat(event.value()).isEqualTo(1);
        });
    }

    @Test
    void negativeTimestampCannotReplaceNewerTraitState() {
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 1, 200L, 0, "newer"));
        broadcaster.publish(traitEvent("matt.u200", "2.DoorLock.LockState", 2, -1L, 0, "negative"));

        assertThat(broadcaster.snapshot().events()).singleElement().satisfies(event -> {
            assertThat(event.value()).isEqualTo(1);
            assertThat(event.time()).isEqualTo(200L);
        });
    }

    private AqaraEvent traitEvent(String subjectId, String resourceId, Object value, String msgId) {
        return traitEvent(subjectId, resourceId, value, 1710000000000L, 0, msgId);
    }

    private AqaraEvent traitEvent(
            String subjectId,
            String resourceId,
            Object value,
            long time,
            int statusCode,
            String msgId
    ) {
        return new AqaraEvent(
                "spec_report",
                subjectId,
                resourceId,
                value,
                time,
                statusCode,
                null,
                null,
                msgId,
                "open"
        );
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
