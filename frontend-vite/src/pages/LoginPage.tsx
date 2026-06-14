/**
 * LoginPage — 인증 격상 (실 사용자 + bcrypt, 2026-06-12).
 *
 * BE `/api/auth/login` 실 호출 (AuthProvider — dev stub fallback 폐기).
 * 페르소나 SegmentedControl 5종 (store-hr/time "데모 계정 체험" 패턴) — 선택 시
 * DevAccountSeeder 시드 계정(email + pw 'dev') 자동 입력 (시더 게이트 ON 환경 전용 편의).
 */
import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Button,
  Card,
  Group,
  PasswordInput,
  SegmentedControl,
  Stack,
  TextInput,
  Title,
  Text,
  Alert,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconChartBar, IconLock, IconLogin2, IconMail } from '@tabler/icons-react';
import { LoginBrandMark, LoginVisualShell } from '@easy/ui-components';

import { useAuth } from '../auth/AuthProvider';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';
import { loginVisual } from './loginVisual';

interface FromState {
  from?: { pathname?: string };
}

/** DevAccountSeeder 5 페르소나 (pw 'dev') — BE com.easyperformance.security.DevAccountSeeder 정합. */
const PERSONA_EMAIL: Record<string, string> = {
  SUPER_ADMIN: 'dev-super-admin@performance.dev',
  HR_ADMIN: 'dev-hr-admin@performance.dev',
  DIRECTOR: 'dev-director@performance.dev',
  MANAGER: 'dev-manager@performance.dev',
  EMPLOYEE: 'dev-employee@performance.dev',
};

export function LoginPage(): React.ReactNode {
  const navigate = useNavigate();
  const location = useLocation();
  const t = useT();
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [persona, setPersona] = useState<string | null>(null);

  const form = useForm({
    initialValues: { email: '', password: '' },
    validate: {
      email: (v) => (!v.includes('@') ? '유효한 이메일이 필요합니다' : null),
      password: (v) => (!v ? '비밀번호는 필수입니다' : null),
    },
  });

  const applyPersona = (value: string): void => {
    setPersona(value);
    const email = PERSONA_EMAIL[value];
    if (email) {
      form.setValues({ email, password: 'dev' });
    }
  };

  const onSubmit = form.onSubmit(async (values) => {
    setSubmitting(true);
    setError(null);
    try {
      await login(values);
      const fromState = (location.state as FromState | null) ?? null;
      const target = fromState?.from?.pathname ?? '/';
      navigate(target, { replace: true });
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <LoginVisualShell image={loginVisual.image}>
      <Card shadow="md" padding="xl" bg="color-mix(in srgb, var(--easy-color-surface) 90%, transparent)">
        <Stack gap="lg">
          <Group gap="sm" align="flex-start">
            <LoginBrandMark size={32} radius={8}>
              <IconChartBar size={18} />
            </LoginBrandMark>
            <Stack gap={0}>
              <Title order={3}>{t.domain.app.title}</Title>
              <Text size="xs" c="dimmed">
                {t.domain.app.subtitle}
              </Text>
            </Stack>
          </Group>
          <Stack gap={4}>
            <Text size="xs" c="dimmed">
              {t.login.personaLabel}
            </Text>
            <SegmentedControl
              fullWidth
              size="xs"
              value={persona ?? ''}
              onChange={applyPersona}
              data={[
                { label: t.login.persona.superAdmin, value: 'SUPER_ADMIN' },
                { label: t.login.persona.hrAdmin, value: 'HR_ADMIN' },
                { label: t.login.persona.director, value: 'DIRECTOR' },
                { label: t.login.persona.manager, value: 'MANAGER' },
                { label: t.login.persona.employee, value: 'EMPLOYEE' },
              ]}
            />
            <Text size="xs" c="dimmed">
              {t.login.personaHint}
            </Text>
          </Stack>
          {error && (
            <Alert color="red" title={t.error.boundary}>
              {error}
            </Alert>
          )}
          <form onSubmit={onSubmit}>
            <Stack>
              <TextInput
                label={t.login.emailLabel}
                placeholder="user@example.com"
                required
                leftSection={<IconMail size={16} />}
                autoComplete="username"
                {...form.getInputProps('email')}
              />
              <PasswordInput
                label={t.login.passwordLabel}
                required
                leftSection={<IconLock size={16} />}
                autoComplete="current-password"
                {...form.getInputProps('password')}
              />
              <Button type="submit" color="brand" loading={submitting} leftSection={<IconLogin2 size={16} />}>
                {t.login.submit}
              </Button>
            </Stack>
          </form>
        </Stack>
      </Card>
    </LoginVisualShell>
  );
}
