resource "aws_dynamodb_table" "readings" {
  name         = var.table_name
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "sensor_type"
  range_key    = "sort_key"

  attribute {
    name = "sensor_type"
    type = "S"
  }

  attribute {
    name = "sort_key"
    type = "S"
  }
}
