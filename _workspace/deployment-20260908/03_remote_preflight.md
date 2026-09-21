# 현재 원격 상태 직접 확인

2026-09-08 Astra. `02_deploy_gate.md`의 과거 기록 기반 미확인 항목 중 아래를 실제 SSH/GitHub API로 확인했다.

## Naver

- 대상101.79.21.163, easyops, hostname vm-naver-20260824101825. 기존 WSL known_hosts 기반 StrictHostKeyChecking=yes 성공. sudo noninteractive 성공.
- WSL exec는 echo조차 응답하지 않아 해당 진단 세션만 취소했다. Windows OpenSSH를 사용했다.
- 원본 PEM은 Windows SSH의 ACL 검사에서 거부되어 원본 변경 없이 현재 사용자 전용 ACL 임시 디렉터리에 사본을 만들었다. 개인키 내용은 출력하지 않았다.
- foundation: control-plane/ware/hcm/time healthy, edge 동작. 다른 제품 재기동 없음.
- performance 컨테이너는 실행/종료 상태 모두0. 기존 easy-lab/performance:2026-08-24 이미지282MB만 존재.
- `/opt/easy-suite/secrets/performance.env` 설정 키0. DB/계정/테넌트가 준비됐다고 볼 수 없다.
- `/opt/easy-suite/src/easy-performance-management`는 소스 snapshot 디렉터리이며 .git 없음.
- 현재 edge의 실제 bind mount는 `/opt/easy-suite/deploy/Caddyfile.apps`다. 기본 Caddyfile과 혼동하지 않는다.
- performance service는 internal10000,768MiB,0.50CPU,performance_data volume, talent/all profile. 전체 talent profile 기동 금지, 서비스만 선택.
- disk 여유71GiB, memory available약12GiB, swap사용0. 자원 증설하지 않음.

서버에 업로드한 파일은 `/tmp/easy-pa-inspect-20260908.py` 한 개의 read-only 진단 스크립트뿐이다. 최초 시스템 Python3.6 호환 오류를 수정했고 메타데이터 재조회 성공. 앱/DB/route/env는 변경하지 않았다.

## GitHub

- WSL gh 설정을 Windows GH_CONFIG_DIR로 읽어 인증 확인. 저장된 Junhokang452 계정은 PA와 공유 저장소 admin/push 권한 보유. 비밀은 프로세스 환경으로만 사용, 로그/소스에 저장하지 않음.
- PA public main 원격/로컬 HEAD 모두1d9d282bfe752ecb77eadea0dd314b2011a1abe1. 보호 branch false, repo hooks0, Actions secrets0.
- 공유 submodule은 private Junhokang452/easy-standards. 현재 제품은 로컬b1654e9 plus dirty 자산에 의존하며 해당 commit은 GitHub에 없음(권한 확인 후 commit422).
- 따라서 PA의 submodule gitlink만 올리면 fresh checkout 빌드가 재현되지 않는다. WorkflowPhaseRail/Boot4 두 기존 로컬commit과 MasterDetailWorkspace/SimpleXlsx 등 필수 변경을 별도 PA용 공유 branch로 좁혀 publish하는 절차가 필요하다. 공유main을 임의로 변경하지 않는다.
- 현재 PA/GitHub/공유repo commit·push0. dirty작업/검증출력/비밀을 git add -A로 묶지 않는다.

## 사용자 선택 대기

easy-pa 최초 개발서버 DB 연결방식: (A) 기존서비스/control-plane을 유지한 격리 테스트DB·새 관리자계정, 또는 (B) LABDEV의 Neon제품DB 및 공유control-plane 연결. B는 control-plane 외부상태 추가설정이 필요하므로 단순 이미지교체로 추정하지 않는다. 질문 전달완료, 선택 전 신규DB/계정/프로비저닝/발송은 수행하지 않는다.
