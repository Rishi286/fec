package com.fec.wildlife.processor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real AWS Lambda entry point, registered with an SQS event-source mapping in deploy_lambda.sh. */
public class WildlifeHandler implements RequestHandler<SQSEvent, Map<String, Object>> {

    static final String TABLE_NAME = System.getenv().getOrDefault("TABLE_NAME", "wcm-readings");
    static DynamoDbClient client;

    // Lambda (even under LocalStack) can reuse a warm execution environment
    // across invocations, so the client is cached in a static field instead
    // of being rebuilt on every handleRequest() call.
    static synchronized DynamoDbClient client() {
        if (client == null) {
            String endpoint = System.getenv("AWS_ENDPOINT_URL");
            String region = System.getenv().getOrDefault("AWS_REGION", "eu-west-1");
            var builder = DynamoDbClient.builder().region(Region.of(region));
            // LocalStack accepts any static credentials; real AWS issues temporary
            // ones (session token required) via the execution role, so this
            // override must not apply outside the LocalStack case.
            if (endpoint != null) {
                builder.endpointOverride(URI.create(endpoint));
                builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
            }
            client = builder.build();
        }
        return client;
    }

    static int processRecords(List<SQSEvent.SQSMessage> records, DynamoDbClient dynamo, String tableName) {
        int processed = 0;
        for (SQSEvent.SQSMessage record : records) {
            try {
                Map<String, AttributeValue> item = RecordMapper.toItem(record.getBody());
                dynamo.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
                processed++;
            } catch (Exception e) {
                // Deliberately fail the whole batch on any single bad record
                // rather than skipping it: the SQS event source mapping will
                // then leave the batch unacked and retry it, the simplest
                // correct behaviour at this deployment's data volume.
                throw new RuntimeException(e);
            }
        }
        return processed;
    }

    @Override
    public Map<String, Object> handleRequest(SQSEvent event, Context context) {
        int processed = processRecords(event.getRecords(), client(), TABLE_NAME);
        Map<String, Object> result = new HashMap<>();
        result.put("processed", processed);
        return result;
    }
}
