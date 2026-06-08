/**
 * AppHeaderActions — 다크모드 토글 + 언어 토글 (단계 4 강화).
 *
 * Mantine v9 `useMantineColorScheme()` 으로 light/dark 전환.
 * `useI18n()` 로 ko/en 전환.
 * jobeval 단계 4 cutover `cc1bc03` 패턴 정합.
 */
import { ActionIcon, Group, SegmentedControl, useMantineColorScheme, Tooltip } from '@mantine/core';
import { IconMoon, IconSun } from '@tabler/icons-react';

import { useI18n } from '../i18n';

export function AppHeaderActions(): React.ReactNode {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const { locale, setLocale, t } = useI18n();
  const isDark = colorScheme === 'dark';

  return (
    <Group gap="xs">
      <SegmentedControl
        size="xs"
        value={locale}
        onChange={(v) => setLocale(v as 'ko' | 'en')}
        data={[
          { label: 'KO', value: 'ko' },
          { label: 'EN', value: 'en' },
        ]}
      />
      <Tooltip label={t.common.label.darkMode}>
        <ActionIcon
          variant="subtle"
          aria-label={t.common.label.darkMode}
          onClick={() => toggleColorScheme()}
        >
          {isDark ? <IconSun size={18} /> : <IconMoon size={18} />}
        </ActionIcon>
      </Tooltip>
    </Group>
  );
}
