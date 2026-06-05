FROM eclipse-temurin:21-jre

WORKDIR /app
COPY services/reporting-service/build/libs/reporting-service-*-migration.jar /app/reporting-service.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/reporting-service.jar"]
