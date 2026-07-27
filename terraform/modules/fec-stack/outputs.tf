output "dashboard_url" {
  value = "https://${aws_s3_bucket.frontend.bucket}.s3.${var.region}.amazonaws.com/${var.frontend_index_file}"
}

output "api_url" {
  value = local.api_base
}

output "fog_public_ip" {
  value = aws_eip.fog.public_ip
}

output "ec2_instance_id" {
  value = aws_instance.fog.id
}

output "dynamodb_table" {
  value = aws_dynamodb_table.readings.name
}

output "sqs_queue" {
  value = aws_sqs_queue.agg.id
}

output "processor_lambda" {
  value = aws_lambda_function.processor.function_name
}

output "dashboard_lambda" {
  value = aws_lambda_function.dashboard.function_name
}

output "api_gateway_id" {
  value = aws_api_gateway_rest_api.dashboard.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "deploy_bucket" {
  value = aws_s3_bucket.deploy.bucket
}

output "security_group_id" {
  value = aws_security_group.fog.id
}

output "eip_allocation_id" {
  value = aws_eip.fog.id
}
