# ============================================================
# AKB 后端镜像(多阶段:build → jre run)
# 生产配置走 application-prod.properties(环境变量注入,见 docker-compose.yml)
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷 pom 预拉依赖(利用层缓存)
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true
COPY src ./src
RUN mvn -B package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
