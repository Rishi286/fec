# Wildlife Conservation & Habitat Monitoring

Fog and Edge Computing project: multi-sensor wildlife reserve monitoring with on-edge aggregation, AWS SQS/Lambda/DynamoDB backend, and a live operations dashboard.

## Stack
- **Sensors** – JDK 17 simulated reserve sensors
- **Fog node** – window aggregation, alerts, SQS publish
- **Backend** – SQS-triggered processor Lambda + dashboard API Lambda
- **Frontend** – static S3-hosted dashboard
- **Local** – Docker Compose + LocalStack

## Quick start
```bash
# prerequisites: Docker, JDK 17, Maven 3.9+
cd infra && docker compose up --build
```

See `readme.txt` for full install, configuration, testing, and AWS deployment steps.

## Modules
| Path | Role |
|------|------|
| `sensors/` | Reserve sensor units |
| `fog/` | Habitat gateway (edge) |
| `backend/processor/` | SQS → DynamoDB Lambda |
| `backend/dashboard/` | Dashboard API + static UI |
| `infra/` | Compose, LocalStack, verify scripts |
