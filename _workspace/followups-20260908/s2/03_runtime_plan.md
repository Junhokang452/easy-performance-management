# S2 실제 런타임 검증 계획

Root 소유: `scripts/verify-program-kpi-local.py`, 이 문서 및 실제 API 산출물. 아직 결과가 아니며 실행 전이다.

전용 Windows 검증 사본/PG55489/API8089만 재사용한다. 기존 PG5432/5433·다른 작업 프로세스는 보호한다. 기존 S1 런타임 제어 스크립트는 그대로 재사용하며 로컬 합성 receiver 자격증명 이외의 운영 설정을 읽지 않는다.

검증 경로:

1. 합성 cycle/tree/node/assignment/actual root→정정 leaf, 프로그램·참여자·합의 목표를 실제 API로 생성.
2. 후보 페이지·명시 cutoff·선택 실적·현재 target override·달성률·추천값 확인. 조직 기준일과 KPI 기준일 차이 검증.
3. 명시 preview/apply/replay/동시 요청·새 revision·이전 근거 불변. 목표 A 갱신 시 목표 B 근거 보존.
4. 원본 target/actual 변경 후 old preview 409, 신규 preview 후 revision 증가, 기존 snapshot 불변.
5. 원본 없거나 target0/미래일/다른employee/다른program/다른tenant는 실패하고 쓰기 없음.
6. HR 쓰기, self/assigned 읽기, 동료 읽기·쓰기 차단. 목표/가중치/target/계산/상태 암묵 변경 없음.
7. reviewer 제출 완료 후 추가 근거 변경 차단. 기존 수동 목표·평가 경로와 S1 회귀 확인.
8. Terra 브라우저 실제 동작·5언어/모바일·사유 확인·stale·read-only 표면; 최종 OpenAPI 재생성 후 타입검증.
9. 최종 JAR hash/source parity 확인 및 전용 프로세스만 종료, 데이터·증거 보존.

테스트 환경 오류와 제품 결함을 구분한다. 성공 코드를 느슨하게 늘려 오류를 가리지 않는다. 실패 시 원인 확인 후 제한된 수정/재실행을 하고 미해결은 보고한다.
