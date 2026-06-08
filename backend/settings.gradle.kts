rootProject.name = "easy-performance-management-backend"

// ADR-013 B 수렴: easy-platform-core(공유 플랫폼 lib — 테넌트 라우팅/control plane 스토어/프로비저닝/
// 다계층 시드/cipher) 를 Gradle composite build 로 로컬 소비한다. 좌표
// com.easyware.platform:easy-platform-core 의존을 인접 repo 빌드로 substitute → "복붙 금지=단일저자"(ADR-007).
// 배포 시 GitHub Packages 퍼블리시로 전환(이 줄 제거 + build.gradle.kts 의 maven repo 로 해석).
//
// 자매품 9호 — easy-performance-management 단계 1 BE-CC-1 진입 (jobeval/jobstructure/mra/sign 정합).
// Phase Docker 사전 적용 (Task #176, 2026-06-08) — submodule 진입으로 경로 조정:
//   ../../easy-platform-core → ../lib/easy-platform/easy-platform-core
// lib/easy-platform 은 https://github.com/Junhokang452/easy-standards.git submodule.
// recruit `0ea4d0a` 모범 정합. 추후 GitHub Packages 퍼블리시 시 이 줄 제거.
includeBuild("../lib/easy-platform/easy-platform-core")
