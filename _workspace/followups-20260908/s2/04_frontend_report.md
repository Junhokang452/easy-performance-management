# S2 프런트 구현 및 검증

Terra가 API/RQ 초안·운영 화면 접점·근거 도구 컴포넌트를 작성한 뒤, Astra가 남은 구현과 검증을 인수했다. 최종 수정 책임은 Astra다. 기존 S1 및 다른 dirty 변경은 보존했다.

## 구현

- 목표 하나/KPI 배정 하나/실적 기준일을 명시 선택하고 서버 preview를 표시한다. 계산은 서버 값만 사용한다.
- 사유 필수 확인 모달에서만 적용한다. 요청 중 입력 변경을 막고, 실패 시 재미리보기를 요구한다. apply body는 계약 필드만 전송한다.
- 목표별 이력 조회 URL을 `/goals/{goalId}/kpi-links`로 바로잡았다. 주기·후보·이력 페이지 이동을 제공한다.
- 직원 목표 화면과 배정된 평가자 화면에 읽기 전용 근거 선택기/이력을 연결했다. HR 운영 화면만 쓰기 도구를 표시한다.
- 현재/이전 근거, 캡처 목표값·실적·추천점수·산식·기준일·사유를 표시한다. 목표값과 가중치가 저장 시점 정의라는 제한을 명시했다.
- `kpiLinkageI18n.ts`를 기존 namespace composition 방식으로 ko/en/ja/zh-CN/vi에 결합했다.

## 직접 실행한 게이트

Windows 기존 의존성 검증 사본에 최종 변경 10파일 및 생성 schema를 동일 복사한 뒤 실행했다. 11/11 파일 SHA 동등성을 확인했다. 패키지 설치/공유 라이브러리 변경 없음.

| 게이트 | 결과 |
|---|---|
| TypeScript `tsc -b --pretty false` | PASS (exit 0) |
| Vite production build | 최종 PASS (1.77s), 기존 공통 client chunk >500kB 경고 |
| i18n + evaluation-workspace Node tests | 12/12 PASS |
| design-system audit | hex 0/0, inline-style 0/0 |
| local-ui audit | tags 0/0, blocked-imports 0/0 |

실제 `/v3/api-docs`를 `openapi.json`으로 보존했고 openapi-typescript 7.13.0으로 `src/api/generated/schema.d.ts`를 재생성했다. 이후 tsc exit 0 재확인. 생성 타입 SHA256 `652384580d6f6d502e69785ce97cd9f97733299b4b028475fdf95a906fb5b73d`.

브라우저에서 발견한 새 `KPI_LINK_APPLIED` 감사 이벤트의 raw enum 표시는 기존 타임라인에 5언어 KPI namespace 라벨을 연결해 보완했다. 모바일은 기존 UiTable.ScrollContainer와 산식 overflow-wrap CSS를 사용한다. 모든 수정 후 위 게이트를 재실행했다. 브라우저 최종 결과는 `browser/result.json` 및 `99_final_report.md`가 우선한다.
