/**
 * AdminTenantsPage — /admin/tenants (SUPER_ADMIN 전용).
 *
 * 시스템 관리자 — control plane 테넌트 lifecycle (목록/생성/재시도/일시중지/재개).
 *
 * BE: `/api/admin/tenants` (SystemAdminTenantsController, hasAuthority('SUPER_ADMIN')).
 * create/retry 202 + PROVISIONING 행 — `useTenants` refetchInterval 5s 폴링으로
 * ACTIVE/FAILED 전이 추적.
 *
 * 게이트 OFF (단일 DB 개발) → BE 503 → isError → 게이트 OFF 안내 카드.
 * store-hr `f8df3db` AdminTenantsPage 사본 (performance i18n ko/en 정합).
 */
import { useState } from 'react';

import {
  Alert,
  Badge,
  Code,
  Group,
  Loader,
  Modal,
  Stack,
  Table,
  Text,
  Title,
} from '@easy/ui-components/mantine';
import {
  DangerButton,
  EasyTooltip,
  FormActions,
  FormTextInput,
  PrimaryButton,
  SecondaryButton,
  SubtleButton,
  SurfaceCard,
} from '@easy/ui-components';

import {
  useCreateTenant,
  useTenantAction,
  useTenants,
  type PlatformTenant,
} from '../api/adminTenants';
import { useT } from '../i18n';

const DEFAULT_REGION = 'aws-ap-southeast-1';

const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'green',
  PROVISIONING: 'blue',
  FAILED: 'red',
  SUSPENDED: 'gray',
};

function StatusBadge({ status }: { status?: string | null }): React.ReactNode {
  const value = status ?? 'UNKNOWN';
  return (
    <Group gap={6} wrap="nowrap">
      <Badge color={STATUS_COLOR[value] ?? 'gray'} variant="light">
        {value}
      </Badge>
      {value === 'PROVISIONING' && <Loader size="xs" />}
    </Group>
  );
}

function TenantRowActions({ tenant }: { tenant: PlatformTenant }): React.ReactNode {
  const t = useT();
  const retry = useTenantAction('retry');
  const suspend = useTenantAction('suspend');
  const resume = useTenantAction('resume');

  if (tenant.status === 'FAILED') {
    return (
      <DangerButton
        size="compact-xs"
        loading={retry.isPending}
        onClick={() => retry.mutate(tenant.id)}
      >
        {t.adminTenants.action.retry}
      </DangerButton>
    );
  }
  if (tenant.status === 'ACTIVE') {
    return (
      <SubtleButton
        size="compact-xs"
        loading={suspend.isPending}
        onClick={() => suspend.mutate(tenant.id)}
      >
        {t.adminTenants.action.suspend}
      </SubtleButton>
    );
  }
  if (tenant.status === 'SUSPENDED') {
    return (
      <PrimaryButton
        size="compact-xs"
        loading={resume.isPending}
        onClick={() => resume.mutate(tenant.id)}
      >
        {t.adminTenants.action.resume}
      </PrimaryButton>
    );
  }
  return null; // PROVISIONING — 폴링이 상태 전이를 추적
}

function CreateTenantModal({
  opened,
  onClose,
}: {
  opened: boolean;
  onClose: () => void;
}): React.ReactNode {
  const t = useT();
  const create = useCreateTenant();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [region, setRegion] = useState(DEFAULT_REGION);
  const [adminUsername, setAdminUsername] = useState('');
  const [adminEmail, setAdminEmail] = useState('');

  const valid =
    code.trim() !== '' &&
    name.trim() !== '' &&
    region.trim() !== '' &&
    adminUsername.trim() !== '' &&
    /.+@.+\..+/.test(adminEmail.trim());

  const submit = (): void => {
    create.mutate(
      {
        code: code.trim(),
        name: name.trim(),
        region: region.trim(),
        adminUsername: adminUsername.trim(),
        adminEmail: adminEmail.trim(),
      },
      {
        onSuccess: () => {
          setCode('');
          setName('');
          setRegion(DEFAULT_REGION);
          setAdminUsername('');
          setAdminEmail('');
          onClose();
        },
      },
    );
  };

  return (
    <Modal opened={opened} onClose={onClose} title={t.adminTenants.createTitle} centered>
      <Stack gap="sm">
        <Text size="xs" c="dimmed">
          {t.adminTenants.createHint}
        </Text>
        <FormTextInput
          label={t.adminTenants.field.code}
          placeholder="ACME"
          required
          value={code}
          onChange={(e) => setCode(e.currentTarget.value.toUpperCase())}
        />
        <FormTextInput
          label={t.adminTenants.field.name}
          placeholder="acme-company"
          required
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <FormTextInput
          label={t.adminTenants.field.region}
          required
          value={region}
          onChange={(e) => setRegion(e.currentTarget.value)}
        />
        <FormTextInput
          label={t.adminTenants.field.adminUsername}
          placeholder="admin"
          required
          value={adminUsername}
          onChange={(e) => setAdminUsername(e.currentTarget.value)}
        />
        <FormTextInput
          label={t.adminTenants.field.adminEmail}
          placeholder="admin@acme.com"
          required
          value={adminEmail}
          onChange={(e) => setAdminEmail(e.currentTarget.value)}
        />
        {create.isError && (
          <Alert color="red" variant="light">
            {t.adminTenants.createFailed}
          </Alert>
        )}
        <FormActions
          secondary={<SecondaryButton onClick={onClose}>{t.adminTenants.action.cancel}</SecondaryButton>}
          primary={
            <PrimaryButton onClick={submit} disabled={!valid} loading={create.isPending}>
              {t.adminTenants.action.create}
            </PrimaryButton>
          }
        />
      </Stack>
    </Modal>
  );
}

export function AdminTenantsPage(): React.ReactNode {
  const t = useT();
  const { data: tenants, isLoading, isError } = useTenants();
  const [createOpened, setCreateOpened] = useState(false);

  return (
    <Stack gap="md">
      <Stack gap={2}>
        <Title order={3}>{t.adminTenants.title}</Title>
        <Text size="sm" c="dimmed">
          {t.adminTenants.description}
        </Text>
      </Stack>

      {isError && (
        <SurfaceCard p="md">
          <Stack gap="xs">
            <Text fw={500} size="sm">
              {t.adminTenants.gateOffTitle}
            </Text>
            <Text size="xs" c="dimmed">
              {t.adminTenants.gateOffBody}
            </Text>
            <Code fz="xs">APP_NEON_MULTITENANCY_ENABLED=true</Code>
          </Stack>
        </SurfaceCard>
      )}

      {!isError && (
        <Group justify="flex-end">
          <PrimaryButton size="xs" onClick={() => setCreateOpened(true)}>
            {t.adminTenants.create}
          </PrimaryButton>
        </Group>
      )}

      {isLoading && (
        <Group justify="center" p="xl">
          <Loader />
        </Group>
      )}

      {!isLoading && !isError && (tenants?.length ?? 0) === 0 && (
        <SurfaceCard p="xl">
          <Stack gap="xs" align="center">
            <Text fw={500}>{t.adminTenants.empty}</Text>
            <Text size="xs" c="dimmed">
              {t.adminTenants.emptyHint}
            </Text>
          </Stack>
        </SurfaceCard>
      )}

      {!isLoading && !isError && (tenants?.length ?? 0) > 0 && (
        <SurfaceCard p={0}>
          <Table striped highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>{t.adminTenants.column.code}</Table.Th>
                <Table.Th>{t.adminTenants.column.name}</Table.Th>
                <Table.Th>{t.adminTenants.column.status}</Table.Th>
                <Table.Th>{t.adminTenants.column.region}</Table.Th>
                <Table.Th>{t.adminTenants.column.neonProject}</Table.Th>
                <Table.Th>{t.adminTenants.column.admin}</Table.Th>
                <Table.Th>{t.adminTenants.column.createdAt}</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {tenants!.map((tenant) => (
                <Table.Tr key={tenant.id}>
                  <Table.Td>
                    <Text fw={600} size="sm">
                      {tenant.code}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{tenant.name}</Text>
                  </Table.Td>
                  <Table.Td>
                    {tenant.status === 'FAILED' && tenant.lastError ? (
                      <EasyTooltip label={tenant.lastError} multiline maw={360}>
                        <span>
                          <StatusBadge status={tenant.status} />
                        </span>
                      </EasyTooltip>
                    ) : (
                      <StatusBadge status={tenant.status} />
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {tenant.region ?? '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Code fz="xs">{tenant.neonProjectId ?? '—'}</Code>
                  </Table.Td>
                  <Table.Td>
                    <Text size="xs">{tenant.adminUsername ?? '—'}</Text>
                    <Text size="xs" c="dimmed">
                      {tenant.adminEmail ?? ''}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {tenant.createdAt ? new Date(tenant.createdAt).toLocaleDateString() : '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <TenantRowActions tenant={tenant} />
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </SurfaceCard>
      )}

      <CreateTenantModal opened={createOpened} onClose={() => setCreateOpened(false)} />
    </Stack>
  );
}
