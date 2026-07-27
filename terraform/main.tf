terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.region
}

module "stack" {
  source = "./modules/fec-stack"

  region                   = var.region
  prefix                   = var.prefix
  project_root             = var.project_root
  table_name               = var.table_name
  queue_name               = var.queue_name
  processor_lambda_name    = var.processor_lambda_name
  processor_build_command  = var.processor_build_command
  processor_zip_path       = var.processor_zip_path
  processor_handler        = var.processor_handler
  processor_runtime        = var.processor_runtime
  dashboard_lambda_name    = var.dashboard_lambda_name
  dashboard_build_command  = var.dashboard_build_command
  dashboard_zip_path       = var.dashboard_zip_path
  dashboard_handler        = var.dashboard_handler
  dashboard_runtime        = var.dashboard_runtime
  ec2_source_dirs          = var.ec2_source_dirs
  ec2_compose_file         = var.ec2_compose_file
  frontend_local_dir       = var.frontend_local_dir
  frontend_index_file      = var.frontend_index_file
  api_base_placeholder     = var.api_base_placeholder
  api_base_search_files    = var.api_base_search_files
  fog_availability_zone    = var.fog_availability_zone
  frontend_bucket_name     = var.frontend_bucket_name
}

output "dashboard_url" {
  value = module.stack.dashboard_url
}

output "api_url" {
  value = module.stack.api_url
}

output "fog_public_ip" {
  value = module.stack.fog_public_ip
}

output "summary" {
  value = <<-EOT
    DynamoDB:  ${module.stack.dynamodb_table}
    SQS:       ${module.stack.sqs_queue}
    Lambdas:   ${module.stack.processor_lambda}, ${module.stack.dashboard_lambda}
    API GW:    ${module.stack.api_gateway_id}
    EC2:       ${module.stack.ec2_instance_id} (${module.stack.fog_public_ip})
    S3:        ${module.stack.frontend_bucket}, ${module.stack.deploy_bucket}
    Dashboard: ${module.stack.dashboard_url}
    API:       ${module.stack.api_url}
  EOT
}
