package com.fec.wildlife.dashboard;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReserveRepositoryTest {

    static Map<String, AttributeValue> item(String sensorType, String siteId, String windowEnd, double avg,
                                             double latest, List<String> alerts) {
        return Map.of(
            "sensor_type", AttributeValue.fromS(sensorType),
            "site_id", AttributeValue.fromS(siteId),
            "unit", AttributeValue.fromS("x"),
            "window_end", AttributeValue.fromS(windowEnd),
            "count", AttributeValue.fromN("3"),
            "min", AttributeValue.fromN("1.0"),
            "max", AttributeValue.fromN(String.valueOf(latest)),
            "avg", AttributeValue.fromN(String.valueOf(avg)),
            "latest", AttributeValue.fromN(String.valueOf(latest)),
            "alerts", AttributeValue.fromL(alerts.stream().map(AttributeValue::fromS).toList())
        );
    }

    @Test
    void recentWindowsReturnsChronologicalOrder() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient(
            Map.of("waterhole_level_cm", List.of(
                item("waterhole_level_cm", "reserve-a", "t2", 90.0, 92.0, List.of()),
                item("waterhole_level_cm", "reserve-a", "t1", 88.0, 89.0, List.of()))), 0);

        var items = new ReserveRepository().recentWindows(fake, "table", "waterhole_level_cm", 10);
        assertEquals("t1", items.get(0).get("window_end"));
        assertEquals("t2", items.get(1).get("window_end"));
    }

    @Test
    void byReserveGroupsMetricsUnderEachSiteId() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient(Map.of(
            "waterhole_level_cm", List.of(
                item("waterhole_level_cm", "reserve-a", "t1", 90.0, 91.0, List.of()),
                item("waterhole_level_cm", "reserve-b", "t1", 60.0, 58.0, List.of())),
            "soil_moisture_pct", List.of(item("soil_moisture_pct", "reserve-a", "t1", 35.0, 34.0, List.of()))
        ), 0);

        var result = new ReserveRepository().byReserve(fake, "table",
            new String[]{"waterhole_level_cm", "soil_moisture_pct"}, 5, 10);
        @SuppressWarnings("unchecked")
        var reserves = (List<Map<String, Object>>) result.get("reserves");

        assertEquals(2, reserves.size());
        var byId = reserves.stream().collect(java.util.stream.Collectors.toMap(r -> r.get("site_id"), r -> r));
        assertTrue(byId.containsKey("reserve-a"));
        assertTrue(byId.containsKey("reserve-b"));

        @SuppressWarnings("unchecked")
        var reserveAMetrics = (Map<String, Object>) byId.get("reserve-a").get("metrics");
        assertTrue(reserveAMetrics.containsKey("waterhole_level_cm"));
        assertTrue(reserveAMetrics.containsKey("soil_moisture_pct"));

        @SuppressWarnings("unchecked")
        var reserveBMetrics = (Map<String, Object>) byId.get("reserve-b").get("metrics");
        assertTrue(reserveBMetrics.containsKey("waterhole_level_cm"));
        assertFalse(reserveBMetrics.containsKey("soil_moisture_pct"));
    }

    @Test
    void byReserveBuildsALogSortedMostRecentFirstAcrossSensorTypes() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient(Map.of(
            "waterhole_level_cm", List.of(
                item("waterhole_level_cm", "reserve-a", "2024-01-01T00:00:02Z", 90.0, 91.0, List.of()),
                item("waterhole_level_cm", "reserve-a", "2024-01-01T00:00:00Z", 88.0, 87.0, List.of())),
            "soil_moisture_pct", List.of(
                item("soil_moisture_pct", "reserve-a", "2024-01-01T00:00:01Z", 8.0, 7.0, List.of("habitat_dryness_risk")))
        ), 0);

        var result = new ReserveRepository().byReserve(fake, "table",
            new String[]{"waterhole_level_cm", "soil_moisture_pct"}, 5, 10);
        @SuppressWarnings("unchecked")
        var reserves = (List<Map<String, Object>>) result.get("reserves");
        @SuppressWarnings("unchecked")
        var log = (List<Map<String, Object>>) reserves.get(0).get("log");

        assertEquals(3, log.size());
        // Most recent window_end first, merged across both sensor types.
        assertEquals("2024-01-01T00:00:02Z", log.get(0).get("window_end"));
        assertEquals("waterhole_level_cm", log.get(0).get("sensor_type"));
        assertEquals("2024-01-01T00:00:01Z", log.get(1).get("window_end"));
        assertEquals("soil_moisture_pct", log.get(1).get("sensor_type"));
        assertEquals("2024-01-01T00:00:00Z", log.get(2).get("window_end"));
    }

    @Test
    void byReserveTrimsTheLogToTheRequestedEntryCount() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient(Map.of(
            "waterhole_level_cm", List.of(
                item("waterhole_level_cm", "reserve-a", "t3", 90.0, 91.0, List.of()),
                item("waterhole_level_cm", "reserve-a", "t2", 89.0, 90.0, List.of()),
                item("waterhole_level_cm", "reserve-a", "t1", 88.0, 89.0, List.of()))
        ), 0);

        var result = new ReserveRepository().byReserve(fake, "table", new String[]{"waterhole_level_cm"}, 5, 2);
        @SuppressWarnings("unchecked")
        var reserves = (List<Map<String, Object>>) result.get("reserves");
        @SuppressWarnings("unchecked")
        var log = (List<Map<String, Object>>) reserves.get(0).get("log");
        assertEquals(2, log.size());
        assertEquals("t3", log.get(0).get("window_end"));
        assertEquals("t2", log.get(1).get("window_end"));
    }

    @Test
    void byReserveWithNoDataStillReturnsAnEmptyList() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient(Map.of(), 0);
        var result = new ReserveRepository().byReserve(fake, "table", new String[]{"waterhole_level_cm"}, 5, 10);
        @SuppressWarnings("unchecked")
        var reserves = (List<Map<String, Object>>) result.get("reserves");
        assertTrue(reserves.isEmpty());
    }
}
