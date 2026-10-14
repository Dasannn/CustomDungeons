'use strict';
const assert=require('node:assert/strict');
const kinds=['distance','shield','dodge','group','learning','choices','debug','legacy0','legacy1'];
const scenarios=['v13-flat-distance','v13-flat-debug','zero','profile',...['irregular','live'].map(a=>'v13-'+a+'-distance'),
 ...['shield','dodge','group','learning','choices'].flatMap(k=>['flat','irregular','live'].map(a=>'v13-'+a+'-'+k)),
 'v13-flat-legacy0','v13-flat-legacy1','warden-dungeon','warden-irregular','warden-live','cold-trio','zones-irregular-decoys'];
function profileWindowCount(label,{v13Run=false,first=false,seconds=120}={}) {
 const labels=['zero','profile','warden-dungeon','cold-trio','zones-room-vortex','v13-flat-distance','v13-flat-group'];
 if(!labels.includes(label))return 0;
 const cold=seconds>=120&&(v13Run&&['profile','warden-dungeon','v13-flat-group'].includes(label)
  ||first&&['v13-flat-distance','warden-dungeon','cold-trio','zones-room-vortex'].includes(label));
 return cold?3:1;
}
function debugScenario(label,warden='except-profile') {
 return ['choices','debug'].includes(kind(label))||label.startsWith('warden')&&warden!=='0'
  &&!(['except-profile','live-only'].includes(warden)&&label==='warden-dungeon');
}
function validateAbilityUses(label,started,expected,{v13Run=false,seconds=120}={}) {
 if(seconds<120||v13Run&&kind(label)==='debug') {if(expected.length)assert(started.size>0,'at least one configured skill');return;}
 if(v13Run&&(label.startsWith('warden')||kind(label)==='choices')) {
  assert(expected.filter(id=>started.has(id)).length>=4,'at least four configured abilities used');return;
 }
 for(const ability of expected)assert(started.has(ability),'ability started: '+ability+' in '+label);
}
function kind(label){return label.startsWith('v13-')?label.split('-').at(-1):null;}
function attackSecondary(label,index){return kind(label)!=="group"||index===0;}
function shieldTurnSpeed(value=30){const speed=Number(value);assert(Number.isFinite(speed)&&speed>0&&speed<=720,"shield turn speed must be in (0,720]");return speed;}
function turn(current,target,maximum){const delta=Math.atan2(Math.sin(target-current),Math.cos(target-current));return current+Math.max(-maximum,Math.min(maximum,delta));}
function learningSound(sound){return (sound?.data?.soundName??sound?.soundName)==='minecraft:block.respawn_anchor.charge';}
function validateLifetime(rows,seconds,expectedActors=1){
 const actors=new Map();for(const r of rows){const span=actors.get(r.mob.uuid)||[r.tick,r.tick];span[0]=Math.min(span[0],r.tick);span[1]=Math.max(span[1],r.tick);actors.set(r.mob.uuid,span);}
 assert.equal(actors.size,expectedActors,'all actors observed');
 for(const [start,end] of actors.values())assert(end-start>=seconds*20-40,'mob present for the entire encounter');
 return [...actors].map(([uuid,[first,last]])=>({uuid,first,last,ticks:last-first}));
}
function steer(position,goal,look){
 const dx=goal.x-position.x,dz=goal.z-position.z,d=Math.hypot(dx,dz);
 const tx=look.x-position.x,tz=look.z-position.z,t=Math.hypot(tx,tz);
 if(d<.35||t<1e-6)return {forward:false,back:false,left:false,right:false};
 const forward=(dx*tx+dz*tz)/(d*t),left=(dx*tz-dz*tx)/(d*t);
 return {forward:forward>.25,back:forward<-.25,left:left>.25,right:left<-.25};
}
function intelligence(label){const k=kind(label);return {level:k==='legacy0'?0:k==='legacy1'?1:k==='distance'?2:5,
 'weak-point':'NONE',advanced:k==='learning'?{duration:10,cooldown:10}: {},
 'disabled-adaptations':k==='learning'?['consumables','totem','flight','critical','mace','pearl','shield']:[],
 movement:{style:k==='distance'?'RANGED':'MELEE',disabled:k==='group'?['circle','shield']:k==='choices'?['spread']:[]}};}
function fixture(k,id){return {'entity-type':k==='distance'?'SKELETON':'HUSK','display-name':id+'-'+k,
 'max-health':k==='shield'?200:4096,damage:1,speed:.23,boss:true,
 equipment:k==='distance'?{HAND:{item:{'==':'org.bukkit.inventory.ItemStack',type:'BOW',amount:1},'drop-chance':0}}:{},
 intelligence:intelligence('v13-flat-'+k),
 abilities:(k==='choices'?['sonic_boom','sweep','charged_beam','silence','roots','blink_behind','decoys']:k==='debug'?['roots','levitation_cage','grab_throw','interruptible_ultimate']:[])
 .map(a=>({'ability-id':a,trigger:'EVERY_X_SECONDS','trigger-value':5,target:'NEAREST',range:16,'cooldown-ticks':100,chance:1,'telegraph-ticks':20,params:{}}))};}
function metrics(rows){
 const distances=[],angles=[],groups=new Map(),previous=new Map(),quiet=new Map(),adaptationNames=new Set(),reasons=new Set();let maxQuietTicks=0,hazards=0,retreatSamples=0,dodgeSamples=0,flankSamples=0,shieldSamples=0,ownedPaths=0,learningSamples=0,restoredSamples=0,dodgeLateralSamples=0,retreatOutwardSamples=0;
 for(const r of rows){
  const target=r.players.find(p=>p.uuid===r.target),id=r.mob.uuid;
  const old=previous.get(id);previous.set(id,r.mob);
  if(target){const dx=r.mob.x-target.x,dz=r.mob.z-target.z,d=Math.hypot(dx,dz),angle=Math.atan2(dz,dx);distances.push(d);angles.push(angle);
   if(old&&d>0){const mx=r.mob.x-old.x,mz=r.mob.z-old.z;
    if(r.tacticalDodge&&r.ownedPath&&Math.abs(mx*dz/d-mz*dx/d)>.04)dodgeLateralSamples++;
    if(r.tacticalRetreat&&r.ownedPath&&(mx*dx/d+mz*dz/d)>.04)retreatOutwardSamples++;
   }
   if(target.blocking){shieldSamples++;const yaw=target.yaw*Math.PI/180;if(dx*-Math.sin(yaw)+dz*Math.cos(yaw)<0)flankSamples++;}
   const key=[r.tick,r.hostId||r.host,r.target].join(':');if(!groups.has(key))groups.set(key,[]);groups.get(key).push(angle);
  }
  if(r.advance<.01&&!r.quietCause){if(!quiet.has(id))quiet.set(id,r.tick);maxQuietTicks=Math.max(maxQuietTicks,r.tick-quiet.get(id));}else quiet.delete(id);
  if(r.hazard||r.mob.y<94)hazards++;
  if(r.tacticalRetreat)retreatSamples++;if(r.tacticalDodge)dodgeSamples++;if(r.ownedPath)ownedPaths++;
  if(r.learningVisible){learningSamples++;adaptationNames.add(r.customName);}else if(adaptationNames.size)restoredSamples++;
  if(r.lastDiscarded?.reason)reasons.add(r.lastDiscarded.reason);
 }
 const separations=[];for(const aa of groups.values())if(aa.length>=3){aa.sort((a,b)=>a-b);separations.push(Math.min(...aa.map((a,i)=>(aa[(i+1)%aa.length]-a+2*Math.PI)%(2*Math.PI))));}
 const mean=a=>a.length?a.reduce((s,x)=>s+x,0)/a.length:null;
 const uses=[...new Set(rows.flatMap(r=>r.started).map(k=>k.split('#')[0]).filter(k=>!k.startsWith('combo:')))];
 return {samples:rows.length,distanceMean:mean(distances),distanceMin:distances.length?Math.min(...distances):null,
  distanceBandFraction:distances.length?distances.filter(d=>d>=8&&d<=14).length/distances.length:0,
  angularVariance:angles.length?1-Math.hypot(mean(angles.map(Math.cos)),mean(angles.map(Math.sin))):0,
  groupSamples:separations.length,groupSeparationMean:mean(separations),groupSeparationMin:separations.length?Math.min(...separations):null,
  maxQuietTicks,hazards,retreatSamples,retreatOutwardSamples,dodgeSamples,dodgeLateralSamples,ownedPaths,shieldSamples,flankSamples,learningSamples,restoredSamples,uses,reasons:[...reasons],
  backwards:rows.flatMap(r=>r.physicalSteps||[]).filter(s=>require('./t58-bot-movement.cjs').unexplainedBackwards(s)).length,
  legacyMoverSamples:rows.filter(r=>r.tacticalMover||r.tacticalSelection).length};
}
function validate(label,m){
 assert.equal(m.hazards,0,'mob never enters water/lava/void');assert.equal(m.backwards,0,'unexplained backwards movement');assert(m.maxQuietTicks<=20,'unexplained quiet interval');
 const k=kind(label);
 if(k==='distance'){assert(m.distanceMean>=8&&m.distanceMean<=14&&m.distanceBandFraction>=.65,'distance band 8..14 for at least 65% of targeted samples');}
 if(k==='shield'){assert(m.angularVariance>.08&&m.flankSamples>0,'shield flanking and lateral motion');assert(m.retreatSamples>0&&m.retreatOutwardSamples>0,'actual retreat at low health');}
 if(k==='dodge')assert(m.dodgeSamples>0&&m.dodgeLateralSamples>0,'actual lateral dodge against aimed bow');
 if(k==='group')assert(m.groupSamples>=20&&m.groupSeparationMean>.7,'three same-target mobs spread their angles');
 if(k==='learning'){assert(m.learningSamples>0&&m.restoredSamples>0,'adaptation name activated and restored after expiry');assert(m.eyeParticlePackets>0&&m.learningSoundPackets>0,'eye particles and learning sound received by clients');}
 if(k==='debug'){assert(m.actionBars>0,'debug action bar received by admin');for(const reason of ['PAUSED','COOLDOWN','OUT_OF_RANGE','CONTROL_OCCUPIED'])assert(m.reasons.includes(reason),'actual debug reason '+reason);}
 if(k==='choices') {
  assert(m.uses.length>=4,'at least four configured abilities used');
  const exact=(m.choiceScores||[]).filter(c=>c.sampling==='at_selection'&&Number.isFinite(c.score)&&c.situation&&Array.isArray(c.roles));
  assert(new Set(exact.map(c=>c.ability)).size>=4,'decision-time scores for at least four abilities');
 }
 if(k?.startsWith('legacy'))assert.equal(m.legacyMoverSamples,0,'levels zero and one have no tactical mover or selection');
 return m;
}
function validateSuite(summaries,cold,warm,zones,warden,wardenWarm){
 assert.equal(summaries.length,scenarios.length,'all integrated scenarios');
 assert.deepEqual(new Set(summaries.map(s=>s.scenario)),new Set(scenarios),'exact integrated coverage');
 for(const s of summaries){assert(!s.validationError,'recorded failure: '+s.validationError);assert(s.seconds>=120,'full two-minute scenario');if(kind(s.scenario))validate(s.scenario,s.v13);
  if(s.scenario==='profile')assert(s.zoneHits.length>0&&s.zoneHits.every(h=>h.cancelled||h.inside&&h.warned),'mixed-zone damage stays inside actual client warnings');
 }
 require('./t58-profile-windows.cjs').validateColdWindows(cold,warm.baselinePluginMsPerTick);
 require('./t58-profile-windows.cjs').validateColdWindows(zones,warm.baselinePluginMsPerTick);
 require('./t58-profile-windows.cjs').validateColdWindows(warden,wardenWarm.baselinePluginMsPerTick);
 assert(wardenWarm.extraMsPerTick<=.5,'level-five Warden extra encounter cost');
 assert(warm.extraMsPerTick<=.5,'stable extra encounter cost');
 return {acceptance:true,version:'v1.3',scenarios:summaries.length,cold,warm,zones,warden,wardenWarm};
}
module.exports={kinds,scenarios,kind,fixture,metrics,validate,validateSuite,steer,learningSound,validateLifetime,turn,shieldTurnSpeed,attackSecondary,profileWindowCount,debugScenario,validateAbilityUses};
