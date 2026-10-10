'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {normalRoomKiting,normalRoomWaypoint}=require('./t58-bot-movement.cjs');

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
