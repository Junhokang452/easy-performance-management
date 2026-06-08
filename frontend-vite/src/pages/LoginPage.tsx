/**
 * LoginPage — 단계 3 BE-CC-2 JWT 5분리 미진입 stub (단계 4 EC-FE 진입).
 *
 * dev stub login (BE 단계 3 미진입 — AuthProvider 가 BE 호출 실패 시 dev stub fallback).
 * 단계 3+ 격상 시 사용자 + bcrypt 정합 + silent refresh 정합.
 * jobeval `cc1bc03` 패턴 정합.
 */
import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Button,
  Card,
  Center,
  PasswordInput,
  Stack,
  TextInput,
  Title,
  Text,
  Alert,
} from '@mantine/core';
import { useForm } from '@mantine/form';

import { useAuth } from '../auth/AuthProvider';
import { useT } from '../i18n';
import { getErrorMessage } from '../api/error';

interface FromState {
  from?: { pathname?: string };
}

export function LoginPage(): React.ReactNode {
  const navigate = useNavigate();
  const location = useLocation();
  const t = useT();
  const { login } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const form = useForm({
    initialValues: { email: '', password: '' },
    validate: {
      email: (v) => (!v.includes('@') ? '유효한 이메일이 필요합니다' : null),
      password: (v) => (!v ? '비밀번호는 필수입니다' : null),
    },
  });

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
    <Center mih="100vh" p="md">
      <Card shadow="md" padding="xl" radius="md" withBorder w={420}>
        <Stack>
          <div>
            <Title order={3}>{t.domain.app.title}</Title>
            <Text size="sm" c="dimmed">
              {t.domain.app.subtitle}
            </Text>
          </div>
          {error && (
            <Alert color="red" title={t.error.boundary}>
              {error}
            </Alert>
          )}
          <form onSubmit={onSubmit}>
            <Stack>
              <TextInput
                label="Email"
                placeholder="user@example.com"
                required
                {...form.getInputProps('email')}
              />
              <PasswordInput
                label="Password"
                required
                {...form.getInputProps('password')}
              />
              <Button type="submit" loading={submitting}>
                {t.common.action.submit}
              </Button>
            </Stack>
          </form>
        </Stack>
      </Card>
    </Center>
  );
}
