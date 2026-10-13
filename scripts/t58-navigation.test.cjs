'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {nextWalkingPoint}=require('./t58-navigation.cjs');

test('walking follows collision-safe intermediate nodes instead of cutting a corner toward the final goal',()=>{
 const nodes=[{x:0.5,y:101,z:.5},{x:.5,y:101,z:1.5},{x:1.5,y:101,z:1.5}];
 assert.deepEqual(nextWalkingPoint({x:.5,y:101,z:.5},nodes,{x:10,z:10}),{x:.5,y:101,z:1.5});
 assert.equal(nodes.length,2);
});
test('a higher stair node is retained until the real jump reaches its surface',()=>{
 const nodes=[{x:.5,y:102,z:.5},{x:1.5,y:102,z:.5}];
 assert.equal(nextWalkingPoint({x:.5,y:101,z:.5},nodes,{x:10,z:10}),nodes[0]);
 assert.deepEqual(nextWalkingPoint({x:.5,y:102,z:.5},nodes,{x:10,z:10}),{x:1.5,y:102,z:.5});
});
test('half-block support and an exhausted path preserve the requested edge margin',()=>{
 const nodes=[{x:.5,y:100.5,z:.5}],goal={x:24.35,z:24.35};
 assert.equal(nextWalkingPoint({x:.5,y:100.5,z:.5},nodes,goal),goal);
});
test('an incomplete route stops at its last safe node instead of walking directly into a closed hole',()=>{
 const position={x:-5,y:98,z:9},goal={x:0,y:101,z:0};
 assert.equal(nextWalkingPoint(position,[],goal,false),position);
 assert.equal(nextWalkingPoint(position,[],goal,true),goal);
 const node={x:-6,y:98,z:9};assert.equal(nextWalkingPoint(position,[node],goal,false),node);
});
