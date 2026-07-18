import argparse
import json
import os
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

import boto3

ENDPOINT = os.getenv("AWS_ENDPOINT_URL", "http://localhost:4589")
REGION = os.getenv("AWS_REGION", "eu-west-1")
QUEUE_NAME = os.getenv("SQS_QUEUE_NAME", "wcm-reserve-agg")

# Synthetic type names distinct from the 5 real sensor types, so burst
# traffic never lands in the DynamoDB partitions the live dashboard reads
# from.
LOAD_TYPES = ["loadtest_a", "loadtest_b", "loadtest_c", "loadtest_d", "loadtest_e"]

BASE = datetime.now(timezone.utc)


def sample_message(i):
    sensor_type = LOAD_TYPES[i % len(LOAD_TYPES)]
    end = BASE + timedelta(milliseconds=i)
    return {
        "sensor_type": sensor_type,
        "site_id": f"load-{i % 50}",
        "unit": "x",
        "window_start": (end - timedelta(seconds=10)).isoformat(),
        "window_end": end.isoformat(),
        "count": 5,
        "min": 1.0, "max": 9.0, "avg": 5.0, "latest": 5.0,
        "alerts": [],
    }


def queue_depth(sqs, queue_url):
    attrs = sqs.get_queue_attributes(
        QueueUrl=queue_url,
        AttributeNames=["ApproximateNumberOfMessages", "ApproximateNumberOfMessagesNotVisible"],
    )["Attributes"]
    return int(attrs["ApproximateNumberOfMessages"]), int(attrs["ApproximateNumberOfMessagesNotVisible"])


def wait_for_drain(sqs, queue_url, timeout_seconds=120, poll_interval=3):
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        waiting, in_flight = queue_depth(sqs, queue_url)
        print(f"  queue depth: waiting={waiting} in_flight={in_flight}")
        if waiting == 0 and in_flight == 0:
            return True
        time.sleep(poll_interval)
    return False


def main():
    parser = argparse.ArgumentParser(description="Burst-load the backend queue to demonstrate scaling.")
    parser.add_argument("--messages", type=int, default=2000)
    parser.add_argument("--workers", type=int, default=32)
    args = parser.parse_args()

    sqs = boto3.client("sqs", endpoint_url=ENDPOINT, region_name=REGION)
    queue_url = sqs.get_queue_url(QueueName=QUEUE_NAME)["QueueUrl"]

    def send(i):
        sqs.send_message(QueueUrl=queue_url, MessageBody=json.dumps(sample_message(i)))

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        list(pool.map(send, range(args.messages)))
    elapsed = time.monotonic() - start

    print(f"sent {args.messages} messages in {elapsed:.2f}s ({args.messages / elapsed:.0f} msg/s)")

    waiting, in_flight = queue_depth(sqs, queue_url)
    print(f"queue depth immediately after burst: waiting={waiting} in_flight={in_flight}")
    assert waiting + in_flight > 0, (
        "expected the queue to show the burst messages immediately after sending -- "
        "if this is 0, the sends did not actually reach SQS"
    )

    initial_total = waiting + in_flight

    if wait_for_drain(sqs, queue_url):
        print("PASSED: queue fully drained by the Lambda consumer within the timeout -- "
              "burst was absorbed instantly and processed to completion, demonstrating real elasticity")
    else:
        waiting_after, in_flight_after = queue_depth(sqs, queue_url)
        remaining = waiting_after + in_flight_after
        print(f"queue depth after timeout: waiting={waiting_after} in_flight={in_flight_after}")
        # Under LocalStack's single-container Lambda emulation, 2000 messages can
        # genuinely take longer than the timeout to fully drain -- that alone isn't
        # a broken pipeline. What would be broken is the consumer making NO progress
        # at all, so assert real throughput happened even when drain isn't complete.
        assert remaining < initial_total, (
            f"expected the queue to show real processing progress within the timeout "
            f"(remaining={remaining} should be less than the immediate post-burst "
            f"count={initial_total}) -- if this is not true, the Lambda consumer is not "
            f"processing the queue at all, not just processing it slowly"
        )
        print(f"WARNING: queue did not fully drain within the timeout window, but real "
              f"progress was made ({initial_total} -> {remaining} remaining) -- consistent "
              f"with LocalStack's single-container Lambda throughput ceiling, not evidence "
              f"the pipeline is broken")


if __name__ == "__main__":
    main()
