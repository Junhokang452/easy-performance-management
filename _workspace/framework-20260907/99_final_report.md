# 성과관리 프레임워크 보안 전환 검증

2026-09-07. 기존 성과평가 업무·공유 디자인 자산·5개 언어 지원을 유지하면서 승인된 자매품 보안 기준으로 전환했습니다. 원격 저장소 push, 외부 DB 변경, Neon 프로비저닝 및 배포는 실행하지 않았습니다.

## 적용 범위

- Java 21 / Spring Boot 4.1.1 / Gradle 8.14.5 / springdoc 3.1.1.
- Boot BOM보다 최신 보안 패치인 Tomcat 11.0.25를 명시하고 실제 실행 클래스 버전 검증을 추가했습니다.
- 제품이 사용하는 공통 core는 1.0.0-SNAPSHOT. 중앙 저장소의 검증된 프레임워크 변경만 제품-local pin에 이식했습니다. 공유 WorkflowPhaseRail 자산 commit `7c814bc`를 보존한 후 로컬 commit `b1654e9`를 생성했습니다. 중앙 저장소 HEAD 전체를 끌어오지 않았습니다.
- Boot 4의 모듈별 starter·자동설정/테스트 import를 적용했습니다. 기존 공개 JSON/S2S 계약은 Jackson 2 변환기로 유지하며 실제 MVC 변환기, null·날짜·소수 정밀도·enum·오류 응답을 검증했습니다. Jackson 3도 의존성으로 존재하지만 MVC의 우선 JSON 변환기는 Jackson 2임을 실측했습니다.
- Docker 빌드 이미지는 Gradle 8.14.5 JDK21의 검증된 digest로 변경했습니다. Docker engine 연결 부재로 실제 이미지 빌드는 실행하지 못했습니다.

## 실측 검증

| 항목 | 결과 |
|---|---|
| 제품 백엔드 | 219/219, 실패·오류·건너뜀 0 |
| 제품 pin 공통 core | 193/193, 실패·오류·건너뜀 0. 외부 PostgreSQL 통합 8개는 명시적으로 제외 |
| 실제 API 업무 흐름 | 97/97. 역할·타인/다른 테넌트 접근 차단, 목표 합의→실적→평가→보정→발행→확인/이의→마감 |
| 실제 로그인 | HttpOnly 쿠키, access 쿠키 제거 후 자동 갱신 200, JS 저장소 토큰 없음, 로그아웃 후 재접속 차단 |
| 실제 다국어 화면 | 5개 언어 × 로그인/업무/모바일 = 15/15, 화면 오류 0 |
| 다국어 코드 | 한국어·영어·일본어·중국어 간체·베트남어 각각 928키. 검사 7/7 |
| 프런트 계약·디자인 | 새 springdoc 실제 API에서 타입 재생성 후 타입 검사·프로덕션 빌드 통과. 업무 상태/입력 테스트 5/5, 디자인·로컬 UI 위반 0 |
| 실제 역할별 화면 조작 | 관리자 생성/배정/개시 → 구성원 목표/실적/자기평가 → 평가자 승인/점수 → 보정/발행 → 피드백/결과수용 → 마감 전체 통과. 모바일 390px 가로 넘침·화면 오류 0 |
| 진행률 | 90% 저장 후 API 재조회와 실제 브라우저 새로고침 후 유지 |
| 로컬 재기동 | Boot 4.1.1 / Tomcat 11.0.25, 40.487초 기동. 기존 합성 DB의 Flyway 11개 검증, schema 20260907.003 유지, 추가 migration 없음 |

다국어 브라우저 첫 실행은 메뉴 표시 대기에서 한 번 시간 초과됐습니다. 진단 로그를 추가한 동일 검증 재실행에서 15개 항목 전부 통과했으며, 제품 코드는 이 재실행 사이 변경하지 않았습니다.

## 실행 파일 보안 검사

Syft 1.51.0 / Grype 0.116.1, **동일 DB v6.1.9 (2026-09-06)** 로 이전·이후 실행 JAR을 비교했습니다. DB 자동 갱신 OFF, 사용자 억제 규칙 없음, ignored 0.

| 등급 | 이전 | 최종 |
|---|---:|---:|
| Critical | 8 | 0 |
| High | 27 | 0 |
| Medium | 30 | 0 |
| Low | 13 | 0 |
| 합계 | 78 | 0 |

최종 JAR SHA-256: `1e83919664e3ec9eee5923292d45f7fb0424278413c6f609f1ce0306f3ee3a8b`.

이는 고정된 취약점 DB와 실행 JAR 범위의 결과입니다. Docker OS 이미지·미래 신규 advisory·실제 Neon 고객 DB 라우팅 검증을 포함하지 않습니다. Jackson 2 호환 계층은 차기 공통 JSON 전환까지의 과도기이며 관련 deprecation 경고가 남습니다.

## 증거 및 로컬 확인

`test-summary.json`, `artifact-versions.json`, `runtime-evidence.log`, `api-flow-result.json`, `browser-session-result.json`, `i18n-browser-result.json`, `progress-browser-result.json`, `scan-final-summary.json` 및 원본 SBOM/Grype 결과를 같은 디렉터리에 보존했습니다. core 제외 테스트 2클래스/8개와 근거는 `test-summary.json`에 명시했습니다.

화면: http://localhost:5174 . 합성 계정 `dev-hr-admin@performance.dev`, `dev-manager@performance.dev`, `dev-employee@performance.dev`, 비밀번호 `dev`, 회사 코드 공란. 실제 고객 운영 연결은 수행하지 않았습니다.

이전 기능·공통 자산·Neon 토폴로지 범위는 `../roothr-20260907/99_final_report.md`를 참조합니다.

## 개발 화면 동시 빌드 검증 보정

첫 전체 UI 검증은 업무 단계 모두 통과했으나, 동시에 실행한 공유 UI prebuild가 Vite AuthProvider 모듈을 반복 갱신하여 `useAuth must be used inside AuthProvider` 오류가 기록됐습니다. 로그에서 16:18:06 Fast Refresh invalidate 및 page reload와 이어지는 오류를 확인했습니다. 정적 Provider 구성과 React 의존성 중복 검사에는 문제가 없었습니다. 빌드 완료 후 코드 변경 없이 새 브라우저에서 전체 흐름을 다시 실행하여 exit 0, 모든 단계와 모바일 렌더링 통과, 화면 오류 0을 확인했습니다. 개발 중 공유 패키지 빌드와 E2E는 순차 실행합니다. 이전 오류 로그도 보존했습니다.
