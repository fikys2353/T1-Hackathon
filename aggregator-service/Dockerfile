# Stage 1: сборка
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Копируем Gradle wrapper и файлы сборки
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
RUN chmod +x gradlew

# Копируем исходники
COPY src ./src

# Собираем jar
RUN ./gradlew clean bootJar --no-daemon

# Stage 2: финальный образ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Копируем собранный jar из builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Порт приложения
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java","-jar","app.jar"]