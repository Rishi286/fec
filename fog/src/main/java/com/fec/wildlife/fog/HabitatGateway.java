package com.fec.wildlife.fog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Routes are discovered by AnnotatedRouter via reflection over this class's own @Route-annotated methods, not a manual dispatch table -- the ninth distinct Java fog gateway routing mechanism in this portfolio. */
public class HabitatGateway {

    static final ObjectMapper JSON = new ObjectMapper();
    static final double WINDOW_SECONDS = Double.parseDouble(System.getenv().getOrDefault("WINDOW_SECONDS", "10"));
    static final String QUEUE_NAME = System.getenv().getOrDefault("SQS_QUEUE_NAME", "wcm-reserve-agg");
    static final String ENDPOINT = System.getenv("AWS_ENDPOINT_URL");
    static final String REGION = System.getenv().getOrDefault("AWS_REGION", "eu-west-1");

    final HabitatBuffer buffer = new HabitatBuffer();
    ReservePublisher publisher;

    void ingest(String sensorType, String siteId, String unit, List<Reading> readings) {
        buffer.ingest(sensorType, siteId, unit, readings);
    }

    List<WindowAggregate> flushWindow() {
        String windowEnd = Instant.now().toString();
        String windowStart = Instant.now().minusSeconds((long) WINDOW_SECONDS).toString();
        List<WindowAggregate> results = new ArrayList<>();
        for (var entry : buffer.drainAll().entrySet()) {
            List<Reading> readings = entry.getValue();
            if (readings.isEmpty()) continue;
            FieldKey key = entry.getKey();
            results.add(WindowAggregate.of(key.sensorType(), key.siteId(), buffer.unitFor(key.sensorType()),
                readings, windowStart, windowEnd));
        }
        return results;
    }

    void runWindowCycle() {
        try {
            List<AggregatePayload> payloads = new ArrayList<>();
            for (WindowAggregate window : flushWindow()) {
                List<String> alerts = HabitatAlerts.evaluate(window.sensorType(), window);
                payloads.add(new AggregatePayload(window, alerts));
            }
            publisher.publishBatch(payloads);
        } catch (Exception exc) {
            System.out.println("window flush failed: " + exc.getMessage());
        }
    }

    String thresholdsJson() {
        Map<String, List<CompiledRule>> bySensorType = new LinkedHashMap<>();
        for (CompiledRule rule : HabitatAlerts.CATALOG) {
            bySensorType.computeIfAbsent(rule.sensorType(), k -> new ArrayList<>()).add(rule);
        }
        ObjectNode root = JSON.createObjectNode();
        bySensorType.forEach((sensorType, rules) -> {
            ArrayNode arr = root.putArray(sensorType);
            for (CompiledRule rule : rules) {
                ObjectNode r = JSON.createObjectNode();
                r.put("field", rule.field());
                r.put("op", rule.op());
                r.put("limit", rule.limit());
                r.put("key", rule.key());
                arr.add(r);
            }
        });
        return root.toString();
    }

    static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Route(method = "GET", path = "/health")
    void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    @Route(method = "GET", path = "/thresholds")
    void handleThresholds(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, thresholdsJson());
    }

    @Route(method = "POST", path = "/ingest")
    void handleIngest(HttpExchange exchange) throws IOException {
        JsonNode body;
        try {
            body = JSON.readTree(readBody(exchange));
        } catch (Exception e) {
            sendJson(exchange, 400, "{\"error\":\"malformed JSON body\"}");
            return;
        }
        IngestRequest payload;
        try {
            payload = IngestRequest.parse(body);
        } catch (IngestRequest.ValidationException e) {
            sendJson(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            return;
        }
        ingest(payload.sensorType(), payload.siteId(), payload.unit(), payload.readings());
        sendJson(exchange, 202, "{\"accepted\":" + payload.readings().size() + "}");
    }

    public static void main(String[] args) throws Exception {
        HabitatGateway gateway = new HabitatGateway();
        gateway.publisher = new ReservePublisher(ENDPOINT, REGION, QUEUE_NAME);

        var server = AnnotatedRouter.bind(8000, 4, gateway);
        server.start();
        System.out.println("fog listening on :8000");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long periodMs = (long) (WINDOW_SECONDS * 1000);
        // Initial delay is also periodMs (not 0) so the first flush only fires
        // once a full window has actually accumulated -- flushing at t=0 would
        // emit an aggregate over an empty/near-empty buffer before any sensor
        // has had a chance to dispatch a reading into it.
        scheduler.scheduleAtFixedRate(gateway::runWindowCycle, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }
}
