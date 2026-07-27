data "aws_iam_role" "lab" {
  name = var.lab_role_name
}

resource "aws_lambda_function" "processor" {
  function_name    = var.processor_lambda_name
  role             = data.aws_iam_role.lab.arn
  handler          = var.processor_handler
  runtime          = var.processor_runtime
  memory_size      = var.processor_memory
  timeout          = var.processor_timeout
  filename         = "${var.project_root}/${var.processor_zip_path}"
  source_code_hash = filebase64sha256("${var.project_root}/${var.processor_zip_path}")

  environment {
    variables = {
      TABLE_NAME = var.table_name
    }
  }
}

resource "aws_lambda_event_source_mapping" "processor_from_queue" {
  event_source_arn = aws_sqs_queue.agg.arn
  function_name    = aws_lambda_function.processor.arn
  batch_size       = 10
}

resource "aws_lambda_function" "dashboard" {
  function_name    = var.dashboard_lambda_name
  role             = data.aws_iam_role.lab.arn
  handler          = var.dashboard_handler
  runtime          = var.dashboard_runtime
  memory_size      = var.dashboard_memory
  timeout          = var.dashboard_timeout
  filename         = "${var.project_root}/${var.dashboard_zip_path}"
  source_code_hash = filebase64sha256("${var.project_root}/${var.dashboard_zip_path}")

  environment {
    variables = {
      TABLE_NAME          = var.table_name
      SQS_QUEUE_NAME      = var.queue_name
      LAMBDA_FUNCTION_NAME = var.processor_lambda_name
      FOG_HEALTH_URL      = "http://${aws_eip.fog.public_ip}:${var.ec2_port}/health"
      FOG_THRESHOLDS_URL  = "http://${aws_eip.fog.public_ip}:${var.ec2_port}/thresholds"
    }
  }
}
