#!/bin/bash
# LocalStack-only deploy tooling: the endpoint default below and the
# hardcoded test/test credentials further down only make sense against
# LocalStack's fake account (000000000000) and its lambda-role stub. Never
# invoke this script against real AWS -- it would silently target
# localstack:4566 if AWS_ENDPOINT_URL happens to be unset, and would
# overwrite any real credentials already exported in the environment. The
# real AWS deployment uses its own separate CLI commands (see readme.txt).
set -e

ENDPOINT="${AWS_ENDPOINT_URL:-http://localstack:4566}"
QUEUE_NAME="${SQS_QUEUE_NAME:-wcm-reserve-agg}"
FUNCTION_NAME="${LAMBDA_FUNCTION_NAME:-wcm-processor}"
TABLE_NAME="${TABLE_NAME:-wcm-readings}"

export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION="${AWS_REGION:-eu-west-1}"

echo "waiting for queue $QUEUE_NAME..."
QUEUE_URL=""
for i in $(seq 1 60); do
  QUEUE_URL=$(aws --endpoint-url "$ENDPOINT" sqs get-queue-url --queue-name "$QUEUE_NAME" --query QueueUrl --output text 2>/dev/null || true)
  [ -n "$QUEUE_URL" ] && [ "$QUEUE_URL" != "None" ] && break
  sleep 3
done
if [ -z "$QUEUE_URL" ] || [ "$QUEUE_URL" = "None" ]; then
  echo "queue $QUEUE_NAME never became available"
  exit 1
fi

QUEUE_ARN=$(aws --endpoint-url "$ENDPOINT" sqs get-queue-attributes --queue-url "$QUEUE_URL" --attribute-names QueueArn --query "Attributes.QueueArn" --output text)

if aws --endpoint-url "$ENDPOINT" lambda get-function --function-name "$FUNCTION_NAME" >/dev/null 2>&1; then
  echo "updating existing function code"
  aws --endpoint-url "$ENDPOINT" lambda update-function-code --function-name "$FUNCTION_NAME" --zip-file fileb://function.jar >/dev/null
else
  echo "creating function $FUNCTION_NAME"
  aws --endpoint-url "$ENDPOINT" lambda create-function \
    --function-name "$FUNCTION_NAME" \
    --runtime java17 \
    --role arn:aws:iam::000000000000:role/lambda-role \
    --handler com.fec.wildlife.processor.WildlifeHandler::handleRequest \
    --zip-file fileb://function.jar \
    --timeout 30 \
    --memory-size 512 \
    --environment "Variables={TABLE_NAME=$TABLE_NAME,AWS_ENDPOINT_URL=$ENDPOINT,AWS_REGION=$AWS_DEFAULT_REGION}" >/dev/null
fi

echo "waiting for lambda to become active..."
for i in $(seq 1 60); do
  STATE=$(aws --endpoint-url "$ENDPOINT" lambda get-function --function-name "$FUNCTION_NAME" --query "Configuration.State" --output text 2>/dev/null || true)
  [ "$STATE" = "Active" ] && break
  sleep 3
done
if [ "$STATE" != "Active" ]; then
  echo "lambda $FUNCTION_NAME never became active"
  exit 1
fi

aws --endpoint-url "$ENDPOINT" lambda create-event-source-mapping \
  --function-name "$FUNCTION_NAME" \
  --event-source-arn "$QUEUE_ARN" \
  --batch-size 10 >/dev/null 2>&1 || echo "event source mapping already exists, continuing"

echo "lambda $FUNCTION_NAME deployed and wired to queue $QUEUE_NAME"
