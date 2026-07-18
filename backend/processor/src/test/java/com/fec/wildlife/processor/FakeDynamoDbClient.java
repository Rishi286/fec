package com.fec.wildlife.processor;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.util.ArrayList;
import java.util.List;

public class FakeDynamoDbClient implements DynamoDbClient {

    public final List<PutItemRequest> puts = new ArrayList<>();

    @Override
    public PutItemResponse putItem(PutItemRequest request) {
        puts.add(request);
        return PutItemResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return "dynamodb";
    }

    @Override
    public void close() {
    }
}
