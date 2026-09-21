const { chromium } = require('/home/samsung/.cache/ms-playwright-go/1.57.0/package');
const fs = require('fs');

const TARGET_URL = process.env.TARGET_URL || 'http://127.0.0.1:5174';
const CHROME = '/home/samsung/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome';
const SHOTS = '/home/samsung/code/easy-performance-management/_workspace/framework-20260907/screenshots';
const runName = process.env.RESUME_CYCLE || `UI 전체흐름 ${Date.now().toString().slice(-6)}`;

async function login(browser, personaLabel, errors) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`));
  page.on('response', (response) => {
    if (response.status() >= 400) errors.push(`http ${response.status()}: ${response.url()}`);
  });
  await page.goto(`${TARGET_URL}/login`, { waitUntil: 'networkidle' });
  await page.getByText(personaLabel, { exact: true }).click();
  await Promise.all([
    page.waitForResponse((response) => response.url().includes('/auth/session/login') && response.ok()),
    page.getByRole('button', { name: /로그인|Sign in/ }).click(),
  ]);
  await page.waitForURL((url) => !url.pathname.endsWith('/login'));
  await page.waitForLoadState('networkidle');
  return { context, page };
}

async function selectMantine(page, label, optionText) {
  await page.getByRole('combobox', { name: label }).click();
  await page.getByRole('option', { name: optionText }).click();
}

async function clickMutation(page, buttonName, endpoint) {
  const button = page.getByRole('button', { name: buttonName }).last();
  await Promise.all([
    page.waitForResponse((response) => response.url().includes(endpoint) && response.request().method() !== 'GET' && response.ok(), { timeout: 15000 }),
    button.click(),
  ]);
  await page.waitForLoadState('networkidle');
}

async function openCycle(page, name) {
  await page.goto(TARGET_URL, { waitUntil: 'networkidle' });
  await selectMantine(page, /평가 사이클|Evaluation cycle/, name);
  await page.waitForLoadState('networkidle');
  await page.getByRole('heading', { name }).waitFor({ timeout: 15000 });
}

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const browser = await chromium.launch({ headless: false, executablePath: CHROME, slowMo: 35, args: ['--no-sandbox'] });
  const errors = [];
  const actors = [];
  try {
    const hr = await login(browser, 'HR', errors); actors.push(hr);
    const employee = await login(browser, '구성원', errors); actors.push(employee);
    const manager = await login(browser, '매니저', errors); actors.push(manager);

    if (process.env.SCREEN_ONLY) {
      await manager.page.setViewportSize({ width: 390, height: 844 });
      await openCycle(manager.page, runName);
      await manager.page.getByText('신규 고객 유지율 92% 달성', { exact: true }).waitFor({ timeout: 15000 });
      await manager.page.screenshot({ path: `${SHOTS}/workspace-full-mobile.png`, fullPage: true });
      const bodyWidth = await manager.page.evaluate(() => document.body.scrollWidth);
      if (bodyWidth > 390) errors.push(`mobile overflow ${bodyWidth}px`);
      console.log('PASS stable mobile render', bodyWidth);
      return;
    }

    if (!process.env.RESUME_CYCLE) {
    await hr.page.getByRole('link', { name: /사이클 관리|Cycles/ }).click();
    await hr.page.getByRole('button', { name: /사이클 생성|Create cycle/ }).click();
    await hr.page.getByLabel(/이름|Name/).fill(runName);
    await hr.page.getByLabel(/시작일|Start date/).fill('2026-07-01');
    await hr.page.getByLabel(/종료일|End date/).fill('2026-12-31');
    await hr.page.getByLabel(/평가 정책|Evaluation Policy/).check();
    await clickMutation(hr.page, /^추가$|^Create$/, '/api/v1/cycles');
    await hr.page.getByText(runName, { exact: true }).waitFor();
    console.log('PASS HR cycle create', runName);

    await openCycle(hr.page, runName);
    const subject = hr.page.getByRole('combobox', { name: /평가 대상자|Review subjects/ });
    await subject.fill('DEMO-001');
    await hr.page.getByRole('option', { name: /김나리.*DEMO-001/ }).click();
    const reviewer = hr.page.getByRole('combobox', { name: /1차 평가자|Primary reviewer/ });
    await reviewer.fill('DEMO-002');
    await hr.page.getByRole('option', { name: /박민서.*DEMO-002/ }).click();
    await clickMutation(hr.page, /배정 저장|Save assignments/, '/participants');
    console.log('PASS server-search roster assignment');

    await hr.page.getByRole('button', { name: /평가 열기|Open evaluation/ }).click();
    await clickMutation(hr.page, /평가 열기|Open evaluation/, '/open');
    await hr.page.screenshot({ path: `${SHOTS}/workspace-full-01-hr-open.png`, fullPage: true });
    console.log('PASS HR open');

    await openCycle(employee.page, runName);
    await employee.page.getByLabel(/^목표$|^Goal$/).fill('신규 고객 유지율 92% 달성');
    await employee.page.getByLabel(/성공 기준|Success criteria/).fill('월별 유지율을 측정하고 이탈 고객 원인을 개선합니다.');
    await employee.page.getByLabel(/가중치|Weight/).fill('100');
    await employee.page.getByLabel(/목표 수치|Target value/).fill('92');
    await employee.page.getByLabel(/^단위$|^Unit$/).fill('%');
    await clickMutation(employee.page, /목표 저장|Save goal/, '/goals');
    await clickMutation(employee.page, /승인 요청|Request approval/, '/submit');
    await employee.page.screenshot({ path: `${SHOTS}/workspace-full-02-employee-goal.png`, fullPage: true });
    console.log('PASS employee goal create + numeric hydration + submit');

    await openCycle(manager.page, runName);
    await manager.page.getByLabel(/목표 검토 의견|Goal decision comment/).fill('측정 기준과 목표 수치를 확인했습니다.');
    await clickMutation(manager.page, /^승인$|^Approve$/, '/decision');
    console.log('PASS manager goal approve');

    await openCycle(hr.page, runName);
    await clickMutation(hr.page, /다음 단계.*중간점검|Next stage.*Check-in/, '/advance');
    console.log('PASS HR advance check-in');

    await openCycle(employee.page, runName);
    await employee.page.getByLabel(/기준일|As-of date/).fill('2026-09-07');
    await employee.page.getByLabel(/실적 수치|Actual value/).fill('88');
    await employee.page.getByLabel(/진행률|Progress/).fill('96');
    await employee.page.getByLabel(/진행 내용|Progress note/).fill('유지율 88%를 달성했고 이탈 원인을 정리했습니다.');
    await employee.page.getByLabel(/근거 링크|Evidence link/).fill('https://example.com/evidence/retention');
    await clickMutation(employee.page, /실적 기록|Save progress/, '/check-ins');
    await employee.page.getByLabel(/^진행 상황$|^Progress summary$/).fill('목표 대비 96% 진행했습니다.');
    await employee.page.getByLabel(/달성한 성과|Achievements/).fill('이탈 고객 인터뷰와 개선안을 완료했습니다.');
    await employee.page.getByLabel(/막힌 점|Blockers/).fill('데이터 반영 주기가 늦습니다.');
    await employee.page.getByRole('textbox', { name: '필요한 지원', exact: true }).fill('주간 데이터 갱신을 요청합니다.');
    await clickMutation(employee.page, /관리자에게 제출|Submit to manager/, '/intermediate-review/submit');
    await employee.page.screenshot({ path: `${SHOTS}/workspace-full-03-employee-checkin.png`, fullPage: true });
    console.log('PASS employee actual check-in + intermediate submit');

    await openCycle(manager.page, runName);
    await manager.page.getByLabel(/중간점검 피드백|Check-in feedback/).fill('진행 상황이 좋습니다. 데이터 지원을 연결하겠습니다.');
    await clickMutation(manager.page, /중간점검 완료|Complete check-in/, '/intermediate-review/complete');
    await manager.page.screenshot({ path: `${SHOTS}/workspace-full-04-manager-checkin.png`, fullPage: true });
    console.log('PASS manager intermediate save + complete');
    }

    await openCycle(hr.page, runName);
    await clickMutation(hr.page, /다음 단계.*자기평가|Next stage.*Self review/, '/advance');
    await openCycle(employee.page, runName);
    await employee.page.getByLabel(/성과와 다음 성장 계획|Achievements and next growth plan/).fill('고객 이탈 원인을 구조화했고 다음 반기에는 자동 경보를 만들겠습니다.');
    await clickMutation(employee.page, /^제출$|^Submit$/, '/self/submit');
    console.log('PASS employee self review submit');

    await openCycle(hr.page, runName);
    await clickMutation(hr.page, /다음 단계.*팀 평가|Next stage.*Team review/, '/advance');
    await openCycle(manager.page, runName);
    await manager.page.getByLabel(/관리자 점수|Manager score/).fill('88');
    await manager.page.getByLabel(/평가 의견|Review comment/).fill('목표 달성 과정과 개선 활동을 높게 평가합니다.');
    await clickMutation(manager.page, /팀 평가 제출|Submit team review/, '/manager/submit');
    await manager.page.screenshot({ path: `${SHOTS}/workspace-full-05-manager-score.png`, fullPage: true });
    console.log('PASS manager score + submit');

    await openCycle(hr.page, runName);
    await clickMutation(hr.page, /다음 단계.*보정|Next stage.*Calibration/, '/advance');
    await clickMutation(hr.page, /보정 세션 만들기|Create session/, '/calibration-sessions');
    await clickMutation(hr.page, /분포 적용|Apply distribution/, '/calibration/apply');
    await clickMutation(hr.page, /보정 확정|Confirm calibration/, '/calibration/confirm');
    await clickMutation(hr.page, /결과 발행|Publish results/, '/reports/publish');
    await hr.page.screenshot({ path: `${SHOTS}/workspace-full-06-calibration-publish.png`, fullPage: true });
    console.log('PASS calibration create + apply + confirm + publish');

    await openCycle(manager.page, runName);
    await manager.page.getByLabel(/결과 피드백|Feedback comment/).fill('최종 결과와 다음 성장 과제를 함께 확인했습니다.');
    await clickMutation(manager.page, /피드백 완료|Complete feedback/, '/feedback/complete');
    console.log('PASS manager feedback complete');

    await openCycle(employee.page, runName);
    await clickMutation(employee.page, /결과 확인|Acknowledge result/, '/acknowledge');
    await clickMutation(employee.page, /피드백 수용|Accept feedback/, '/accept');
    await employee.page.screenshot({ path: `${SHOTS}/workspace-full-07-employee-result.png`, fullPage: true });
    console.log('PASS employee acknowledge + accept');

    await openCycle(hr.page, runName);
    await clickMutation(hr.page, /평가 마감|Close evaluation/, '/close');
    await hr.page.screenshot({ path: `${SHOTS}/workspace-full-08-closed.png`, fullPage: true });
    console.log('PASS HR close');

    await manager.page.setViewportSize({ width: 390, height: 844 });
    await openCycle(manager.page, runName);
    await manager.page.screenshot({ path: `${SHOTS}/workspace-full-mobile.png`, fullPage: true });
    const bodyWidth = await manager.page.evaluate(() => document.body.scrollWidth);
    if (bodyWidth > 390) errors.push(`mobile overflow ${bodyWidth}px`);
    console.log('PASS mobile final render', bodyWidth);

    const unexpected = [...new Set(errors)].filter((entry) => !entry.includes('/favicon') && !entry.includes('/api/auth/session'));
    if (unexpected.length) {
      console.error('BROWSER_ERRORS', JSON.stringify(unexpected, null, 2));
      process.exitCode = 1;
    }
  } catch (error) {
    console.error('FAIL', error);
    for (const [index, actor] of actors.entries()) {
      await actor.page.screenshot({ path: `${SHOTS}/workspace-full-failure-${index}.png`, fullPage: true }).catch(() => {});
    }
    process.exitCode = 1;
  } finally {
    for (const actor of actors) await actor.context.close().catch(() => {});
    await browser.close();
  }
})();
