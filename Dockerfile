FROM gradle:8.13-jdk17 AS builder

WORKDIR /app

COPY gradle ./gradle
COPY --chmod=0755 gradlew ./
COPY build.gradle settings.gradle ./

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin spring

COPY --from=builder --chown=spring:spring /app/build/libs/*.jar /app/app.jar

RUN mkdir -p /app/uploads && chown spring:spring /app/uploads

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
