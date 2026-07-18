package com.fec.wildlife.fog;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises /health, /thresholds and /ingest over a real HttpServer bound to
 * an ephemeral port (via AnnotatedRouter.bind(0, ...)), so the 400-on-
 * malformed-input behaviour required by the brief is proven at the actual
 * HTTP layer -- status code and body -- not just a unit test of
 * IngestRequest.parse() in isolation. gateway.publisher is left null on
 * purpose: none of these tests reach a code path that calls it.
 */
class HabitatGatewayHttpTest {

    private HttpServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        HabitatGateway gateway = new HabitatGateway();
        server = AnnotatedRouter.bind(0, 2, gateway);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/ingest"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthReturns200Ok() throws Exception {
        HttpResponse<String> response = get("/health");
        assertEquals(200, response.statusCode());
        assertEquals("{\"status\":\"ok\"}", response.body());
    }

    @Test
    void thresholdsExposesTheRealCompiledRules() throws Exception {
        HttpResponse<String> response = get("/thresholds");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("poaching_risk_detected"));
        assertTrue(response.body().contains("drought_stress_risk"));
        assertTrue(response.body().contains("unusual_activity_surge"));
        assertTrue(response.body().contains("habitat_dryness_risk"));
    }

    @Test
    void nonJsonBodyReturns400() throws Exception {
        HttpResponse<String> response = post("this is not json");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("malformed JSON body"));
    }

    @Test
    void missingSensorTypeReturns400() throws Exception {
        HttpResponse<String> response = post("{\"readings\":[{\"value\":42.0}]}");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("sensor_type is required"));
    }

    @Test
    void readingsNotAnArrayReturns400() throws Exception {
        HttpResponse<String> response = post("{\"sensor_type\":\"soil_moisture_pct\",\"readings\":\"oops\"}");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("readings must be a JSON array"));
    }

    @Test
    void readingMissingNumericValueReturns400() throws Exception {
        HttpResponse<String> response = post("{\"sensor_type\":\"soil_moisture_pct\",\"readings\":[{\"ts\":\"x\"}]}");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("numeric value"));
    }

    @Test
    void validPayloadIsAccepted() throws Exception {
        HttpResponse<String> response = post(
            "{\"sensor_type\":\"waterhole_level_cm\",\"site_id\":\"reserve-a\",\"unit\":\"cm\",\"readings\":[{\"value\":90.5}]}");
        assertEquals(202, response.statusCode());
        assertTrue(response.body().contains("\"accepted\":1"));
    }

    @Test
    void unregisteredPathReturns404() throws Exception {
        assertEquals(404, get("/nope").statusCode());
    }

    @Test
    void wrongMethodOnIngestReturns405() throws Exception {
        assertEquals(405, get("/ingest").statusCode());
    }
}
