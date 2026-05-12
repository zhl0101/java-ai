FROM openjdk:21-jdk-slim
WORKDIR /app
COPY target/java-ai-day1-1.0.0.jar app.jar
EXPOSE 9876
ENTRYPOINT ["java", "-jar", "app.jar"]