/**
 * Suite header actions: compact language menu, matching ware/hcm chrome.
 */
import { Group, Menu, useMantineColorScheme } from '@easy/ui-components/mantine';
import { IconCheck, IconLanguage, IconMoon, IconSun } from '@tabler/icons-react';

import { useI18n } from '../i18n';
import { LOCALE_LABELS, SUPPORTED_LOCALES } from '../i18n/locales';
import { UiActionIcon, UiTooltip } from '@easy/ui-components';

export function AppHeaderActions(): React.ReactNode {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const { locale, setLocale, t } = useI18n();
  const isDark = colorScheme === 'dark';

  return (
    <Group gap="xs" wrap="nowrap">
      <Menu position="bottom-end" shadow="md" width={180}>
        <Menu.Target>
          <UiActionIcon variant="subtle" aria-label={t.common.label.language} title={LOCALE_LABELS[locale]}>
            <IconLanguage size={18} />
          </UiActionIcon>
        </Menu.Target>
        <Menu.Dropdown>
          <Menu.Label>{t.common.label.language}</Menu.Label>
          {SUPPORTED_LOCALES.map((language) => (
            <Menu.Item key={language} onClick={() => setLocale(language)}
              rightSection={language === locale ? <IconCheck size={14} aria-hidden /> : undefined}>
              {LOCALE_LABELS[language]}
            </Menu.Item>
          ))}
        </Menu.Dropdown>
      </Menu>
      <UiTooltip label={t.common.label.darkMode}>
        <UiActionIcon
          variant="subtle"
          visibleFrom="sm"
          aria-label={t.common.label.darkMode}
          onClick={() => toggleColorScheme()}
        >
          {isDark ? <IconSun size={18} /> : <IconMoon size={18} />}
        </UiActionIcon>
      </UiTooltip>
    </Group>
  );
}
