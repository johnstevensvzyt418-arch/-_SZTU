# ============================================================
# Elevator Monitor — Docker Image
# Spring Boot 2.3.1 / Java 8 / 端口 10008
# ============================================================

# ---- 基础镜像：Eclipse Temurin OpenJDK 8 JRE ----
# 替代已废弃的 java:8；JRE 镜像体积更小，适合运行阶段
FROM eclipse-temurin:8-jre

# ---- 元数据（MAINTAINER 已废弃，改用标准 LABEL）----
LABEL maintainer="xhuanc <xhhuanc@gmail.com>" \
      description="Elevator Monitor Spring Boot Application" \
      version="0.1.4-SNAPSHOT"

# ---- 工作目录 ----
WORKDIR /app

# ---- 声明卷（Tomcat 临时文件目录）----
VOLUME /tmp

# ---- 暴露应用端口（与 application.yml 中 server.port 一致）----
EXPOSE 10008

# ---- 复制可执行 JAR（通配符匹配，版本号变更无需改 Dockerfile）----
# 注意：target/*.original 已在 .dockerignore 中排除，确保只复制可执行 JAR
COPY target/*.jar app.jar

# ---- 容器入口点 ----
# -Djava.security.egd=file:/dev/./urandom  加速 Tomcat SecureRandom 初始化
ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", \
            "/app/app.jar"]