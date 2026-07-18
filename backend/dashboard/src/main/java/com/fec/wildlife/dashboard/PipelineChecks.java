package com.fec.wildlife.dashboard;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.LinkedHashMap;
import java.util.Map;

public class PipelineChecks {

    public boolean queueReachable(SqsClient sqs, String queueName) {
        try {
            String queueUrl = sqs.getQueueUrl(b -> b.queueName(queueName)).queueUrl();
            sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN).build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean lambdaDeployed(LambdaClient lambda, String functionName) {
        try {
            var resp = lambda.getFunction(GetFunctionRequest.builder().functionName(functionName).build());
            return "Active".equals(resp.configuration().stateAsString());
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> queueDepth(SqsClient sqs, String queueName) {
        try {
            String queueUrl = sqs.getQueueUrl(b -> b.queueName(queueName)).queueUrl();
            var attrs = sqs.getQueueAttributes(GetQueueAttributesRequest.builder().queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE).build()).attributesAsStrings();
            Map<String, Object> depth = new LinkedHashMap<>();
            depth.put("waiting", Integer.parseInt(attrs.get("ApproximateNumberOfMessages")));
            depth.put("in_flight", Integer.parseInt(attrs.get("ApproximateNumberOfMessagesNotVisible")));
            return depth;
        } catch (Exception e) {
            return null;
        }
    }

    // A single Select.COUNT scan only counts the ~1MB page DynamoDB happens
    // to return, silently undercounting any table larger than that (the
    // same undercount bug already found and fixed, with four other code
    // shapes, in this portfolio's four other reassigned projects). Here the
    // fix uses the SDK's own scanPaginator() -- an Iterable that follows
    // LastEvaluatedKey across pages automatically -- rather than a hand-
    // rolled loop, do-while, or recursive call.
    public int itemCount(DynamoDbClient dynamo, String tableName) {
        ScanRequest request = ScanRequest.builder().tableName(tableName).select(Select.COUNT).build();
        int total = 0;
        for (var page : dynamo.scanPaginator(request)) {
            total += page.count();
        }
        return total;
    }
}
