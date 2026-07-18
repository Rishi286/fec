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

/** Lazily resolves the SQS queue URL on first publish() with a jittered (+/-20% via ThreadLocalRandom) exponential backoff (300ms base, doubling, capped 5000ms, 12 attempts) -- the only one of nine sibling fog publishers whose retry delay is randomized rather than fixed/linear/plain-exponential, to desynchronize concurrent LocalStack queue-bootstrap races. */
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
        // LocalStack accepts any static credentials; real AWS issues temporary
        // ones (session token required) via the execution role, so this
        // override must not apply outside the LocalStack case.
        if (endpointUrl != null) {
            builder.endpointOverride(URI.create(endpointUrl));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
        }
        this.client = builder.build();
        this.queueName = queueName;
    }

    /** Test-only entry point: injects a pre-built client directly instead of going through the SqsClient.builder() endpoint/credentials wiring above. */
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

    // A single flush cycle can close several (sensor_type, site_id) groups
    // at once; sending each as its own sendMessage() call is one SQS API
    // call per group. This chunks the whole batch at SendMessageBatch's
    // 10-entry limit instead, issuing at most ceil(n/10) calls per flush.
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
