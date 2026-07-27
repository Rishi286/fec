prefix       = "wcm"
project_root = ".."

fog_availability_zone = "us-east-1a"

table_name = "wcm-readings"
queue_name = "wcm-reserve-agg"

processor_lambda_name   = "wcm-processor"
processor_build_command = "cd backend/processor && mvn package -DskipTests -q"
processor_zip_path      = "backend/processor/target/processor.jar"
processor_handler       = "com.fec.wildlife.processor.WildlifeHandler::handleRequest"
processor_runtime       = "java17"

dashboard_lambda_name   = "wcm-dashboard-api"
dashboard_build_command = "cd backend/dashboard && mvn package -DskipTests -q"
dashboard_zip_path      = "backend/dashboard/target/dashboard.jar"
dashboard_handler       = "com.fec.wildlife.dashboard.WildlifeDashboardLambda::handleRequest"
dashboard_runtime       = "java17"

frontend_local_dir    = "backend/dashboard/static"
api_base_placeholder  = "__API_BASE__"
api_base_search_files = ["api-config.json"]
