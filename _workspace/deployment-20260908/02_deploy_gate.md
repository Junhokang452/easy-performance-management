# easy-performance-management GitHub/Naver 개발서버 배포 안전 게이트

작성일: 2026-09-08 (KST)  
담당: Luna (read-only deployment gate review)  
판정 범위: 기존 기록·런북·표준을 이용한 배포 전 안전 매핑만 수행. 원격 SSH, GitHub push,
빌드·재기동·migration·DB 쓰기·제품 코드 수정은 수행하지 않았다.

## 1. 확인된 대상과 증거 수준

| 항목 | 기록된 값/경로 | 상태 및 주의 |
|---|---|---|
| GitHub | `https://github.com/Junhokang452/easy-performance-management.git`, branch `main` | 공개 원격 URL은 로컬 설정에서 확인. 원격 최신 HEAD/권한은 이 세션에서 조회하지 않음. |
| 현재 로컬 revision | `1d9d282` | 현재 working tree가 dirty이므로 배포 artifact identity로 사용 금지. 정확한 승인 commit/tag 필요. |
| Naver 대상 | `101.79.21.163`, `easyops`, `/opt/easy-suite` | 2026-08 testbed 운영 인계의 historical target. 현재 health/이미지/DB 상태는 미확인. |
| 원격 source path | `/opt/easy-suite/src/easy-performance-management` | `easy-platform/deploy/aitestbed/compose.yml`의 performance build context. |
| 원격 deploy path | `/opt/easy-suite/deploy` | `compose.yml`, `.env`, Caddy 및 sequential scripts 위치. `.env` 내용은 읽지 않음. |
| runtime secret path | `/opt/easy-suite/secrets/performance.env` | compose가 참조하는 root-only 파일. 값·credential은 본 문서/로그에 기록하지 않음. |
| Compose service | `performance`, image `easy-lab/performance:${IMAGE_TAG:-latest}` | profile은 `[talent, all]`, internal `10000`, `performance_data` volume, 768 MiB/0.50 CPU. `latest` fallback은 배포 시 금지하고 immutable tag/digest를 요구. |
| edge route | `performance:10000` → `performance.{$LAB_BASE_DOMAIN}` | historical base domain 기록을 적용하면 예상 URL은 `https://performance.101-79-21-163.sslip.io`; current DNS/HTTPS는 미검증. |
| 기존 공개 서비스 | foundation(control-plane/WARE/HCM/TIME/edge) | historical handoff상 foundation만 상시 기동, performance는 on-demand. 다른 서비스 자동 재기동 금지. |

근거: `easy-platform/deploy/aitestbed/README.md`, `compose.yml`, `Caddyfile.apps.example`,
`_workspace/aitestbed-suite-lab-2026-08-24/05_operator_handoff.md`,
`easy-performance-management/_workspace/deployment-20260908/00_task.md`.

표준 SoT 근거: `easy-platform/easy-standards/00-principles/05-cicd-harness-gates.md`,
`09-database.md`, `14-data-backup-offboarding.md`, `04-observability.md`.

## 2. 현재 배포 전 NO-GO 사유

다음은 배포 실패가 아니라, 원격 preflight가 완료되기 전까지의 안전 차단이다.

- 현 Naver runtime에서 `performance` container/image/config, tenant DB route, schema/Flyway
  version, control-plane row, 계정·권한, disk/memory 여유가 아직 결속되지 않았다.
- 현재 저장소는 대규모 tracked modification과 untracked audit 산출물이 있고
  `lib/easy-platform` submodule도 recorded ref와 working content가 다르다. 전체 dirty tree를
  그대로 GitHub에 push하거나 remote build context로 사용하면 안 된다.
- testbed의 performance snapshot `1d9d282`와 현재 local `main`이 같아 보이더라도 remote 최신
  HEAD, submodule commit, 생성 JAR/image digest를 별도로 고정해야 한다.
- `render.yaml`은 Render Singapore mono 서비스용 문서이지 Naver Compose 절차가 아니다. Render의
  `SPRING_FLYWAY_ENABLED`, Model B, tenant/bootstrap 값 또는 `autoDeploy`를 Naver에 복사하지
  않는다.
- 저장소에는 배포 전용 GitHub Actions가 없다. migration naming/FE quality workflow만 확인되므로
  backend/full-image/SBOM/secret scan 결과를 별도 증거로 고정해야 한다.

## 3. 안전 게이트 매핑

### A. GitHub/source provenance — 배포 전 필수

1. 현재 dirty tree에서 승인 범위만 분리하고, 기존 `_workspace`와 공유 lib/HCM 변경을 임의로
   포함하거나 되돌리지 않는다.
2. 승인 source commit, `lib/easy-platform` submodule commit, generated schema/static asset,
   Gradle/npm lockfile을 고정하고 GitHub `main`에 반영한다. push 전 secret scan에서 `.env*`,
   PEM/P12/JKS/key, DB/JWT/Neon 값이 0건이어야 한다.
3. Dockerfile은 FE Vite dist + BE bootJar + Temurin 21 runtime을 묶는 multi-stage 패키지다.
   root context에서 빌드하고 source commit, submodule ref, JAR SHA, image digest, SBOM/SCA,
   image non-root/user·healthcheck를 기록한다. `latest` 또는 local bootJar 단독 배포는 금지한다.
4. GitHub Actions의 migration naming과 FE quality가 PASS인지 확인하되, 이것만으로 backend
   test/컨테이너/배포 승인을 주장하지 않는다.

### B. Backup/restore — DB 변경 전에 필수

1. remote에서 현재 performance product DB와 shared control-plane metadata의 정확한 대상·DB명·
   tenant row·schema version을 metadata-only로 확인한다. 기존 5432/5433 또는 다른 제품 DB를
   대상이라고 추정하지 않는다.
2. application role이 아닌 승인된 migration/backup role로 direct PostgreSQL 연결을 사용해
   quiesced protected backup을 만든다. backup directory는 root-only, 파일 hash와 크기만 문서화하고
   dump body/credential은 노출하지 않는다.
3. 동일 백업을 network-none·non-root·resource-bounded disposable DB에 restore하고 catalog,
   Flyway history/checksum, RLS/tenant constraints, row counts/privileges를 비교한다. restore가
   검증되지 않으면 migration/deploy를 중단한다.
4. 첫 performance 활성화라 해도 DB가 없다고 가정하지 않는다. empty/new tenant provision,
   existing tenant upgrade, shared control-plane consumer 경로를 정확히 분류한다. Model B
   consumer는 control-plane owner가 아니며 control DB에 product migration을 적용하지 않는다.
5. 운영 표준 `05-cicd-harness-gates.md`의 pre-deploy rollback plan/승인과
   `09-database.md`의 direct connection, app/migration role 분리, PITR·복구 리허설을 충족한다.

### C. Migration/config — 정확한 target과 순서가 확인된 뒤

- pending migration을 source와 target의 Flyway history로 비교하고 forward-only 적용한다. 기존
  migration 파일 수정·checksum 무시는 금지한다.
- 자동 self-migrate, scheduler, fan-out, control-plane owner 설정을 명시적으로 확인한다. 현재
  Compose의 `performance`는 env-file에 의존하므로 Render `render.yaml`의 값을 이식하지 않는다.
- tenant route는 ACTIVE 상태와 exact tenant/database binding을 확인한 뒤에만 설정한다. 임의
  default tenant 승격, shared control DB fallback, 미설정 S2S endpoint 활성화는 차단한다.
- 이번 승인 범위는 easy-pa 배포다. HCM/Talent S2S, SMTP, 운영 발신, 신규 Neon project/유료
  resource는 별도 승인 없이는 활성화하지 않는다. optional S2S는 미설정 fail-closed를 유지한다.

### D. Deploy/health/smoke — performance 단독

1. old image ID, compose service definition, env key 목록/비밀 hash, volume mapping, backup
   위치를 보존한다. foundation/edge/control-plane은 재기동하지 않는다.
2. `talent` profile 전체(mra/job-management/performance/talent)를 한꺼번에 올리는 기존
   `up-sequential.sh talent`는 사용하지 않는다. exact `performance`만 명시적으로 기동하고
   의존성이 입증된 경우에만 별도 승인한다.
3. 내부 앱 포트는 `10000`으로 유지하고 public은 Caddy 80/443만 통과시킨다. control-plane
   `127.0.0.1:8099`를 외부 공개하지 않는다.
4. healthcheck/readiness/liveness와 로그를 확인한 뒤 smoke 순서를 고정한다: expected HTTPS
   route → SPA/login/session → tenant-scoped evaluation API → finalized result/PDF download
   및 audit event → employee/foreign-tenant denial → existing foundation services unchanged.
5. 배포 후 표준 §6의 5xx 증가, readiness/liveness 반복 실패, restart loop, OOM/heap 90% 지속,
   Hikari pending/DB connection 고갈을 확인한다. 모든 health가 정상이어야 완료로 판정한다.

### E. Rollback — 즉시 중단 조건과 복구

| 신호 | 조치 |
|---|---|
| image/config/source provenance 불일치 | 기동 전 중단; old image/config와 승인 baseline 보존 |
| migration checksum/target/tenant mismatch | 앱 시작 중단; SQL을 임의 수정하지 말고 restore/forward-fix 검토 |
| health/readiness 실패, 5xx 급증, restart/OOM/Hikari 고갈 | performance만 중지하고 old image/config로 재기동; foundation 불변 확인 |
| migration이 이미 적용된 뒤 앱 rollback 필요 | image만 되돌리지 말고 protected backup의 isolated restore 및 승인된 DB rollback 절차 사용 |
| tenant/control-plane 오염 또는 비인가 데이터 노출 | 즉시 외부 route 차단·서비스 중지, audit/로그 보존, 사용자 승인 전 재개 금지 |

첫 활성화에서 schema migration이 1건이라도 적용되면 “컨테이너를 내리면 rollback”으로
간주하지 않는다. 복구 가능 백업과 restore rehearsal가 없으면 배포는 NO-GO다.

## 4. 좁은 재개 순서

1. Root가 GitHub 원격 권한·승인 source/submodule ref와 Naver SSH known-hosts를 확인한다(비밀값
   출력 금지).
2. Remote metadata-only preflight로 performance container/image, exact DB/tenant route,
   Flyway version, resource baseline을 확보한다.
3. Protected backup + isolated restore rehearsal와 migration diff를 완료한다.
4. 승인 commit에서 immutable image를 만들고 artifact/SBOM/secret/health evidence를 고정한다.
5. performance만 단계적으로 기동해 migration·health·tenant landing·로그·smoke를 검증한다.
6. 실패 시 E 표의 rollback을 적용하고, 성공 시 public URL/health/audit/hash/old-service
   unchanged evidence를 보존한다.

## 5. 독립 결론

현재 문서 근거만으로는 **배포 착수 BLOCKED — remote target/backup/tenant/artifact preflight
필요**다. 이는 사용자 승인 자체를 부정하는 판정이 아니며, 위 preflight와 복구 증거가 채워지면
performance 단독 개발서버 배포를 재판정할 수 있다. NCP historical lab 기록은 절차·토폴로지·
예상 URL을 제공하지만 현재 서비스가 존재하거나 최신 easy-pa artifact가 배포됐다는 증명으로
승격하지 않는다.

비밀값, `.env` 원문, PEM, DB URL/credential, JWT/Neon key는 본 문서에 기록하지 않았다.
