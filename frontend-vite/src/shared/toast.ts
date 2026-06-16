/**
 * 토스트 헬퍼 — 디자인 시스템(@easy/ui-components)의 showToast 재노출.
 *
 * 색상·스타일·위치는 DS 가 소유한다(tone → 색상 매핑은 DS SoT). 앱은 tone 의미값만
 * 전달하므로 DS 의 토스트 디자인 변경이 자동으로 자매품에 전파된다.
 * raw @mantine/notifications 직접 호출은 금지(DS 우회 → 전파 단절).
 * 자매품 정본: easy-talent-management `src/lib/notify.ts`.
 */
export { showToast } from '@easy/ui-components';
export type { ToastOptions, ToastTone } from '@easy/ui-components';
