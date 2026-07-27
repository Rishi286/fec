resource "aws_sqs_queue" "agg" {
  name = var.queue_name
}
