package com.fec.wildlife.fog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IngestRequestTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parsesAValidPayload() throws Exception {
        var body = JSON.readTree("{\"sensor_type\":\"soil_moisture_pct\",\"site_id\":\"reserve-b\",\"unit\":\"%\","
            + "\"readings\":[{\"ts\":\"t0\",\"value\":35.5}]}");
        IngestRequest parsed = IngestRequest.parse(body);
        assertEquals("soil_moisture_pct", parsed.sensorType());
        assertEquals("reserve-b", parsed.siteId());
        assertEquals("%", parsed.unit());
        assertEquals(1, parsed.readings().size());
        assertEquals(35.5, parsed.readings().get(0).value());
    }

    @Test
    void defaultsSiteIdAndUnitWhenAbsent() throws Exception {
        var body = JSON.readTree("{\"sensor_type\":\"ambient_temp_c\",\"readings\":[{\"value\":28.0}]}");
        IngestRequest parsed = IngestRequest.parse(body);
        assertEquals("reserve-a", parsed.siteId());
        assertEquals("", parsed.unit());
    }

    @Test
    void rejectsANonObjectBody() throws Exception {
        var body = JSON.readTree("\"just a string\"");
        assertThrows(IngestRequest.ValidationException.class, () -> IngestRequest.parse(body));
    }

    @Test
    void rejectsAMissingSensorType() throws Exception {
        var body = JSON.readTree("{\"readings\":[{\"value\":1.0}]}");
        var ex = assertThrows(IngestRequest.ValidationException.class, () -> IngestRequest.parse(body));
        assertTrue(ex.getMessage().contains("sensor_type"));
    }

    @Test
    void rejectsReadingsThatIsNotAnArray() throws Exception {
        var body = JSON.readTree("{\"sensor_type\":\"soil_moisture_pct\",\"readings\":\"oops\"}");
        var ex = assertThrows(IngestRequest.ValidationException.class, () -> IngestRequest.parse(body));
        assertTrue(ex.getMessage().contains("readings"));
    }

    @Test
    void rejectsAReadingMissingANumericValue() throws Exception {
        var body = JSON.readTree("{\"sensor_type\":\"soil_moisture_pct\",\"readings\":[{\"ts\":\"t0\"}]}");
        var ex = assertThrows(IngestRequest.ValidationException.class, () -> IngestRequest.parse(body));
        assertTrue(ex.getMessage().contains("numeric value"));
    }
}
