package com.fec.wildlife.dashboard;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.List;
import java.util.Map;

class FakeDynamoDbClient implements DynamoDbClient {

    private final Map<String, List<Map<String, AttributeValue>>> itemsBySensorType;
    private final List<Integer> scanPageCounts;
    private int scanCallsMade = 0;

    FakeDynamoDbClient(Map<String, List<Map<String, AttributeValue>>> itemsBySensorType, int scanCount) {
        this(itemsBySensorType, List.of(scanCount));
    }

    // Simulates a multi-page scan: each call to scan() returns the next
    // count in scanPageCounts, with a LastEvaluatedKey set on every page
    // except the last -- enough for DynamoDbClient's own scanPaginator()
    // default method to keep following pages, without this fake needing to
    // inspect the request's exclusiveStartKey() at all.
    FakeDynamoDbClient(Map<String, List<Map<String, AttributeValue>>> itemsBySensorType, List<Integer> scanPageCounts) {
        this.itemsBySensorType = itemsBySensorType;
        this.scanPageCounts = scanPageCounts;
    }

    @Override
    public QueryResponse query(QueryRequest request) {
        String sensorType = request.expressionAttributeValues().get(":st").s();
        List<Map<String, AttributeValue>> items = itemsBySensorType.getOrDefault(sensorType, List.of());
        return QueryResponse.builder().items(items).build();
    }

    @Override
    public ScanResponse scan(ScanRequest request) {
        int pageIndex = scanCallsMade++;
        ScanResponse.Builder response = ScanResponse.builder().count(scanPageCounts.get(pageIndex));
        if (pageIndex < scanPageCounts.size() - 1) {
            response.lastEvaluatedKey(Map.of("sensor_type", AttributeValue.fromS("page-" + pageIndex)));
        }
        return response.build();
    }

    @Override
    public String serviceName() {
        return "dynamodb";
    }

    @Override
    public void close() {
    }
}
