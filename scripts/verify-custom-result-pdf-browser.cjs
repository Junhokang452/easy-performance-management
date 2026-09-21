/* S4 production frontend and real local PDF API. No live service or mail. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const dist = path.resolve(process.env.PDF_DIST || 'frontend-vite/dist');
const source = path.resolve(process.env.PDF_SOURCE || 'frontend-vite');
const output = path.resolve(process.env.PDF_OUTPUT || '_workspace/followups-20260908/s4/browser');
const fixture = JSON.parse(fs.readFileSync(process.env.PDF_FIXTURE || '_workspace/followups-20260908/s4/pdf-local.json', 'utf8'));
assert.equal(fixture.completed, true);
const program = fixture.browserFixture;
const endpoint = `/api/v1/evaluation-programs/${program.id}/results.pdf`;
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
let browser, activePage, labels, base;
const checks = [], consoleErrors = [];
const record = (name, extra = {}) => checks.push({ case: name, passed: true, ...extra });
async function contextFor(locale, width = 1440, role = 'hr-admin') {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
  await context.addInitScript(value => localStorage.setItem('easyperformance.locale', value), locale);
  assert.equal((await context.request.post(base + '/api/auth/session/login', { data: { email: `dev-${role}@performance.dev`, password: 'dev' } })).status(), 200);
  return context;
}
async function setup(context, locale) {
  const page = await context.newPage(); activePage = page; page.setDefaultTimeout(30000);
  page.on('pageerror', error => consoleErrors.push(error.message));
  await page.goto(base + '/evaluation-analytics');
  const picker = page.getByRole('combobox').first();
  await picker.fill(program.name);
  await page.getByRole('option', { name: `2026 · ${program.name}`, exact: true }).click();
  const action = page.getByRole('button', { name: labels[locale].action, exact: true }); await action.waitFor();
  await action.click();
  const modal = page.getByRole('dialog'); await modal.waitFor();
  return { page, modal, action };
}
async function download(page, modal, locale) {
  const responsePromise = page.waitForResponse(r => r.url().endsWith(endpoint));
  const downloadPromise = page.waitForEvent('download');
  await modal.getByRole('button', { name: labels[locale].generate, exact: true }).click();
  const response = await responsePromise;
  if (response.status() !== 200) throw new Error(`PDF HTTP ${response.status()}: ${await response.text()}`);
  assert.equal(response.request().headers().accept, '*/*', 'Binary PDF success and JSON errors must both be acceptable');
  const file = await downloadPromise;
  assert.equal(file.suggestedFilename(), `evaluation-results-${program.id}.pdf`);
  const bytes = await response.body(); assert.equal(bytes.subarray(0, 5).toString(), '%PDF-');
  assert.equal(response.headers()['content-type'], 'application/pdf');
  return { bytes: bytes.length, payload: response.request().postDataJSON() };
}
(async () => {
  labels = (await import(pathToFileURL(path.join(source, 'src/features/evaluation-programs/customPdfI18n.ts')).href)).customPdfI18n;
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  for (const width of [1440, 390]) for (const locale of Object.keys(labels)) {
    console.log(`S4 browser ${locale} ${width}`);
    const context = await contextFor(locale, width);
    const { page, modal } = await setup(context, locale);
    assert.ok(!(await modal.innerText()).includes('program.customPdf.'));
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), true);
    await page.screenshot({ path: path.join(output, `${locale}-${width}.png`), fullPage: true, animations: 'disabled' });
    const result = await download(page, modal, locale);
    assert.ok(['ko', 'en'].includes(result.payload.locale));
    assert.equal(result.payload.orientation, 'LANDSCAPE');
    assert.equal(result.payload.participantColumns.length, 8);
    record(`${locale} ${width} actual PDF download`, { bytes: result.bytes });
    await context.close();
  }
  {
    const context = await contextFor('ko'); const { page, modal, action } = await setup(context, 'ko');
    let posts = 0; page.on('request', request => { if (request.url().endsWith(endpoint)) posts++; });
    await modal.getByRole('button', { name: labels.ko.cancel, exact: true }).click();
    await modal.waitFor({ state: 'hidden' }); assert.equal(posts, 0); record('cancel sends no PDF request');
    await action.click();
    await modal.getByLabel(labels.ko.orientation, { exact: true }).click();
    await page.getByRole('option', { name: labels.ko.portrait, exact: true }).click();
    assert.equal(await modal.getByRole('button', { name: labels.ko.generate, exact: true }).isDisabled(), true);
    record('portrait does not silently drop selected columns');
    await modal.getByLabel(labels.ko.orientation, { exact: true }).click();
    await page.getByRole('option', { name: labels.ko.landscape, exact: true }).click();
    await modal.getByLabel(labels.ko.title, { exact: true }).fill('😀');
    const rejected = page.waitForResponse(r => r.url().endsWith(endpoint) && r.status() === 422);
    await modal.getByRole('button', { name: labels.ko.generate, exact: true }).click(); await rejected;
    await modal.getByRole('alert').waitFor(); assert.equal(await modal.isVisible(), true);
    record('actual unsupported glyph 422 stays visible and recoverable');
    await modal.getByLabel(labels.ko.title, { exact: true }).fill('수정 후 정상 출력');
    await download(page, modal, 'ko'); record('corrected input retries to real PDF');
    await context.close();
  }
  {
    const context = await contextFor('ko', 1440, 'employee'); const page = await context.newPage(); activePage = page;
    await page.goto(base + '/evaluations');
    await page.waitForLoadState('networkidle');
    assert.equal(await page.getByRole('button', { name: labels.ko.action, exact: true }).count(), 0);
    assert.equal((await context.request.post(base + endpoint, { data: { locale: 'ko', orientation: 'LANDSCAPE', sections: ['SUMMARY'], participantColumns: [] } })).status(), 403);
    record('employee surface no PDF action and direct request 403'); await context.close();
  }
  assert.deepEqual(consoleErrors, []);
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ completed: true, checks, consoleErrors }, null, 2));
  console.log(JSON.stringify({ completed: true, passed: checks.length }));
})().catch(async error => {
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ completed: false, checks, consoleErrors, error: String(error) }, null, 2));
  if (activePage && !activePage.isClosed()) await activePage.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
  console.error(error); process.exitCode = 1;
}).finally(async () => { if (browser) await browser.close(); server.close(); });
