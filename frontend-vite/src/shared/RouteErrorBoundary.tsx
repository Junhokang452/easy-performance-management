/**
 * RouteErrorBoundary — STD-FE-ERROR-BOUNDARY 정합 (단계 4 강화).
 *
 * 라우트 경계 last-resort 안전망. throw 발생 시 root unmount cascade를 차단하고
 * fallback UI 노출. `key={location.pathname}` 으로 라우트 전환 시 상태 리셋.
 *
 * easy-standards `00-principles/07-frontend.md` §6 STD-FE-ERROR-BOUNDARY 정합.
 * 자매품 공통 패턴 — easy-job-structure 박제 케이스 (2026-06-05).
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { Component, type ReactNode } from 'react';
import { Stack, Text } from '@easy/ui-components/mantine';
import { UiAlert, UiButton } from '@easy/ui-components';

interface Props {
  children: ReactNode;
  resetKey?: string;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class RouteErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidUpdate(prevProps: Props): void {
    if (prevProps.resetKey !== this.props.resetKey && this.state.hasError) {
      this.setState({ hasError: false, error: null });
    }
  }

  componentDidCatch(error: Error, info: { componentStack?: string | null }): void {
    // eslint-disable-next-line no-console
    console.error('[RouteErrorBoundary]', error, info.componentStack);
  }

  reset = (): void => {
    this.setState({ hasError: false, error: null });
  };

  override render(): ReactNode {
    if (this.state.hasError) {
      return (
        <UiAlert color="red" title="페이지 렌더링 오류" m="md">
          <Stack gap="xs">
            <Text size="sm">
              {this.state.error?.message ?? '알 수 없는 오류가 발생했습니다.'}
            </Text>
            <UiButton variant="light" size="xs" onClick={this.reset}>
              다시 시도
            </UiButton>
          </Stack>
        </UiAlert>
      );
    }
    return this.props.children;
  }
}
