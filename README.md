# Easy Performance — 성과평가

기업 성과평가의 관리자·구성원·평가자 업무를 연결하는 시스템입니다. React 19 / Mantine 9 / Spring Boot 4.1.1 / PostgreSQL 기반이며, 인사 원장은 HCM에서 받은 읽기 모델을 사용합니다.

## 운영 DB 구조

운영 대상은 **Neon Model B**입니다. 고객사마다 Neon 프로젝트를 하나 두고, 그 안에 구매한 자매품별 전용 DB를 만듭니다. 성과관리는 해당 고객사의 `performance` DB를 사용합니다(제품 코드 `PERFORMANCE`, 앱 DB 역할 `performance_app`). 테넌트 UUID는 자매품 전체가 공유하고, 공통 관리 DB에는 고객·제품 구독·암호화된 DB 연결 정보를 보관합니다. 실제 평가·목표·결과 테이블은 고객사의 제품 DB에 둡니다.

요청은 로그인 토큰의 테넌트 ID와 제품 코드로 연결할 DB를 결정합니다. 다른 자매품의 데이터는 DB 직접 조회 대신 HCM 읽기 모델과 제품 간 API로 연동합니다. 기준은 `easy-standards/00-principles/13-tenancy-provisioning.md`의 고객사=프로젝트, 제품=DB 규칙입니다.

아래 로컬 DB는 기능·권한·새 스키마 검증용이며, 실제 Neon 고객사 DB 적용 여부와는 구분합니다.

## 로컬 합성 데이터 데모

Java 21, Node.js 20 이상과 프로젝트 의존성이 필요합니다. 저장소에 포함된 공유 라이브러리도 초기화되어 있어야 합니다.

```bash
./scripts/local-demo.sh start
```

브라우저: **http://localhost:5174**

| 역할 | 계정 | 비밀번호 |
|---|---|---|
| HR 운영자 | dev-hr-admin@performance.dev | dev |
| 평가자 | dev-manager@performance.dev | dev |
| 구성원 | dev-employee@performance.dev | dev |
| 본부장 | dev-director@performance.dev | dev |
| 시스템 관리자 | dev-super-admin@performance.dev | dev |

회사 코드는 비워 둡니다. 위 계정은 로컬 합성 데이터 전용입니다.

```bash
./scripts/local-demo.sh status
./scripts/local-demo.sh stop
```

- 새 PostgreSQL 데이터는 `.local-demo/pg`, 로그와 프로세스 정보는 `.local-demo/`에 보존합니다.
- DB 비밀번호와 JWT 키는 첫 실행 때 무작위 생성하여 접근이 제한된 파일에 보관합니다. Git 추적 대상이 아닙니다.
- PostgreSQL이 없다면 Ubuntu 패키지를 사용자 폴더에 다운로드·압축 해제합니다. 시스템 서비스나 기존 DB를 변경하지 않습니다. 다른 환경은 `PERFORMANCE_PG_BIN`으로 PostgreSQL 실행 파일 디렉터리를 지정하세요.
- 사용 포트는 5174, 8087, 55487입니다. 기존 프로세스가 점유하면 시작을 중단합니다.
- `local-demo` 프로필은 외부 Neon 프로비저닝·자매품 송신을 끄고 합성 데이터만 사용합니다. 클라우드 배포와 운영 DB 반영은 이 실행 절차에 포함되지 않습니다.

## 평가 운영 순서

1. **HR 운영자:** 평가 프로그램을 만들고 입력·결과 척도, 조건별 대상 그룹, 실제 평가자 수별 가중치, 공개 정책을 설정합니다.
2. **HR 운영자:** 직원·소속별 참여와 평가자를 배정하고 프로그램을 공개합니다. 공개와 개인별 단계 시작은 구분됩니다.
3. **구성원·목표 합의자:** 목표와 달성 수준을 작성하고 합의 요청 → 승인 또는 반려 → 수정·재요청을 진행합니다.
4. **중간점검 담당자:** 과제 근거와 점검 의견을 저장·완료합니다. 자기신고 방식을 선택하면 중간점검과 중복 운영하지 않습니다.
5. **구성원·평가자:** 자기평가와 배정된 1~3차 평가를 차례로 저장·제출합니다. 이전 차수는 공개 정책에 따라 표시됩니다.
6. **HR 운영자·조정자:** 완료자 계산 → 배정된 조정자 검토·조정 완료 → 최종 피드백 전달로 진행합니다.
7. **구성원·최종 피드백 담당자:** 결과 동의 또는 이의 제기·처리를 완료합니다. 실제 점수 정정은 새 결과 이력으로 남습니다.
8. **HR 운영자:** 평가를 마감하고 대상자별 결과를 공개합니다. 마감 취소 시 공개가 회수되고, 재계산·새 조정·피드백을 거쳐 재마감합니다.

주요 화면은 `/admin/evaluation-programs`(관리), `/evaluations`(내 평가), `/review-queue`(배정된 평가 업무), `/performance-tasks`(성과과제), `/interviews`(면담), `/evaluation-analytics`(분석), `/evaluation-reports`(개인 이력)입니다. 이전 평가 UI는 `/legacy-evaluations`에 보존합니다.

단계 전환은 서버가 완료 조건을 검사합니다. 화면에 내부 직원 UUID를 입력할 필요가 없으며, 내 평가와 평가자 업무는 로그인 계정의 사원 연결 및 배정 정보를 기준으로 조회합니다.

## 검증 및 설계 기록

관리자 32개·구성원 16개 가이드 분석과 전체 구현·검증 기록은 `_workspace/full-cycle-20260907/`, `_workspace/member-cycle-analysis-20260907/`에 보존합니다. 최초 정비 기록은 `_workspace/roothr-20260907/`에 있습니다. 실행 확인은 최종 검증 보고서 기준으로 판단합니다. 다면평가(360)는 `easy-mra`, 직무가치 평가는 `easy-job-management`의 제품 경계를 유지합니다.

화면은 한국어·영어·일본어·중국어 간체·베트남어를 지원합니다. 로그인 화면 또는 상단 언어 메뉴에서 선택하며 새로고침 이후에도 유지됩니다. 모든 언어의 동일 키와 공통 문구 정합성을 `cd frontend-vite && npm run test:i18n`으로 확인할 수 있습니다.

공유 자산은 `@easy/ui-components`의 WorkflowPhaseRail·MasterDetailWorkspace와 평가용 공통 표현 컴포넌트를 사용합니다. 실제 XLSX 읽기/쓰기는 `easy-platform-core`의 SimpleXlsx를 재사용하며, 도메인별 검증과 권한 확인은 성과관리에서 처리합니다. 자산 등록 근거는 `easy-standards/90-conformance/performance-evaluation-shared-assets-2026-09-07.md`입니다.

새 DB 전체 검증은 `bash scripts/verify-fresh-full-cycle.sh`로 실행합니다. 이 명령은 전용 합성 PostgreSQL(55488)/백엔드(8088)를 만들고 검증 후 프로세스를 종료하며, 데이터와 증거는 보존합니다. 이메일은 설정된 SMTP 전송과 앱 알림을 구분하며, SMTP가 없을 때 발송 성공으로 표시하지 않습니다. 설정은 `_workspace/full-cycle-20260907/07_mail_transport.md`를 참고하세요.
