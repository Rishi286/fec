package com.fec.wildlife.fog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregateSerializerTest {

    @Test
    void moduleRegistrationRoutesWriteValueAsStringThroughTheCustomSerializer() throws Exception {
        ObjectMapper mapper = AggregateSerializer.newMapper();
        WindowAggregate window = new WindowAggregate("acoustic_poaching_risk_db", "reserve-a", "dB", "s", "e",
            3, 60.0, 90.0, 78.5, 82.0);
        AggregatePayload payload = new AggregatePayload(window, List.of("poaching_risk_detected"));

        String json = mapper.writeValueAsString(payload);

        JsonNode node = new ObjectMapper().readTree(json);
        assertEquals("acoustic_poaching_risk_db", node.get("sensor_type").asText());
        assertEquals("reserve-a", node.get("site_id").asText());
        assertEquals("dB", node.get("unit").asText());
        assertEquals(3, node.get("count").asInt());
        assertEquals(60.0, node.get("min").asDouble());
        assertEquals(90.0, node.get("max").asDouble());
        assertEquals(78.5, node.get("avg").asDouble());
        assertEquals(82.0, node.get("latest").asDouble());
        assertTrue(node.get("alerts").isArray());
        assertEquals("poaching_risk_detected", node.get("alerts").get(0).asText());
    }

    @Test
    void emptyAlertsListSerializesAsAnEmptyArray() throws Exception {
        ObjectMapper mapper = AggregateSerializer.newMapper();
        WindowAggregate window = new WindowAggregate("ambient_temp_c", "reserve-b", "C", "s", "e", 2, 27.0, 29.0, 28.0, 28.5);
        String json = mapper.writeValueAsString(new AggregatePayload(window, List.of()));

        JsonNode node = new ObjectMapper().readTree(json);
        assertTrue(node.get("alerts").isArray());
        assertEquals(0, node.get("alerts").size());
    }
}
