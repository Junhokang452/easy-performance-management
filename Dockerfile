# syntax=docker/dockerfile:1
# Render BuildKit 호환 정정 (2026-06-14): 1.7 frontend image grpc timeout 회피 (store-hr/talent 실측).
# syntax=1 = BuildKit 안정 latest 1.x 자동 선택. 박제: 90-conformance/render-buildkit-dockerfile-syntax-2026-06-14.md
#
# Copyright 2026 The easy-performance-management Authors.
# Licensed under the Apache License, Version 2.0 (the "License").
# See LICENSE in the repository root or https://www.apache.org/licenses/LICENSE-2.0.
#
# ============================================================================
# easy-performance-management — multi-stage Dockerfile
# (Phase Docker 사전 적용, Task #176, 2026-06-08, recruit 0ea4d0a 모범 정합)
# ============================================================================
#
# 목적:
#   - 단일 컨테이너로 BE (Spring Boot 3.4.5 + Java 21) + FE (Vite 8 + React 19) 통합 빌드/배포.
#   - Render Docker Web Service 호환 (port 10000 + healthcheck + Asia/Seoul TZ).
#   - EC2 전환 100% 호환 (동일 Dockerfile + docker-compose).
#
# 빌드 컨텍스트:
#   build context = easy-performance-management/ 자체 (submodule lib/easy-platform 포함).
#   빌드 명령:
#     cd ~/code/easy-performance-management && docker build -t easy-performance-management:latest .
#
#   Render Docker Web Service:
#     - root directory = easy-performance-management/
#     - submodule auto-fetch (lib/easy-platform → easy-standards monorepo)
#
# lib FE 12 의존 (submodule 경로):
#   frontend-vite/package.json — file:../lib/easy-platform/easy-platform-core/packages/*
#     http-client / query-client / tokens / ui-components (performance FE 4 패키지)
#
# lib BE composite build (submodule 경로):
#   backend/settings.gradle.kts — includeBuild("../lib/easy-platform/easy-platform-core")
#
# 정적 파일 서빙:
#   Spring Boot 의 spring.web.resources.static-locations 를 file:/app/static/ 로 확장.
#   /actuator/health → liveness/readiness probe.
#
# 자매품 정합:
#   - recruit 0ea4d0a + 9 fix 누적 (Dockerfile lib FE 빌드 / yml fallback / multitenancy default false /
#     refresh.enabled false → 본 자매품은 단계 3 진입 완료라 true 유지 / app: 통합 / Flyway VARCHAR→TEXT)
#   - ADR-031 B2B-Enterprise per-tenant + SMB Shared 본질 보존
#   - 그린필드 100% Apache-2.0 (license history rewrite 불필요)
#
# ============================================================================
# Stage 1: Frontend build (Vite 8 + React 19 + Mantine v9)
# ============================================================================
FROM node:20-alpine AS frontend-build
WORKDIR /workspace

# lib FE 12 root (package.json with workspaces) + packages 6개.
# submodule 경로: lib/easy-platform/easy-platform-core/
# - root package.json workspaces=packages/* (npm install --workspaces)
# - root scripts.build=npm run build --workspaces --if-present (packages 6개 일괄 빌드)
COPY lib/easy-platform/easy-platform-core/ ./lib/easy-platform/easy-platform-core/

# lib FE 12 packages 빌드 — 각 package의 main: dist/index.js, types: dist/index.d.ts.
# 이 단계가 없으면 frontend-vite tsc에서 TS2307 Cannot find module @easy/* 발생.
# (recruit fix #4: lib FE 12 빌드 단계 필수)
WORKDIR /workspace/lib/easy-platform/easy-platform-core
RUN npm install --no-fund --no-audit --legacy-peer-deps
RUN npm run build

# frontend-vite 전체 (package.json + tsconfig + src + public + index.html + vite.config.ts).
COPY frontend-vite/ /workspace/frontend-vite/

WORKDIR /workspace/frontend-vite

# install — --legacy-peer-deps 는 React 19 peer 충돌 회피용 (자매품 표준).
# file: 의존성 (../lib/easy-platform/easy-platform-core/packages/*) → 위 빌드된 dist 인식.
RUN npm install --no-fund --no-audit --legacy-peer-deps

# tsc -b && vite build (package.json scripts.build)
RUN npm run build

# ============================================================================
# Stage 2: Backend build (Spring Boot 3.4.5 + Gradle KDSL + Java 21)
# ============================================================================
FROM gradle:8.10-jdk21-alpine AS backend-build
WORKDIR /workspace

# lib easy-platform-core (composite build) — settings.gradle.kts includeBuild 해석.
# submodule 경로: lib/easy-platform/easy-platform-core/
COPY lib/easy-platform/easy-platform-core/ ./lib/easy-platform/easy-platform-core/

# backend 전체.
COPY backend/ ./backend/

WORKDIR /workspace/backend

# bootJar (테스트 제외 — 빌드 속도 + Render Docker timeout 회피).
# --no-daemon — 컨테이너 빌드 환경에서 daemon 불필요.
RUN gradle bootJar -x test --no-daemon --console=plain

# ============================================================================
# Stage 3: Runtime (eclipse-temurin 21 JRE + Asia/Seoul TZ + healthcheck)
# ============================================================================
FROM eclipse-temurin:21-jre-alpine
LABEL org.opencontainers.image.title="easy-performance-management"
LABEL org.opencontainers.image.description="easy-performance-management (Spring Boot 3.4.5 + Vite 8 단일 컨테이너) — Apache-2.0"
LABEL org.opencontainers.image.licenses="Apache-2.0"
LABEL org.opencontainers.image.vendor="The easy-performance-management Authors"

# Asia/Seoul TZ + curl (healthcheck용).
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone \
    && apk del tzdata
ENV TZ=Asia/Seoul

# 비루트 사용자 (보안 — 자매품 표준).
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

# BE jar (Spring Boot 3.4.5 bootJar).
COPY --from=backend-build --chown=app:app /workspace/backend/build/libs/*.jar /app/app.jar

# FE 정적 파일 (Spring Boot 가 file:/app/static/ 에서 서빙).
COPY --from=frontend-build --chown=app:app /workspace/frontend-vite/dist/ /app/static/

USER app

# Render port 10000 (Docker Web Service 기본).
# application.yml 의 server.port 는 ${PORT:8087} — Render 는 SERVER_PORT=10000 또는 PORT=10000 으로 override.
EXPOSE 10000
ENV SERVER_PORT=10000
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Healthcheck — Spring Boot Actuator /actuator/health (application.yml management.endpoints 노출).
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

# 정적 파일 서빙 경로:
#   1) file:/app/static/  (FE 빌드 산출물)
#   2) classpath:/static/ (Spring Boot 기본 — fallback)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \
    --server.port=${SERVER_PORT:-10000} \
    --spring.web.resources.static-locations=file:/app/static/,classpath:/static/"]
