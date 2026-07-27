resource "aws_api_gateway_rest_api" "dashboard" {
  name = "${var.prefix}-dashboard-api"
}

resource "aws_api_gateway_resource" "proxy" {
  rest_api_id = aws_api_gateway_rest_api.dashboard.id
  parent_id   = aws_api_gateway_rest_api.dashboard.root_resource_id
  path_part   = "{proxy+}"
}

resource "aws_api_gateway_method" "root_any" {
  rest_api_id   = aws_api_gateway_rest_api.dashboard.id
  resource_id   = aws_api_gateway_rest_api.dashboard.root_resource_id
  http_method   = "ANY"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "root_any" {
  rest_api_id             = aws_api_gateway_rest_api.dashboard.id
  resource_id             = aws_api_gateway_rest_api.dashboard.root_resource_id
  http_method             = aws_api_gateway_method.root_any.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.dashboard.invoke_arn
}

resource "aws_api_gateway_method" "proxy_any" {
  rest_api_id   = aws_api_gateway_rest_api.dashboard.id
  resource_id   = aws_api_gateway_resource.proxy.id
  http_method   = "ANY"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "proxy_any" {
  rest_api_id             = aws_api_gateway_rest_api.dashboard.id
  resource_id             = aws_api_gateway_resource.proxy.id
  http_method             = aws_api_gateway_method.proxy_any.http_method
  type                    = "AWS_PROXY"
  integration_http_method = "POST"
  uri                     = aws_lambda_function.dashboard.invoke_arn
}

resource "aws_lambda_permission" "apigw_invoke" {
  statement_id  = "apigw-invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.dashboard.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.dashboard.execution_arn}/*/*"
}

resource "aws_api_gateway_deployment" "dashboard" {
  rest_api_id = aws_api_gateway_rest_api.dashboard.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_integration.root_any.uri,
      aws_api_gateway_integration.proxy_any.uri,
    ]))
  }

  depends_on = [
    aws_api_gateway_integration.root_any,
    aws_api_gateway_integration.proxy_any,
  ]

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "prod" {
  deployment_id = aws_api_gateway_deployment.dashboard.id
  rest_api_id   = aws_api_gateway_rest_api.dashboard.id
  stage_name    = "prod"
}
