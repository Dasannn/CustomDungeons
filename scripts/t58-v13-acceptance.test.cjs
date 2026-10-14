'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {metrics,validate,validateSuite,fixture,steer,learningSound,validateLifetime,turn}=require('./t58-v13-acceptance.cjs');
function trace(tick,x,z,target='player') {return {tick,mob:{uuid:'mob',x,y:101,z},target,players:[{uuid:'player',x:0,y:101,z:0}],physicalSteps:[],started:[],adaptations:[],healthFraction:1};}
test('distance statistics follow the actual target, not the nearest bystander',()=>{
 const rows=[trace(0,10,0),trace(5,12,0),trace(10,14,0)];rows.forEach(r=>r.players.push({uuid:'bystander',x:r.mob.x,y:101,z:r.mob.z}));
 const m=metrics(rows);assert.equal(m.distanceMean,12);assert.equal(m.distanceMin,10);assert.equal(m.distanceBandFraction,1);
});
test('the circular variance handles the angle seam',()=>{
 const m=metrics([trace(0,-10,.01),trace(5,-10,-.01)]);assert(m.angularVariance<.001);
});
test('quiet periods are computed separately for every mob',()=>{
 const rows=[];for(let t=0;t<=50;t+=5){rows.push({...trace(t,10,0),advance:0});rows.push({...trace(t,10,0),mob:{uuid:'second',x:10,y:101,z:0},advance:.1});}
 assert.equal(metrics(rows).maxQuietTicks,50);assert.throws(()=>validate('v13-flat-distance',metrics(rows)),/quiet/);
});
test('good distance average cannot hide sustained collapse into melee',()=>{
 const m=metrics(Array.from({length:100},(_,i)=>({...trace(i*5,i<40?1:15,0),advance:.1})));assert.throws(()=>validate('v13-flat-distance',m),/distance/);
});
test('the group metric requires three actors with the same host and target at the same tick',()=>{
 const rows=[0,1,2].map(i=>({...trace(5,Math.cos(i*2*Math.PI/3),Math.sin(i*2*Math.PI/3)),hostId:'host',mob:{uuid:String(i),x:Math.cos(i*2*Math.PI/3),y:101,z:Math.sin(i*2*Math.PI/3)}}));
 assert(metrics(rows).groupSeparationMean>2);rows[2].target='other';assert.equal(metrics(rows).groupSamples,0);
});
test('late backwards samples are still rejected',()=>{
 const rows=[trace(0,10,0),trace(5,10,0),trace(10,10,0)];
 rows[2].physicalSteps=[{advance:.2,facingDot:-1}];assert.equal(metrics(rows).backwards,1);
});
test('a subset or a smoke run never qualifies as integrated acceptance',()=>{
 assert.throws(()=>validateSuite([{scenario:'v13-flat-distance',seconds:20}],{},{}),/integrated scenarios/);
});
test('the multi-ability fixture keeps at least six declared skills and learning isolates dominant damage',()=>{
 assert(fixture('choices','t58b-aaaaaa').abilities.length>=6);
 const d=fixture('learning','t58b-aaaaaa').intelligence;
 assert.equal(d.level,5);assert(!d['disabled-adaptations'].includes('damage'));assert(d['disabled-adaptations'].includes('critical'));
});
test('a dodge or retreat clock without real movement cannot satisfy acceptance',()=>{
 const rows=[trace(0,2,0),trace(5,2,0)];rows.forEach(r=>{r.tacticalDodge=true;r.tacticalRetreat=true;r.ownedPath=false;r.quietCause='melee';r.advance=0;});
 assert.throws(()=>validate('v13-flat-dodge',metrics(rows)),/lateral/);
});
test('a bot can follow a waypoint while keeping a shield aimed north',()=>{
 assert.deepEqual(steer({x:0,z:0},{x:-2,z:0},{x:0,z:-5}),{forward:false,back:false,left:true,right:false});
 assert.deepEqual(steer({x:0,z:0},{x:0,z:2},{x:0,z:-5}),{forward:false,back:true,left:false,right:false});
});
test('waypoint steering rotates with the opponent and stops at the goal',()=>{
 assert.deepEqual(steer({x:0,z:0},{x:0,z:-2},{x:5,z:0}),{forward:false,back:false,left:true,right:false});
 assert.deepEqual(steer({x:0,z:0},{x:0,z:0},{x:5,z:0}),{forward:false,back:false,left:false,right:false});
});
test('the learning sound is proven by the literal sound reference received from Paper',()=>{
 assert(learningSound({data:{soundName:'minecraft:block.respawn_anchor.charge'}}));
 assert(learningSound({soundName:'minecraft:block.respawn_anchor.charge'}));
 assert(!learningSound({data:{soundName:'minecraft:block.stone.step'}}));
 assert(!learningSound({soundId:1600}));
});
test('an encounter cannot pass after its mob dies before two minutes',()=>{
 assert.throws(()=>validateLifetime([trace(0,2,0),trace(1600,2,0)],120),/entire encounter/);
 assert.doesNotThrow(()=>validateLifetime([trace(10,2,0),trace(2400,2,0)],120));
});
test('every member of a group must survive the measured encounter',()=>{
 const rows=[trace(0,2,0),trace(2400,2,0),{...trace(0,2,0),mob:{uuid:'second'}},{...trace(1000,2,0),mob:{uuid:'second'}}];
 assert.throws(()=>validateLifetime(rows,120,2),/entire encounter/);
 assert.throws(()=>validateLifetime(rows.slice(0,2),120,3),/all actors/);
});
test('shield tracking has a finite turn speed and crosses the angle seam by the short arc',()=>{
 assert.equal(turn(0,Math.PI/2,.1),.1);
 assert(Math.abs(turn(Math.PI-.02,-Math.PI+.02,.01)-(Math.PI-.01))<1e-8);
 assert(Math.abs(turn(0,.02,.1)-.02)<1e-8);
});
test('shield acceptance preserves slow tracking and supports realistic tracking explicitly',()=>{
 const {shieldTurnSpeed}=require('./t58-v13-acceptance.cjs');
 assert.equal(shieldTurnSpeed(),30);assert.equal(shieldTurnSpeed('180'),180);
 for(const bad of ['0','-1','NaN','Infinity'])assert.throws(()=>shieldTurnSpeed(bad));
});
test('group support players do not steal aggro by hitting secondary mobs',()=>{
 const {attackSecondary}=require('./t58-v13-acceptance.cjs');
 assert(attackSecondary('v13-flat-group',0));
 for(let i=1;i<5;i++)assert(!attackSecondary('v13-irregular-group',i));
 assert(attackSecondary('warden-dungeon',3));
});
test('a continued diagnostic with a recorded failure can never become integrated acceptance',()=>{
 const {scenarios}=require('./t58-v13-acceptance.cjs');
 const records=scenarios.map(scenario=>({scenario,seconds:120}));
 records[0].validationError='shield flanking failed';
 assert.throws(()=>validateSuite(records),/recorded failure/);
});
test('four skill uses require decision-time scores rather than reconstructed estimates',()=>{
 const m={hazards:0,backwards:0,maxQuietTicks:0,uses:['a','b','c','d'],choiceScores:[]};
 assert.throws(()=>validate('v13-flat-choices',m),/decision-time/);
 m.choiceScores=m.uses.map(ability=>({ability,score:35,sampling:'after_host_evaluation_estimate',roles:[],situation:{distance:5}}));
 assert.throws(()=>validate('v13-flat-choices',m),/decision-time/);
 m.choiceScores.forEach(c=>c.sampling='at_selection');
 assert.doesNotThrow(()=>validate('v13-flat-choices',m));
});
test('a profiled trio still starts a warm sampler when it follows other encounters',()=>{
 const {profileWindowCount}=require('./t58-v13-acceptance.cjs');
 assert.equal(profileWindowCount('cold-trio',{v13Run:true,first:false,seconds:120}),1);
 assert.equal(profileWindowCount('cold-trio',{first:true,seconds:120}),3);
 assert.equal(profileWindowCount('v13-flat-distance',{v13Run:true,first:false,seconds:120}),1);
 for(const label of ['profile','warden-dungeon','v13-flat-group'])assert.equal(profileWindowCount(label,{v13Run:true,first:false,seconds:120}),3);
 assert.equal(profileWindowCount('zero',{v13Run:true,first:true,seconds:120}),1);
 assert.equal(profileWindowCount('v13-flat-shield',{v13Run:true,first:true,seconds:120}),0);
});
test('a normal Warden profile can keep debug off while the live decision case keeps it on',()=>{
 const {debugScenario}=require('./t58-v13-acceptance.cjs');
 assert(!debugScenario('warden-dungeon','live-only'));assert(debugScenario('warden-live','live-only'));
 assert(!debugScenario('warden-live','0'));assert(debugScenario('v13-flat-choices','0'));
 assert(debugScenario('v13-flat-debug','0'));assert(!debugScenario('v13-flat-group','1'));
});
test('v13 Warden requires four configured uses while the legacy T58 runner still requires every skill',()=>{
 const {validateAbilityUses}=require('./t58-v13-acceptance.cjs');
 const expected=['a','b','c','d','e','f','g','h'],started=new Set(['a','b','c','d','e','f','g','combo:one']);
 assert.doesNotThrow(()=>validateAbilityUses('warden-live',started,expected,{v13Run:true}));
 assert.throws(()=>validateAbilityUses('warden-live',started,expected,{v13Run:false}),/h/);
 assert.throws(()=>validateAbilityUses('warden-live',new Set(['a','b','c','combo:one']),expected,{v13Run:true}),/four/);
});
