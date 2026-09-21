# S1 표준 갱신 후보 — 로컬 제안만

2026-09-08. easy-standards 원본, ADR 번호, 공통 라이브러리 및 운영 설정은 변경하지 않았다.

1. S2S 사용자 지정 UUID 신규 수신에는 tenant-scoped miss 이후 JPA merge를 금지한다. 전역 PK가 다른 tenant에 존재하면 typed 403, 신규 행은 INSERT-only로 생성하며 충돌 레이스는 전체 트랜잭션을 롤백한다. 근거: PA receiver 및 실제 PostgreSQL 3종 충돌 검증.
2. sourceVersion 비교 후 저장은 단순 READ_COMMITTED만으로 단조성을 보장하지 못한다. 해당 수신 트랜잭션은 REPEATABLE_READ 또는 동등한 충돌 제어를 사용하고 충돌 시 명시적 재시도로 처리한다. 원본 PostgreSQL 시각은 epoch microseconds로 정밀도를 보존한다.
3. opaque S2S bearer는 사용자 JWT가 아니다. raw-body HMAC 검증 이후 signed tenantId/header/배포 allowlist를 일치시키고 ACTIVE 상태의 Model B route를 검증한 뒤 제한된 request context를 설치한다. 기존 context와 route는 종료/실패 시 보존한다. 현재 공유 `setByActiveId`는 ACTIVE를 검사하며 별도 승인 플래그를 검사한다고 주장하지 않는다.
4. 자동 평가라인은 preview fingerprint + 저장된 실행 결과 재사용 + 기존 수동/Excel 경로와 공유하는 프로그램 잠금으로 중복을 제어한다. 기존 평가자가 있으면 참여자 전체를 건너뛰며 기존 multi-reviewer 제약을 넓은 unique index로 덮어쓰지 않는다.

실운영 활성화 시 별도 확인: HCM/PA 동일 tenant 설정, Model B 활성·승인 경로, 단일 HTTPS 발신 origin 및 인프라 egress/DNS 제한, 최소 권한의 실제 자격증명. 이번 로컬 합성 데이터 검증은 이 활성화 검증을 대체하지 않는다.
