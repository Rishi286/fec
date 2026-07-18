package com.fec.wildlife.dashboard;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.GetFunctionResponse;
import software.amazon.awssdk.services.lambda.model.State;

class FakeLambdaClient implements LambdaClient {

    private final boolean exists;
    private final State state;

    FakeLambdaClient(boolean exists, State state) {
        this.exists = exists;
        this.state = state;
    }

    @Override
    public GetFunctionResponse getFunction(GetFunctionRequest request) {
        if (!exists) throw new RuntimeException("function not found");
        return GetFunctionResponse.builder()
            .configuration(FunctionConfiguration.builder().state(state).build())
            .build();
    }

    @Override
    public String serviceName() {
        return "lambda";
    }

    @Override
    public void close() {
    }
}
