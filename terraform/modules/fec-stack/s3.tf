data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "frontend" {
  bucket        = var.frontend_bucket_name != "" ? var.frontend_bucket_name : "${var.prefix}-frontend-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket" "deploy" {
  bucket = "${var.prefix}-deploy-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = false
  ignore_public_acls      = false
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "frontend_public_read" {
  bucket     = aws_s3_bucket.frontend.id
  depends_on = [aws_s3_bucket_public_access_block.frontend]
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadGetObject"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.frontend.arn}/*"
    }]
  })
}

resource "aws_s3_bucket_website_configuration" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  index_document {
    suffix = "index.html"
  }
}

resource "aws_s3_object" "deploy_tarball" {
  bucket = aws_s3_bucket.deploy.bucket
  key    = "deploy-src.tar.gz"
  source = "/tmp/${var.prefix}-build/deploy-src.tar.gz"
  etag   = filemd5("/tmp/${var.prefix}-build/deploy-src.tar.gz")
}

resource "null_resource" "upload_frontend" {
  triggers = {
    api_base = local.api_base
  }

  provisioner "local-exec" {
    working_dir = var.project_root
    command     = <<-EOT
      set -e
      rm -rf /tmp/${var.prefix}-frontend
      cp -R ${var.frontend_local_dir} /tmp/${var.prefix}-frontend
      %{for f in var.api_base_search_files~}
      sed -i.bak 's#${var.api_base_placeholder}#${local.api_base}#g' "/tmp/${var.prefix}-frontend/${f}"
      rm -f "/tmp/${var.prefix}-frontend/${f}.bak"
      %{endfor~}
      aws s3 cp "/tmp/${var.prefix}-frontend/${var.frontend_index_file}" "s3://${aws_s3_bucket.frontend.bucket}/${var.frontend_index_file}" --region ${var.region}
      aws s3 sync "/tmp/${var.prefix}-frontend" "s3://${aws_s3_bucket.frontend.bucket}/static" --region ${var.region} --exclude "${var.frontend_index_file}"
    EOT
  }

  depends_on = [aws_api_gateway_stage.prod, aws_s3_bucket_website_configuration.frontend]
}

locals {
  api_base = "https://${aws_api_gateway_rest_api.dashboard.id}.execute-api.${var.region}.amazonaws.com/${aws_api_gateway_stage.prod.stage_name}"
}
