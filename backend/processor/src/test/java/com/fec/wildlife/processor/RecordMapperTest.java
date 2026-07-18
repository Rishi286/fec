package com.fec.wildlife.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecordMapperTest {

    static final String MESSAGE = "{\"sensor_type\":\"acoustic_poaching_risk_db\",\"site_id\":\"reserve-a\",\"unit\":\"dB\"," +
        "\"window_start\":\"s\",\"window_end\":\"e\",\"count\":4,\"min\":60.0,\"max\":90.0,\"avg\":78.5," +
        "\"latest\":82.0,\"alerts\":[\"poaching_risk_detected\"]}";

    @Test
    void mapsAllFieldsAndComputesSortKey() throws Exception {
        Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item = RecordMapper.toItem(MESSAGE);

        assertEquals("acoustic_poaching_risk_db", item.get("sensor_type").s());
        assertEquals("e#reserve-a", item.get("sort_key").s());
        assertEquals("reserve-a", item.get("site_id").s());
        assertEquals("dB", item.get("unit").s());
        assertEquals("4", item.get("count").n());
        assertEquals("poaching_risk_detected", item.get("alerts").l().get(0).s());
    }

    @Test
    void defaultsSiteIdWhenAbsent() throws Exception {
        String message = "{\"sensor_type\":\"ambient_temp_c\",\"window_start\":\"s\",\"window_end\":\"e\"," +
            "\"count\":1,\"min\":28.0,\"max\":28.0,\"avg\":28.0,\"latest\":28.0}";
        var item = RecordMapper.toItem(message);
        assertEquals("reserve-a", item.get("site_id").s());
        assertEquals("e#reserve-a", item.get("sort_key").s());
        assertTrue(item.get("alerts").l().isEmpty());
    }
}
