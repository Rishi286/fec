Wildlife Conservation & Habitat Monitoring

PREREQUISITES
--------------
- Docker and Docker Compose (for running the local sensors/fog/dashboard stack)
- JDK 17
- Maven 3.9+ (for building/testing modules outside Docker)
- AWS CLI v2 (only needed for the AWS Deployment Steps section below)
- Terraform >= 1.5 with the hashicorp/aws provider ~> 5.0 (only needed for the AWS Deployment Steps section below)

INSTALLATION STEPS
--------------------
1. Clone the repository and change into this project's directory:
   cd projects/24-wildlife-conservation-monitoring
2. Each module (sensors, fog, backend/processor, backend/dashboard) is a standalone Maven project with its own pom.xml. No separate dependency-install step is required beyond Maven resolving each module's dependencies on first build/test (see BUILD INSTRUCTIONS and TESTING INSTRUCTIONS below).

CONFIGURATION
--------------
Environment variables read by each component (all have working defaults except SENSOR_TYPE, which is required):

sensors (ReserveSensorUnit):
  SENSOR_TYPE       - sensor type name to emit (e.g. motion_detection_count, acoustic_poaching_risk_db, waterhole_level_cm, ambient_temp_c, soil_moisture_pct); no default, must be set
  SITE_ID           - reserve identifier tagged on each reading (default: reserve-a)
  SAMPLE_INTERVAL   - seconds between samples (default: 2)
  DISPATCH_INTERVAL - seconds between batched POSTs to the fog node (default: 10)
  FOG_URL           - fog node ingest endpoint (default: http://fog:8000/ingest)

fog (HabitatGateway):
  WINDOW_SECONDS    - aggregation window length in seconds (default: 10)
  SQS_QUEUE_NAME    - target SQS queue name for published aggregates (default: wcm-reserve-agg)
  AWS_ENDPOINT_URL  - AWS endpoint override; set to LocalStack's endpoint locally, unset in real AWS (no default)
  AWS_REGION        - AWS region (default: eu-west-1)

backend/processor (WildlifeHandler, SQS-triggered Lambda):
  TABLE_NAME        - DynamoDB table name to write readings to (default: wcm-readings)
  AWS_ENDPOINT_URL  - AWS endpoint override; set to LocalStack's endpoint locally, unset in real AWS (no default)
  AWS_REGION        - AWS region (default: eu-west-1)

backend/dashboard (WildlifeDashboardApp / WildlifeDashboardLambda):
  TABLE_NAME           - DynamoDB table to read readings from (default: wcm-readings)
  SQS_QUEUE_NAME       - SQS queue name to report queue depth/reachability for (default: wcm-reserve-agg)
  LAMBDA_FUNCTION_NAME - processor Lambda name to check deployment status of (default: wcm-processor)
  AWS_ENDPOINT_URL     - AWS endpoint override; set to LocalStack's endpoint locally, unset in real AWS (no default)
  AWS_REGION           - AWS region (default: eu-west-1)
  FOG_HEALTH_URL       - fog node health-check URL (default: http://fog:8000/health)
  FOG_THRESHOLDS_URL   - fog node thresholds URL (default: http://fog:8000/thresholds)

BUILD INSTRUCTIONS
--------------------
Each module builds independently with Maven and produces a jar under its own target/ directory:

  cd sensors && mvn package                  -> target/sensor.jar
  cd fog && mvn package                      -> target/fog.jar (shaded)
  cd backend/processor && mvn package        -> target/processor.jar (shaded)
  cd backend/dashboard && mvn package        -> target/dashboard.jar (shaded)

Add -DskipTests to any of the above to skip running tests during the build.

RUN INSTRUCTIONS
------------------
The local stack is defined in infra/docker-compose.yml and runs LocalStack, the fog node, a one-shot Lambda-deploy container, the dashboard, and 10 sensor containers (5 sensor types x 2 reserves):

  cd infra
  docker compose up --build

Exposed host ports:
  localhost:4589 -> LocalStack (container port 4566)
  localhost:8103 -> dashboard (container port 8000)

The fog node and sensor containers are only reachable on the internal Docker network (no host port is published for them in this file).

AWS DEPLOYMENT STEPS
-----------------------
Deployment uses the Terraform module in terraform/, driven by the settings file terraform/deployments/wcm.tfvars. The frontend does not read its API base from index.html: dashboard.js fetches static/api-config.json at page load, so that file carries the __API_BASE__ token and the deploy step substitutes the real API Gateway URL into it; the unsubstituted token resolves to same-origin locally. Follow these steps to deploy:

1. Configure AWS credentials for the target AWS account (access key, secret key, and session token if using temporary credentials), then confirm the active identity:
   aws sts get-caller-identity

2. From the terraform/ directory, create and switch to a dedicated Terraform workspace for this project (never apply directly on the "default" workspace):
   cd terraform
   terraform workspace new wcm
   terraform workspace list

3. Build the Lambda jars and the EC2 source tarball, then apply:
   ./build.sh deployments/wcm.tfvars
   terraform apply -var-file=deployments/wcm.tfvars

   Read the plan output before confirming; a nonzero "destroy" count against a shared-module apply is a stop-and-check signal.

4. Switch back to the default workspace when finished:
   terraform workspace select default

TESTING INSTRUCTIONS
-----------------------
Each module has its own JUnit 5 test suite, run independently with Maven:

  cd sensors && mvn test                  -> 7 tests
  cd fog && mvn test                      -> 44 tests
  cd backend/processor && mvn test        -> 5 tests
  cd backend/dashboard && mvn test        -> 26 tests

Total: 82 tests across all four modules. All 82 pass as of this writing.
