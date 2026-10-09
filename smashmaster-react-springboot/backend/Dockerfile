# --- Stage 1: Build the application ---
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy the source code and pom.xml
COPY . .

# Build the project and package it into a JAR file, skipping tests for faster deployment
RUN mvn clean package -DskipTests

# --- Stage 2: Run the application ---
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the compiled JAR file from the build stage
# Note: Adjust 'target/*.jar' if your JAR file has a specific fixed name
COPY --from=build /app/target/*.jar app.jar

# Expose the port your application listens on (Render uses PORT env variable)
EXPOSE 8080

# Command to execute the application
ENTRYPOINT ["java", "-jar", "app.jar"]
