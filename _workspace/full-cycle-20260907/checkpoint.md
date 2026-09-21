# 전체 평가 구현 체크포인트

상태: 구현 진행 중. 구성원 16 + 관리자 32개 원문 분석 완료, 전체 기능 완료 아님.

- 요구/소유권: `05_implementation_contract.md`.
- backend_flow: `program/**`, V20260907_004 및 테스트. typed ProgramDtos 작성, 평가 엔진 구현 진행.
- auth_boundary_review: `resources/**`, V20260907_005 및 테스트. 과제/카탈로그/부서목표/면담 구현 진행.
- frontend_completion: frontend-vite 전체. 5국어·새 메뉴·프로그램/협업 화면 및 API 연결.
- root: 공유 자산, 통합 검증, 합성 로컬 DB/서버. 다른 담당 파일 덮어쓰기 금지.

완료된 공통 부분:
- product-local core `MasterDetailWorkspace` 4개 파일+export, 타입빌드 PASS; 중앙 core에 같은 자산만 additive 동기화.
- Storybook 빌드에서 기존 recharts peer 누락 발견. 명시 의존성 추가/재검증 중.
- product-local core SimpleXlsx: 실제 OOXML 읽기/쓰기, XXE/수식/매크로/ZIP경로/용량 차단, 테스트 12/12 PASS. 도메인 검증은 각 소비 API 책임.
- shared npm audit 발견 7건에 대해 호환 범위 fix 진행. FE 의존성은 FE 담당이 관리.

잔여 필수:
- 실제 API/화면 구현 완료 후 계약·권한·상태·계산·파일 교차 검증.
- 마이그레이션 _004/_005 도착 후 기대 스키마 버전 _005 갱신.
- 기존 합성 local-demo(5174/8087/55487)와 신선 PG에서 부팅/실 API 시나리오/브라우저 전 과정/5국어/모바일 검증.
- core hermetic 전체 테스트(외부 PG 전용 8개 별도), BE 전체, FE 빌드 및 audit, 최종 JAR SCA.

외부 Neon/control plane/배포/push 수행하지 않음. 기존 미커밋 작업·Boot 4.1.1 보안 업그레이드·기존 평가흐름을 보존한다. 상세 이전 증거는 ../framework-20260907 및 ../roothr-20260907.

추가 체크포인트:
- 공유 core 전체 hermetic 테스트 205/205 PASS, 외부 PG/testcontainers 8개는 제외 상태를 유지.
- Storybook production build PASS, 실제 Chromium 1440/390px master/detail 배치·overflow·pageerror PASS.
- SimpleXlsx 신규파일/문서 및 MasterDetail 자산을 중앙core에도 additive 동기화; 중앙 타작업 보존.
- easy-standards 기존 performance-evaluation-shared-assets 문서에 새 자산 및 검증 증거 등록.
- SecurityConfig resources/programs authenticated facade 예외 추가. 기존 raw operator 제한 보존, 회귀 3개 작성(전체빌드에서 실행 예정).
- 기대 스키마 버전 20260907.005 및 scheduler test 갱신.
- `verify-full-resources.py`, `verify-full-program.py` 실제 API 검증 준비. 서버에 새 코드가 아직 미적용이므로 실행 전.
- ProgramAnalyticsService는 A가 B에게 resources 완료 후 read-only 구현 위임. A는 roster xlsx·실행·계산/공개 계속 담당.
- 新 `scripts/verify-fresh-full-cycle.sh`: 별도 PG18 cluster 55488/BE8088, synthetic performance_demo DB만 생성; 기존 .local-demo 비밀값을 clean env로 주입, 실패시 프로세스 종료·자료 보존. 아직 새 JAR 부팅 대기.
- multipart20MiB/21MiB 명시(개별첨부10MiB 서비스 제한 유지), framework 크기초과를 기존 E9804261로 응답하게 연결+회귀 테스트 준비.
- B resources focused 테스트 9/9 PASS. 교체 DELETE→flush→INSERT unique 충돌 방지 보강. B는 analytics 구현으로 계속 진행.

- 1차 fresh 부팅 PASS: `.local-demo/fresh-20260907-173924`, Boot 64.6초, V004/V005 + Hibernate validate 성공. resources 실HTTP 75/75 PASS. 프로그램은 목표·부분배치·중간·자기평가 진행 확인; 차수권한 조회시험을 실제 PUT차단으로 정정 후 계속 예정.
- main local-demo5174/8087 기존DB 업그레이드 성공. resources 재검증 + 기존97API 회귀 실행 중.
- 독립 B 권한리뷰 P1(grade-only calculation누출, round999, feedback scope, operator content변경, finalized goal mutation, draft calculation finalization) A 수정 진행. root 실API verifier에 부정경로 추가했고 완료로 판정하지 않음.
- SMTP 어댑터 mail/** root 추가, Boot mail dependency 및 tenant-scoped account-email조회 사용. local SMTP 포함 focused 23/23 PASS. 실제 외부발송 0.
- main upgraded resources75/75 + legacy97/97 HTTP PASS. 프로그램 부정경로 baseline 테스트와 수정 후 재검증 준비.
- 현재 JAR(SMTP 의존성 추가 포함) SCA checkpoint matches0/ignored0; 최종 소스/JAR은 완료 후 다시 해시/스캔.
- UI 실캡처로 발견한 1차 차단: DRAFT 기본정보읽기전용(편집API/UI A+C 추가중), 팀장기본진입이 /me404(C수정), 자기평가 첫submission없을때입력폼없음(C수정), 구성원 중간완료버튼이CHECKER권한과불일치(C수정), raw상태/척도고정(C수정). interim캡처는 검토증거이며 최종완료증거아님.
- 새 SENDING atomic dispatch claim을 위한 V20260907_006__notification_dispatch_claim.sql A 소유예약. 완료 후 root scheduler 기대버전006 갱신 필요.

## 19시 통합 상태
- Backend 최신 test+bootJar 성공(263 이후 normalize empty 대상 1회귀 추가). 마지막 소스에 normalize zero-target raw 보존 경고 포함.
- 신선 `.local-demo/fresh-20260907-185822` 전체 PASS: resources75, program147, policy68, revisions30, calculation policies112. V007까지 fresh migration/validate.
- root AnalyticsPage 소유권 인수: 실제2평가 matrix grid/drilldown, pivot grade필수/drilldown/xlsx/svg, finalized 선택, unavailable null 미표시+방법론문구.
- browser19route 로딩대기후 render오류0/API오류0, favicon404 잔존. 실제 download 버튼 클릭 테스트 미완(요청/다운로드 안잡힘), root 조사중. evidence browser-analytics.json 현재 실패.
- A/B agents 사용량한도로 중단, source/disk 산출물보존; root가후속인수. C FE 작업진행중, 지금 npm install ignore-scripts로lock 동기화중.
- 미완: C 목표수정/상태번역/scaleUI/preview/operations밀도/5locale/FEbuildaudit 최종, root actualbrowser actions+mobile5locale, finalSCA/APIgen, mainruntime 최신jar재시작.

## 최종 완료
- member/admin 구현 및 검증 완료. 기준문서 `09_verification_report.md`, 기계 판독 `verification-summary.json`.
- 최종 backend265/265, fresh461/461(75+147+68+30+141), legacy97/97, core205(외부PG8제외). GRADE numeric 우회 차단 및 모집단별 정규화 포함.
- browser setup/analytics/grade/locales/member draft 모두PASS. FEtype/build/i18n7/workspace5/design/localUI/audit0. App page직접lazy 및favicon 포함.
- 최종JAR SHA256 6db3daa0b4bd9f52af94a179da2d3b35aca662041dea84865205b0ba755f36ef, SCA0. mainruntime immutable copy 최신jar로재시작. fresh194105검증프로세스종료·증거보존.
- 외부Neon/SMTP/push/deploy미실행. 소스와로컬검증작업완료; 추가자동진행대기작업없음.
