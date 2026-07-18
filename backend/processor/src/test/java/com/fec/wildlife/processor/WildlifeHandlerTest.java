package com.fec.wildlife.processor;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WildlifeHandlerTest {

    static final String MESSAGE = "{\"sensor_type\":\"waterhole_level_cm\",\"site_id\":\"reserve-b\",\"unit\":\"cm\"," +
        "\"window_start\":\"s\",\"window_end\":\"e\",\"count\":3,\"min\":10.0,\"max\":25.0,\"avg\":15.0," +
        "\"latest\":12.0,\"alerts\":[\"drought_stress_risk\"]}";

    static SQSEvent.SQSMessage message(String body) {
        SQSEvent.SQSMessage m = new SQSEvent.SQSMessage();
        m.setBody(body);
        return m;
    }

    @Test
    void processRecordsWritesEachRecord() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient();
        int processed = WildlifeHandler.processRecords(List.of(message(MESSAGE)), fake, "test-table");

        assertEquals(1, processed);
        assertEquals(1, fake.puts.size());
        assertEquals("test-table", fake.puts.get(0).tableName());
        assertEquals("waterhole_level_cm", fake.puts.get(0).item().get("sensor_type").s());
    }

    @Test
    void processRecordsHandlesABatch() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient();
        int processed = WildlifeHandler.processRecords(List.of(message(MESSAGE), message(MESSAGE)), fake, "test-table");

        assertEquals(2, processed);
        assertEquals(2, fake.puts.size());
    }

    @Test
    void malformedRecordThrowsAndFailsTheWholeBatch() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient();
        assertThrows(RuntimeException.class, () ->
            WildlifeHandler.processRecords(List.of(message("not json")), fake, "test-table"));
    }
}
