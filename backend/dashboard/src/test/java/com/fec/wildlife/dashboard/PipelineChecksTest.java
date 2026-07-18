package com.fec.wildlife.dashboard;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.model.State;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelineChecksTest {

    private final PipelineChecks checks = new PipelineChecks();

    @Test
    void queueReachableIsTrueWhenTheQueueExistsAndAttributesAreReadable() {
        assertTrue(checks.queueReachable(new FakeSqsClient(true, Map.of("QueueArn", "arn:x")), "wcm-reserve-agg"));
    }

    @Test
    void queueReachableIsFalseWhenTheQueueDoesNotExist() {
        assertFalse(checks.queueReachable(new FakeSqsClient(false, Map.of()), "wcm-reserve-agg"));
    }

    @Test
    void lambdaDeployedIsTrueOnlyWhenStateIsActive() {
        assertTrue(checks.lambdaDeployed(new FakeLambdaClient(true, State.ACTIVE), "wcm-processor"));
        assertFalse(checks.lambdaDeployed(new FakeLambdaClient(true, State.PENDING), "wcm-processor"));
        assertFalse(checks.lambdaDeployed(new FakeLambdaClient(false, State.ACTIVE), "wcm-processor"));
    }

    @Test
    void queueDepthParsesWaitingAndInFlightCounts() {
        var depth = checks.queueDepth(new FakeSqsClient(true,
            Map.of("ApproximateNumberOfMessages", "5", "ApproximateNumberOfMessagesNotVisible", "2")), "wcm-reserve-agg");
        assertEquals(5, depth.get("waiting"));
        assertEquals(2, depth.get("in_flight"));
    }

    @Test
    void queueDepthReturnsNullWhenUnreachable() {
        assertNull(checks.queueDepth(new FakeSqsClient(false, Map.of()), "wcm-reserve-agg"));
    }

    @Test
    void itemCountReadsTheScanCount() {
        assertEquals(42, checks.itemCount(new FakeDynamoDbClient(Map.of(), 42), "wcm-readings"));
    }

    @Test
    void itemCountFollowsLastEvaluatedKeyAcrossPagesInsteadOfCountingOnlyTheFirst() {
        FakeDynamoDbClient fake = new FakeDynamoDbClient(Map.of(), List.of(400, 400, 400, 87));
        assertEquals(1287, checks.itemCount(fake, "wcm-readings"));
    }
}
