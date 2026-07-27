variable "region" {
  type    = string
  default = "us-east-1"
}

variable "prefix" {
  type = string
}

variable "project_root" {
  type = string
}

variable "table_name" {
  type = string
}

variable "queue_name" {
  type = string
}

variable "processor_lambda_name" {
  type = string
}

variable "processor_build_command" {
  type = string
}

variable "processor_zip_path" {
  type = string
}

variable "processor_handler" {
  type = string
}

variable "processor_runtime" {
  type = string
}

variable "dashboard_lambda_name" {
  type = string
}

variable "dashboard_build_command" {
  type = string
}

variable "dashboard_zip_path" {
  type = string
}

variable "dashboard_handler" {
  type = string
}

variable "dashboard_runtime" {
  type = string
}

variable "ec2_source_dirs" {
  type    = list(string)
  default = ["sensors", "fog", "infra"]
}

variable "ec2_compose_file" {
  type    = string
  default = "docker-compose.aws.yml"
}

variable "frontend_local_dir" {
  type = string
}

variable "frontend_index_file" {
  type    = string
  default = "index.html"
}

variable "api_base_placeholder" {
  type = string
}

variable "api_base_search_files" {
  type = list(string)
}

variable "fog_availability_zone" {
  type    = string
  default = ""
}

variable "frontend_bucket_name" {
  type    = string
  default = ""
}
