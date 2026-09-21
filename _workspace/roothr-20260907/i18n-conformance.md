# 자매품 5개 언어 정합 검증

## 적용 기준

- SoT: `easy-standards/00-principles/07-frontend.md`의 5-locale 동시 갱신과 `17-i18n-label-conventions.md`의 공통 문구.
- 언어: 한국어(ko), 영어(en), 일본어(ja), 중국어 간체(브라우저 zh-CN / 공유 번들 zh_CN), 베트남어(vi).
- 기존 2-locale 설정 및 잘못된 zh-TW 확장 주석 제거.

## 구현

- 각 언어 928개 키. `ko.ts`의 `typeof ko`가 단일 타입 스키마이며, 느슨한 `workspace.copy: Record<string,string>`를 제거했다.
- 일본어·중국어·베트남어 모두 완전한 자체 번역 사전을 제공한다. 영어나 한국어 번들을 상속하여 지원 언어로 표시하지 않는다.
- 공통 동작·상태·로딩·저장 결과·이메일 라벨 14개는 `@easy/i18n-common/locales/*`를 직접 참조한다. 제품 고유 평가 문구는 제품 사전에 둔다.
- easy-ware와 같은 메뉴 방식으로 언어를 선택한다. 로그인 화면과 모바일에서도 접근할 수 있다. 선택을 저장하고 문서의 `lang`을 갱신한다.
- 날짜 선택기와 날짜/시각 표시를 선택 언어에 연결했다.
- 로그인 입력 검증, 오류 화면, 평가 단계 차단 사유도 번역한다. 서버 영어 오류 메시지를 그대로 화면에 노출하는 fallback을 제거했다.
- 사용자가 작성한 이름·평가 제목·코멘트 등 실제 데이터는 번역하거나 변경하지 않는다.

## 검증

- `npm run test:i18n`: 7/7 통과. 전체 키 누락·추가, 빈 문구, placeholder 불일치, 한국어 잔존, 긴 영문 복사 및 일/중 혼합 영문, 언어 코드 정규화, 오류 번역을 검사한다.
- `npm run build`: 실제 TypeScript 프로젝트 검사와 Vite 빌드 통과.
- 실제 브라우저 15/15: 각 5언어 로그인 라벨/입력 오류/새로고침 유지, 로그인 이후 평가 화면 전환, 모바일 메뉴 표시와 가로 넘침 확인. page error 0.
- 결과: `i18n-browser-result.json`; 화면: `screenshots/i18n-zh-CN-mobile.png`, `screenshots/i18n-vi-mobile.png`.
