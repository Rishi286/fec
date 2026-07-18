package com.fec.wildlife.dashboard;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;
import java.util.function.Consumer;

class FakeSqsClient implements SqsClient {

    private final boolean queueExists;
    private final Map<String, String> attributes;

    FakeSqsClient(boolean queueExists, Map<String, String> attributes) {
        this.queueExists = queueExists;
        this.attributes = attributes;
    }

    @Override
    public GetQueueUrlResponse getQueueUrl(Consumer<GetQueueUrlRequest.Builder> builder) {
        if (!queueExists) throw new RuntimeException("no queue");
        return GetQueueUrlResponse.builder().queueUrl("http://queue-url").build();
    }

    @Override
    public GetQueueAttributesResponse getQueueAttributes(GetQueueAttributesRequest request) {
        return GetQueueAttributesResponse.builder().attributesWithStrings(attributes).build();
    }

    @Override
    public String serviceName() {
        return "sqs";
    }

    @Override
    public void close() {
    }
}
