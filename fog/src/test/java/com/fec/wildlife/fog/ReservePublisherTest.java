package com.fec.wildlife.fog;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReservePublisherTest {

    @Test
    void jitteredStaysWithinPlusOrMinusTwentyPercentOfTheBaseDelay() {
        long base = 1000;
        for (int i = 0; i < 200; i++) {
            long jittered = ReservePublisher.jittered(base);
            assertTrue(jittered >= 800 && jittered <= 1200, "jittered delay " + jittered + " left the expected band");
        }
    }

    @Test
    void jitteredScalesWithTheInputDelay() {
        long jittered = ReservePublisher.jittered(5000);
        assertTrue(jittered >= 4000 && jittered <= 6000);
    }

    private static AggregatePayload payload(int i) {
        WindowAggregate window = new WindowAggregate("waterhole_level_cm", "reserve-a", "cm",
            "2026-01-01T00:00:00Z", "2026-01-01T00:00:10Z", 1, i, i, i, i);
        return new AggregatePayload(window, List.of());
    }

    @Test
    void publishBatchChunksAtTenEntriesPerSendMessageBatchCall() {
        FakeSqsClient client = new FakeSqsClient();
        ReservePublisher publisher = new ReservePublisher(client, "wcm-reserve-agg");

        List<AggregatePayload> payloads = new ArrayList<>();
        for (int i = 0; i < 23; i++) payloads.add(payload(i));
        publisher.publishBatch(payloads);

        assertEquals(List.of(10, 10, 3), client.batchSizes);
    }

    @Test
    void publishBatchOfEmptyListSendsNothing() {
        FakeSqsClient client = new FakeSqsClient();
        ReservePublisher publisher = new ReservePublisher(client, "wcm-reserve-agg");

        publisher.publishBatch(List.of());

        assertTrue(client.batchSizes.isEmpty());
    }

    @Test
    void publishBatchOfOneEntryIssuesOneCallOfSizeOne() {
        FakeSqsClient client = new FakeSqsClient();
        ReservePublisher publisher = new ReservePublisher(client, "wcm-reserve-agg");

        publisher.publishBatch(List.of(payload(1)));

        assertEquals(List.of(1), client.batchSizes);
    }
}
