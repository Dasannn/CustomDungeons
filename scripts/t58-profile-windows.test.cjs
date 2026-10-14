'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {validateColdWindows,validateFullAcceptance}=require('./t58-profile-windows.cjs');
const profile=()=>({limitMsPerTick:.5,warmupLimitMsPerTick:1,windows:[{label:'0-30',startSeconds:0,endSeconds:30,ticks:600,pluginMsPerTick:1.54},{label:'31-91',startSeconds:31,endSeconds:91,ticks:1200,pluginMsPerTick:.49},{label:'91-end',startSeconds:91,endSeconds:120,ticks:580,pluginMsPerTick:.48}]});
test('reports the initial cold cost and enforces separate warmup and stable limits',()=>{const p=profile();p.windows[1].pluginMsPerTick=1;p.windows[2].pluginMsPerTick=.5;assert.doesNotThrow(()=>validateColdWindows(p));for(const [index,cost] of [[1,1.001],[2,.501]]){const over=profile();over.windows[index].pluginMsPerTick=cost;assert.throws(()=>validateColdWindows(over));}});
test('the reviewed JVM warmup is accepted but cannot hide an over-budget stable window',()=>{const p=profile();p.windows[1].pluginMsPerTick=.703333;p.windows[2].pluginMsPerTick=.361;assert.doesNotThrow(()=>validateColdWindows(p));p.windows[2].pluginMsPerTick=.501;assert.throws(()=>validateColdWindows(p));});
test('both architecture-approved limits are explicit in the report',()=>{for(const edit of [p=>delete p.warmupLimitMsPerTick,p=>p.warmupLimitMsPerTick=2,p=>p.limitMsPerTick=1]){const p=profile();edit(p);assert.throws(()=>validateColdWindows(p));}});
test('missing, shifted or truncated windows are not accepted',()=>{for(const edit of [p=>p.windows.pop(),p=>p.windows[1].startSeconds=60,p=>p.windows[1].endSeconds=60,p=>p.windows[2].endSeconds=100,p=>p.windows[1].ticks=0]){const p=profile();edit(p);assert.throws(()=>validateColdWindows(p));}});
test('the final seventeen-scenario verdict enforces both cold workloads and the warm profile',()=>{
  const suite=()=>({scenarios:17,coldWarden:profile(),coldTrio:profile(),warmProfile:{baselineTicks:1200,activeTicks:1200,activePluginMsPerTick:.3}});
  assert.doesNotThrow(()=>validateFullAcceptance(suite()));
  for(const edit of [p=>p.scenarios=16,p=>p.coldWarden.windows[2].pluginMsPerTick=.5125,p=>p.coldTrio.windows[1].pluginMsPerTick=1.001,p=>p.warmProfile.activePluginMsPerTick=.501,p=>p.warmProfile.activeTicks=0]) {
    const p=suite();edit(p);assert.throws(()=>validateFullAcceptance(p));
  }
});
test('RF-IA-08 subtracts a measured baseline only from the stable encounter cost',()=>{
 const p=profile();p.windows[2].pluginMsPerTick=.65;
 assert.throws(()=>validateColdWindows(p));
 assert.doesNotThrow(()=>validateColdWindows(p,.2));
 p.windows[2].pluginMsPerTick=.701;assert.throws(()=>validateColdWindows(p,.2));
 for(const base of [-1,NaN,Infinity])assert.throws(()=>validateColdWindows(profile(),base));
});
