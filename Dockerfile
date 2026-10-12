# Build stage - Backend
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /build
COPY dockerportal-server/pom.xml .
RUN mvn dependency:go-offline -B
COPY dockerportal-server/src ./src
RUN mvn package -DskipTests -B

# Build stage - Frontend
FROM node:20-alpine AS frontend-build
WORKDIR /build
COPY dockerportal-web/package.json dockerportal-web/package-lock.json* ./
RUN npm install
COPY dockerportal-web/ .
RUN npm run build

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY --from=backend-build /build/target/dockerportal-server-*.jar app.jar
COPY --from=frontend-build /build/dist /app/static

RUN mkdir -p /app/data && chown -R app:app /app

USER app

EXPOSE 9800

# 数据路径与前端静态目录通过环境变量注入（见 docker-compose.yml / .env）
ENV DP_DATA_PATH=/app/data \
    DP_STATIC_LOCATION=file:/app/static/

ENTRYPOINT ["java", "-jar", "app.jar"]
