package com.fec.wildlife.dashboard;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.lambda.model.State;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WildlifeDashboardLambdaTest {

    private final WildlifeDashboardLambda handler = new WildlifeDashboardLambda();

    @BeforeEach
    void resetClients() {
        WildlifeDashboardLambda.useClientsForTesting(
            new FakeDynamoDbClient(Map.of("waterhole_level_cm", List.of(
                ReserveRepositoryTest.item("waterhole_level_cm", "reserve-a", "2026-01-01T00:00:00Z", 90.0, 91.0, List.of()))), 5),
            new FakeSqsClient(true, Map.of("ApproximateNumberOfMessages", "1", "ApproximateNumberOfMessagesNotVisible", "0")),
            new FakeLambdaClient(true, State.ACTIVE));
    }

    private static APIGatewayProxyRequestEvent request(String method, String path, Map<String, String> query) {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setHttpMethod(method);
        event.setPath(path);
        event.setQueryStringParameters(query);
        return event;
    }

    @Test
    void reservesRouteReturnsTheGroupedReserveView() throws Exception {
        APIGatewayProxyResponseEvent response = handler.handleRequest(request("GET", "/api/reserves", null), null);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("reserve-a"));
        assertTrue(response.getBody().contains("waterhole_level_cm"));
    }

    @Test
    void readingsRouteHonoursTheSensorTypeAndLimitQueryParams() {
        var response = handler.handleRequest(
            request("GET", "/api/readings", Map.of("sensor_type", "waterhole_level_cm", "limit", "5")), null);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"sensor_type\":\"waterhole_level_cm\""));
    }

    @Test
    void healthRouteReflectsTheInjectedClientsState() {
        var response = handler.handleRequest(request("GET", "/api/health", null), null);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"queue\":true"));
        assertTrue(response.getBody().contains("\"lambda\":true"));
    }

    @Test
    void backendStatsRouteReflectsQueueDepthAndItemCount() {
        var response = handler.handleRequest(request("GET", "/api/backend-stats", null), null);
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"items_in_table\":5"));
        assertTrue(response.getBody().contains("\"waiting\":1"));
    }

    @Test
    void backendStatsItemCountFollowsPaginationAcrossMultiplePages() {
        WildlifeDashboardLambda.useClientsForTesting(
            new FakeDynamoDbClient(Map.of(), List.of(400, 400, 400, 87)),
            new FakeSqsClient(true, Map.of("ApproximateNumberOfMessages", "0", "ApproximateNumberOfMessagesNotVisible", "0")),
            new FakeLambdaClient(true, State.ACTIVE));

        var response = handler.handleRequest(request("GET", "/api/backend-stats", null), null);
        assertTrue(response.getBody().contains("\"items_in_table\":1287"));
    }

    @Test
    void unknownPathReturns404() {
        var response = handler.handleRequest(request("GET", "/api/nope", null), null);
        assertEquals(404, response.getStatusCode());
    }

    @Test
    void unsupportedMethodOnAKnownPathReturns404() {
        var response = handler.handleRequest(request("POST", "/api/reserves", null), null);
        assertEquals(404, response.getStatusCode());
    }

    @Test
    void everyResponseCarriesTheCorsHeaderForTheCrossOriginS3Frontend() {
        var response = handler.handleRequest(request("GET", "/api/health", null), null);
        assertEquals("*", response.getHeaders().get("Access-Control-Allow-Origin"));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }
}
