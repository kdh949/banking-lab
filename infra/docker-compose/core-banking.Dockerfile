FROM eclipse-temurin:21-jre

WORKDIR /app
COPY services/core-banking/build/libs/core-banking-*-migration.jar /app/core-banking.jar
COPY db/migrations /app/db/migrations

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/core-banking.jar"]
