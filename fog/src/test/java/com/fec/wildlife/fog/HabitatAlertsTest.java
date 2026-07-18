package com.fec.wildlife.fog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HabitatAlertsTest {

    static WindowAggregate window(String sensorType, double min, double max, double avg, double latest) {
        return new WindowAggregate(sensorType, "reserve-a", "x", "s", "e", 5, min, max, avg, latest);
    }

    @Test
    void catalogHasExactlyFourAlertBearingRules() {
        assertEquals(4, HabitatAlerts.CATALOG.size());
    }

    @Test
    void acousticPoachingRiskFiresWhenAvgExceeds75() {
        assertEquals(
            java.util.List.of("poaching_risk_detected"),
            HabitatAlerts.evaluate("acoustic_poaching_risk_db", window("acoustic_poaching_risk_db", 60, 90, 80.0, 85))
        );
        assertTrue(HabitatAlerts.evaluate("acoustic_poaching_risk_db", window("acoustic_poaching_risk_db", 60, 90, 75.0, 85)).isEmpty());
    }

    @Test
    void droughtStressRiskFiresWhenAvgBelow20() {
        assertEquals(
            java.util.List.of("drought_stress_risk"),
            HabitatAlerts.evaluate("waterhole_level_cm", window("waterhole_level_cm", 10, 25, 15.0, 12))
        );
        assertTrue(HabitatAlerts.evaluate("waterhole_level_cm", window("waterhole_level_cm", 10, 25, 20.0, 12)).isEmpty());
    }

    @Test
    void unusualActivitySurgeFiresOnMaxNotAvg() {
        // max=35 exceeds 30 even though avg stays low -- proves the rule
        // reads window.max(), not window.avg().
        assertEquals(
            java.util.List.of("unusual_activity_surge"),
            HabitatAlerts.evaluate("motion_detection_count", window("motion_detection_count", 2, 35, 10.0, 8))
        );
        assertTrue(HabitatAlerts.evaluate("motion_detection_count", window("motion_detection_count", 2, 28, 10.0, 8)).isEmpty());
    }

    @Test
    void habitatDrynessRiskFiresWhenAvgBelow10() {
        assertEquals(
            java.util.List.of("habitat_dryness_risk"),
            HabitatAlerts.evaluate("soil_moisture_pct", window("soil_moisture_pct", 5, 15, 8.0, 6))
        );
        assertTrue(HabitatAlerts.evaluate("soil_moisture_pct", window("soil_moisture_pct", 5, 15, 10.0, 6)).isEmpty());
    }

    @Test
    void ambientTempHasNoRuleAndNeverFires() {
        assertTrue(HabitatAlerts.evaluate("ambient_temp_c", window("ambient_temp_c", 10, 45, 30.0, 28)).isEmpty());
    }

    @Test
    void compileRejectsAMalformedSpec() {
        assertThrows(IllegalStateException.class, () -> HabitatAlerts.compile("not a valid rule spec"));
    }

    @Test
    void compiledRuleExposesFieldOpLimitKeyForThresholdsEndpoint() {
        CompiledRule rule = HabitatAlerts.CATALOG.stream()
            .filter(r -> r.sensorType().equals("acoustic_poaching_risk_db"))
            .findFirst().orElseThrow();
        assertEquals("avg", rule.field());
        assertEquals(">", rule.op());
        assertEquals(75.0, rule.limit());
        assertEquals("poaching_risk_detected", rule.key());
    }
}
