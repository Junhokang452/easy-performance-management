# Backend resume audit

작성: 2026-09-08 KST  
범위: 코드 수정·테스트 재실행 전 읽기 전용 감사

## 판정

- 기존 완료 증거는 내부적으로 일치한다. `backend/build/test-results/test` 44개 XML 합계는 265 tests / failures 0 / errors 0 / skipped 0이고, fresh HTTP JSON은 75+147+68+30+141=461, legacy JSON의 `results`는 97개이며 모두 통과다.
- `_workspace/full-cycle-20260907/verification-summary.json`, `09_verification_report.md`, `checkpoint.md`의 핵심 수치와 위 원자료가 일치한다.
- `.local-demo/easy-performance-management-runtime.jar`의 현재 SHA-256은 `6db3daa0b4bd9f52af94a179da2d3b35aca662041dea84865205b0ba755f36ef`로 보고서와 같다.
- Storybook 정적 서버의 종료 코드 `-1`은 제품 백엔드 실패 증거가 아니다. 다만 현재 8087, 5174, 55487도 모두 닫혀 있고 `backend.pid=39461`, `frontend.pid=39842`는 `/proc`에 없는 stale PID다. PostgreSQL 로그의 마지막 시작 이후 정상 shutdown 기록이 없으므로 개별 애플리케이션 오류보다 WSL/세션 프로세스 수명 종료와 더 잘 맞는다.

## 현재 변경 상태

- 기준: `main` / `1d9d282`.
- tracked backend·실행 관련 변경: 39파일, +305/-123. 여기에 신규 backend 136파일(프로그램 53, workflow 31, resources 11, migration 7 및 테스트 등)과 신규 scripts 17파일이 아직 미추적 상태다.
- `scripts/` 미추적 17파일 중 6개는 `__pycache__/*.pyc`; 현재 `.gitignore`에는 `.local-demo/`만 추가되어 Python cache는 제외되지 않았다.
- `lib/easy-platform`은 기록된 gitlink `172e5ed`에서 `b1654e9-dirty`를 가리킨다. 최종 JAR/검증이 이 공유 core 작업 트리에 의존하므로 커밋·재현 경계에서 분리 확인이 필요하다.
- `scripts/local-demo.sh`는 합성 DB·비밀값을 `.local-demo/`에 격리하고 immutable JAR copy를 비차단 `setsid`로 실행하며, 실패 시 자신이 시작한 프로세스를 정리한다. 현재 stale PID/socket은 재기동 전에 `stop` 또는 동등한 안전 정리가 필요하다.

## 잠재 결함/후속 분류

1. **API 계약(P1)**: 신규 program 목록 API가 `Page<...>`/`PageImpl`을 직접 반환하고 실제 로그에 Spring Data의 불안정 JSON shape 경고가 남는다. 안정 DTO 페이지 래퍼 또는 `VIA_DTO` 정책이 필요하다.
2. **검증 API 사용법(P2)**: `ProgramDtos`와 `ResourceDtos`가 `@Valid List<T>`를 사용해 실행 로그에 HV000271 deprecation 경고가 반복된다. `List<@Valid T>`로의 전환 후보이며, 현재 통과 결과를 무효화하지는 않는다.
3. **재현성(P1)**: 대규모 신규 backend/scripts가 미추적이고 공유 core gitlink가 dirty이므로 현재 성공 상태는 아직 깨끗한 checkout에서 재현할 수 없다.
4. **작업 트리 위생(P2)**: `scripts/__pycache__` 6개와 `gradlew.bat` 2개 trailing whitespace가 남아 있다.
5. **보안 설정 관찰(P2)**: local-demo 부팅 로그에 Spring의 generated security password/in-memory manager 경고가 있으나 HTTP 체인은 JWT 기반이며 기존 실HTTP 권한 검증은 통과했다. 불필요 auto-config인지 후속 확인 대상이다.

본 감사에서는 요청대로 backend 테스트·fresh DB를 재실행하지 않았고, 외부 Neon/control plane/SMTP/push/deploy 변경을 하지 않았다.
