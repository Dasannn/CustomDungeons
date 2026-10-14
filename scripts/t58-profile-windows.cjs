'use strict';
const assert=require('node:assert/strict');
function validateColdWindows(result,baselinePluginMsPerTick=0) {
  assert(Number.isFinite(baselinePluginMsPerTick)&&baselinePluginMsPerTick>=0,"measured nonnegative baseline");
  assert.equal(result.limitMsPerTick,.5,'the stable active plugin budget must stay at 0.5 ms/tick');
  assert.equal(result.warmupLimitMsPerTick,1,'the JVM warmup budget must stay at 1.0 ms/tick');
  assert.equal(result.windows?.length,3,'three separate cold-combat windows are required');
  const expected=[['0-30',0,30],['31-91',31,91],['91-end',91,null]];
  for(let i=0;i<3;i++) {
    const window=result.windows[i], [label,start,end]=expected[i];
    assert.equal(window.label,label);
    assert(Number.isFinite(window.startSeconds)&&Math.abs(window.startSeconds-start)<=1.5,'window starts at its combat deadline: '+label);
    assert(Number.isFinite(window.endSeconds)&&window.endSeconds>window.startSeconds);
    assert(Number.isInteger(window.ticks)&&window.ticks>0,'measured server ticks');
    assert(Number.isFinite(window.pluginMsPerTick)&&window.pluginMsPerTick>=0,'measured active cost');
    if(end!==null)assert(Math.abs(window.endSeconds-end)<=1.5,'complete window: '+label);
    else assert(window.endSeconds>=118.5,'the last window includes the end of the two-minute fight');
    if(i>0) {
      const limit=i===1?result.warmupLimitMsPerTick:result.limitMsPerTick;
      const cost=i===2?Math.max(0,window.pluginMsPerTick-baselinePluginMsPerTick):window.pluginMsPerTick;
      assert(cost<=limit,'encounter cost exceeds '+limit+' ms/tick in '+label);
    }
  }
  return result;
}
function validateFullAcceptance(result) {
  assert.equal(result.scenarios,17,'all seventeen scenarios must finish');
  validateColdWindows(result.coldWarden);
  validateColdWindows(result.coldTrio);
  const warm=result.warmProfile;
  assert(Number.isInteger(warm?.baselineTicks)&&warm.baselineTicks>0,'measured reference ticks');
  assert(Number.isInteger(warm?.activeTicks)&&warm.activeTicks>0,'measured warm combat ticks');
  assert(Number.isFinite(warm?.activePluginMsPerTick)&&warm.activePluginMsPerTick>=0&&warm.activePluginMsPerTick<=.5,'warm active cost exceeds 0.5 ms/tick');
  return result;
}
module.exports={validateColdWindows,validateFullAcceptance};
if(require.main===module) {
  const fs=require('node:fs'),suite=process.argv[2]==='--suite',file=process.argv[suite?3:2];
  const result=JSON.parse(fs.readFileSync(file,'utf8'));
  if(suite) {
    validateFullAcceptance(result);
    result.acceptance=true;fs.writeFileSync(file,JSON.stringify(result,null,2));
    console.log('PASS T58 COLD '+JSON.stringify(result));
  } else validateColdWindows(result);
}
