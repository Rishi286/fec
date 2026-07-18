package com.fec.wildlife.fog;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.List;

/** Records every sendMessage/sendMessageBatch call instead of talking to real SQS, so ReservePublisherTest can assert on batch shape directly. */
class FakeSqsClient implements SqsClient {

    final List<String> singleSends = new ArrayList<>();
    final List<Integer> batchSizes = new ArrayList<>();

    @Override
    public GetQueueUrlResponse getQueueUrl(GetQueueUrlRequest request) {
        return GetQueueUrlResponse.builder().queueUrl("http://queue-url").build();
    }

    @Override
    public SendMessageResponse sendMessage(SendMessageRequest request) {
        singleSends.add(request.messageBody());
        return SendMessageResponse.builder().messageId("m-" + singleSends.size()).build();
    }

    @Override
    public SendMessageBatchResponse sendMessageBatch(SendMessageBatchRequest request) {
        batchSizes.add(request.entries().size());
        return SendMessageBatchResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return "sqs";
    }

    @Override
    public void close() {
    }
}
