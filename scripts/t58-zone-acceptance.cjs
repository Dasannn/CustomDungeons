'use strict';
const assert=require('node:assert/strict');
const zoneIds=['vortex','inverted_gravity','cracked_floor','sweep','falling_pillars','poison_pools','charged_beam','arrow_rain','rift'];
const combatIds=['charge','boss_totem','decoys','interruptible_ultimate','purge','buff_steal','silence','pain_link','blink_behind'];
const ids=[...zoneIds,...combatIds];
function fixtureName(run,ability) {return run+'-'+(ability==='interruptible_ultimate'?'ultimate':ability.replaceAll('_','-'));}
const damaging=new Set(['vortex','cracked_floor','sweep','falling_pillars','poison_pools','charged_beam','arrow_rain','charge','decoys','interruptible_ultimate','pain_link','blink_behind']);
function validateScenario(summary,ability,seconds) {
 assert(ids.includes(ability));
 if(seconds<120)return; // Smoke evidence never claims full acceptance.
 assert(summary.particlePackets>0,'warning packets actually received');
 assert(summary.zoneHits.every(h=>h.cancelled||h.evidence==='world_particles'),'warnings proven at the client');
 assert(summary.started.includes(ability),'ability started: '+ability);
 assert(summary.zoneHits.every(h=>h.cancelled||h.inside&&h.warned),'damage stays in the warned host intersection');
 if(damaging.has(ability))assert(summary.zoneHits.some(h=>h.ability===ability&&!h.cancelled&&(h.damage>0||h.absorbed>0)),'effective warned health or absorption loss: '+ability);
 if(ability==='inverted_gravity')assert(summary.zoneEffects.some(e=>e.ability===ability&&e.kind==='LEVITATION'),'gravity applied');
 if(['rift','blink_behind'].includes(ability)) {
   const landings=summary.zoneEffects.filter(e=>e.ability===ability&&e.kind==='teleport'&&!e.cancelled);
   assert(landings.length>0&&landings.every(e=>e.safeGround&&e.inside&&e.walkableExit),'teleport landed on an escapable interior floor inside the host');
 }
 if(['boss_totem','decoys'].includes(ability)) {
   const spawns=summary.zoneEffects.filter(e=>e.ability===ability&&e.kind==='spawn');
   assert(spawns.length>0&&spawns.every(e=>e.safeGround&&e.inside&&e.walkableExit),'every summon on an escapable interior floor inside the host');
   if(ability==='decoys')assert(summary.zoneEffects.filter(e=>e.ability===ability&&e.kind==='teleport'&&!e.cancelled).every(e=>e.safeGround&&e.inside&&e.walkableExit),'the relocated owner also has a safe exit');
 }
 if(ability==='arrow_rain') {
   const arrows=summary.zoneEffects.filter(e=>e.ability===ability&&e.kind==='spawn');
   assert(arrows.length>0&&arrows.every(e=>Number.isFinite(e.floorY)&&Number.isFinite(e.ceilingY)&&e.y-e.floorY>=3&&e.y<e.ceilingY),'arrows below roof and at least three blocks above floor');
   assert(summary.zoneHits.some(h=>h.ability===ability&&h.shield&&!h.cancelled&&h.inside&&h.warned),'a native rain arrow was blocked by a shield');
 }
 if(ability==='falling_pillars')assert(summary.pillarPlayers>=3,'three distinct eligible pillar anchors');
 if(ability==='buff_steal')assert(summary.zoneEffects.some(e=>e.ability===ability&&e.kind==='buff_removed'),'buff stolen from warned anchor');
 if(ability==='purge')assert(summary.zoneEffects.some(e=>e.ability===ability&&e.kind==='buff_removed'),'positive potion removed');
 if(ability==='silence')assert(summary.zoneEffects.some(e=>e.ability===ability&&e.kind==='use_cancelled'),'silence blocks a real consumable attempt');
}
function validateCoverage(s) {
 if(s.scenario.startsWith('zones-'))assert(s.arenaVersion===1&&s.borderVisits>0&&s.cornerVisits>0,'fixed arena and real edge/corner visits: '+s.scenario);
 if(s.scenario==='warden-irregular')assert.equal(s.arenaVersion,1);
 if(s.scenario.includes('irregular'))assert(s.terrainContacts>0,'actual standing contact with non-flat terrain: '+s.scenario);
}
function validateSuite(results,cold,warm) {
 assert.equal(results.length,52);
 const required=new Set(['warden-live','warden-irregular','zero','profile']);
 for(const host of ['room','live','irregular'])for(const ability of host==='irregular'?[...zoneIds,'boss_totem','decoys','charge']:ids)required.add(`zones-${host}-${ability}`);
 assert.equal(new Set(results.map(s=>s.scenario)).size,52,'all scenarios are distinct');
 for(const s of results) {
   assert(required.has(s.scenario),'expected acceptance scenario: '+s.scenario);
   assert(s.seconds>=120&&s.backwards===0&&s.backwardsCharge===0&&s.unplannedTeleports===0&&s.maxQuietTicks<=20,'original movement criteria: '+s.scenario);
   validateCoverage(s);
   if(s.scenario.startsWith('zones-'))validateScenario(s,s.scenario.replace(/^zones-(room|live|irregular)-/,''),s.seconds);
   if(s.scenario==='profile') {
     assert(zoneIds.every(id=>s.started.includes(id)),'all nine mixed zones started');
     assert(s.particlePackets>0&&Array.isArray(s.zoneHits)&&s.zoneHits.every(h=>h.cancelled||h.inside&&h.warned&&h.evidence==='world_particles'),'mixed damage stays inside received warnings');
   }
 }
 require('./t58-profile-windows.cjs').validateColdWindows(cold);
 assert(Number.isInteger(warm?.baselineTicks)&&warm.baselineTicks>0,'measured reference ticks');
 assert(Number.isInteger(warm?.activeTicks)&&warm.activeTicks>0,'measured stable combat ticks');
 assert(Number.isFinite(warm?.activePluginMsPerTick)&&warm.activePluginMsPerTick>=0&&warm.activePluginMsPerTick<=.5,'stable active cost <= 0.5 ms/tick');
 return {acceptance:true,scenarios:results.length,cold,warm};
}
module.exports={zoneIds,combatIds,ids,damaging,fixtureName,validateScenario,validateSuite,validateCoverage};
