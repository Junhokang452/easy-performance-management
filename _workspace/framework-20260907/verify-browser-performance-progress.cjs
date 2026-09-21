const fs=require('node:fs');
const {chromium}=require('/home/samsung/.cache/ms-playwright-go/1.57.0/package');
(async()=>{
 const root='/home/samsung/code/easy-performance-management';
 const result=JSON.parse(fs.readFileSync(`${root}/_workspace/framework-20260907/api-flow-result.json`));
 const browser=await chromium.launch({headless:false,executablePath:'/home/samsung/.cache/ms-playwright/chromium-1234/chrome-linux64/chrome',args:['--no-sandbox']});
 const context=await browser.newContext({viewport:{width:1440,height:1000}});const page=await context.newPage();const errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto('http://localhost:5174/login');await page.getByRole('textbox',{name:'이메일',exact:true}).fill('dev-employee@performance.dev');await page.locator('input[type=password]').fill('dev');await page.getByRole('button',{name:'로그인',exact:true}).click();await page.waitForURL('http://localhost:5174/');
  await page.goto(`http://localhost:5174/workspace/${result.cycleId}`);await page.getByRole('cell',{name:'90%',exact:true}).waitFor();
  await page.reload({waitUntil:'networkidle'});await page.getByRole('cell',{name:'90%',exact:true}).waitFor();
  await page.evaluate(()=>window.scrollTo(0,0));await page.screenshot({path:`${root}/_workspace/framework-20260907/screenshots/final-member-result.png`,fullPage:true});
  if(errors.length)throw Error(errors.join('; '));
  const proof={cycleId:result.cycleId,progressPercent:90,survivesBrowserReload:true,pageErrors:errors};fs.writeFileSync(`${root}/_workspace/framework-20260907/progress-browser-result.json`,JSON.stringify(proof,null,2));console.log(JSON.stringify(proof));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
