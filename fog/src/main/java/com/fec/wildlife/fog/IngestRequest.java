package com.fec.wildlife.fog;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and validates an /ingest request body, rejecting malformed or
 * missing-field payloads with a descriptive reason instead of throwing an
 * unchecked NPE deep in field access. The caller (HabitatGateway.
 * handleIngest) turns a non-null rejection reason into a 400 response,
 * proven at the real HTTP layer by HabitatGatewayHttpTest.
 */
record IngestRequest(String sensorType, String siteId, String unit, List<Reading> readings) {

    static final class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }

    static IngestRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ValidationException("request body must be a JSON object");
        }
        if (!body.hasNonNull("sensor_type") || body.get("sensor_type").asText().isBlank()) {
            throw new ValidationException("sensor_type is required");
        }
        if (!body.has("readings") || !body.get("readings").isArray()) {
            throw new ValidationException("readings must be a JSON array");
        }
        String sensorType = body.get("sensor_type").asText();
        String siteId = body.has("site_id") && !body.get("site_id").isNull() ? body.get("site_id").asText() : "reserve-a";
        String unit = body.has("unit") && !body.get("unit").isNull() ? body.get("unit").asText() : "";

        List<Reading> readings = new ArrayList<>();
        for (JsonNode r : body.get("readings")) {
            if (!r.isObject() || !r.hasNonNull("value") || !r.get("value").isNumber()) {
                throw new ValidationException("each reading requires a numeric value field");
            }
            String ts = r.hasNonNull("ts") ? r.get("ts").asText() : Instant.now().toString();
            readings.add(new Reading(ts, r.get("value").asDouble()));
        }
        return new IngestRequest(sensorType, siteId, unit, readings);
    }
}
