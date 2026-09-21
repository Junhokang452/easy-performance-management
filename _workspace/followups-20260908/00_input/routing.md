# 라우팅

주 변경: easy-performance-management backend(program/readmodel/sync 필요 범위) + frontend-vite(features/evaluation-programs).
추가 변경(범위 확인 후): easy-hcm의 신규 수동 송신기/스냅샷/권한 테스트 및 신규 endpoint 감사 연결. lib/easy-platform의 공유 표준·HTTP/UI/PDF 자산은 읽기 전용.

Sol은 PA backend, Terra는 PA frontend를 독점 소유한다. Root는 HCM 송신 코드와 테스트, 계약/진행 기록 및 통합 검증 스크립트, Luna는 표준/QA 보고서를 소유한다. 서로의 변경을 되돌리지 않는다.
공유 lib·HCM 송신 계약 변경이 불가피한 경우 먼저 상호 호환성과 파일 소유권을 확정하고, 외부 시스템 연결은 실행하지 않는다.
