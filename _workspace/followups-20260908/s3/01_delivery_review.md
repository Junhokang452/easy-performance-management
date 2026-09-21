# S3 기존 전달 경로 안전검토 (Astra)

## 현재 동작

`ProgramNotificationService.queue`는 IN_APP이면 ProgramNotification을 SENT 및 sentAt으로 저장한다. 기존 알림함의 tenant+recipient 페이지 조회와 읽음 처리가 이 행을 소비한다. 즉 저장 트랜잭션 완료는 내부 알림함 노출을 뜻하며, 사용자의 실제 열람 또는 업무 완료는 별개다.

EMAIL은 adapter 설정에 따라 READY/CONFIG_REQUIRED로 저장한다. `dispatch`는 프로그램의 EMAIL READY 전체를 가져와 claim/send/status 업데이트를 수행한다. 따라서 기존 dispatch를 S3 후속 단계로 호출하면 선택한 독려와 무관한 이메일도 나갈 수 있다.

## S3 경계

- S3 전용 요청은 IN_APP 고정. channel 수신/이메일 옵션/dispatch 호출을 추가하지 않는다.
- 기존 generic 알림 preview/queue/dispatch는 변경하지 않는다.
- 서버가 계산한 활성 책임자만 수신자로 사용한다. UI가 주입하는 recipientEmployeeIds를 수용하지 않는다.
- 명시 preview→사유 확인→내부 알림 등록. 쓰기 시 현재 업무·책임자·완료 상태 재검증과 DB unique 기반 중복 억제 필요.
- 감사에 행위자/프로그램/선택 범위/사유/등록·차단 건수를 기록하되 평가 점수나 자유서술 평가 내용은 알림에 포함하지 않는다.
- 미배정·비활성 수신자·운영자에게만 가능한 단계는 임의 상사/전체 직원으로 대체하지 않는다.

정확한 단계 책임자·dedupe 기간/업무 식별자·응답 계약은 Sol 계약 및 Luna Phase3a 게이트에서 고정한다.

## 실제 검증 계획

전용 PG55489/API8089의 합성 프로그램과 dev 직원 계정만 사용. 서로 다른 stage/assignment/submission 상태를 만든 뒤 수신자 정확성·완료제외·미배정·권한·tenant-object 경계·동시 요청·재시도·stale·알림함 읽음 소유권·기존 상태 불변을 확인한다. 전용 runtime 이외 DB/SMTP/발신 설정은 접근하지 않는다. 최종 JAR과 source parity를 확인한 뒤 시작하고 완료 시 소유 프로세스만 종료한다.
