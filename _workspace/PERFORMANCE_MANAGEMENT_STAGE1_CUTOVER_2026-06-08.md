# easy-performance-management 단계 1 BE-CC-1 cutover 박제 (2026-06-08, Task #86)

> **⚠️ 토폴로지 정정 (2026-06-08, Task #98)**: 본 박제 작성 시점 위상은 "듀얼 모드 5호"였으나, 사용자 명시 정정 "easy-performance-management는 SMB는 있지만 B2C는 없음" 후속 **B2B-Enterprise per-tenant + SMB Shared 분류 (ADR-031 정합, ware/hcm/recruit 패턴)** 로 정정됨. 단계 5 정의도 듀얼 모드 풀 진입 → SMB Shared 진입 옵션으로 정정. 본 단계 1 cutover 코드는 보존 (영향 없음). 정정 상세: `PERFORMANCE_TOPOLOGY_CORRECTION_2026-06-08.md`.
>
> **위상 (정정 후)**: 자매품 9호 (ADR-022 정식 편입 + **ADR-031 B2B-Enterprise + SMB Shared**). 단계 0 baseline `58bf09d` + tag `v0.0.0-baseline` (2026-06-07) 후속 **단계 1 BE-CC-1 TenantAware…AuditEntity + 4 도메인 스캐폴드 진입**. easy-platform-core composite build (`includeBuild("../../easy-platform-core")`) 자연 결합 + lib BE 17 v2 TenantContextResolver thin adapter (`com.easyperformance.common.TenantSupport`) + ADR-013 정합 단계 1 컬럼 보유.
>
> **G46 D=A** (단계 1 진입 권고 풀 통과) — 사용자 D=A 결정 직후 진입.
>
> **LIVE 영향 0** (옵션 A 보수 + lib autoconfig 게이트 OFF 기본 + dev 만 명시 ON + 단일 DB 부팅 가능).

---

## 1. 컨텍스트

- **저장소**: `/home/samsung/code/easy-performance-management/`
- **단계 0 풀 완성** (2026-06-07): baseline + tag `v0.0.0-baseline` + GitHub 단독 저장소 패턴 자매품 9/9 도달 진입 시작.
- **도메인 본질**: 성과 평가 (Performance Management) = 자기평가 + 개인 OKR + 회고 저널 + 멘토 피드백 (4 도메인 통합, jobeval 본질 정정 분리 인계, Task #70).
- **격상 4단계** (CLAUDE.md, 2026-06-08 정정): 단계 0 ✅ → **단계 1 BE-CC-1 ← 본 슬라이스** → 단계 2 Model B → 단계 3 BE-CC-2 JWT → 단계 4 EC-FE → ~~단계 5 듀얼 모드 풀 진입~~ → **단계 5 (옵션): SMB Shared 진입** (`shared-smb-easy-performance-management` + RLS tenant_id 격리, ware/hcm/recruit 패턴 정합, ADR-031).

## 2. 단계 1 스캐폴드 산출물 (신규 파일 풀 리스트)

### 2.1 backend/ 루트 (Gradle KDSL + Java 21)

```
backend/
├── build.gradle.kts                        # Spring Boot 3.4.5 + easy-platform-core composite
├── settings.gradle.kts                     # includeBuild("../../easy-platform-core")
├── gradle.properties
├── gradlew + gradlew.bat
└── gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}   # gradle 8.10.2
```

### 2.2 src/main/resources/

```
src/main/resources/
├── application.yml                                          # default + lib 게이트 OFF (옵션 A 보수)
├── application-dev.yml                                      # dev — 게이트 ON + localhost PG
├── application-prod.yml                                     # LIVE 가시화 ${} 환경 변수
└── db/migration/V20260608_001__stage1_init.sql              # Flyway V1 (4 테이블)
```

### 2.3 src/main/java/com/easyperformance/

#### Application + 공통
```
PerformanceManagementApplication.java
common/UuidV7.java                          # uuid-creator 6.1.1 wrapper (UUIDv7 RFC 9562)
common/TenantSupport.java                   # lib TenantContext thin adapter (단계 1 fallback)
config/JpaAuditConfig.java                  # @EnableJpaAuditing + AuditorAware
config/SecurityConfig.java                  # 단계 1 minimal Security (단계 3 BE-CC-2 진입 시 풀)
```

#### 4 도메인 (자기평가 / 개인 OKR / 회고 저널 / 멘토 피드백)

```
domain/selfevaluation/
├── entity/SelfEvaluation.java              # extends TenantAwareAuditEntity (lib BE-CC-1)
├── entity/SelfEvaluationStatus.java        # DRAFT / SUBMITTED / REVIEWED / FINALIZED
├── repository/SelfEvaluationRepository.java # ADR-026 명명 (findById/findAllByTenantId/existsBy/countBy)
├── service/SelfEvaluationService.java       # TenantSupport thin adapter
├── controller/SelfEvaluationController.java # /api/internal/performance/self-evaluations
└── dto/SelfEvaluationDtos.java              # record 기반 RequestDTO/ResponseDTO

domain/personalokr/
├── entity/PersonalOkr.java                  # extends TenantAwareAuditEntity
├── entity/PersonalOkrStatus.java            # ACTIVE / AT_RISK / COMPLETED / ARCHIVED
├── repository/PersonalOkrRepository.java
├── service/PersonalOkrService.java
├── controller/PersonalOkrController.java    # /api/internal/performance/personal-okrs
└── dto/PersonalOkrDtos.java

domain/reflectionjournal/
├── entity/ReflectionJournal.java            # extends TenantAwareAuditEntity
├── entity/ReflectionMethod.java             # KPT / FOUR_LS / SSC
├── repository/ReflectionJournalRepository.java
├── service/ReflectionJournalService.java
├── controller/ReflectionJournalController.java   # /api/internal/performance/reflection-journals
└── dto/ReflectionJournalDtos.java

domain/mentorfeedback/
├── entity/MentorFeedback.java               # extends TenantAwareAuditEntity
├── entity/FeedbackCategory.java             # GROWTH / RECOGNITION / COACHING / CONVERSATION
├── repository/MentorFeedbackRepository.java
├── service/MentorFeedbackService.java
├── controller/MentorFeedbackController.java # /api/internal/performance/mentor-feedbacks
└── dto/MentorFeedbackDtos.java
```

### 2.4 src/test/java/

```
src/test/java/com/easyperformance/UuidV7Test.java   # UUIDv7 생성 검증 + version 7 확인
```

---

## 3. lib `easy-platform-core` 자연 결합

| lib 단위 | 사용 클래스 | 본 슬라이스 활용 |
|---------|-----------|----------------|
| **BE-CC-1 BaseAuditEntity / TenantAwareAuditEntity** (`com.easyware.platform.audit.*`) | 4 도메인 entity 전체 상속 | `created_at` / `updated_at` / `created_by` / `updated_by` / `tenant_id` 공통 컬럼 + AuditorAware fan-out |
| **BE 17 v2 TenantContextResolver** (`com.easyware.platform.tenantctx.TenantContext`) | `common/TenantSupport.java` thin adapter | 단계 1 게이트 OFF fallback (FALLBACK_TENANT_ID) — 단계 2 진입 시 fallback 제거 |
| **UUIDv7** (lib `com.easyware.platform.UuidV7`) | 미사용 (control plane 행 전용) | 도메인은 `com.easyperformance.common.UuidV7` 분리 (uuid-creator 6.1.1 직접 wrapper) |

**ADR-007 정합**: 복붙 0 — composite build 단일 저자. **ADR-014 정합**: 자매품 8 → 9 GitHub 단독 저장소 + 표준 스택 단일화 풀 도달 진입 시작.

---

## 4. ADR-026 명명 표준 풀 정합

| 영역 | 패턴 | 본 슬라이스 적용 |
|------|------|----------------|
| Repository | `findById` / `findAllByTenantId` / `existsBy` / `countBy` | 4 repository 모두 정합 (e.g. `findAllByTenantIdAndEmployeeId`) |
| Service | `create` / `get` / `update` / `delete` + 도메인 액션 (e.g. `submit`, `markAtRisk`, `acknowledge`) | 4 service 정합 |
| Controller REST | `GET` / `POST` / `PUT` / `DELETE` + `/api/internal/performance/{domain}` prefix | 4 controller 정합 |
| DTO | `{Entity}CreateRequest` / `{Entity}UpdateRequest` / `{Entity}Response` record | `SelfEvaluationDtos` 등 record 묶음 |
| Enum | `Status` / `Category` / `Method` UPPER_SNAKE | 4 enum 정합 |
| 변수 명명 | camelCase: `employeeId` / `tenantId` / `cycleId` / `periodStart` / `createdAt` / `score` | 정합 |

---

## 5. Flyway V20260608_001 — 4 테이블 + tenant_id 선두 복합 인덱스

| 테이블 | 컬럼 (핵심) | tenant_id 선두 복합 인덱스 (easy-ware 규칙 #2) |
|--------|------------|--------------------------------------------|
| `self_evaluation` | id UUID PK + tenant_id + employee_id + cycle_id + period_start/end + content + score + status | `(tenant_id, employee_id)` + `(tenant_id, cycle_id)` + `(tenant_id, status)` |
| `personal_okr` | id UUID PK + tenant_id + employee_id + objective + progress + period_start/end + status | `(tenant_id, employee_id)` + `(tenant_id, status)` + `(tenant_id, period_end)` |
| `reflection_journal` | id UUID PK + tenant_id + employee_id + reflection_date + method + content + is_private | `(tenant_id, employee_id)` + `(tenant_id, reflection_date)` |
| `mentor_feedback` | id UUID PK + tenant_id + mentor_id + mentee_id + feedback_date + category + content + acknowledged | `(tenant_id, mentee_id)` + `(tenant_id, mentor_id)` + `(tenant_id, feedback_date)` |

**공통 컬럼**: `created_at` / `updated_at` / `created_by` / `updated_by` (lib BaseAuditEntity fan-out). **CHECK 제약**: status / category / method enum 값 + period 범위 + progress 0~100.

**Apache-2.0** 헤더 적용 (ADR-028 신규 코드 분기 B 정합).

---

## 6. 빌드 + 테스트 검증

### 6.1 compileJava

```
cd backend && ./gradlew compileJava --no-daemon
> Task :easy-platform-core:compileJava UP-TO-DATE
> Task :compileJava UP-TO-DATE
BUILD SUCCESSFUL in 18s
2 actionable tasks: 2 up-to-date
```

### 6.2 test

```
./gradlew test --no-daemon
> Task :test
BUILD SUCCESSFUL in 16s
7 actionable tasks: 3 executed, 4 up-to-date
```

`UuidV7Test` 통과 — uuid-creator 6.1.1 wrapper 정상.

---

## 7. 옵션 A 보수 + 게이트 OFF 정합

`application.yml` 의 `easyplatform.*` 게이트 — 단계 1 진입 시 기본 OFF (LIVE 안전):

| 게이트 | 기본 (default profile) | dev 명시 | 단계 진입 |
|--------|---------------------|---------|----------|
| `easyplatform.tenantctx.enabled` | `false` | `true` (단계 1 진입 ON) | 단계 1 |
| `easyplatform.rls.tenant.enabled` | `false` | `false` (단계 2 진입 시 ON) | 단계 2 |
| `easyplatform.b2c.enabled` | `false` | `false` (단계 5 진입 시 ON) | 단계 5 |
| `easyplatform.error.enabled` | `false` | `true` (단계 1 진입 ON) | 단계 1 |
| `easyplatform.audit.enabled` | `false` | `true` (단계 1 진입 ON) | 단계 1 |

**ADR-013 정합**: 단계 1 = 단일 DB + tenant_id 컬럼 보유 (Model A 흉내). 단계 2 진입 시 RLS 정책 SQL 박제 + per-tenant DB 분리. **`easyware.neon.multitenancy-enabled=false` 기본** — lib 멀티테넌시 빈 미로드, 로컬 단일 DB 부팅 가능.

---

## 8. 단계 1 다음 진입 (단계 2 사전 박제)

- **단계 2 진입 게이트** G46.2 (가칭) — Model B 단번 전환 (per-tenant DB 분리 + Flyway fan-out + lib BE 14 TenantBootstrap 3 SPI seam 결합).
- `TenantSupport.FALLBACK_TENANT_ID` 제거 → `TenantContext.requireTenantId()` 직접 호출.
- RLS 정책 SQL 박제 (4 테이블 모두 `tenant_id = current_setting('app.tenant_id')::uuid` + 단계 5 진입 시 user_id RLS 추가).
- ADR-024 정합 — 1 고객사 = 1 Neon 프로젝트 + `easyrecord-performance-management` DB 분리.

---

## 9. 자매품 9 진화 매트릭스 정합

| 자매품 | 단계 진입 | BE-CC-1 진입 | Model B | JWT 5분리 | EC-FE | 듀얼 모드 |
|--------|---------|------------|---------|---------|-------|---------|
| ware | LIVE EC2 | ✅ | 진행 중 (adr-013 branch) | ✅ | 진행 | — (B2B-only) |
| hcm | LIVE Render | ✅ | 진행 중 (adr-013 branch) | ✅ | 진행 | — (B2B-only) |
| time | LIVE | ✅ | V17 안전군 + V18+ 사전 | ✅ | 진행 | — (B2B-only) |
| recruit | Vite 진행 | ✅ | 진행 | ✅ | Vite 30+ 페이지 | 보류 (AGPL) |
| mra | G31 단계 1 사전 | ✅ | G31 단계 2 사전 | ✅ | ✅ | 듀얼 1호 자연 |
| jobeval | 단계 1 진입 사전 (G38) | ✅ | 단계 2 사전 | 단계 3 사전 | 단계 4 사전 | 듀얼 2호 후보 |
| jobstructure | G30 옵션 C 단계 0 | 박제 | — | — | — | 듀얼 3호 후보 |
| sign | Phase γ W8.7 B2B 분리 ✅ + W9 | ✅ | ✅ | ✅ | ✅ (Mantine v9) | 듀얼 4호 풀 통합 검증 ✅ |
| **performance-management** | **단계 1 본 슬라이스 ✅** | **✅** | 단계 2 사전 | 단계 3 사전 | 단계 4 사전 | **듀얼 5호 (단계 5)** |

자매품 8/8 → 9/9 진화 마일스톤 진입 — 듀얼 모드 5호 단계 1 본격 진입.

---

## 10. 변경 이력

| 날짜 | 변경 내용 | 사유 |
|------|----------|------|
| 2026-06-08 | 단계 1 BE-CC-1 cutover 본 슬라이스 + 4 도메인 스캐폴드 + Flyway V1 + 빌드 + 테스트 통과 | Task #86 (재spawn). G46 D=A 사용자 결정 후속. LIVE 영향 0. |
