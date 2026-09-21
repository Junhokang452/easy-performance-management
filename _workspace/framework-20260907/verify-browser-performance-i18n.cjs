const fs = require('node:fs');
const assert = require('node:assert/strict');
const { chromium } = require('/home/samsung/.cache/ms-playwright-go/1.57.0/package');
const ROOT = '/home/samsung/code/easy-performance-management';
const BASE = 'http://localhost:5174';
const names = {ko:'한국어',en:'English',ja:'日本語','zh-CN':'中文（简体）',vi:'Tiếng Việt'};
(async()=>{
 const dictionaries={};
 for(const [locale, file, symbol] of [['ko','ko','ko'],['en','en','en'],['ja','ja','ja'],['zh-CN','zh-CN','zhCN'],['vi','vi','vi']]){
  dictionaries[locale]=(await import(`${ROOT}/frontend-vite/src/i18n/${file}.ts`))[symbol];
 }
 const browser=await chromium.launch({headless:false,executablePath:'/home/samsung/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome',args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:1440,height:960}});
 const page=await context.newPage(); const errors=[]; const checks=[];
 page.on('pageerror',e=>errors.push(e.message));
 let current='ko';
 async function change(locale){
  await page.getByRole('button',{name:dictionaries[current].common.label.language,exact:true}).click();
  for(const label of Object.values(names)) await page.getByRole('menuitem',{name:label,exact:true}).waitFor();
  await page.getByRole('menuitem',{name:names[locale],exact:true}).click();
  await page.waitForFunction(expected=>document.documentElement.lang===expected,locale);
  current=locale;
 }
 try{
  await page.goto(`${BASE}/login`);
  for(const locale of (process.env.I18N_TEST_LOCALES?.split(',') ?? Object.keys(names))){
   console.log('switch',current,'to',locale,'at',page.url()); await change(locale); const t=dictionaries[locale];
   await page.getByRole('textbox',{name:t.login.emailLabel,exact:true}).fill('invalid-email');
   await page.locator('input[type=password]').fill('dev');
   await page.getByRole('button',{name:t.login.submit,exact:true}).click();
   await page.getByText(t.validation.email,{exact:true}).waitFor();
   await page.reload({waitUntil:'networkidle'});
   assert.equal(await page.locator('html').getAttribute('lang'),locale);
   await page.getByRole('button',{name:t.login.submit,exact:true}).waitFor();
   checks.push({locale,loginLabels:true,localizedValidation:true,persistedAfterReload:true});
  }
  const t=dictionaries[current];
  await page.getByRole('textbox',{name:t.login.emailLabel,exact:true}).fill('dev-hr-admin@performance.dev');
  await page.locator('input[type=password]').fill('dev');
  await page.getByRole('button',{name:t.login.submit,exact:true}).click();
  await page.waitForURL(`${BASE}/`);
  await page.waitForLoadState('networkidle');
  for(const locale of (process.env.I18N_TEST_LOCALES?.split(',') ?? Object.keys(names))){
   console.log('switch',current,'to',locale,'at',page.url()); await change(locale); const t=dictionaries[locale];
   await page.getByRole('link',{name:t.workspace.navOperate,exact:true}).waitFor();
   await page.getByRole('combobox',{name:t.workspace.cycle,exact:true}).waitFor();
   const htmlLang=await page.locator('html').getAttribute('lang'); assert.equal(htmlLang,locale);
   checks.push({locale,workspaceLabels:true,authenticatedLanguageSwitch:true});
  }
  await page.setViewportSize({width:390,height:844});
  for(const locale of (process.env.I18N_TEST_LOCALES?.split(',') ?? Object.keys(names))){
   console.log('switch',current,'to',locale,'at',page.url()); await change(locale); const t=dictionaries[locale];
   await page.getByRole('button',{name:t.common.label.logout,exact:true}).waitFor();
   const bounds=await page.getByRole('button',{name:t.common.label.language,exact:true}).boundingBox();
   assert.ok(bounds&&bounds.x>=0&&bounds.x+bounds.width<=390);
   const width=await page.evaluate(()=>document.documentElement.scrollWidth);assert.ok(width<=390,`${locale} overflow ${width}`);
   if(locale==='vi'||locale==='zh-CN') {
    await page.evaluate(()=>window.scrollTo(0,0));
    await page.screenshot({path:`${ROOT}/_workspace/framework-20260907/screenshots/i18n-${locale}-mobile.png`,fullPage:true});
   }
   checks.push({locale,mobileLanguageControlVisible:true,mobileOverflow:false});
  }
  const result={verifiedAt:new Date().toISOString(),checks,pageErrors:errors,passed:checks.length};
  fs.writeFileSync(`${ROOT}/_workspace/framework-20260907/i18n-browser-${process.env.I18N_TEST_LOCALES ? 'partial' : 'result'}.json`,JSON.stringify(result,null,2));
  assert.deepEqual(errors,[]);console.log(JSON.stringify(result));
 } catch(error) { console.error(await page.locator('body').innerText()); await page.screenshot({path:`${ROOT}/_workspace/framework-20260907/screenshots/i18n-failure.png`}); throw error; } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
