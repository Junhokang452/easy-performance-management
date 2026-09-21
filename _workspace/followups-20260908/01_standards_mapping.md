# Follow-up S1–S4 표준 매핑 및 구현 전 게이트

생성일: 2026-09-08  
담당: standards-guardian / code-quality-reviewer (Luna)  
범위: 승인된 증분 S1 평가라인 자동화 → S2 KPI 연계 → S3 미완료 책임자 독려 → S4 맞춤 PDF

적용 스킬: `standards-lookup`으로 제품 내 읽기 가능한 easy-standards 사본의 원칙/부록/
conformance 근거를 조회했고, `full-stack-quality-gate`의 변경 전 계약·표준 게이트 관점으로
매핑했다. 이 스킬 적용의 영향은 **SoT 경로·ADR 원칙·검증 게이트를 문서화하는 것**에 한정되며,
빌드/런타임 실행이나 제품 코드 수정 권한을 부여하지 않는다.

## 1. 근거와 적용 범위

이번 문서는 구현 설계의 표준 매핑만 기록한다. 제품 코드, Flyway, OpenAPI, 프런트 파일,
공유 라이브러리 및 외부 환경은 변경하지 않았다. 구현·계약 검증은 Root(계약/스모크),
Sol(backend/HCM), Terra(frontend)의 산출물 수신 후 incremental QA에서 별도 수행한다.

### SoT 상태

- 실제 읽힌 기준 사본: `lib/easy-platform/easy-standards/`.
- 사본 README: `v0.5.0 (draft, 2026-06-01)`, 상태 `수립 중`; 이 사본이 이번 매핑의
  재현 가능한 기준이다.
- 루트 `~/code/easy-standards`는 Windows에서 WSL reparse point로 확인되었으나 해당
  경로의 README 내용과 git pin을 이 세션에서 독립적으로 읽지 못했다. 따라서 루트 pin
  hash를 추정하거나 표준 저장소에 쓰지 않는다. 루트 경로가 읽히는 환경에서 별도 pin
  확인이 필요하다.
- 비밀 환경변수·Neon/control-plane·SMTP·배포·관리자 권한은 읽거나 변경하지 않는다.
- 공식 slice/ADR 번호를 선점하지 않는다. 이번 문서는 로컬 작업 매핑이며 SoT 변경 제안이
  아니다.

### 직접 적용한 SoT 문서

| 주제 | 기준 문서 | 이번 증분에서의 의미 |
|---|---|---|
| 식별자·tenant·PII 경계 | `00-principles/01-identity-and-data.md` | 공유 UUID, 불변 `employee_id`, `employee_no` business key, tenant 선두 조회와 S2S tenant 지정 |
| 보안·인가·입력·파일 | `00-principles/02-security-owasp.md` | 서버 측 object/function/field 권한, fail-closed, PII 최소화·마스킹, 파일/다운로드 검증 |
| 감사·관측·알림 | `00-principles/04-observability.md` | actor/time/action/result 감사, 관리자·평가·민감 조회 기록, 중복 알림 방지 근거 |
| 프런트 계약 | `00-principles/07-frontend.md` | React Query 서버 상태 SSOT, Mantine v9/공통 UI, i18n, 서버 권한과 UI 표시 분리 |
| 공유 코드 | `00-principles/08-shared-code.md` | 인증/tenant/공통 UI·자산은 재사용하되 제품 규칙을 shared lib로 이동하지 않음 |
| DB·수치·동시성 | `00-principles/09-database.md` | `BigDecimal`, `numeric(19,4)` 기준, UTC/timestamptz, `@Version`, idempotency unique, tenant/index 규칙 |
| 제품군·Core Master | `00-principles/11-suite-architecture.md` | 조직·사원·발령은 Core Master SoR, 제품은 Read Model/S2S 소비, effective-dated/source_version |
| Identity·PII | `00-principles/12-sso-identity.md` | login user와 employee 분리, 최소 claims, 퇴사/잠금 경계, actor 해석 |
| 물리 tenant 격리 | `00-principles/13-tenancy-provisioning.md` | Neon Model B/제품별 tenant DB, cross-DB 직접 조인 금지, 잘못된 라우팅 fail-closed |
| 공통 라벨 | `00-principles/17-i18n-label-conventions.md` | `@easy/i18n-common` + 제품 namespace, 5 locale schema 정합, raw key 0 |
| JPA 영속성 | `10-appendix-spring-jpa/persistence.md` | UUIDv7 PK, optional date JPQL 금지, DTO/Page, lazy/transaction, SXSSF export 원칙 |
| S2S | `10-appendix-spring-jpa/s2s-integration.md` | `X-Tenant-Uuid`, constant-time key, 미설정 fail-safe, employee_no 매칭, HTTP와 DB TX 분리 |
| 목록 wire | `10-appendix-spring-jpa/page-response-envelope.md` | 목록은 `PageResponse{pagination,data}`, `Page`/entity 직접 노출 금지 |
| 평가 공유 자산 | `90-conformance/performance-evaluation-shared-assets-2026-09-07.md` | WorkflowPhaseRail/공통 shell·lookup·form/table 재사용; 평가 정책과 권한은 제품에 유지 |

## 2. 공통 설계 불변조건

1. **Core Master 소비**: 조직·사원·발령·직책·직급의 원본은 HCM/Core Master다. 제품은
   동기화된 read model과 `source_version`/`synced_at`을 소비하며 HCM DB를 직접 join하지
   않는다. read model은 제품 UI에서 수정하지 않는다.
2. **유효기간은 명시적이다**: 관계/발령을 적용할 때 기준일(as-of), `valid_from`/`valid_to`,
   source version과 overlap/conflict 결과를 서버가 반환해야 한다. 유효기간이 없거나
   충돌·지연 상태이면 자동 적용하지 않고 이유를 preview에 표시한다.
3. **tenant/operator/PII**: 모든 읽기·변경·다운로드는 서버에서 tenant와 object/function/field
   권한을 검증한다. operator는 JWT/Identity의 actor UUID로 감사하며, employee 이름·조직·평가
   결과는 화면/파일/로그에 필요한 최소 필드만 노출한다.
4. **수동 경로 보존**: 현재 수동 reviewer 편집, Excel import/export, KPI 수동 actual/source,
   기존 notification/결과 XLSX 계약은 제거·대체하지 않는다. 자동화는 별도 preview와 명시적
   confirm을 거친다.
5. **계산 근거 보존**: 자동 계산·선택·독려 대상·PDF 생성은 입력 snapshot, 정책/계약 버전,
   as-of, 결과와 reason을 재현할 수 있어야 한다. 평가 점수나 evaluator finalization을 암묵적으로
   실행하지 않는다.
6. **멱등·감사**: apply, KPI 재계산/연계, reminder queue/dispatch, PDF 발행/다운로드는
   idempotency key 또는 동일 목적의 unique constraint를 사용한다. 성공·거부·충돌·재시도·다운로드를
   actor/time/action/result 및 correlation/idempotency 정보와 함께 감사한다.
7. **계약 단일화**: server가 계산한 값과 권한 결과를 API/OpenAPI DTO로 제공한다. FE가 KPI/대상자/
   수신자를 복제 계산하거나 권한을 UI 숨김으로 대체하지 않는다. 목록은 표준 page envelope를 사용한다.
8. **외부 연동 제한**: 이번 승인에는 외부 SMTP, Neon API, control-plane 쓰기, 브로커, 배포가
   포함되지 않는다. 내부 알림 abstraction과 기존 HCM/read-model 계약만 사용한다.

## 3. 슬라이스별 SoT 매핑과 필수 게이트

### S1. 평가라인 자동화

**대상**: HCM/ Core Master의 employee–org/assignment 관계와 유효기간을 평가 프로그램의
참여자·평가자 후보에 안전하게 반영하는 preview/apply 흐름.

- 입력 SoR: HCM Core Master의 조직, 사원, 발령/assignment, 재직상태, `employee_id`,
  `employee_no`, `valid_from`/`valid_to`, `source_version`.
- 허용 경계: performance의 동기화 read model 또는 명시된 S2S DTO. HCM tenant DB 직접
  접근, employee 이름/email 기반 전역 매칭, 현재 active participant 목록을 source로 삼는 방식은 금지.
- 정책 결과: `eligible`, `already_assigned`, `no_source`, `expired`, `ambiguous`,
  `conflict`, `terminated` 등 명시적 reason/status를 반환한다. source가 stale하거나
  assignment overlap이면 default는 no-op/차단이다.
- 수동 보존: 기존 reviewer 수동 편집과 Excel import/export는 그대로 동작해야 하며 자동
  preview가 수동 행을 덮어쓰지 않는다. apply는 서버가 발급한 preview version/token,
  기준일, 정책 버전과 명시적 confirm을 요구한다.
- 권한: program scope의 HR_ADMIN/SUPER_ADMIN 등 명시된 operator만 preview/apply한다.
  participant/evaluator가 자신의 UI에서 자동 assignment를 확정할 수 없다.
- 감사/멱등: preview(읽기)와 apply(쓰기)를 구분하고, actor/operator, tenant, program,
  대상 수, 제외 reason, source version, confirmation, idempotency key를 append-only audit에 남긴다.
  동일 preview 재전송은 중복 assignment/audit를 만들지 않아야 한다.
- UI: 별도 automation card/preview table, explicit confirmation, React Query invalidation,
  optimistic row 금지, 5 locale labels. `WorkflowPhaseRail` 등 shared asset은 표시용으로만
  재사용하고 상태 전이는 서버 결과를 따른다.

**구현 전 차단 게이트**: HCM DTO/endpoint 또는 read-model contract, as-of/effective-date
정의, overlap/conflict 정책, operator role, preview token/version/expiry, 수동 행 보호 방식이
확정되기 전에는 S1 apply를 구현하지 않는다.

### S2. KPI 연계

**대상**: 기존 KPI tree/node/assignment/actual을 평가 item에 연결하고, 서버가 버전과
근거를 포함해 계산/조회하는 흐름.

- SoR: 기존 performance KPI 모델(`KpiTree`, `KpiNode`, `KpiAssignment`, `KpiActual`)과
  append-only actual/supersede history. 현재 KPI source는 MANUAL이며, 이번 승인에서 별도
  역량계층이나 상대평가 비율을 추가하지 않는다.
- 계산 책임: effective KPI, achievement, weight/target override, score는 서버가 계산한다.
  FE `ReviewKpiItemsTable`/`src/api/kpi.ts`는 read-only 계약을 소비하며 `useState`로 서버
  응답을 복제하거나 점수를 재계산하지 않는다.
- 정밀도/시간: 표준은 `BigDecimal` + `numeric(19,4)`, `Instant`/UTC/timestamptz다.
  기존 KPI 스키마가 `numeric(18,4)`인 경우 호환성·마이그레이션 판단을 별도 계약으로 먼저
  확정하며 조용히 precision을 바꾸지 않는다.
- 근거 동결: 계산 시 KPI node/assignment/actual id, actual as-of date, policy/formula
  version, target/weight snapshot, source version, evidence reference, manual correction
  reason을 저장/반환한다. 기존 manual actual 및 supersede chain은 보존한다.
- tenant/인가: KPI tree, assignment, actual, review item 모두 tenant scope이며 evaluator가
  허용되지 않은 employee/program KPI를 조회하거나 수정할 수 없다. sensitive score는 field
  level permission을 적용한다.
- 감사/멱등: linkage/apply/recalculate/manual correction 각각을 구분하고 동일 요청의 중복
  처리와 새 계산 버전을 구별한다. 계산 실패는 부분 반영 없이 reason을 반환한다.

**구현 전 차단 게이트**: review item↔KPI mapping key, 공식/rounding, effective date,
formula/policy version, snapshot/evidence shape, manual correction 및 재계산 semantics,
기존 `numeric(18,4)`와 표준 `numeric(19,4)` 호환 결정을 OpenAPI와 함께 확정한다.

### S3. 미완료 책임자 독려

**대상**: 실제 workflow owner와 미완료 source를 집계해 기존 내부 notification 도구로
중복 없이 preview/queue/dispatch하는 흐름.

- 수신자 SoR: stage별 실제 owner/reviewer/manager/participant 관계 또는 서버 read model.
  FE의 active participant 목록, 현재 로그인 사용자, 임의의 employee 목록으로 recipient를
  추론하지 않는다.
- 미완료 정의: 서버가 평가 단계, item/reviewer, cutoff/as-of, status, 제외 reason을 함께
  반환한다. `PENDING`과 `NOT_APPLICABLE`/권한 없음/원본 지연을 혼동하지 않는다.
- 내부 알림: 기존 NotificationTools/notification abstraction을 재사용한다. SMTP·외부
  push·새 발송 공급자를 도입하지 않는다. preview → queue → dispatch 상태와 실패/retry를
  관측 가능하게 한다.
- 멱등: `(tenant, program/cycle, stage, owner, due/as-of, reminder policy/version)` 또는
  동등한 server idempotency key를 unique하게 하여 재실행/동시 worker가 중복 알림을 만들지
  않게 한다. 이미 보낸 건은 reason/status를 보존하고 정책상 재독려만 새 version으로 만든다.
- 권한/PII: HR_ADMIN/operator만 전체 preview/dispatch하며 owner는 허용 범위의 본인 알림만
  볼 수 있다. 메시지에는 최소 식별자와 안전한 deep link만 넣고 평가 점수/민감 원문을 로그와
  알림 payload에 과다 포함하지 않는다.
- 감사: 대상 산출 입력 snapshot, owner source/version, policy, actor, queued/dispatched/
  failed 결과, idempotency key와 correlation id를 감사한다.

**구현 전 차단 게이트**: stage별 owner contract, incomplete state machine, cutoff/as-of,
notification template/locale, queue/dispatch retry, duplicate policy와 권한 scope를 확정한다.

### S4. 맞춤 PDF

**대상**: 기존 평가 결과와 권한을 재사용해 서버가 맞춤 PDF를 생성하고, 기존 XLSX 결과
export와 함께 다운로드하는 흐름.

- 결과 SoR: 서버 결과/summary/permission contract. FE에서 html-to-pdf, canvas snapshot,
  자체 score 계산 또는 PII 필터링을 수행하지 않는다.
- 권한: PDF 생성·다운로드 모두 서버에서 tenant/program/cycle/employee/object 및 field
  permission을 재검증한다. HR_ADMIN/operator 전체 결과와 participant 본인 범위를 분리한다.
  URL/파일 token만으로 권한을 우회하지 않는다.
- 문서 안전: 출력 필드 allowlist, safe filename/Content-Disposition, 크기·시간 제한,
  스트리밍/임시파일 정리, 로그 PII 마스킹을 적용한다. PDF에 포함한 employee/score/feedback의
  source snapshot, result version, generatedAt, policy/version을 재현 가능한 근거로 남긴다.
- 공유 자산: `easy-platform-core`의 tokens/UI와 등록된 평가 shared assets를 재사용하되,
  PDF 전용 폰트·템플릿을 공통 SoT라고 새로 선언하지 않는다. 한국어 글꼴 fallback, 문자
  깨짐, 표/페이지 break, 긴 이름/긴 코멘트, 빈 섹션, locale 날짜/숫자 fixture를 서버 렌더링
  테스트로 확인한다.
- 계약: `Content-Type: application/pdf`, 다운로드 오류의 공통 error envelope, 권한 거부,
  만료/재시도 및 파일명 locale 정책을 OpenAPI에 명시한다. 기존 `programsApi.exportResults`
  XLSX와 `downloadBlob` 계약은 보존한다.
- i18n: 제품 namespace + `@easy/i18n-common`, 5 locale strict schema, raw key 0.
  PDF의 언어/날짜/숫자 선택도 서버 계약으로 명시하며 FE locale만 믿고 파일 내용을 바꾸지 않는다.
- 감사: generate/preview/download/deny를 actor, tenant, result version, field policy,
  locale, outcome으로 감사한다. 동일 요청 재시도 시 불필요한 산출물/감사를 중복 생성하지 않는다.

**구현 전 차단 게이트**: 결과 권한 matrix, PDF field/template option allowlist, shared font
asset/license/source, page-break fixture, 한국어 렌더링, size/timeout, download audit와 XLSX
회귀 계약을 확정한다.

## 4. 검증 계획 (incremental QA 입력)

| 축 | S1 | S2 | S3 | S4 |
|---|---|---|---|---|
| 계약/OpenAPI | preview/apply DTO·version·reason | mapping/formula/version/evidence | owner/incomplete/dedupe | PDF response/error/permission/options |
| tenant/인가 | tenant+program operator, cross-tenant deny | KPI/object/field scope | dispatch/admin scope | result field/download scope |
| Core Master | HCM read model/S2S, effective date | employee/assignment snapshot | owner source/version | 표시 employee snapshot만 소비 |
| 보존성 | 수동 reviewer·Excel 불변 | manual actual/supersede 불변 | 기존 notification 상태 보존 | 기존 XLSX 보존 |
| 멱등/감사 | preview/apply | linkage/recalculate | queue/dispatch | generate/download |
| 계산/렌더 | 자동 score 금지 | server-only formula | server-only recipient | server-generated PDF |
| FE | Query + wrapper + 5 locale | read-only Query | preview/dispatch Query | `downloadBlob`, no client PDF |

최소 negative fixture는 cross-tenant, stale/expired assignment, overlapping assignment,
terminated employee, missing/ambiguous source, unauthorized operator, duplicate retry,
manual override, empty result, Korean long text, malformed/oversize download option을 포함한다.

## 5. 이번 범위의 명시적 제외

- 평가 점수 자동 확정, evaluator 자동 finalization, 새 역량계층, 상대평가 비율/분포.
- HCM/Core Master DB 직접 조인 또는 제품 간 DB SQL join.
- 외부 Neon/control-plane/SMTP/push/broker, 운영 배포, 비밀·환경변수 취급.
- 기존 수동 assignment, reviewer Excel, KPI manual source/history, notification abstraction,
  XLSX export의 제거 또는 무단 wire 변경.
- easy-standards SoT, ADR, 공식 conformance slice의 번호 선점·외부 push.

## 6. 미결정 및 담당 요청

1. **S1 / Sol·Root**: HCM assignment DTO 및 valid interval/source_version, overlap 처리,
   owner/operator role과 preview apply token semantics.
2. **S2 / Sol·Root**: KPI mapping identity, formula/rounding/version/evidence, `18,4` 대
   `19,4` 호환과 재계산/versioning semantics.
3. **S3 / Sol·Root**: stage별 owner, incomplete state machine, notification template/locale,
   idempotency/dedupe와 retry ownership.
4. **S4 / Sol·Terra·Root**: 권한 matrix, PDF template/field options, shared font asset/license,
   Korean/page-break fixture, content-disposition 및 download audit.
5. **전체 / Root**: 각 계약의 OpenAPI와 실제 tenant/permission negative smoke를 먼저 확보한
   뒤 Luna incremental QA에 전달한다.

## 7. 현재 판정

**표준 매핑 게이트: PASS (문서화 단계 한정).**  
**범위 승인 게이트: PASS — HCM 로컬 송신 코드 확장을 이번 작업 범위에 포함.**  
**S1 구현/계약 게이트: PASS — PA 핵심 구현·런타임·격리·OpenAPI·migration 경계 확인.**
**S2–S4 구현 게이트: 미착수/별도 계약 대기.**

사용자가 HCM 로컬 송신 코드 확장을 포함해 Astra가 오케스트레이션하도록 승인했으므로,
기존 `SCOPE_APPROVAL_REQUIRED` 사유는 해소되었다. 이 승인은 HCM의 로컬 sender/read-model
wire 구현 범위만 포함하며, 운영 발신·외부 연동 설정·Neon/control-plane 쓰기·배포는 포함하지
않는다.

HCM sender focused 15/15, PA focused 36/36·전체 294/294·bootJar, final local HTTP 22/22,
typed-403 isolation 3/3, migration success, OpenAPI byte-identical SHA, 기존 route의 ACTIVE
재검증/inactive negative test, FE plural-ID 계약 정합을 현재 S1 근거로 인정한다. 따라서
**S1 최종 품질 게이트는 PASS**다. 이는 아직 구현하지 않은 S2–S4를 완료했다는 의미가 아니다.

HCM 전체 suite compile은 `easy-hcm/backend/src/test/java/.../payrollpreparation/
PayrollPreparationControllerTest.java:17`의 기존 존재하지 않는
`AuthenticationPrincipalArgumentResolver` import로 차단되었다. 이 오류는 S1 신규 HCM
sender 코드/테스트와 무관하며, HCM focused `*EasyPerformance*` **15/15 PASS**의 범위를
전체 suite PASS로 확대하지 않는다. HCM full-suite 상태는 **BLOCKED (pre-existing test
compile defect)**로 별도 분류한다.

## 8. S1 독립 계약 게이트 및 테스트 시나리오

Luna는 다음 산출물이 전달될 때 독립 검토한다. 코드를 수정하지 않고 계약·권한·격리·보존성의
증거만 판정한다.

### 필수 계약 증거

1. HCM sender payload/endpoint: 공유 `tenant UUID`, 불변 `employee_id`, `employee_no`,
   assignment/org/manager 관계, `valid_from`/`valid_to`, `source_version`, tombstone 의미.
2. PA 수신 DTO/read model: 원본 필드 보존, stale/out-of-order/duplicate 정책, manager 해석과
   null/tombstone 처리, tenant-scoped 저장·조회.
3. Preview/apply API: 기준일, eligible/excluded reason, conflict/expired 상태, preview
   version/token/expiry, 명시적 confirm, idempotency key와 응답 envelope.
4. 권한: operator role, program scope, cross-tenant deny, participant/evaluator의 apply 차단.
5. 보존성: 기존 수동 reviewer 편집과 Excel import/export가 자동 preview/apply에 의해
   덮어써지지 않는 증거와 append-only audit 필드.

### 최소 QA 시나리오

| ID | 시나리오 | 기대 결과 |
|---|---|---|
| S1-C01 | 유효 assignment + 유효 manager + 동일 tenant | preview가 eligible로 반환되고 확인된 preview만 apply |
| S1-C02 | `valid_from` 이전/`valid_to` 이후 | 자동 대상 제외, 명시적 expired/not-effective reason |
| S1-C03 | assignment overlap 또는 manager 다중 후보 | 자동 적용 금지, ambiguous/conflict reason과 source version 노출 |
| S1-C04 | HCM source_version 역순/중복 재전송 | 낮은 버전 무시, 같은 버전은 멱등, 중복 participant/audit 없음 |
| S1-C05 | manager tombstone/퇴사/해지 | stale manager를 유효 owner로 사용하지 않고 차단 또는 정책상 명시된 fallback |
| S1-C06 | tenant A 요청으로 tenant B employee/program 조회·apply | 서버 403/404 정책에 맞게 거부, B 데이터·audit 미변경 |
| S1-C07 | 권한 없는 participant/evaluator의 preview/apply | 서버에서 거부; UI 숨김만으로 PASS하지 않음 |
| S1-C08 | 기존 수동 reviewer 행 + 자동 preview/apply | 수동 값 보존, 덮어쓰기 없음, 충돌 reason/audit 존재 |
| S1-C09 | preview 후 source_version 변경/preview 만료 | apply 거부 또는 재preview 요구; stale token으로 변경 불가 |
| S1-C10 | 네트워크 재시도/동시 apply | idempotency로 한 번만 assignment·audit 생성 |
| S1-C11 | employee PII 최소 응답/로그 | 필요한 label만 반환하고 token·민감 원문·과도한 PII 미기록 |
| S1-C12 | 페이지·필터·빈 결과 | 표준 page envelope와 0건 reason을 반환; 전체 스캔/무제한 응답 없음 |

현재 Windows backend 36/36 focused·294/294 full·bootJar PASS와 final local HTTP 22/22,
isolation 3/3 근거가 도착했다. 이 근거는 C01~C10의 핵심 preview/apply, effective date,
source replay, tombstone, tenant/auth negative, manual/Excel preserve, concurrency/idempotency
를 **PASS**로 뒷받침한다. C11 PII 로그 최소화와 C12 page/filter envelope는 이 S1 verifier의
완전한 독립 assertion이 아니므로 **범위 제한 미검증**으로 남긴다. 기존 route active
revalidation과 500→403 오류 매핑은 최신 source/test 및 final runtime에서 **PASS**다.
따라서 S1 최종 병합은 **PASS**로 판정한다.

## 9. S1 설계 게이트 검토 결과 (2026-09-08, 최신 판정)

초기 설계 검토에서 제기한 다섯 조건은 `01_s1_backend_contract.md`와
`01_hcm_sender_contract.md`의 최신본에 반영되었다. 따라서 **Phase3a design PASS**로
확정하며, 구현 검증은 아래 runtime/DB evidence로 PASS한다. 단일 HTTPS origin allowlist/no-redirect,
apply 1~100 및 중복 거부, tenant/program/previewHash 범위의 persisted idempotency,
PostgreSQL epoch-microsecond sourceVersion, signed body/header/config/current context
삼중 일치가 현재 계약의 기준이다. 운영 egress/DNS 보호는 별도 운영 사전조건이며 이
문서의 제품 구현 PASS를 의미하지 않는다.

초기 five-findings 전문은 당시 설계 보강의 역사적 기록으로만 취급한다. 현재 판정에
초기 `milliseconds`, `Idempotency-Key` 헤더 강제, apply 상한 미정, host allowlist 미정
상태를 재사용하지 않는다.

## 10. 구현 경계 정적검토 및 현재 차단

### PA API/FE 계약 정합

- `ProgramReviewerLineController`의 preview/apply 경로, FE `reviewerLineAutomation.ts`의
  request/response 타입과 URL은 서로 일치한다.
- preview/apply 모두 participant 1~100·중복 거부, operator gate, `previewHash`, as-of,
  source version/deleted, HCM manager provenance를 노출한다. FE는 React Query mutation과
  `@easy/ui-components`를 사용하고 서버 계산/수신자/권한을 복제하지 않는다.
- apply 성공 후 program query prefix를 invalidate하며, 기존 reviewer/Excel 화면과 별도
  자동화 카드로 배치되어 있다. 다섯 locale에 automation 문자열이 추가된 것은 확인했다.

### 수동 다중 reviewer 회귀 — 이전 finding 해소, DB 회귀 증거 대기

초기 검토에서 보고한 `uq_program_reviewer_active_slot` partial unique index는 최신
`V20260908_001__reviewer_line_automation.sql`에서 제거되었다. 따라서 기존
`reviewer_employee_id`까지 포함한 수동/Excel 다중 reviewer 정합을 DDL이 차단한다는
**이전 finding은 FIXED**다. 자동/수동 경합은 동일 program pessimistic lock을 사용한다.

다만 fresh/upgrade Flyway 실행에서 기존 다중 reviewer 데이터 보존과 FK 순서를 실제 DB로
확인하는 증거는 별도 대기 중이다. 정적 DDL 순서는 run table 생성 후 assignment FK를 추가해
**PASS (static/runtime)**이며, final Flyway migration application도 성공해 migration
compatibility는 **PASS**로 분류한다.

### S1 focused verification 상태 (최신)

PA 전용 테스트와 실제 Windows runtime verifier가 도착했다. `s1-backend-verification-
summary.json`은 focused **32/32**, 전체 **290/290**, `bootJar` exit 0을 기록하며,
`reviewer-line-local-final.json`은 최종 로컬 HTTP verifier **22/22 PASS**를 기록한다. 포함
범위는 signed UTF-8 source, body/header tenant mismatch, legacy context deny, source replay,
as-of/effective date, tombstone/stale preview, manual/Excel 보존, concurrent apply의 단일
persisted run, cross-program/employee 권한, size/duplicate 거부이다. FE의 plural
`participantIds`와 PA DTO/URL도 정적으로 일치한다.

따라서 위 항목은 **PASS (current evidence)**이다. 추가 isolation verifier의 employees/orgUnits/
assignments 3/3은 tenant B 행 불변과 tenant A batch rollback을 확인했고, 최신 typed
`SYNC_TENANT_MISMATCH`(E9804303/403) global-PK guard가 반영되었다. insert-only 격리와
cross-tenant/domain error의 403 fail-closed 표면은 **PASS (latest source/evidence)**로
승격한다. guard와 insert 사이 race는 `persist` PK 충돌 rollback으로 계속 안전하다.
Model B에서 이미 존재하는 route는 최신 코드가 매번 `setByActiveId`로 ACTIVE 상태를
재검증하고, 성공 시 이전 route를 `finally`에서 복원한다. inactive existing-route negative
test와 final runtime이 추가되어 이 경계는 **PASS**다.

## 11. S2S receiver tenant-context 보강 gate

현재 receiver는 opaque S2S Bearer를 JWT로 잘못 해석하지 않도록 별도 channel 인증을 수행하고,
extended body에서는 설정된 tenant allowlist와 body/header를 먼저 일치시킨 뒤 요청 범위
`TenantContext`를 설치한다. legacy body는 기존 authenticated context가 없으면 거부한다.
`TenantSupport.currentTenantId()`의 fallback UUID는 receiver 경계에서 신뢰하지 않는다.
검증된 안전 순서는 다음과 같다.

1. Bearer+raw-body HMAC 검증 후에만 body를 parse하고, signed body `tenantId`,
   `X-Tenant-Uuid`, 설정된 `performance.s2s.hcm.tenant-id`를 모두 비교한다.
2. 현재 `TenantContext`가 있으면 대상 tenant와 불일치 시 즉시 거부한다. 인증되지 않은
   header/body만으로 context를 만들지 않는다.
3. 현재 context가 없을 때만, 명시된 config tenant와 일치하는 경우에 한해 단일 DB context를
   임시 설정한다. Model B는 configured signed-tenant allowlist와 ACTIVE control-plane route를
   `setByActiveId`로 검증하고 route+context를 함께 설정한다. fallback UUID나 arbitrary body
   tenant는 금지한다.
4. sync service 진입 전에 실제 context가 존재함을 보장하고, `finally`에서 이전 context와
   route를 정확히 복원/clear한다. 이 경계에서는 `TenantSupport` fallback을 신뢰하지 않는다.
5. context 설치·복원, current mismatch, missing route, body/header/config mismatch,
   cross-tenant mutation 0을 테스트한다.

최신 소스 기준 PA backend는 focused **36/36**, 전체 **294/294**, `bootJar` PASS
(SHA-256 `1D3AABF5DCFEC0EFE13FF9DF39355FA185701F9CAA5990BF5E4C83F84483F454`)이며, 원본과
Windows 검증 사본 변경 해시도 **23/23** 일치한다. `reviewer-line-local.json`의
local HTTP verifier **22/22**가 body/header mismatch와 context deny를 PASS했으므로 receiver의
기본 삼중 일치 경계는 **PASS (runtime)**이다. A/B 동일 PK insert-only 충돌도
employees/orgUnits/assignments **3/3 PASS**로 tenant B 행 불변과 tenant A batch rollback이
확인되어 데이터 격리는 **PASS (DB evidence)**다. 최신 typed guard 반영으로 collision의
정상 경계 응답도 **PASS (source/tests)**다.

참고로 `s1-backend-verification-summary.json`의 `runtimeHttp.status=pending-root-verifier`와
기존 `reviewer-line-isolation.json`의 500은 갱신 전 stale 산출물이다. 최종
`reviewer-line-local-final.json` 22/22 및 `reviewer-line-isolation-final.json` typed 403
3/3을 사용한다. 최종 JAR SHA는 `1D3AABF5DCFEC0EFE13FF9DF39355FA185701F9CAA5990BF5E4C83F84483F454`,
OpenAPI SHA는 `AE0AC5E953E861E75CC03A5704ADA36ADD3A2A8B0524FBA81C2135291BC01C5D`이며,
`V20260908_001` migration 성공도 확인되었다. 이 경계의 최종 QA 판정은 **PASS**다.
