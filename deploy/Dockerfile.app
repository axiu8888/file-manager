# kiftd Spring Boot 应用镜像（Eclipse Temurin 21 JRE，Ubuntu Jammy）
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.title="kiftd-app" \
      org.opencontainers.image.description="kiftd Spring Boot backend"

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

# 与宿主机 /opt/apps/kiftd 布局对齐（数据目录由 compose 卷挂载，保持可写）
RUN mkdir -p /opt/apps/kiftd/data/filenodes \
             /opt/apps/kiftd/data/temp \
             /opt/apps/kiftd/config \
             /opt/apps/kiftd/logs

WORKDIR /opt/apps/kiftd

# 构建产物由 build.sh 放入 deploy/dist/（或 install 后的 /opt/apps/kiftd/dist）
COPY dist/app.jar /opt/apps/kiftd/app.jar
COPY application-prod.yml /opt/apps/kiftd/config/application-prod.yml

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/opt/apps/kiftd/config/ \
    JAVA_OPTS="-Xms256m -Xmx1024m"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/system/ping" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/apps/kiftd/app.jar"]
