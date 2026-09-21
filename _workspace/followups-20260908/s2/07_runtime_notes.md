# S2 런타임 및 재현 주의사항

## 검증 환경

- 전용 Windows PG18 `127.0.0.1:55489/performance_demo`, API `127.0.0.1:8089`만 사용. 기존 PG5432/5433 보호.
- 최종 JAR SHA256 `8F1B2132EBAAB78166007F449CC791D2A3C1BC2F2E954F603F20C8D0DDA0FFDA`, 실부팅 24.173s.
- `flyway_schema_history`에서 version `20260908.002`, description `program kpi evidence`, success `t` 직접 확인.
- API/브라우저는 서로 다른 fixture가 아닌 같은 합성 프로그램을 이어 검증한다. 브라우저 성공 마지막 단계는 self/reviewer 제출을 완료하므로 동일 fixture로 처음부터 재실행하면 안 된다. 다시 실행할 때는 API verifier가 새 fixture를 생성한 후 브라우저를 실행한다.

## 수정한 검증 스크립트 가정

- KPI 원본 가중치는 0..1 비율인데 초기 fixture가 ProgramGoal의 0..100 퍼센트와 혼동하여 노드 생성에서 정상적으로 422 거부됐다. 원본 0.5/배정 1로 수정했다. 제품 규칙은 바꾸지 않았다.
- GET 페이지 상한 오류 검증의 expected 인자를 위치 인자로 잘못 전달한 harness 오류를 키워드 인자로 수정했다. 실제 API는 이미 422로 올바르게 거부했다.
- 필수 사유 label은 접근성 이름에 `*`가 포함되어 exact selector가 찾지 못했다. 원래 label을 보존하고 정규식 selector로 보완했다. 첫 실행은 10개 locale/viewport와 취소 무변경까지 통과했으며 저장은 수행하지 않았다.

## 보존·제한

실패 시 생성된 합성 프로그램/주기와 진단 이미지는 삭제하지 않았다. `browser/failure.png`는 중간 진단이며 최종 판정은 `browser/result.json`이다. 운영 연결·외부 DB·SMTP·push·배포·커밋은 없었다.

전역 `git diff --check`는 기존 `backend/gradlew.bat` 73/77행 공백 경고로 exit 1이었다. 해당 기존 파일을 수정하지 않았다. 범위를 `backend/src frontend-vite/src scripts`로 한 `git -c core.autocrlf=false diff --check`는 exit 0이다.
