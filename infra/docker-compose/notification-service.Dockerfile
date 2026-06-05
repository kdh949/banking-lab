FROM eclipse-temurin:21-jre

WORKDIR /app
COPY services/notification-service/build/libs/notification-service-*-migration.jar /app/notification-service.jar

EXPOSE 8089
ENTRYPOINT ["java", "-jar", "/app/notification-service.jar"]
