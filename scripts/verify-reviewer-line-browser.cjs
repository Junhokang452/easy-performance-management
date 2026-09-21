/* Local synthetic S1 reviewer-line UI acceptance against production assets. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const dist = path.resolve(process.env.REVIEWER_LINE_DIST || 'frontend-vite/dist');
const api = new URL(process.env.REVIEWER_LINE_API || 'http://127.0.0.1:8089');
const fixturePath = path.resolve(process.env.REVIEWER_LINE_FIXTURE || '_workspace/followups-20260908/reviewer-line-local.json');
const output = path.resolve(process.env.REVIEWER_LINE_OUTPUT || '_workspace/followups-20260908/browser-reviewer-line');
assert.equal(api.hostname, '127.0.0.1', 'Only the local synthetic API is supported');
assert.ok(fs.existsSync(path.join(dist, 'index.html')), 'Production build missing');
assert.ok(fs.existsSync(fixturePath), 'S1 local verifier JSON missing');
const fixture = JSON.parse(fs.readFileSync(fixturePath, 'utf8'));
const programId = fixture.browserProgramId;
const participantIds = (fixture.browserParticipants || []).map(row => row.id);
assert.match(programId || '', /^[0-9a-f-]{36}$/i, 'browserProgramId is required');
assert.equal(participantIds.length, 2, 'S1 browser fixture must contain exactly two participants');
fs.mkdirSync(output, { recursive: true });

const labels = {
  ko: { title: '평가라인 자동 배정', preview: '자동 배정 미리보기', apply: '미리보기 적용', source: '관계 원본', blocked: '배정 차단', skippedExisting: '기존 평가자 있어 건너뜀', reason: '적용 사유', cancel: '취소' },
  en: { title: 'Automatic reviewer line', preview: 'Preview automatic assignment' },
  ja: { title: '評価ライン自動割当', preview: '自動割当をプレビュー' },
  'zh-CN': { title: '自动分配评价关系', preview: '预览自动分配' },
  vi: { title: 'Phân công tuyến đánh giá tự động', preview: 'Xem trước phân công tự động' },
};
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname.startsWith('/api/')) {
    const upstream = http.request(new URL(req.url, api), { method: req.method, headers: { ...req.headers, host: api.host } }, response => {
      res.writeHead(response.statusCode, response.headers); response.pipe(res);
    });
    upstream.on('error', () => { res.writeHead(502); res.end('Local API unavailable'); });
    req.pipe(upstream); return;
  }
  let file = path.resolve(dist, '.' + decodeURIComponent(url.pathname));
  if (!file.startsWith(dist + path.sep) && file !== dist) { res.writeHead(403); res.end(); return; }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    if (url.pathname.startsWith('/assets/')) { res.writeHead(404); res.end(); return; }
    file = path.join(dist, 'index.html');
  }
  res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

const checks = [];
let browser;
let activePage;
let activeErrors = [];

function record(name, details = {}) { checks.push({ case: name, passed: true, ...details }); }
function participantNameMap(pageRows) {
  return new Map(pageRows.filter(row => participantIds.includes(row.id)).map(row => [row.id, row.employee.name]));
}
async function selectParticipants(page, card, names) {
  const input = card.locator('input:visible').first();
  for (const name of names) {
    await input.click();
    const option = page.getByRole('option').filter({ hasText: name }).first();
    await option.waitFor();
    await option.click();
  }
}
async function preview(page, card, label) {
  const button = card.getByRole('button', { name: label.preview, exact: true });
  await button.click({ trial: true });
  const response = page.waitForResponse(item => item.url().includes('/reviewer-line:preview') && item.status() === 200);
  await button.click();
  const received = await response;
  await received.finished();
  return received.json();
}
function attachErrors(page, errors) {
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('requestfailed', request => {
    if (request.url().includes('/api/') && request.failure()?.errorText !== 'net::ERR_ABORTED') errors.push(`Failed ${new URL(request.url()).pathname}: ${request.failure()?.errorText}`);
  });
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 400) errors.push(`${response.status()} ${new URL(response.url()).pathname}`);
  });
}

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  for (const width of [1440, 390]) for (const [locale, label] of Object.entries(labels)) {
    console.log(`Checking reviewer-line UI: ${locale} ${width}px`);
    const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
    await context.addInitScript(value => localStorage.setItem('easyperformance.locale', value), locale);
    assert.equal((await context.request.post(base + '/api/auth/session/login', { data: { email: 'dev-hr-admin@performance.dev', password: 'dev' } })).status(), 200, 'HR login');
    const pageRows = (await (await context.request.get(base + `/api/v1/evaluation-programs/${programId}/participants?page=0&size=100`)).json()).content;
    const names = participantIds.map(id => participantNameMap(pageRows).get(id));
    assert.ok(names.every(Boolean), 'Fixture participants must resolve to names');
    const page = await context.newPage();
    activePage = page; activeErrors = [];
    page.setDefaultTimeout(60000); attachErrors(page, activeErrors);
    await page.goto(base + `/admin/evaluation-programs/${programId}/operations`);
    const heading = page.getByRole('heading', { name: label.title, exact: true });
    await heading.waitFor();
    const card = page.locator('.mantine-Card-root').filter({ has: heading });
    await card.scrollIntoViewIfNeeded();
    await selectParticipants(page, card, names);
    const result = await preview(page, card, label);
    assert.equal(result.summary.ready, 1, 'Fixture has one READY preview row');
    assert.equal(result.summary.blocked, 1, 'Fixture has one BLOCKED preview row');
    if (locale === 'ko') {
      await card.getByText(new RegExp(`^${label.source}:`)).first().waitFor();
      await card.getByText(label.blocked, { exact: true }).waitFor();
    }
    assert.equal(await page.locator('html').getAttribute('lang'), locale);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false, `${locale} ${width}px overflow`);
    assert.deepEqual(activeErrors, [], 'Browser/API errors');
    await card.screenshot({ path: path.join(output, `reviewer-line-${locale}-${width}.png`) });
    record('locale preview and layout', { locale, width, ready: result.summary.ready, blocked: result.summary.blocked });
    await context.close();
  }

  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
  await context.addInitScript(() => localStorage.setItem('easyperformance.locale', 'ko'));
  assert.equal((await context.request.post(base + '/api/auth/session/login', { data: { email: 'dev-hr-admin@performance.dev', password: 'dev' } })).status(), 200, 'HR apply-flow login');
  const pageRows = (await (await context.request.get(base + `/api/v1/evaluation-programs/${programId}/participants?page=0&size=100`)).json()).content;
  const names = participantIds.map(id => participantNameMap(pageRows).get(id));
  const page = await context.newPage(); activePage = page; activeErrors = [];
  page.setDefaultTimeout(60000); attachErrors(page, activeErrors);
  await page.goto(base + `/admin/evaluation-programs/${programId}/operations`);
  const heading = page.getByRole('heading', { name: labels.ko.title, exact: true }); await heading.waitFor();
  const card = page.locator('.mantine-Card-root').filter({ has: heading }); await card.scrollIntoViewIfNeeded();
  await selectParticipants(page, card, names);
  const firstPreview = await preview(page, card, labels.ko);
  assert.equal(firstPreview.summary.ready, 1); assert.equal(firstPreview.summary.blocked, 1);
  await card.getByRole('button', { name: labels.ko.apply, exact: true }).click();
  const dialog = page.getByRole('dialog'); await dialog.waitFor();
  const dialogApply = dialog.getByRole('button', { name: labels.ko.apply, exact: true });
  assert.equal(await dialogApply.isDisabled(), true, 'Apply is disabled without a reason');
  await dialog.getByRole('button', { name: labels.ko.cancel, exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(await dialog.isVisible(), false, 'Closing confirmation applies nothing');
  record('reason required and confirmation close has no mutation');
  const freshPreview = await preview(page, card, labels.ko);
  await card.getByRole('button', { name: labels.ko.apply, exact: true }).click();
  const applyDialog = page.getByRole('dialog'); await applyDialog.getByLabel(labels.ko.reason, { exact: true }).fill('S1 browser explicit apply');
  const appliedResponse = page.waitForResponse(item => item.url().includes('/reviewer-line:apply') && item.status() === 200);
  await applyDialog.getByRole('button', { name: labels.ko.apply, exact: true }).click();
  const applied = await (await appliedResponse).json();
  assert.equal(applied.applied, 1, 'Exactly one ready reviewer is applied');
  assert.equal(applied.skipped, 1, 'Exactly one blocked reviewer is skipped');
  record('fresh preview applies one and skips one');
  const afterApply = await preview(page, card, labels.ko);
  assert.equal(afterApply.summary.skippedExisting, 1, 'Applied reviewer is skipped by re-preview');
  await card.getByText(labels.ko.skippedExisting, { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false, 'apply flow overflow');
  assert.deepEqual(activeErrors, [], 'Apply flow browser/API errors');
  await card.screenshot({ path: path.join(output, 'reviewer-line-ko-apply.png') });
  await context.close();

  const employee = await browser.newContext({ extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
  assert.equal((await employee.request.post(base + '/api/auth/session/login', { data: { email: 'dev-employee@performance.dev', password: 'dev' } })).status(), 200, 'Employee login');
  assert.equal((await employee.request.post(base + `/api/v1/evaluation-programs/${programId}/reviewer-line:preview`, { data: { participantIds, roles: ['REVIEWER'] } })).status(), 403, 'Employee preview is denied');
  const employeePage = await employee.newPage();
  await employeePage.goto(base + `/admin/evaluation-programs/${programId}/operations`);
  await employeePage.waitForLoadState('networkidle');
  assert.equal(await employeePage.getByRole('heading', { name: labels.ko.title, exact: true }).count(), 0, 'Employee reviewer-line card absent');
  record('employee API denied and reviewer-line UI absent');
  await employee.close();

  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: true, programId, participantIds, checks }, null, 2));
  console.log(`Reviewer-line browser checks passed: ${checks.length}`);
})().catch(async error => {
  const diagnostic = { browserErrors: activeErrors };
  if (activePage && !activePage.isClosed()) {
    diagnostic.url = activePage.url();
    diagnostic.body = await activePage.locator('body').innerText({ timeout: 5000 }).catch(() => 'unavailable');
    await activePage.screenshot({ path: path.join(output, 'failure.png'), timeout: 15000 }).catch(() => {});
  }
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: false, programId, participantIds, error: String(error), checks, diagnostic }, null, 2));
  console.error(error); process.exitCode = 1;
}).finally(async () => { if (browser) await browser.close(); server.close(); });
