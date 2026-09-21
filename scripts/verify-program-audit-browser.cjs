/* Local synthetic audit UI acceptance against a real API and production assets. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const dist = path.resolve(process.env.AUDIT_DIST || 'frontend-vite/dist');
const api = new URL(process.env.AUDIT_API || 'http://127.0.0.1:8089');
assert.equal(api.hostname, '127.0.0.1', 'Only the local synthetic API is supported');
const output = path.resolve(process.env.AUDIT_OUTPUT || '_workspace/5240-evaluation-20260908/browser');
const programId = process.env.AUDIT_PROGRAM_ID;
assert.match(programId || '', /^[0-9a-f-]{36}$/i, 'AUDIT_PROGRAM_ID is required');
assert.ok(fs.existsSync(path.join(dist, 'index.html')), 'Production build missing');
fs.mkdirSync(output, { recursive: true });
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname.startsWith('/api/')) {
    const upstream = http.request(new URL(req.url, api), { method: req.method, headers: { ...req.headers, host: api.host } }, r => {
      res.writeHead(r.statusCode, r.headers); r.pipe(res);
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
const labels = {
  ko: ['운영 감사 이력', '평가 생성', '이전', '다음', '새로고침', '전체'],
  en: ['Operation audit history', 'Evaluation created', 'Previous', 'Next', 'Refresh', 'All'],
  ja: ['運用監査履歴', '評価作成', '前へ', '次へ', '更新'],
  'zh-CN': ['操作审计记录', '创建评价', '上一页', '下一页', '刷新'],
  vi: ['Nhật ký kiểm toán vận hành', 'Tạo kỳ đánh giá', 'Trước', 'Sau', 'Làm mới'],
};
const checks = [];
let browser;
let activePage;
let activeErrors = [];
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  for (const width of [1440, 390]) for (const [locale, text] of Object.entries(labels)) {
    console.log(`Checking audit UI: ${locale} ${width}px`);
    const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
    await context.addInitScript(value => localStorage.setItem('easyperformance.locale', value), locale);
    const login = await context.request.post(base + '/api/auth/session/login', { data: { email: 'dev-hr-admin@performance.dev', password: 'dev' } });
    assert.equal(login.status(), 200, 'Admin login');
    const page = await context.newPage();
    activePage = page;
    page.setDefaultTimeout(60000);
    const errors = [];
    activeErrors = errors;
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
    page.on('requestfailed', request => {
      if (request.url().includes('/api/') && request.failure()?.errorText !== 'net::ERR_ABORTED') errors.push(`Failed ${new URL(request.url()).pathname}: ${request.failure()?.errorText}`);
    });
    page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 400) errors.push(`${response.status()} ${new URL(response.url()).pathname}`); });
    await page.goto(base + `/admin/evaluation-programs/${programId}/operations`);
    const heading = page.getByRole('heading', { name: text[0], exact: true });
    await heading.waitFor();
    const card = page.locator('.mantine-Card-root').filter({ has: heading });
    await card.scrollIntoViewIfNeeded();
    const refresh = card.getByRole('button', { name: text[4], exact: true });
    // Returning to a fresh query key can use the shared 60-second cache. Force
    // one explicit refresh after each UI action rather than requiring a cache miss.
    const refreshAudit = async (matches = () => true) => {
      await refresh.click({ trial: true });
      const response = page.waitForResponse(r => r.url().includes('/audit-events') && r.status() === 200 && matches(new URL(r.url())));
      await refresh.click();
      const received = await response;
      await received.finished();
      return received;
    };
    const data = await (await refreshAudit()).json();
    assert.ok(data.content.length > 0, 'Audit fixture has records');
    if (locale === 'ko' && width === 1440) {
      assert.ok(data.totalPages > 1, 'Fixture must exercise pagination');
      await card.getByRole('button', { name: text[3], exact: true }).click();
      assert.equal((await (await refreshAudit(u => u.searchParams.get('page') === '1')).json()).number, 1);
      await card.getByText('2 / 2', { exact: true }).waitFor();
      await card.getByText('행위자: 직원 연결 정보 없음', { exact: true }).waitFor();
      await card.getByText(/· 평가 전체$/).first().waitFor();
      await card.screenshot({ path: path.join(output, 'audit-null-fallback-ko-1440.png') });
      checks.push({ case: 'null actor and participant labels', passed: true });
      await card.getByRole('button', { name: text[2], exact: true }).click();
      await refreshAudit(u => u.searchParams.get('page') === '0');
      await card.getByText('1 / 2', { exact: true }).waitFor();
      await card.locator('input:visible').first().click();
      await page.getByRole('option', { name: text[1], exact: true }).click();
      const filtered = await (await refreshAudit(u => u.searchParams.get('eventType') === 'PROGRAM_CREATED')).json();
      assert.ok(filtered.content.length > 0 && filtered.content.every(row => row.eventType === 'PROGRAM_CREATED'));
      checks.push({ case: 'pagination and event filter', passed: true });
      await card.locator('input:visible').first().click();
      await page.getByRole('option', { name: text[5], exact: true }).click();
      await refreshAudit(u => !u.searchParams.has('eventType'));
      await card.getByRole('button', { name: text[3], exact: true }).click();
      assert.equal((await (await refreshAudit(u => u.searchParams.get('page') === '1')).json()).number, 1);
      await card.locator('input:visible').nth(1).click();
      const participantOption = page.getByRole('option').filter({ hasNotText: /^전체$/ }).first();
      assert.ok(await participantOption.count(), 'Fixture must include a selectable participant');
      await participantOption.click();
      const participantResponse = await refreshAudit(u => !!u.searchParams.get('participantId'));
      const selectedParticipant = new URL(participantResponse.url()).searchParams.get('participantId');
      const participantRows = await participantResponse.json();
      assert.equal(participantResponse.status(), 200);
      assert.equal(participantRows.number, 0);
      assert.ok(participantRows.content.length > 0 && participantRows.content.every(row => row.participantId === selectedParticipant));
      checks.push({ case: 'participant filter and page reset', passed: true });
      await card.locator('input:visible').nth(1).click();
      await page.getByRole('option', { name: text[5], exact: true }).click();
      await refreshAudit(u => !u.searchParams.has('participantId'));
    }
    assert.equal(await page.locator('html').getAttribute('lang'), locale);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2);
    assert.equal(overflow, false, `${locale} ${width}px overflow`);
    assert.deepEqual(errors, [], 'Browser/API errors');
    if ((locale === 'ko' && width === 390) || (locale === 'en' && width === 1440)) {
      await heading.evaluate(element => window.scrollTo(0, window.scrollY + element.getBoundingClientRect().top - 88));
      await page.screenshot({ path: path.join(output, `audit-viewport-${locale}-${width}.png`) });
    }
    await card.screenshot({ path: path.join(output, `audit-${locale}-${width}.png`) });
    checks.push({ locale, width, records: data.content.length, overflow, passed: true });
    await context.close();
  }
  const employee = await browser.newContext({ extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
  assert.equal((await employee.request.post(base + '/api/auth/session/login', { data: { email: 'dev-employee@performance.dev', password: 'dev' } })).status(), 200);
  assert.equal((await employee.request.get(base + `/api/v1/evaluation-programs/${programId}/audit-events`)).status(), 403);
  const employeePage = await employee.newPage();
  await employeePage.goto(base + `/admin/evaluation-programs/${programId}/operations`);
  await employeePage.waitForLoadState('networkidle');
  assert.equal(await employeePage.getByRole('heading', { name: labels.ko[0], exact: true }).count(), 0);
  checks.push({ case: 'employee API denied and audit UI absent', passed: true });
  await employee.close();
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: true, checks }, null, 2));
  console.log(`Audit browser checks passed: ${checks.length}`);
})().catch(async error => {
  const diagnostic = { browserErrors: activeErrors };
  if (activePage && !activePage.isClosed()) {
    diagnostic.url = activePage.url();
    diagnostic.body = await activePage.locator('body').innerText({ timeout: 5000 }).catch(() => 'unavailable');
    await activePage.screenshot({ path: path.join(output, 'failure.png'), timeout: 15000 }).catch(() => {});
  }
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: false, error: String(error), checks, diagnostic }, null, 2));
  console.error(error); process.exitCode = 1;
}).finally(async () => { if (browser) await browser.close(); server.close(); });
