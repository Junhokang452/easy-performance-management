/* S2 production-asset acceptance. Only the dedicated synthetic loopback API. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const assert = require('node:assert/strict');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const dist = path.resolve(process.env.KPI_DIST || 'frontend-vite/dist');
const output = path.resolve(process.env.KPI_OUTPUT || '_workspace/followups-20260908/s2/browser');
const fixture = JSON.parse(fs.readFileSync(process.env.KPI_FIXTURE || '_workspace/followups-20260908/s2/program-kpi-local.json', 'utf8'));
const { browserProgramId: programId, participantId, goalId, cycleId, kpiAssignmentId, actualCutoffDate } = fixture;
for (const id of [programId, participantId, goalId, cycleId, kpiAssignmentId]) assert.match(id || '', /^[0-9a-f-]{36}$/i);
assert.equal(fixture.completed, true, 'API verification must pass first');
fs.mkdirSync(output, { recursive: true });
const labels = {
  ko: { title: 'KPI 근거 연결', goal: '평가 목표', cycle: 'KPI 주기', cutoff: '실적 마감 기준일', assignment: 'KPI 배정', preview: '근거 미리보기' },
  en: { title: 'KPI evidence linkage', goal: 'Evaluation goal', cycle: 'KPI cycle', cutoff: 'Actual cutoff date', assignment: 'KPI assignment', preview: 'Preview evidence' },
  ja: { title: 'KPI根拠の連携', goal: '評価目標', cycle: 'KPI期間', cutoff: '実績締切基準日', assignment: 'KPI割当', preview: '根拠プレビュー' },
  'zh-CN': { title: '关联KPI依据', goal: '评价目标', cycle: 'KPI周期', cutoff: '实际值截止日', assignment: 'KPI分配', preview: '预览依据' },
  vi: { title: 'Liên kết minh chứng KPI', goal: 'Mục tiêu đánh giá', cycle: 'Chu kỳ KPI', cutoff: 'Ngày chốt số thực tế', assignment: 'Phân công KPI', preview: 'Xem trước minh chứng' },
};
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
const checks = [];
let browser, activePage;
let errors = [];
const historyPath = `/api/v1/evaluation-programs/${programId}/participants/${participantId}/goals/${goalId}/kpi-links`;
function record(name, detail = {}) { checks.push({ case: name, passed: true, ...detail }); }
function watch(page) {
  activePage = page; errors = []; page.setDefaultTimeout(30000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
  page.on('response', res => { if (res.url().includes('/api/') && res.status() >= 400) errors.push(`${res.status()} ${new URL(res.url()).pathname}`); });
}
async function select(page, card, label, text) {
  await card.getByLabel(label, { exact: true }).click();
  await page.getByRole('option').filter({ hasText: text }).first().click();
}
async function login(base, locale, email, width = 1440) {
  const context = await browser.newContext({ viewport: { width, height: width === 390 ? 844 : 1000 }, extraHTTPHeaders: { 'X-Requested-With': 'XMLHttpRequest' } });
  await context.addInitScript(value => localStorage.setItem('easyperformance.locale', value), locale);
  assert.equal((await context.request.post(base + '/api/auth/session/login', { data: { email, password: 'dev' } })).status(), 200);
  return context;
}
async function setup(base, context, locale) {
  const label = labels[locale];
  const goalResponse = await context.request.get(base + `/api/v1/evaluation-programs/participants/${participantId}/goals`);
  assert.equal(goalResponse.status(), 200);
  const goalsPayload = await goalResponse.json();
  const goals = Array.isArray(goalsPayload) ? goalsPayload : goalsPayload.content;
  const goal = goals.find(row => row.id === goalId);
  const cycleResponse = await context.request.get(base + `/api/v1/cycles/${cycleId}`);
  assert.equal(cycleResponse.status(), 200);
  const cycle = await cycleResponse.json();
  const candidates = await (await context.request.get(base + `/api/v1/evaluation-programs/${programId}/participants/${participantId}/kpi-candidates?cycleId=${cycleId}&actualCutoffDate=${actualCutoffDate}&size=100`)).json();
  const candidate = candidates.content.find(row => row.kpiAssignmentId === kpiAssignmentId);
  const page = await context.newPage(); watch(page);
  await page.goto(base + `/admin/evaluation-programs/${programId}/operations`);
  // The synthetic program has one participant, selected by the operations page.
  const heading = page.getByRole('heading', { name: label.title, exact: true });
  await heading.waitFor();
  const card = page.locator('.mantine-Card-root').filter({ has: heading });
  await select(page, card, label.goal, goal.title);
  await select(page, card, label.cycle, cycle.name);
  await card.getByLabel(label.cutoff, { exact: true }).fill(actualCutoffDate);
  await select(page, card, label.assignment, candidate.nodeLabel);
  return { page, card, goal };
}
async function preview(page, card, locale) {
  const response = page.waitForResponse(res => res.url().includes('/kpi-link:preview') && res.status() === 200);
  await card.getByRole('button', { name: labels[locale].preview, exact: true }).click();
  const body = await (await response).json();
  assert.equal(body.row.goalId, goalId); assert.equal(body.row.status, 'READY'); assert.equal(body.row.autoScore, 40);
  return body;
}
(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({ headless: true, ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}) });
  for (const width of [1440, 390]) for (const locale of Object.keys(labels)) {
    console.log(`S2 browser ${locale} ${width}`);
    const context = await login(base, locale, 'dev-hr-admin@performance.dev', width);
    const { page, card } = await setup(base, context, locale);
    await preview(page, card, locale);
    assert.equal(await page.locator('html').getAttribute('lang'), locale);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false, `${locale} ${width} overflow`);
    assert.deepEqual(errors, []);
    await card.screenshot({ path: path.join(output, `kpi-${locale}-${width}.png`) });
    record('localized preview and layout', { locale, width }); await context.close();
  }
  const context = await login(base, 'ko', 'dev-hr-admin@performance.dev');
  const { page, card, goal } = await setup(base, context, 'ko');
  const before = await (await context.request.get(base + historyPath)).json();
  await preview(page, card, 'ko');
  await card.getByRole('button', { name: '근거 저장', exact: true }).click();
  const dialog = page.getByRole('dialog');
  assert.equal(await dialog.getByRole('button', { name: '근거 저장', exact: true }).isDisabled(), true);
  await dialog.getByRole('button', { name: '취소', exact: true }).click();
  assert.equal((await (await context.request.get(base + historyPath)).json()).totalElements, before.totalElements);
  record('reason required; cancelling does not save');
  await card.getByRole('button', { name: '근거 저장', exact: true }).click();
  await dialog.getByLabel(/^연결·갱신 사유/).fill('S2 synthetic browser acceptance');
  const savedResponse = page.waitForResponse(res => res.url().includes('/kpi-link:apply') && res.status() === 200);
  await dialog.getByRole('button', { name: '근거 저장', exact: true }).click();
  const saved = await (await savedResponse).json();
  assert.equal(saved.revision, 3); assert.equal(saved.evidence.autoScore, 40);
  await card.getByText('S2 synthetic browser acceptance', { exact: false }).waitFor();
  assert.deepEqual(errors, []); record('explicit apply and refreshed history');
  await context.close();
  for (const role of ['employee', 'manager']) {
    const reader = await login(base, 'ko', `dev-${role}@performance.dev`);
    const readerPage = await reader.newPage(); watch(readerPage);
    const route = role === 'employee' ? `/evaluations/${programId}` : `/admin/evaluation-programs/${programId}/participants/${participantId}/review/1`;
    await readerPage.goto(base + route);
    const heading = readerPage.getByRole('heading', { name: 'KPI 근거 이력', exact: true }); await heading.waitFor();
    const historyCard = readerPage.locator('.mantine-Card-root').filter({ has: heading });
    await select(readerPage, historyCard, '평가 목표', goal.title);
    await historyCard.getByText('S2 synthetic browser acceptance', { exact: false }).waitFor();
    assert.equal(await readerPage.getByRole('button', { name: '근거 저장', exact: true }).count(), 0);
    assert.deepEqual(errors, []); record('read-only evidence', { role });
    await historyCard.screenshot({ path: path.join(output, `kpi-readonly-${role}.png`) }); await reader.close();
  }
  // Complete real submissions only after visual/read-only checks, using the synthetic program.
  const operator = await login(base, 'ko', 'dev-hr-admin@performance.dev');
  const self = await login(base, 'ko', 'dev-employee@performance.dev');
  const reviewer = await login(base, 'ko', 'dev-manager@performance.dev');
  const participantPath = `/api/v1/evaluation-programs/participants/${participantId}`;
  const linkPath = historyPath.slice(0, -1);
  const selection = { cycleId, actualCutoffDate, kpiAssignmentId };
  async function ok(request) { const response = await request; assert.equal(response.status(), 200, await response.text()); return response.json(); }
  async function completeAs(actor, round) {
    const review = await ok(actor.request.get(base + participantPath + `/review-context?round=${round}`));
    const answers = review.items.map(item => {
      const scale = review.inputScales.find(row => row.id === item.scaleId);
      return { itemId: item.id, ...(scale.kind === 'GRADE' ? { scaleCode: scale.levels[0].code } : { numericScore: 75 }), opinion: 'S2 synthetic freeze acceptance' };
    });
    return ok(actor.request.post(base + participantPath + `/review-submission:complete?round=${round}`, { data: { answers, overallOpinion: 'Synthetic acceptance only' } }));
  }
  await ok(operator.request.post(base + participantPath + '/stage:override', { data: { toStage: 'SELF_REVIEW', toStatus: 'IN_PROGRESS', round: 0, reason: 'S2 synthetic self completion guard' } }));
  assert.equal((await completeAs(self, 0)).status, 'COMPLETED');
  const afterSelf = await ok(operator.request.post(base + linkPath + ':preview', { data: selection }));
  assert.equal(afterSelf.row.status, 'READY'); record('self completion does not freeze KPI evidence');
  await ok(operator.request.post(base + participantPath + '/stage:override', { data: { toStage: 'REVIEW', toStatus: 'IN_PROGRESS', round: 1, reason: 'S2 synthetic reviewer completion guard' } }));
  const beforeCompletion = await ok(operator.request.post(base + linkPath + ':preview', { data: selection }));
  assert.equal((await completeAs(reviewer, 1)).status, 'COMPLETED');
  assert.equal((await operator.request.post(base + linkPath + ':preview', { data: selection })).status(), 409);
  assert.equal((await operator.request.post(base + linkPath + ':apply', { data: { ...selection, previewHash: beforeCompletion.previewHash, reason: 'Must not save after completed review' } })).status(), 409);
  assert.equal((await ok(operator.request.get(base + historyPath))).totalElements, 3);
  record('completed reviewer freezes fresh preview and apply without new revision');
  await operator.close(); await self.close(); await reviewer.close();
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: true, programId, checks }, null, 2));
  console.log(`S2 browser passed: ${checks.length}`);
})().catch(async error => {
  const diagnostic = { errors };
  if (activePage && !activePage.isClosed()) {
    diagnostic.url = activePage.url(); diagnostic.body = await activePage.locator('body').innerText().catch(() => 'unavailable');
    await activePage.screenshot({ path: path.join(output, 'failure.png') }).catch(() => {});
  }
  fs.writeFileSync(path.join(output, 'result.json'), JSON.stringify({ passed: false, error: String(error), checks, diagnostic }, null, 2));
  console.error(error); process.exitCode = 1;
}).finally(async () => { if (browser) await browser.close(); server.close(); });
