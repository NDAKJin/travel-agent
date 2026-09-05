FROM docker.m.daocloud.io/library/maven:3.9.16-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -s .mvn/settings.xml -DskipTests dependency:go-offline
COPY src src
RUN cp src/main/resources/application.example.yml src/main/resources/application.yml \
    && ./mvnw -s .mvn/settings.xml -DskipTests package

FROM docker.m.daocloud.io/library/node:22-alpine AS frontend-build
WORKDIR /workspace
COPY fe/package.json fe/package-lock.json ./
RUN npm ci
COPY fe ./
RUN npm run build

FROM docker.m.daocloud.io/library/node:22-alpine AS agent-web-build
WORKDIR /workspace
COPY agent-web/package.json agent-web/package-lock.json ./
RUN npm ci
COPY agent-web ./
RUN npm run build

FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre AS api
WORKDIR /app
RUN useradd --system --uid 10001 app \
    && mkdir -p logs /tmp/travel-agent-rag \
    && chown -R app:app /app /tmp/travel-agent-rag \
    && chmod 775 /tmp/travel-agent-rag
COPY --from=backend-build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM docker.m.daocloud.io/library/nginx:1.27-alpine AS web
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/dist /usr/share/nginx/html
EXPOSE 80

FROM docker.m.daocloud.io/library/nginx:1.27-alpine AS agent-web
COPY agent-web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=agent-web-build /workspace/dist /usr/share/nginx/html
EXPOSE 80
