variable "region" {
  type    = string
  default = "us-east-1"
}

variable "prefix" {
  description = "Short resource-name prefix for this project, e.g. \"wcm\"."
  type        = string
}

variable "lab_role_name" {
  description = "Pre-existing IAM role every AWS Academy Learner Lab provides (cannot create new roles in this account type)."
  type        = string
  default     = "LabRole"
}

variable "lab_instance_profile_name" {
  type    = string
  default = "LabInstanceProfile"
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
  description = "Shell command, run from repo root, that produces processor_zip_path."
  type        = string
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

variable "processor_memory" {
  type    = number
  default = 256
}

variable "processor_timeout" {
  type    = number
  default = 30
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

variable "dashboard_memory" {
  type    = number
  default = 256
}

variable "dashboard_timeout" {
  type    = number
  default = 30
}

variable "ec2_ami" {
  description = "Amazon Linux 2023 x86_64, us-east-1."
  type        = string
  default     = "ami-0fd6240f599091088"
}

variable "ec2_instance_type" {
  type    = string
  default = "t3.small"
}

variable "fog_availability_zone" {
  type    = string
  default = ""
}

variable "frontend_bucket_name" {
  type    = string
  default = ""
}

variable "ec2_port" {
  type    = number
  default = 8000
}

variable "ec2_source_dirs" {
  description = "Local directories (relative to repo root) to tar up and ship to the instance: normally [sensors, fog, infra]."
  type        = list(string)
  default     = ["sensors", "fog", "infra"]
}

variable "ec2_compose_file" {
  description = "docker-compose file name inside infra/ to run on the instance."
  type        = string
  default     = "docker-compose.aws.yml"
}

variable "project_root" {
  description = "Local path to the project directory, used to resolve build commands and source dirs."
  type        = string
}

variable "frontend_local_dir" {
  description = "Local static frontend directory to upload, e.g. backend/dashboard/static."
  type        = string
}

variable "frontend_index_file" {
  description = "Filename of the frontend's entry HTML file within frontend_local_dir."
  type        = string
  default     = "index.html"
}

variable "api_base_placeholder" {
  description = "The exact placeholder token the frontend uses for its API base (__API_BASE__)."
  type        = string
}

variable "api_base_search_files" {
  description = "Which file(s) inside frontend_local_dir contain api_base_placeholder and need substitution."
  type        = list(string)
}
