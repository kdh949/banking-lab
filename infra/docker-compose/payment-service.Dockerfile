FROM eclipse-temurin:21-jre

WORKDIR /app
COPY services/payment-service/build/libs/payment-service-*-migration.jar /app/payment-service.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/payment-service.jar"]
