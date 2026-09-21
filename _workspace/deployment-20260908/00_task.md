# easy-pa GitHub 및 Naver 개발서버 반영

2026-09-08 사용자 명시 승인: 완료한 easy-pa 보완을 GitHub와 Naver 개발서버에 반영.

대상 GitHub: Junhokang452/easy-performance-management, 현재 main. 원격 접근은 WSL gh 설정을 Windows gh에서 읽으며 비밀값을 출력하지 않는다.
대상 서버: AI Testbed Naver vm-naver-20260824101825 (101.79.21.163), easyops, /opt/easy-suite. SSH known_hosts 검증 성공.

현재 foundation만 동작(control-plane/ware/hcm/time/edge). performance 이미지는 있으나 미기동. 따라서 단순 교체가 아니라 최초 활성화 조건(DB/계정/라우팅)이 충족되는지 먼저 점검한다. 다른 제품 변경/재기동 및 공유 control-plane 변경은 이번 배포에 자동 포함하지 않는다.

라우팅: PA 제품소스·빌드·배포, GitHub publish, Naver performance 서비스. 공유lib/HCM dirty 변경은 소유/의존 확인 전 commit/push 금지. 기존 _workspace 이력 보존, 새 deployment 하위에서 증거 분리.

팀: Astra 원격점검·배포조율, Sol Git/의존/파일범위 preflight, Luna 표준·롤백·검증게이트. 사용자 지정 Sol/Terra/Luna 모델 정책 유지. 실제 필요 없는 제품개발 위임은 추가하지 않는다.

게이트: repository secret/artifact 검토 → 검증된 source/submodule ref 확인 → 서버 current backup/DB범위/rollback 확정 → GitHub 반영 → exact artifact 배포 → 로그인/SPA/API/PDF 및 기존서비스 smoke. 새로운 유료자원/Neon프로비저닝 등 별도선택이 필요하면 중단·확인.
