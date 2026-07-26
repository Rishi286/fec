package com.fec.wildlife.fog;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public class ReservePublisher {

    private static final long BASE_DELAY_MS = 300;
    private static final long MAX_DELAY_MS = 5000;
    private static final int MAX_ATTEMPTS = 12;

    private final SqsClient client;
    private final String queueName;
    private final Object resolveLock = new Object();
    private final ObjectMapper mapper = AggregateSerializer.newMapper();
    private volatile String queueUrl;

    public ReservePublisher(String endpointUrl, String region, String queueName) {
        var builder = SqsClient.builder().region(Region.of(region));

        if (endpointUrl != null) {
            builder.endpointOverride(URI.create(endpointUrl));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        this.client = builder.build();
        this.queueName = queueName;
    }


    ReservePublisher(SqsClient client, String queueName) {
        this.client = client;
        this.queueName = queueName;
    }

    private String resolveQueueUrl() {
        String cached = queueUrl;
        if (cached != null) return cached;
        synchronized (resolveLock) {
            if (queueUrl != null) return queueUrl;
            long delayMs = BASE_DELAY_MS;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    queueUrl = client.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
                    return queueUrl;
                } catch (Exception exc) {
                    if (attempt == MAX_ATTEMPTS) {
                        throw new IllegalStateException("queue " + queueName + " never became available", exc);
                    }
                    sleep(jittered(delayMs));
                    delayMs = Math.min(delayMs * 2, MAX_DELAY_MS);
                }
            }
            throw new IllegalStateException("queue " + queueName + " never became available");
        }
    }

    static long jittered(long delayMs) {
        double factor = 0.8 + ThreadLocalRandom.current().nextDouble(0.4); // 0.8x .. 1.2x
        return Math.round(delayMs * factor);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the queue to become available", ie);
        }
    }

    public void publish(WindowAggregate window, List<String> alerts) {
        String body;
        try {
            body = mapper.writeValueAsString(new AggregatePayload(window, alerts));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize aggregate payload", e);
        }
        client.sendMessage(SendMessageRequest.builder().queueUrl(resolveQueueUrl()).messageBody(body).build());
    }


    public void publishBatch(List<AggregatePayload> payloads) {
        if (payloads.isEmpty()) return;
        String queueUrl = resolveQueueUrl();
        for (int start = 0; start < payloads.size(); start += 10) {
            List<AggregatePayload> chunk = payloads.subList(start, Math.min(start + 10, payloads.size()));
            List<SendMessageBatchRequestEntry> entries = new ArrayList<>(chunk.size());
            for (int i = 0; i < chunk.size(); i++) {
                String body;
                try {
                    body = mapper.writeValueAsString(chunk.get(i));
                } catch (Exception e) {
                    throw new IllegalStateException("failed to serialize aggregate payload", e);
                }
                entries.add(SendMessageBatchRequestEntry.builder().id(Integer.toString(i)).messageBody(body).build());
            }
            client.sendMessageBatch(SendMessageBatchRequest.builder().queueUrl(queueUrl).entries(entries).build());
        }
    }
}
