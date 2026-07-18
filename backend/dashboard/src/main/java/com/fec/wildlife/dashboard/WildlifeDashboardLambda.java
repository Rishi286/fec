package com.fec.wildlife.dashboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Real API Gateway REST API entry point for the dashboard's REST endpoints
 * -- a 5th distinct dispatch shape in this portfolio's dashboard-Lambda
 * lineage (after project 15's ordered regex-list scan, 22's trie-walk
 * router, 01's Mangum-wrapped-FastAPI-native-routes reuse, and 23's flat
 * dict[(method,path)] lookup): a Java switch expression on "METHOD path"
 * calling straight into ReserveRepository/PipelineChecks/ThresholdsGateway
 * -- the same classes WildlifeDashboardApp's HttpExchange-based routes use
 * locally. AnnotatedRouter's reflection-driven binding registers
 * HttpServer createContext() callbacks and can't be reused as-is inside a
 * synchronous Lambda invocation without an ASGI/WSGI-style compatibility
 * shim, so this class owns its own routing instead.
 */
public class WildlifeDashboardLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final ReserveRepository REPOSITORY = new ReserveRepository();
    private static final PipelineChecks CHECKS = new PipelineChecks();
    private static final ThresholdsGateway THRESHOLDS_GATEWAY = new ThresholdsGateway();
    private static final HttpClient UPSTREAM = HttpClient.newHttpClient();

    private static DynamoDbClient dynamo;
    private static SqsClient sqs;
    private static LambdaClient lambda;
    private static String thresholdsCache;

    private static synchronized DynamoDbClient dynamo() {
        if (dynamo == null) dynamo = WildlifeDashboardApp.awsClient(DynamoDbClient.builder());
        return dynamo;
    }

    private static synchronized SqsClient sqs() {
        if (sqs == null) sqs = WildlifeDashboardApp.awsClient(SqsClient.builder());
        return sqs;
    }

    private static synchronized LambdaClient lambda() {
        if (lambda == null) lambda = WildlifeDashboardApp.awsClient(LambdaClient.builder());
        return lambda;
    }

    // Test-only injection point: bypasses the lazy real-AWS client builders
    // above so unit tests can supply fakes directly, without this class
    // needing every method to thread clients through as parameters the way
    // PipelineChecks/ReserveRepository do.
    static synchronized void useClientsForTesting(DynamoDbClient dynamoClient, SqsClient sqsClient, LambdaClient lambdaClient) {
        dynamo = dynamoClient;
        sqs = sqsClient;
        lambda = lambdaClient;
        thresholdsCache = null;
    }

    private static boolean gatewayHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(WildlifeDashboardApp.FOG_HEALTH_URL))
                .timeout(Duration.ofSeconds(2)).GET().build();
            return UPSTREAM.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // Cached for the execution environment's lifetime, same rationale as
    // WildlifeDashboardApp.thresholds(): HabitatAlerts.CATALOG is a static,
    // code-defined constant compiled once at fog startup.
    private static synchronized String thresholds() throws Exception {
        if (thresholdsCache == null) {
            thresholdsCache = THRESHOLDS_GATEWAY.fetch(UPSTREAM, WildlifeDashboardApp.FOG_THRESHOLDS_URL);
        }
        return thresholdsCache;
    }

    private static Double freshestWindowAgeSeconds() {
        Instant now = Instant.now();
        Double best = null;
        for (String sensorType : WildlifeDashboardApp.SENSOR_TYPES) {
            var recent = REPOSITORY.recentWindows(dynamo(), WildlifeDashboardApp.TABLE_NAME, sensorType, 1);
            if (recent.isEmpty()) continue;
            String windowEnd = (String) recent.get(recent.size() - 1).get("window_end");
            double age = Duration.between(Instant.parse(windowEnd), now).toMillis() / 1000.0;
            if (best == null || age < best) best = age;
        }
        return best;
    }

    static Map<String, Object> reserves() {
        return REPOSITORY.byReserve(dynamo(), WildlifeDashboardApp.TABLE_NAME, WildlifeDashboardApp.SENSOR_TYPES,
            WildlifeDashboardApp.RESERVE_HISTORY_PER_TYPE, WildlifeDashboardApp.LOG_ENTRIES_PER_RESERVE);
    }

    static Map<String, Object> readings(Map<String, String> query) {
        String sensorType = query.get("sensor_type");
        int limit = query.containsKey("limit") ? Integer.parseInt(query.get("limit")) : 60;
        var items = REPOSITORY.recentWindows(dynamo(), WildlifeDashboardApp.TABLE_NAME, sensorType, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sensor_type", sensorType);
        body.put("items", items);
        return body;
    }

    static Map<String, Object> health() {
        Double freshestAge = freshestWindowAgeSeconds();
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("gateway", gatewayHealthy());
        health.put("queue", CHECKS.queueReachable(sqs(), WildlifeDashboardApp.QUEUE_NAME));
        health.put("lambda", CHECKS.lambdaDeployed(lambda(), WildlifeDashboardApp.FUNCTION_NAME));
        health.put("pipeline", freshestAge != null && freshestAge <= WildlifeDashboardApp.PIPELINE_FRESH_SECONDS);
        health.put("freshest_age_seconds", freshestAge);
        return health;
    }

    static Map<String, Object> backendStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("queue", CHECKS.queueDepth(sqs(), WildlifeDashboardApp.QUEUE_NAME));
        stats.put("items_in_table", CHECKS.itemCount(dynamo(), WildlifeDashboardApp.TABLE_NAME));
        return stats;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String route = request.getHttpMethod() + " " + request.getPath();
        Map<String, String> query = request.getQueryStringParameters() == null ? Map.of() : request.getQueryStringParameters();

        if (route.equals("GET /api/thresholds")) {
            try {
                return jsonResponse(200, thresholds());
            } catch (Exception e) {
                return jsonResponse(502, Map.of("error", "thresholds unavailable"));
            }
        }

        try {
            Object body = switch (route) {
                case "GET /api/reserves" -> reserves();
                case "GET /api/readings" -> readings(query);
                case "GET /api/health" -> health();
                case "GET /api/backend-stats" -> backendStats();
                default -> null;
            };
            return body == null ? jsonResponse(404, Map.of("error", "not found")) : jsonResponse(200, body);
        } catch (Exception e) {
            return jsonResponse(500, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    static APIGatewayProxyResponseEvent jsonResponse(int status, Object body) {
        String text;
        try {
            text = body instanceof String ? (String) body : WildlifeDashboardApp.JSON.writeValueAsString(body);
        } catch (Exception e) {
            text = "{\"error\":\"failed to serialize response\"}";
            status = 500;
        }
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(status);
        response.setHeaders(Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*", "Cache-Control", "no-store"));
        response.setBody(text);
        return response;
    }
}
