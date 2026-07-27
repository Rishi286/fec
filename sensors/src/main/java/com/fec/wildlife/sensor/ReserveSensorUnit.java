package com.fec.wildlife.sensor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** CompletableFuture.delayedExecutor self-rescheduling chains (no Thread/Timer/ExecutorService) feeding a lock-free ConcurrentLinkedDeque. */
public class ReserveSensorUnit {

    record Profile(String unit, double lo, double hi, double start, double step) {}

    record Reading(String ts, double value) {}

    static final Map<String, Profile> PROFILES = new LinkedHashMap<>();
    static {
        PROFILES.put("motion_detection_count",     new Profile("count", 0, 50, 8, 4.0));
        PROFILES.put("acoustic_poaching_risk_db",   new Profile("dB", 20, 100, 40, 6.0));
        PROFILES.put("waterhole_level_cm",          new Profile("cm", 0, 200, 90, 10.0));
        PROFILES.put("ambient_temp_c",              new Profile("C", 10, 45, 28, 2.0));
        PROFILES.put("soil_moisture_pct",           new Profile("%", 0, 100, 35, 5.0));
    }

    static double clamp(double value, double lo, double hi) {
        return Math.max(lo, Math.min(hi, value));
    }

    // Bounded random walk from the previous value, not a fresh draw each
    // tick, so consecutive samples stay close together like a real sensor
    // trace -- this is what makes the fog's windowed min/max/avg and the
    // dashboard's log readout look like plausible reserve telemetry rather
    // than white noise.
    static double nextValue(double current, Profile profile) {
        double delta = ThreadLocalRandom.current().nextDouble(-profile.step(), profile.step());
        double moved = clamp(current + delta, profile.lo(), profile.hi());
        return Math.round(moved * 100.0) / 100.0;
    }

    static String toJson(String sensorType, String siteId, String unit, List<Reading> batch) {
        StringJoiner readings = new StringJoiner(",", "[", "]");
        for (Reading r : batch) {
            readings.add("{\"ts\":\"" + r.ts() + "\",\"value\":" + r.value() + "}");
        }
        return "{\"sensor_type\":\"" + sensorType + "\",\"site_id\":\"" + siteId + "\",\"unit\":\"" + unit
            + "\",\"readings\":" + readings + "}";
    }

    static boolean dispatch(HttpClient client, String fogUrl, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fogUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception exc) {
            System.out.println("dispatch failed, will retry: " + exc.getMessage());
            return false;
        }
    }

    /** Re-arms itself forever via CompletableFuture.delayedExecutor -- no Thread/Timer/ExecutorService involved. */
    static void scheduleSample(String sensorType, Profile profile, AtomicReference<Double> current,
                                ConcurrentLinkedDeque<Reading> buffer, long sampleMillis) {
        CompletableFuture
            .runAsync(() -> {
                double next = nextValue(current.get(), profile);
                current.set(next);
                buffer.offerLast(new Reading(Instant.now().toString(), next));
            }, CompletableFuture.delayedExecutor(sampleMillis, TimeUnit.MILLISECONDS))
            .whenComplete((v, err) -> scheduleSample(sensorType, profile, current, buffer, sampleMillis));
    }

    static void scheduleDispatch(String sensorType, String siteId, Profile profile, HttpClient client, String fogUrl,
                                  ConcurrentLinkedDeque<Reading> buffer, long dispatchMillis) {
        CompletableFuture
            .runAsync(() -> dispatchOnce(sensorType, siteId, profile, client, fogUrl, buffer),
                CompletableFuture.delayedExecutor(dispatchMillis, TimeUnit.MILLISECONDS))
            .whenComplete((v, err) -> scheduleDispatch(sensorType, siteId, profile, client, fogUrl, buffer, dispatchMillis));
    }

    static void dispatchOnce(String sensorType, String siteId, Profile profile, HttpClient client, String fogUrl,
                              ConcurrentLinkedDeque<Reading> buffer) {
        List<Reading> batch = new ArrayList<>();
        Reading r;
        while ((r = buffer.pollFirst()) != null) batch.add(r);
        if (batch.isEmpty()) return;

        String body = toJson(sensorType, siteId, profile.unit(), batch);
        if (dispatch(client, fogUrl, body)) {
            System.out.printf("%s dispatched %d readings%n", sensorType, batch.size());
        } else {
            // No lock is needed to put the batch back: offerFirst() in
            // reverse order restores original arrival order at the head of
            // the deque, ahead of anything sampled while dispatch was
            // in flight, so a failed POST never silently loses readings.
            for (int i = batch.size() - 1; i >= 0; i--) buffer.offerFirst(batch.get(i));
        }
    }

    public static void main(String[] args) throws Exception {
        String sensorType = System.getenv("SENSOR_TYPE");
        if (sensorType == null) throw new IllegalStateException("SENSOR_TYPE env var is required");
        String siteId = System.getenv().getOrDefault("SITE_ID", "reserve-a");
        double sampleInterval = Double.parseDouble(System.getenv().getOrDefault("SAMPLE_INTERVAL", "2"));
        double dispatchInterval = Double.parseDouble(System.getenv().getOrDefault("DISPATCH_INTERVAL", "10"));
        String fogUrl = System.getenv().getOrDefault("FOG_URL", "http://fog:8000/ingest");

        Profile profile = PROFILES.get(sensorType);
        if (profile == null) throw new IllegalStateException("unknown SENSOR_TYPE: " + sensorType);

        HttpClient client = HttpClient.newHttpClient();
        ConcurrentLinkedDeque<Reading> buffer = new ConcurrentLinkedDeque<>();
        AtomicReference<Double> current = new AtomicReference<>(profile.start());
        long sampleMillis = (long) (sampleInterval * 1000);
        long dispatchMillis = (long) (dispatchInterval * 1000);

        System.out.printf("%s@%s sampling every %ss, dispatching every %ss%n", sensorType, siteId, sampleInterval, dispatchInterval);

        scheduleSample(sensorType, profile, current, buffer, sampleMillis);
        scheduleDispatch(sensorType, siteId, profile, client, fogUrl, buffer, dispatchMillis);

        new CountDownLatch(1).await();
    }
}
