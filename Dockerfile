FROM maven:3.9.11-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM node:22-alpine AS frontend-build
WORKDIR /workspace
COPY fe/package.json fe/package-lock.json ./
RUN npm ci
COPY fe ./
ARG VITE_AMAP_KEY
ARG VITE_AMAP_SECURITY_JS_CODE
ENV VITE_AMAP_KEY=$VITE_AMAP_KEY \
    VITE_AMAP_SECURITY_JS_CODE=$VITE_AMAP_SECURITY_JS_CODE
RUN npm run build

FROM eclipse-temurin:21-jre AS api
WORKDIR /app
RUN useradd --system --uid 10001 app && mkdir logs && chown -R app:app /app
COPY --from=backend-build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM nginx:1.27-alpine AS web
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /workspace/dist /usr/share/nginx/html
EXPOSE 80
