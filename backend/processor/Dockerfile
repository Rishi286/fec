FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir awscli
COPY --from=build /app/target/processor.jar ./function.jar
COPY deploy_lambda.sh .
RUN chmod +x deploy_lambda.sh
CMD ["./deploy_lambda.sh"]
