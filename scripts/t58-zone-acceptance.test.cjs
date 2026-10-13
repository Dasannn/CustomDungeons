
'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {ids,zoneIds,validateScenario,validateSuite,fixtureName,validateCoverage}=require('./t58-zone-acceptance.cjs');
const base=()=>({particlePackets:12,started:['vortex'],zoneHits:[{ability:'vortex',inside:true,warned:true,evidence:"world_particles",damage:6,cancelled:false}],zoneEffects:[]});
test('all eighteen zone and combat abilities are represented',()=>assert.equal(new Set(ids).size,18));
test('each completed irregular encounter checks its coverage before the next fight',()=>{
 const good={scenario:'zones-irregular-cracked_floor',arenaVersion:1,terrainContacts:1,borderVisits:1,cornerVisits:1};
 assert.doesNotThrow(()=>validateCoverage(good));
 for(const key of ['terrainContacts','borderVisits','cornerVisits'])assert.throws(()=>validateCoverage({...good,[key]:0}));
 assert.doesNotThrow(()=>validateCoverage({...good,scenario:'zones-room-cracked_floor',terrainContacts:0}));
});
test('every mob and dungeon fixture ID fits the production 32 character limit',()=>{
 const names=ids.map(id=>fixtureName('t58b-abcdef',id));
 assert.equal(new Set(names).size,18);
 for(const name of names)for(const id of [name,name+'-d'])assert(/^[a-z0-9_-]{1,32}$/.test(id),id);
});
test('real damage must follow a warning inside the host',()=>{
 assert.doesNotThrow(()=>validateScenario(base(),'vortex',120));
 for(const change of [s=>s.zoneHits[0].inside=false,s=>s.zoneHits[0].warned=false,s=>s.zoneHits[0].damage=0,s=>s.started=[]]){
  const s=base();change(s);assert.throws(()=>validateScenario(s,'vortex',120));
 }
});
test('native rain requires shield evidence and a launch below the ceiling',()=>{
 const rain={particlePackets:12,started:['arrow_rain'],zoneHits:[{ability:'arrow_rain',inside:true,warned:true,evidence:"world_particles",damage:3,cancelled:false},{ability:'arrow_rain',inside:true,warned:true,evidence:"world_particles",damage:0,shield:true,cancelled:false}],zoneEffects:[{ability:'arrow_rain',kind:'spawn',y:108,floorY:101,ceilingY:109}]};
 assert.doesNotThrow(()=>validateScenario(rain,'arrow_rain',120));
 rain.zoneEffects[0].y=110;assert.throws(()=>validateScenario(rain,'arrow_rain',120));
 rain.zoneEffects[0].y=103;assert.throws(()=>validateScenario(rain,'arrow_rain',120));
 rain.zoneEffects[0].y=108;rain.zoneHits.pop();assert.throws(()=>validateScenario(rain,'arrow_rain',120));
 rain.zoneHits.push({ability:'arrow_rain',inside:true,warned:true,evidence:"world_particles",damage:0,shield:true,cancelled:true});assert.throws(()=>validateScenario(rain,'arrow_rain',120));
});
test('a real absorption loss is damage while cancelled and fully shielded hits are not',()=>{
 const s=base();s.zoneHits[0].damage=0;s.zoneHits[0].absorbed=.25;
 assert.doesNotThrow(()=>validateScenario(s,'vortex',120));
 s.zoneHits[0].absorbed=0;assert.throws(()=>validateScenario(s,'vortex',120));
 s.zoneHits[0].absorbed=.25;s.zoneHits[0].cancelled=true;assert.throws(()=>validateScenario(s,'vortex',120));
});
test('rift evidence must be the real destination on the interior floor',()=>{
 const rift={particlePackets:12,started:['rift'],zoneHits:[],zoneEffects:[{ability:'rift',kind:'teleport',cancelled:false,y:101,inside:true,safeGround:true,walkableExit:true}]};
 assert.doesNotThrow(()=>validateScenario(rift,'rift',120));
 rift.zoneEffects[0].safeGround=false;assert.throws(()=>validateScenario(rift,'rift',120));
 rift.zoneEffects[0].safeGround=true;rift.zoneEffects[0].inside=false;assert.throws(()=>validateScenario(rift,'rift',120));
});
function suite() {
 const movement={seconds:120,backwards:0,backwardsCharge:0,unplannedTeleports:0,maxQuietTicks:20};
 const results=['room','live','irregular'].flatMap(host=>(host==='irregular'?[...zoneIds,'boss_totem','decoys','charge']:ids).map(ability=>({...movement,scenario:`zones-${host}-${ability}`,
  particlePackets:12,arenaVersion:1,terrainContacts:1,borderVisits:1,cornerVisits:1,started:[ability],zoneHits:[{ability,inside:true,warned:true,evidence:"world_particles",damage:1,cancelled:false,shield:true}],pillarPlayers:3,
  zoneEffects:['LEVITATION','teleport','spawn','buff_removed','use_cancelled'].map(kind=>({ability,kind,inside:true,cancelled:false,safeGround:true,walkableExit:true,floorY:101,ceilingY:109,y:ability==='arrow_rain'?108:101}))})));
 results.push(...['warden-live','warden-irregular','zero','profile'].map(scenario=>({...movement,scenario,particlePackets:12,zoneHits:[],arenaVersion:1,terrainContacts:1,started:[...zoneIds]})));
 const cold={limitMsPerTick:.5,warmupLimitMsPerTick:1,windows:[['0-30',0,30],['31-91',31,91],['91-end',91,120]].map(([label,startSeconds,endSeconds])=>({label,startSeconds,endSeconds,ticks:600,pluginMsPerTick:.2}))};
 const warm={baselineTicks:1200,activeTicks:1200,activePluginMsPerTick:.2};
 return {results,cold,warm};
}
test('full acceptance requires every scenario and its original movement and effect criteria',()=>{
 const valid=suite();assert.doesNotThrow(()=>validateSuite(valid.results,valid.cold,valid.warm));
 for(const change of [s=>s.results[48].scenario='zero',s=>s.results[0].unplannedTeleports=1,
  s=>s.results[0].backwardsCharge=1,s=>s.results[0].zoneHits=[]]) {
  const s=suite();change(s);assert.throws(()=>validateSuite(s.results,s.cold,s.warm));
 }
});
test('full acceptance rejects missing, negative or non-finite stable measurements',()=>{
 for(const change of [s=>s.warm.activePluginMsPerTick=-1,s=>s.warm.activePluginMsPerTick=NaN,
  s=>s.warm.baselineTicks=0,s=>s.warm.activeTicks=undefined]) {
  const s=suite();change(s);assert.throws(()=>validateSuite(s.results,s.cold,s.warm));
 }
});

test('internal warned flags are insufficient without actual particle evidence',()=>{
 const s=base();s.zoneHits[0].evidence='server_flag';assert.throws(()=>validateScenario(s,'vortex',120));
 s.zoneHits[0].evidence='world_particles';s.particlePackets=0;assert.throws(()=>validateScenario(s,'vortex',120));
});
test('all nine mixed zones and irregular terrain with actual edge visits are mandatory',()=>{
 for(const change of [s=>s.results.find(r=>r.scenario==='profile').started.pop(),s=>s.results[0].borderVisits=0,s=>s.results.find(r=>r.scenario==='zones-irregular-charge').arenaVersion=0]) {
  const s=suite();change(s);assert.throws(()=>validateSuite(s.results,s.cold,s.warm));
 }
});

test('irregular acceptance requires standing on non-flat terrain rather than only jumping',()=>{
 const s=suite();s.results.find(r=>r.scenario==='warden-irregular').terrainContacts=0;
 assert.throws(()=>validateSuite(s.results,s.cold,s.warm));
});
test('every summoned entity must be on safe terrain inside the host',()=>{
 for(const ability of ['boss_totem','decoys']) {
  const s=suite().results.find(r=>r.scenario==='zones-irregular-'+ability);
  s.zoneEffects=s.zoneEffects.filter(e=>e.kind==='spawn');
  s.zoneEffects.push({...s.zoneEffects[0],safeGround:false});
  assert.throws(()=>validateScenario(s,ability,120));
  s.zoneEffects.at(-1).safeGround=true;s.zoneEffects.at(-1).inside=false;
  assert.throws(()=>validateScenario(s,ability,120));
 }
});
test('the nine-zone mixed workload also rejects damage without received warnings',()=>{
 const s=suite(),profile=s.results.find(r=>r.scenario==='profile');
 profile.zoneHits=[{ability:'sweep',inside:true,warned:false,evidence:'world_particles',damage:1,cancelled:false}];
 assert.throws(()=>validateSuite(s.results,s.cold,s.warm));
});
test('summons and teleport landings without a walkable exit cannot pass acceptance',()=>{
 for(const ability of ['boss_totem','decoys','rift','blink_behind']) {
  const s=suite().results.find(r=>r.scenario==='zones-'+(ability==='blink_behind'?'room':'irregular')+'-'+ability);
  s.zoneEffects.find(e=>e.kind===(['rift','blink_behind'].includes(ability)?'teleport':'spawn')).walkableExit=false;
  assert.throws(()=>validateScenario(s,ability,120));
 }
});
