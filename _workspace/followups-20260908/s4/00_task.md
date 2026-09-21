# S4 맞춤 결과 PDF

2026-09-08. 사용자 승인된 S1 → S2 → S3 → S4 순서의 마지막 기능 슬라이스.

## 범위와 팀

- Astra: 계약 확정, PDF 의존성/폰트 공급망, 실제 API·브라우저·PDF 시각 검증, 통합 기록.
- Sol: 서버 권한·출력 계약, PDF renderer, endpoint와 단위/매핑 테스트.
- Terra: 기존 AnalyticsPage 결과 요약의 옵션 모달/API 연결, 5 locale UI.
- Luna: 표준 매핑, 구현 후 독립 경계 QA.
- 기존 결과 XLSX/SVG 및 S1/S2/S3는 보존한다. 기존 HR 결과 요약의 맞춤 출력부터 구현한다. 직원별 별도 PDF·발행/공개 정책 변경·자동 발송·외부 배포는 제외한다.

## 설계 게이트

구현 전 `01_backend_contract.md`로 endpoint/options/권한/상한을 고정한다. 직접 PDF 텍스트 렌더링으로 사용자 HTML/JS/URL/첨부 해석 경로를 두지 않는다. 기본 결과는 기존 Analytics DTO를 재사용하며 별도 점수 계산은 하지 않는다. OS 폰트에 의존하지 않는 한글 폰트를 번들한다. 실제 생성 파일의 모든 페이지를 렌더링해 검증한다.

예상 제품 변경 8~12파일에서 조사 후 확정. 폰트 자산·검증 스크립트·문서는 별도 집계한다. S4가 끝나도 전체 종합 게이트는 별도로 확인한다.

## 보존 및 실행 환경

원본 UNC repo와 기존 Windows 검증 사본을 해시 비교한다. 전용 PG55489/API8089만 사용하고 기존 PG5432/5433에는 접근하지 않는다. 운영 Neon/control plane/SMTP/S2S, git commit/push/deploy 및 공통 lib 수정 없음. 원본 dirty 변경을 되돌리지 않는다.
