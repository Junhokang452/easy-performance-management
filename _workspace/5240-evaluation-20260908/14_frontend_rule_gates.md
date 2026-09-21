# 프런트엔드 규칙·회귀 검사 — Windows fallback

검증일: 2026-09-08 KST. 실행자: Astra(root).

## 실행 환경

- Node v24.19.0: `C:/Users/SAMSUNG/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe`
- cwd: `C:/Users/SAMSUNG/AppData/Local/Temp/easy-performance-frontend-validation-5a65261bd15541a78de18e7c0bda4407/easy-performance-management/frontend-vite`
- 실제 child process의 `close` 이벤트 exit code를 수집했다. shell pipeline, 출력 절단 또는 이전 날짜의 결과를 재사용하지 않았다.

## 결과

| 검사 | 결과 | 실제 출력 |
|---|---|---|
| `--experimental-strip-types --test scripts/i18n-conformance.test.ts` | exit 0, 7/7 | ko/en/ja/zh-CN/vi 키·보간 일치, locale 정규화, 오류 메시지 노출 정책 통과 |
| `--experimental-strip-types --test scripts/evaluation-workspace-*.test.ts` | exit 0, 5/5 | 캘리브레이션 단계, 목표 제목·가중치, 분포 합계, setup/action, 미래 단계 잠금 통과 |
| core `scripts/check-design-system.mjs src --max-hex=0 --max-inline-style=0` | exit 0 | `design-system audit: hex=0/0, inline-style=0/0` |
| `scripts/check-local-ui.mjs src --max-tags=0 --max-blocked-imports=0` | exit 0 | `local-ui audit: tags=0/0, blocked-imports=0/0` |

workspace test 파일 glob은 실행 전 실제 파일 목록으로 확장했다. i18n duration 9356.1895ms, workspace duration 6383.901ms.

본 결과는 규칙·회귀 검사에 한정한다. 전체 TypeScript 검사, Vite production build, 실제 브라우저 검사는 별도 게이트다.
