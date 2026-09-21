/* S3 production assets, local synthetic API only. No email dispatch or scheduler. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const dist = path.resolve(process.env.REMINDER_DIST || 'frontend-vite/dist');
const output = path.resolve(process.env.REMINDER_OUTPUT || '_workspace/followups-20260908/s3/browser');
const source = path.resolve(process.env.REMINDER_SOURCE || 'frontend-vite');
const fixture = JSON.parse(fs.readFileSync(process.env.REMINDER_FIXTURE || '_workspace/followups-20260908/s3/responsible-reminders-local.json', 'utf8'));
assert.equal(fixture.completed, true);
const programId = fixture.browserFixture.program.id;
assert.match(programId, /^[0-9a-f-]{36}$/i);
const api = `/api/v1/evaluation-programs/${programId}/incomplete-reminders`;
const operations = `/admin/evaluation-programs/${programId}/operations`;
fs.mkdirSync(output, { recursive: true });
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2' };
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  if (url.pathname.startsWith('/api/')) {
    const upstream = http.request(new URL(req.url, 'http://127.0.0.1:8089'), { method: req.method, headers: { ...req.headers, host: '127.0.0.1:8089' } }, response => { res.writeHead(response.statusCode, response.headers); response.pipe(res); });
    upstream.on('error', () => { res.writeHead(502); res.end(); }); req.pipe(upstream); return;
  }
  let file = path.resolve(dist, '.' + decodeURIComponent(url.pathname));
  if (!file.startsWith(dist + path.sep) && file !== dist) { res.writeHead(403); res.end(); return; }
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    if (url.pathname.startsWith('/assets/')) { res.writeHead(404); res.end(); return; }
    file = path.join(dist, 'index.html');
  }
  res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }); fs.createReadStream(file).pipe(res);
});
let browser, activePage;
let errors = [];
let labels;
const checks = [];
const record = (name, extra = {}) => checks.push({ case: name, passed: true, ...extra });
function watch(page) {
  activePage = page; errors = []; page.setDefaultTimeout(30000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 400) errors.push(`${response.status()} ${new URL(response.url()).pathname}`); });
}
async function contextFor(base, locale, name, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
  await context.addInitScript(value => localStorage.setItem('easyperformance.locale', value), locale);
  assert.equal((await context.request.post(base + '/api/auth/session/login', { data: { email: `dev-${name}@performance.dev`, password: 'dev' } })).status(), 200);
  return context;
}
async function setup(base, context, locale) {
  const l = labels[locale]; const page = await context.newPage(); watch(page);
  await page.goto(base + operations);
  const heading = page.getByRole('heading', { name: l.title, exact: true }); await heading.waitFor();
  const card = page.locator('.mantine-Card-root').filter({ has: heading });
  for (const participant of Object.values(fixture.browserFixture.participants)) {
    await card.getByLabel(l.participants, { exact: true }).click();
    await page.getByRole('option', { name: participant.employee.name, exact: true }).click();
    await page.keyboard.press('Escape');
  }
  return { page, card };
}
async function preview(page, card, locale) {
  const response = page.waitForResponse(r => r.url().endsWith('/incomplete-reminders:preview') && r.status() === 200);
  await card.getByRole('button', { name: labels[locale].preview, exact: true }).click();
  const body = await (await response).json(); assert.equal(body.programId, programId); return body;
}
(async () => {
  labels = (await import(pathToFileURL(path.join(source, 'src/features/evaluation-programs/responsibleReminderI18n.ts')).href)).responsibleReminderI18n;
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  for (const width of [1440, 390]) for (const locale of Object.keys(labels)) {
    console.log(`S3 browser ${locale} ${width}`);
    const context = await contextFor(base, locale, 'hr-admin', width);
    const { page, card } = await setup(base, context, locale);
    assert.equal((await preview(page, card, locale)).summary.ready, 3);
    assert.equal(await page.locator('html').getAttribute('lang'), locale);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false, `${locale} ${width} horizontal overflow`);
    assert.deepEqual(errors, []);
    await card.screenshot({ path: path.join(output, `reminders-${locale}-${width}.png`) });
    record(`${locale} ${width} preview, translations and overflow`, { width, locale }); await context.close();
  }
  const l = labels.ko;
  const context = await contextFor(base, 'ko', 'hr-admin');
  const { page, card } = await setup(base, context, 'ko');
  await preview(page, card, 'ko');
  const posted = [];
  page.on('request', request => { if (request.method() === 'POST') posted.push({ url: request.url(), body: request.postDataJSON() }); });
  await card.getByRole('button', { name: l.queue, exact: true }).click();
  const dialog = page.getByRole('dialog', { name: l.confirmTitle });
  assert.equal(await dialog.getByRole('button', { name: l.confirm, exact: true }).isDisabled(), true);
  await dialog.getByRole('button', { name: l.cancel, exact: true }).click();
  assert.equal(posted.length, 0); record('blank reason blocked and cancellation sends no request');
  await card.getByRole('button', { name: l.queue, exact: true }).click();
  await dialog.getByLabel(new RegExp('^' + l.reason)).fill('S3 browser manual registration');
  // Server commits successfully but the first response is lost: retry must reuse its key.
  let dropped = false;
  await page.route('**/incomplete-reminders:queue', async route => {
    if (!dropped) { dropped = true; const response = await route.fetch(); assert.equal(response.status(), 200); await route.abort('failed'); }
    else await route.continue();
  });
  await dialog.getByRole('button', { name: l.confirm, exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('[role="dialog"] button[data-loading]'));
  await dialog.getByRole('button', { name: l.confirm, exact: true }).waitFor({ state: 'visible' });
  await page.waitForTimeout(300);
  const replayResponse = page.waitForResponse(r => r.url().endsWith('/incomplete-reminders:queue') && r.status() === 200);
  await dialog.getByRole('button', { name: l.confirm, exact: true }).click();
  const queued = await (await replayResponse).json();
  await dialog.waitFor({ state: 'hidden' });
  const queueRequests = posted.filter(request => request.url.endsWith('/incomplete-reminders:queue'));
  assert.equal(queueRequests.length, 2); assert.deepEqual(queueRequests[0].body, queueRequests[1].body);
  assert.equal(queued.queued, 3);
  const history = await (await context.request.get(base + api)).json(); assert.equal(history.totalElements, 3);
  assert.equal(posted.some(request => /notifications:(dispatch|queue)/.test(request.url)), false);
  record('lost response retry retains exact idempotency request and creates only three IN_APP rows');
  await card.getByText(l.history, { exact: true }).waitFor();
  await card.screenshot({ path: path.join(output, 'reminders-registered.png') });
  const repeated = await preview(page, card, 'ko'); assert.equal(repeated.summary.alreadyQueued, 3);
  assert.equal(await card.getByRole('button', { name: l.queue, exact: true }).isDisabled(), true);
  record('same UTC day shows already registered rows and disables empty queue');
  const directorId = fixture.browserFixture.participants.director.id;
  const overridePath = base + `/api/v1/evaluation-programs/participants/${directorId}/stage:override`;
  assert.equal((await context.request.post(overridePath, { data: { toStage: 'INTERMEDIATE', toStatus: 'IN_PROGRESS', round: 0, reason: 'S3 browser stale fixture' } })).status(), 200);
  assert.equal((await preview(page, card, 'ko')).summary.ready, 1);
  await card.getByRole('button', { name: l.queue, exact: true }).click();
  await dialog.getByLabel(new RegExp('^' + l.reason)).fill('S3 stale preview must not register');
  assert.equal((await context.request.post(overridePath, { data: { toStage: 'SELF_REVIEW', toStatus: 'IN_PROGRESS', round: 0, reason: 'S3 browser source changed' } })).status(), 200);
  const staleResponse = page.waitForResponse(r => r.url().endsWith('/incomplete-reminders:queue') && r.status() === 409);
  await dialog.getByRole('button', { name: l.confirm, exact: true }).click(); await staleResponse;
  await dialog.waitFor({ state: 'hidden' });
  await card.getByText(l.stale, { exact: true }).waitFor();
  assert.equal(await card.getByRole('button', { name: l.queue, exact: true }).isDisabled(), true);
  assert.equal((await (await context.request.get(base + api)).json()).totalElements, 3);
  record('real source change returns 409 and UI requires fresh preview without partial registration');
  await context.close();

  const employeeContext = await contextFor(base, 'ko', 'employee', 390);
  const employeePage = await employeeContext.newPage(); watch(employeePage);
  await employeePage.goto(base + '/evaluation-notifications');
  const notificationHeading = employeePage.getByRole('heading', { name: `[${fixture.browserFixture.program.name}] 미완료 평가 업무 알림`, exact: true });
  await notificationHeading.waitFor();
  const notificationCard = employeePage.locator('.mantine-Card-root').filter({ has: notificationHeading });
  const readResponse = employeePage.waitForResponse(r => /notifications\/[^/]+:read$/.test(r.url()) && r.status() === 200);
  await notificationCard.getByRole('button', { name: '읽음 표시', exact: true }).click(); await readResponse;
  assert.equal(await employeePage.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false, 'recipient inbox mobile overflow');
  await notificationCard.screenshot({ path: path.join(output, 'employee-inbox-read.png') });
  assert.deepEqual(errors, []); record('actual recipient inbox renders reminder and accepts read');
  await employeePage.goto(base + `/evaluations/${programId}`);
  assert.equal(await employeePage.getByRole('heading', { name: l.title, exact: true }).count(), 0);
  record('employee surface has no operator reminder controls'); await employeeContext.close();
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: true, checks, programId }, null, 2));
  console.log(JSON.stringify({ passed: true, checks: checks.length, output }));
})().catch(async error => {
  if (activePage && !activePage.isClosed()) await activePage.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: false, checks, error: String(error), errors }, null, 2));
  console.error(error); process.exitCode = 1;
}).finally(async () => { if (browser) await browser.close(); await new Promise(resolve => server.close(resolve)); });
