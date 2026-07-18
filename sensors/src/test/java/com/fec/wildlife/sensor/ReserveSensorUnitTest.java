package com.fec.wildlife.sensor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.*;

class ReserveSensorUnitTest {

    @Test
    void clampKeepsValueWithinBounds() {
        assertEquals(10.0, ReserveSensorUnit.clamp(5.0, 10.0, 20.0));
        assertEquals(20.0, ReserveSensorUnit.clamp(25.0, 10.0, 20.0));
        assertEquals(15.0, ReserveSensorUnit.clamp(15.0, 10.0, 20.0));
    }

    @Test
    void nextValueNeverLeavesTheProfileRange() {
        ReserveSensorUnit.Profile profile = ReserveSensorUnit.PROFILES.get("acoustic_poaching_risk_db");
        double value = profile.start();
        for (int i = 0; i < 500; i++) {
            value = ReserveSensorUnit.nextValue(value, profile);
            assertTrue(value >= profile.lo() && value <= profile.hi(),
                "value " + value + " left [" + profile.lo() + "," + profile.hi() + "]");
        }
    }

    @Test
    void nextValueIsRoundedToTwoDecimals() {
        ReserveSensorUnit.Profile profile = new ReserveSensorUnit.Profile("x", 0, 100, 50, 1.0);
        double value = ReserveSensorUnit.nextValue(50.0, profile);
        assertEquals(value, Math.round(value * 100.0) / 100.0);
    }

    @Test
    void allFiveRequiredSensorTypesHaveProfiles() {
        assertEquals(5, ReserveSensorUnit.PROFILES.size());
        for (String type : List.of("motion_detection_count", "acoustic_poaching_risk_db", "waterhole_level_cm",
                "ambient_temp_c", "soil_moisture_pct")) {
            assertTrue(ReserveSensorUnit.PROFILES.containsKey(type), "missing profile for " + type);
        }
    }

    @Test
    void toJsonProducesExpectedShape() {
        List<ReserveSensorUnit.Reading> batch = List.of(
            new ReserveSensorUnit.Reading("t0", 42.5),
            new ReserveSensorUnit.Reading("t1", 44.0)
        );
        String json = ReserveSensorUnit.toJson("acoustic_poaching_risk_db", "reserve-a", "dB", batch);
        assertTrue(json.contains("\"sensor_type\":\"acoustic_poaching_risk_db\""));
        assertTrue(json.contains("\"site_id\":\"reserve-a\""));
        assertTrue(json.contains("\"unit\":\"dB\""));
        assertTrue(json.contains("\"value\":42.5"));
        assertTrue(json.contains("\"value\":44.0"));
    }

    @Test
    void dispatchOnceDrainsTheDequeInArrivalOrderAndOnFailurePutsItBack() {
        ReserveSensorUnit.Profile profile = ReserveSensorUnit.PROFILES.get("waterhole_level_cm");
        ConcurrentLinkedDeque<ReserveSensorUnit.Reading> buffer = new ConcurrentLinkedDeque<>();
        buffer.offerLast(new ReserveSensorUnit.Reading("t0", 90.0));
        buffer.offerLast(new ReserveSensorUnit.Reading("t1", 91.0));

        // Point at an unroutable address so dispatch() genuinely fails and
        // the batch is restored to the deque in original order.
        ReserveSensorUnit.dispatchOnce("waterhole_level_cm", "reserve-a", profile,
            java.net.http.HttpClient.newHttpClient(), "http://127.0.0.1:1/ingest", buffer);

        assertEquals(2, buffer.size());
        assertEquals(90.0, buffer.pollFirst().value());
        assertEquals(91.0, buffer.pollFirst().value());
    }

    @Test
    void dispatchOnceOnAnEmptyDequeIsANoOp() {
        ReserveSensorUnit.Profile profile = ReserveSensorUnit.PROFILES.get("soil_moisture_pct");
        ConcurrentLinkedDeque<ReserveSensorUnit.Reading> buffer = new ConcurrentLinkedDeque<>();
        ReserveSensorUnit.dispatchOnce("soil_moisture_pct", "reserve-b", profile,
            java.net.http.HttpClient.newHttpClient(), "http://127.0.0.1:1/ingest", buffer);
        assertTrue(buffer.isEmpty());
    }
}
