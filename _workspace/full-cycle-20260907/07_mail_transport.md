# 평가 알림 전송 계약

- 프로그램 알림함/outbox는 제품 도메인에 영속화한다.
- 구체 SMTP 어댑터: `com.easyperformance.mail.PerformanceSmtpMailSender`.
- 공통 플랫폼에 기존 mail sender 구현이 없어 제품의 ProgramMailSender seam에 얇은 어댑터를 둔다. 다른 자매품도 같은 전송 계약이 확인되면 공통 추출 후보가 된다. 평가자/직원 조회는 공유 전송 라이브러리에 넣지 않는다.
- 기본은 비활성. 구성되지 않은 이메일을 SENT로 보고하지 않는다.
- 명시 dispatch는 저장된 알림을 전송하고 성공/실패를 따로 기록한다. DB 트랜잭션 롤백으로 이미 전송된 메일 이력을 지우지 않는다.
- 외부 SMTP 전송은 이번 검증에서 실행하지 않는다. 테스트는 loopback의 임시 SMTP 서버로만 수행한다.

설정:

| Property / environment | 기본 | 의미 |
|---|---|---|
| performance.notifications.smtp.enabled / PERFORMANCE_NOTIFICATIONS_SMTP_ENABLED | false | 실제 전송 사용 |
| performance.notifications.smtp.host / PERFORMANCE_NOTIFICATIONS_SMTP_HOST | 빈 값 | SMTP 서버 |
| performance.notifications.smtp.port / PERFORMANCE_NOTIFICATIONS_SMTP_PORT | 587 | 포트 |
| performance.notifications.smtp.from / PERFORMANCE_NOTIFICATIONS_SMTP_FROM | 빈 값 | 발신 주소 |
| performance.notifications.smtp.username / PERFORMANCE_NOTIFICATIONS_SMTP_USERNAME | 빈 값 | 인증 사용자 |
| performance.notifications.smtp.password / PERFORMANCE_NOTIFICATIONS_SMTP_PASSWORD | 빈 값 | 비밀값, 로그/저장소에 기록하지 않음 |
| performance.notifications.smtp.start-tls / PERFORMANCE_NOTIFICATIONS_SMTP_START_TLS | true | STARTTLS 필수 |
| performance.notifications.smtp.ssl / PERFORMANCE_NOTIFICATIONS_SMTP_SSL | false | 직접 TLS 사용 시 true |

수신 주소는 동일 테넌트의 employeeId에 연결된 활성 user_account에서 조회한다. 활성 이메일이 없거나 복수의 서로 다른 주소가 연결되어 있으면 임의 선택 없이 실패 처리한다. 본문은 일반 텍스트 UTF-8, 제목/주소의 헤더 개행은 거부한다. 연결 5초, 읽기/쓰기 10초 상한이다.
