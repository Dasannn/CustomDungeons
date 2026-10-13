'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {normalRoomKiting,normalRoomWaypoint,arenaEdge,nearbyFloorAim,irregularRampWaypoint,attackEveryTicks,engagedGoal,returnRally,rangedClient,combatJump}=require('./t58-bot-movement.cjs');

test('normal-room bots engage both phases before kiting through the doorway',()=>{
  for(const health of [1,.99,.8,.5,.3])for(const tick of [0,50,99,400])
    assert.equal(normalRoomKiting('warden-room',tick,health),false);
});
test('the boundary leg lasts ten seconds, followed by ten seconds of walking back',()=>{
  for(const tick of [0,99,100,159,199,400,599])
    assert.equal(normalRoomKiting('warden-room',tick,.29),true);
  for(const tick of [200,250,399,600,799])
    assert.equal(normalRoomKiting('warden-room',tick,.29),false);
});
test('other encounters retain their ordinary movement',()=>{
  for(const scenario of ['warden-dungeon','warden-live','charge','cold-trio'])
    assert.equal(normalRoomKiting(scenario,0,.2),false);
});
test('a boss beside the wall is led to the inside of the doorway before crossing',()=>{
  assert.deepEqual(normalRoomWaypoint(6.01,10.05,2),{x:0,z:8});
  assert.deepEqual(normalRoomWaypoint(0,5,2),{x:0,z:8});
  assert.deepEqual(normalRoomWaypoint(.8,7,2),{x:0,z:14});
  for(let index=0;index<5;index++)assert(Math.abs(normalRoomWaypoint(0,8,index).x)<=.7);
});
test('covered zone clients turn toward the interior before reaching the physical walls',()=>{
 for(const scenario of ['zones-room-inverted_gravity','zones-room-boss_totem','profile','zero'])
  assert.equal(arenaEdge(scenario,true),10);
 // Live clients reach their real host edge with at most a one-block margin.
 for(const scenario of ['zones-live-inverted_gravity','zones-live-boss_totem']) {
  assert.equal(arenaEdge(scenario,true),24);
 }
 for(const scenario of ['warden-live','warden-dungeon','charge','cold-trio'])
  assert.equal(arenaEdge(scenario,false),22);
 assert.equal(arenaEdge('warden-live',true),24);
 assert.equal(arenaEdge('warden-room',false),10);
});
test('zero and mixed zone clients initiate combat with the same real attack frequency',()=>{
 for(const scenario of ['zero','profile','zones-live-rift'])assert.equal(attackEveryTicks(true,scenario,1),20);
 assert.equal(attackEveryTicks(false,'charge',1),100);
 assert.equal(attackEveryTicks(false,'warden-live',.5),20);
 assert.equal(attackEveryTicks(false,'warden-live',.2),100);
 assert.equal(attackEveryTicks(true,'warden-irregular',.2),100);
});
test('one real client covers the return origin while the others can reach the requested edge',()=>{
 const edge={x:24.35,z:24.35},enemy={x:22,z:23},rally={x:1,z:2};
 assert.equal(engagedGoal(0,8,edge,enemy,rally),rally);
 assert.equal(engagedGoal(4,8,edge,enemy,rally),edge);
 assert.equal(engagedGoal(4,32,edge,enemy,rally),edge);
 assert.equal(engagedGoal(3,32,edge,enemy,rally),edge);
 assert.equal(engagedGoal(1,16,edge,enemy,rally),enemy);
 assert.equal(engagedGoal(0,6.9,edge,enemy,rally),rally);
});
test('the moving defender stays within melee reach of every ordinary spawner return',()=>{
 for(let phase=0;phase<=120;phase++)for(let angle=0;angle<Math.PI*2;angle+=.2) {
  const goal=returnRally(phase);
  assert(Math.hypot(goal.x-(2+Math.cos(angle)),goal.z-Math.sin(angle))<2);
 }
 assert.notDeepEqual(returnRally(0),returnRally(4));
});
test('zone scouts use bows without slowing the original warden phase transitions',()=>{
 for(const scenario of ['warden-live','warden-irregular'])for(let index=0;index<5;index++)assert.equal(rangedClient(true,scenario,index,8,.5),false);
 assert.equal(rangedClient(true,'warden-irregular',3,8,.3),false);
 assert.equal(rangedClient(true,'warden-irregular',4,8,.3),true);
 assert.equal(rangedClient(false,'warden-live',4,8,.5),true);
 assert.equal(rangedClient(true,'zones-live-rift',3,8,1),true);
});
test('warden players jump twelve ticks before their staggered ordinary melee hits',()=>{
 for(let index=0;index<5;index++) {
  const jump=(index*3-12+20)%20;
  assert.equal(combatJump(true,'warden-irregular',jump,index,.5,false),true);
  assert.equal(combatJump(true,'warden-irregular',(jump+1)%20,index,.5,false),false);
 }
 assert.equal(combatJump(true,'warden-irregular',1,0,.5,true),true);
 for(let tick=0;tick<80;tick++)assert.equal(combatJump(false,'warden-live',tick,2,.5,false),Math.floor(tick/10+2)%13===0);
});
test('live setup waits for the server to see a nearby floor rather than a wall, roof or distant floor',()=>{
 const aim={x:0,y:101,z:0,hitX:2,hitY:100,hitZ:0};
 assert(nearbyFloorAim(aim,101));
 for(const change of [{hitY:102},{hitY:109},{hitX:12},{hitX:undefined},{x:NaN}])
  assert.equal(nearbyFloorAim({...aim,...change},101),false);
});
test('irregular clients approach the ramp from its low end instead of cutting across its high side',()=>{
 assert.deepEqual(irregularRampWaypoint({x:24,z:24},0,2),{stage:0,x:0,z:0});
 assert.deepEqual(irregularRampWaypoint({x:.2,z:.1},0,2),{stage:1,x:-8,z:0});
 assert.deepEqual(irregularRampWaypoint({x:-7.7,z:.1},1,2),{stage:2,x:-15,z:0});
 assert.deepEqual(irregularRampWaypoint({x:-11.4,z:5.3},1,2),{stage:1,x:-8,z:0});
});
test('the five real clients ascend in separate lanes within the same four-block ramp',()=>{
 const goals=Array.from({length:5},(_,i)=>irregularRampWaypoint({x:-8,z:0},1,i));
 assert.equal(new Set(goals.map(g=>g.z)).size,5);
 assert(goals.every(g=>g.stage===2&&g.x===-15&&Math.abs(g.z)<=1.2));
});
test('silence defenders try a real consumable in every twenty-tick window',()=>{
 const {silenceAttempt}=require('./t58-bot-movement.cjs');
 for(let index=0;index<5;index++)for(let start=0;start<100;start++)assert(Array.from({length:20},(_,offset)=>silenceAttempt(true,'zones-live-silence',start+offset,index)).some(Boolean));
 for(const scenario of ['zones-room-rift','warden-live','silence'])for(let tick=0;tick<100;tick++)assert.equal(silenceAttempt(false,scenario,tick,0),false);
 assert.equal(silenceAttempt(true,'zones-room-silence',2,2),true);
});
test('irregular warden players navigate to a melee ring rather than cutting through the closed pit',()=>{
 const {wardenMeleeGoal}=require('./t58-bot-movement.cjs');
 for(let index=0;index<5;index++) {
  const goal=wardenMeleeGoal({x:3,z:-4},index,3.8);
  assert(Math.abs(Math.hypot(goal.x-3,goal.z+4)-3.8)<1e-8);
 }
});
test('warden players refresh enchanted golden apples every twenty-five seconds using their inventory',()=>{
 const {wardenAppleDue}=require('./t58-bot-movement.cjs');
 assert.equal(wardenAppleDue(true,'warden-irregular',25000,25000),true);
 assert.equal(wardenAppleDue(true,'warden-live',24999,25000),false);
 assert.equal(wardenAppleDue(false,'warden-live',25000,25000),false);
 assert.equal(wardenAppleDue(true,'zones-live-charge',25000,25000),false);
});
