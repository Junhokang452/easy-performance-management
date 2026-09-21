import { useRef, useState } from 'react';
import { FormMultiSelect, FormSelect, FormTextInput, UiAlert, UiButton, UiGroup, UiModal, UiStack, UiText } from '@easy/ui-components';
import { useI18n, useT } from '../../../i18n';
import { getErrorMessage } from '../../../api/error';
import { pdfColumns, pdfSections, useCustomPdfMutation, validPdfOptions, type PdfColumn, type PdfSection, type PdfLocale, type PdfOrientation, type CustomPdfRequest } from '../api/customPdf';
import { downloadBlob } from './downloadBlob';

export function CustomResultPdfModal({ programId }: { programId: string }): React.ReactNode {
  const l = useT().program.customPdf;
  const { locale: uiLocale } = useI18n();
  const [opened, setOpened] = useState(false);
  const [title, setTitle] = useState('');
  const [locale, setLocale] = useState<PdfLocale>(uiLocale === 'ko' ? 'ko' : 'en');
  const [orientation, setOrientation] = useState<PdfOrientation>('LANDSCAPE');
  const [sections, setSections] = useState<PdfSection[]>([...pdfSections]);
  const [columns, setColumns] = useState<PdfColumn[]>([...pdfColumns]);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);
  const mutation = useCustomPdfMutation(programId);
  const request: CustomPdfRequest = { locale, orientation, sections,
    participantColumns: sections.includes('PARTICIPANT_TABLE') ? columns : [],
    ...(title.trim() ? { title: title.trim() } : {}),
  };
  const valid = validPdfOptions(request);
  const close = (): void => { if (!inFlight.current) { setOpened(false); setError(null); } };
  const generate = async (): Promise<void> => {
    if (!valid || inFlight.current) return;
    inFlight.current = true; setError(null);
    try {
      const blob = await mutation.mutateAsync(request);
      downloadBlob(blob, `evaluation-results-${programId}.pdf`);
      setOpened(false);
    } catch (failure) {
      setError(getErrorMessage(failure));
    } finally { inFlight.current = false; }
  };
  return <><UiButton variant="light" onClick={() => { setError(null); setOpened(true); }}>{l.action}</UiButton>
    <UiModal opened={opened} onClose={close} title={l.action} centered size="lg"
      closeOnClickOutside={!mutation.isPending} closeOnEscape={!mutation.isPending} withCloseButton={!mutation.isPending}>
      <UiStack gap="md">
        <UiText size="sm" c="dimmed">{l.hint}</UiText>
        <FormTextInput label={l.title} value={title} maxLength={100} disabled={mutation.isPending} onChange={(event) => setTitle(event.currentTarget.value)} />
        <FormSelect label={l.language} value={locale} disabled={mutation.isPending}
          data={[{ value: 'ko', label: l.korean }, { value: 'en', label: l.english }]}
          onChange={(value) => { if (value === 'ko' || value === 'en') setLocale(value); }} />
        <FormSelect label={l.orientation} value={orientation} disabled={mutation.isPending}
          data={[{ value: 'PORTRAIT', label: l.portrait }, { value: 'LANDSCAPE', label: l.landscape }]}
          onChange={(value) => { if (value === 'PORTRAIT' || value === 'LANDSCAPE') setOrientation(value); }} />
        <FormMultiSelect label={l.sections} value={sections} disabled={mutation.isPending}
          data={[{ value: 'SUMMARY', label: l.summary }, { value: 'PARTICIPANT_TABLE', label: l.participants }]}
          onChange={(values) => setSections(pdfSections.filter((section) => values.includes(section)))} />
        {sections.includes('PARTICIPANT_TABLE') ? <FormMultiSelect label={l.columns} value={columns} disabled={mutation.isPending}
          data={pdfColumns.map((value) => ({ value, label: l.columnLabels[value] }))}
          onChange={(values) => setColumns(pdfColumns.filter((column) => values.includes(column)))} /> : null}
        {!valid ? <UiAlert color="yellow">{l.invalidOptions}</UiAlert> : null}
        {error ? <UiAlert color="red" title={l.error}>{error}</UiAlert> : null}
        <UiGroup justify="flex-end">
          <UiButton variant="default" disabled={mutation.isPending} onClick={close}>{l.cancel}</UiButton>
          <UiButton loading={mutation.isPending} disabled={!valid || mutation.isPending} onClick={() => void generate()}>{l.generate}</UiButton>
        </UiGroup>
      </UiStack>
    </UiModal>
  </>;
}
