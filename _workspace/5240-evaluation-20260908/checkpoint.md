# 5240 평가 참고 보완 체크포인트

작성: 2026-09-08 KST

## 완료

- [x] 28개 평가 문서 판정표와 적용 범위 복구.
- [x] 관리자 ProgramAudit 조회 API·React Query·감사 타임라인 정적 계약 확인.
- [x] Storybook `-1` 오류를 foreground 서버 수명 종료로 분류; 정적 빌드 산출물 151개 정상.
- [x] Windows fallback focused audit test 7/7.
- [x] Windows fallback 전체 backend 272/272 및 bootJar PASS.
- [x] HTTP verifier 보강: exact 6필드, page metadata, 단독/복합 필터, 정렬, 권한/tenant, size, 민감 필드.
- [x] API↔FE 경계 QA 정적 PASS.
- [x] 실제 Windows PostgreSQL/JAR HTTP 감사 API 17/17 PASS(유효 B 세션·B 소속 목록 200, 잘못된 B actor 401, A 감사조회 404, size101=422 포함).
- [x] 브라우저 acceptance 스크립트 신설: production dist + 실제 API proxy, 5 locale×1440/390, 이벤트·대상자 필터, page reset, 일반 사용자 차단.

## 추가 완료

- [x] 최신 OpenAPI 188,246 bytes / 195 paths 생성 및 원본 `schema.d.ts` 갱신.
- [x] core 필수 3패키지 build, FE 전체 typecheck exit 0.
- [x] FE i18n 7/7, workspace 5/5, design/local-ui 검사 exit 0.
- [x] Vite production build exit 0 (7,501 modules, 75 assets).
- [x] 실제 브라우저 14/14 exit 0: 5 locale×1440/390, 이벤트·대상자 필터, page reset, refresh, null 표시, employee 차단.
- [x] 최종 캡처 13장과 `browser-20260908-final/result.json` 보존; PC/모바일 viewport 시각 확인.
- [x] 전용 Java/PG 종료 및 8089/55489 닫힘 확인(13:19 KST). 기존 5432/5433 PID 동일, PG 데이터·증거 보존.

## 환경 한계와 범위

- [ ] WSL native 환경: 사용자 승인 후 Ubuntu 종료/서비스 재시작을 시도했으나 WslService StopPending. 후속 관리자 권한 프롬프트는 취소되어 추가 강제 조작하지 않음. Windows 격리 환경에서 검증 지속.
- Windows fallback 검증은 완료했다. 원본↔검증 복사본은 backend 감사 5파일, frontend 관련 8파일 해시가 일치한다.
- 이번 판정은 Program Audit 조회 증분에 한정한다. 전체 dirty tree 병합 또는 `page-decisions.md`의 다른 보완 요구 완료를 의미하지 않는다.
- 날짜 표시는 기존 브라우저 locale 포맷을 유지한다. 5 locale 검사는 UI 라벨·레이아웃 및 번역 키 계약 범위다.

## 다음 순서

1. 전용 Windows 검증 런타임 정리 완료. 근거: `windows-audit-held-stop.json`.
2. 최종 결과는 `99_final_report.md`, 품질 근거는 `12_quality_report.md`를 우선한다.
3. 후속 제품 요구는 `page-decisions.md`의 별도 범위로 진행한다. push/deploy/외부 DB·SMTP는 실행하지 않았다.

## 근거

- `backend-resume-verification.md`
- `windows-backend-verification-summary.json`
- `10_boundary_qa.md`
- `11_frontend_windows_temp_validation.md`
- `12_quality_report.md`
- `windows-audit-runtime-summary.json`
