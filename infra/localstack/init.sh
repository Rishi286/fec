#!/bin/bash
set -e

export AWS_DEFAULT_REGION=eu-west-1

awslocal sqs create-queue --queue-name wcm-reserve-agg

awslocal dynamodb create-table \
  --table-name wcm-readings \
  --attribute-definitions \
    AttributeName=sensor_type,AttributeType=S \
    AttributeName=sort_key,AttributeType=S \
  --key-schema \
    AttributeName=sensor_type,KeyType=HASH \
    AttributeName=sort_key,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

echo "localstack init complete: queue + table created"
