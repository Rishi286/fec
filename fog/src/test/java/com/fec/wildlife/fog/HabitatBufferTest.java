package com.fec.wildlife.fog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HabitatBufferTest {

    @Test
    void ingestGroupsReadingsBySensorTypeAndSite() {
        HabitatBuffer buffer = new HabitatBuffer();
        buffer.ingest("waterhole_level_cm", "reserve-a", "cm", List.of(new Reading("t0", 90.0), new Reading("t1", 92.0)));

        Map<FieldKey, List<Reading>> drained = buffer.drainAll();
        assertEquals(1, drained.size());
        assertTrue(drained.containsKey(new FieldKey("waterhole_level_cm", "reserve-a")));
        assertEquals(2, drained.get(new FieldKey("waterhole_level_cm", "reserve-a")).size());
    }

    @Test
    void differentSitesForTheSameSensorTypeAreSeparateGroups() {
        HabitatBuffer buffer = new HabitatBuffer();
        buffer.ingest("soil_moisture_pct", "reserve-a", "%", List.of(new Reading("t0", 35.0)));
        buffer.ingest("soil_moisture_pct", "reserve-b", "%", List.of(new Reading("t0", 40.0)));

        assertEquals(2, buffer.drainAll().size());
    }

    @Test
    void drainReturnsReadingsInArrivalOrderAndEmptiesTheBuffer() {
        HabitatBuffer buffer = new HabitatBuffer();
        buffer.ingest("acoustic_poaching_risk_db", "reserve-a", "dB", List.of(new Reading("t0", 40.0), new Reading("t1", 45.0)));

        var drained = buffer.drainAll();
        List<Reading> readings = drained.get(new FieldKey("acoustic_poaching_risk_db", "reserve-a"));
        assertEquals(2, readings.size());
        assertEquals(40.0, readings.get(0).value());
        assertEquals(45.0, readings.get(1).value());

        assertTrue(buffer.drainAll().isEmpty());
    }

    @Test
    void drainOnAnEmptyBufferReturnsEmptyMap() {
        assertTrue(new HabitatBuffer().drainAll().isEmpty());
    }

    @Test
    void multipleIngestsForTheSameKeyAccumulateAcrossCalls() {
        HabitatBuffer buffer = new HabitatBuffer();
        buffer.ingest("motion_detection_count", "reserve-b", "count", List.of(new Reading("t0", 8.0)));
        buffer.ingest("motion_detection_count", "reserve-b", "count", List.of(new Reading("t1", 12.0)));

        var drained = buffer.drainAll();
        assertEquals(2, drained.get(new FieldKey("motion_detection_count", "reserve-b")).size());
    }

    @Test
    void unitForReturnsTheMostRecentlySeenUnit() {
        HabitatBuffer buffer = new HabitatBuffer();
        buffer.ingest("ambient_temp_c", "reserve-a", "C", List.of(new Reading("t0", 28.0)));
        assertEquals("C", buffer.unitFor("ambient_temp_c"));
    }

    @Test
    void concurrentIngestsForTheSameKeyNeverLoseAReading() throws InterruptedException {
        // Exercises the CAS retry loop in updateAndGet() under real
        // contention: 16 threads each ingest 50 readings for the SAME key,
        // so every ingest() call must retry until its own write actually
        // lands -- if any were silently dropped by a lost race, the drained
        // total below would be less than 800.
        HabitatBuffer buffer = new HabitatBuffer();
        int threads = 16;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        buffer.ingest("waterhole_level_cm", "reserve-a", "cm",
                            List.of(new Reading("t" + threadId + "-" + i, i)));
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdown();

        var drained = buffer.drainAll();
        assertEquals(threads * perThread, drained.get(new FieldKey("waterhole_level_cm", "reserve-a")).size());
    }
}
