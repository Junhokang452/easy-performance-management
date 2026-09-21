// easy-performance-management backend — Java 21 / Spring Boot 4.1.1 / Gradle Kotlin DSL.
// 자매품 9호 (2026-06-07, ADR-022 정식 편입 + ADR-030 듀얼 모드 5호). License: Apache-2.0 신규 코드.
//
// 빌드툴 표준: easy-standards 10-tech-stack 의 "장기 단일화" 방향 (ADR-014, 2026-06-03) — 자매품 전체
// Gradle KDSL 정렬 (ware/hcm/time/recruit/mra/jobeval/jobstructure). 멀티테넌시(Model B)·control plane·
// 프로비저닝·다계층 시드는 공유 lib easy-platform-core 에 위임 (ADR-007) → settings.gradle.kts 의
// includeBuild composite build 로 로컬 해석.
//
// 격상 4단계 (CLAUDE.md):
//   단계 0 ✅ baseline (58bf09d, tag v0.0.0-baseline)
//   단계 1 BE-CC-1 TenantAware ← 본 슬라이스
//   단계 2 Model B per-tenant DB (B2B)
//   단계 3 BE-CC-2 JWT 5분리 + dual-claim
//   단계 4 EC-FE Vite + Mantine v9
//   단계 5 듀얼 모드 풀 진입 (B2C 공통 테넌트 + ADR-029 정합)
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.easyperformance"
version = "0.1.0"

// Security fix newer than the Boot 4.1.1 BOM; keep the embedded server on the patched release.
extra["tomcat.version"] = "11.0.25"

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

repositories {
    mavenCentral()
}

dependencies {
    // ADR-013 B 수렴 — 공유 플랫폼 코드(테넌트 라우팅 / control plane 스토어 / 프로비저닝 / 시드 / cipher /
    // BaseAuditEntity / TenantAwareAuditEntity / TenantContext / TenantContextResolver / ApiException / ErrorCode /
    // GlobalExceptionHandler / TraceIdFilter / ErrorMessageResolver). composite build (includeBuild) 로컬 해석.
    implementation("com.easyware.platform:easy-platform-core:1.0.0-SNAPSHOT")

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-aspectj")
    // Boot 4 compatibility seam: shared public APIs remain on com.fasterxml.jackson.* until the suite Jackson 3 cutover.
    implementation("org.springframework.boot:spring-boot-jackson2")

    // Spring Cache + Caffeine (easy-standards 03-performance-oom §6). 캐시 키엔 tenant_id 필수.
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // S4 server-side result PDF. Direct PDF primitives only; no HTML/JS or remote-resource renderer.
    implementation("org.apache.pdfbox:pdfbox:3.0.8")

    // JWT (jjwt 0.12.6 — 자매품 버전 정렬). 단계 3 BE-CC-2 진입 시 본격 활용.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // EC-FE-7 (2026-06-06) — OpenAPI spec endpoint (/v3/api-docs) + Swagger UI.
    // 단계 4 EC-FE 진입 시 FE openapi-typescript 가 spec fetch.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// dev bootRun 앱 JVM 힙 상한 — WSL2 OOM Kill(exit 137) 방지 (jobeval/ware 정렬).
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("-Xmx512m", "-Xms256m", "-XX:MaxMetaspaceSize=256m")
}
