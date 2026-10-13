'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {ParticleEvidence,contains}=require('./t58-particle-evidence.cjs');
const polygon=[[0,101.1,0],[2,101.1,0],[2,101.1,2],[0,101.1,2]];
test('a server flag never proves that a recipient saw the warning',()=>{
 const e=new ParticleEvidence();assert.equal(e.warned('bot',{at:1000,since:500,polygons:[polygon]}),false);
});
test('every vertex must actually arrive before the effect, for that player and cast',()=>{
 const e=new ParticleEvidence();const hit={at:1000,since:500,polygons:[polygon]};
 for(const [x,y,z] of polygon)e.receive('other',{x,y,z,particle:{type:'enchant'},amount:1},700);
 assert.equal(e.warned('bot',hit),false);
 for(const [x,y,z] of polygon.slice(0,3))e.receive('bot',{x,y,z,particle:{type:'enchant'},amount:1},700);
 assert.equal(e.warned('bot',hit),false);
 const [x,y,z]=polygon[3];e.receive('bot',{x,y,z,particle:{type:'enchant'},amount:1},1100);
 assert.equal(e.warned('bot',hit),false);
 e.receive('bot',{x,y,z,particle:{type:'enchant'},amount:1},999);assert.equal(e.warned('bot',hit),true);
 hit.since=1001;assert.equal(e.warned('bot',hit),false);
});
test('animation, empty particles and another height cannot authorize a warning',()=>{
 for(const change of [{particle:{type:'end_rod'}},{amount:0},{y:102.1}]) {
  const e=new ParticleEvidence();for(const [x,y,z] of polygon)e.receive('bot',{x,y,z,particle:{type:'enchant'},amount:1,...change},700);
  assert.equal(e.warned('bot',{at:1000,since:500,polygons:[polygon]}),false);
 }
});
test('clipped polygon containment includes boundaries and excludes its missing circle caps',()=>{
 assert(contains(polygon,1,1));assert(contains(polygon,0,1));assert(!contains(polygon,2.01,1));
 assert(!contains([[1,0,1],[2,0,1],[1,0,2]],1.6,1.6));
});
test('zone containment is independently checked against the received polygon',()=>{
 const {insideWarning}=require('./t58-particle-evidence.cjs');
 const hit={inside:true,geometryRequired:true,x:1.5,z:1.5,polygons:[[[0,101,0],[2,101,0],[0,101,2]]]};
 assert.equal(insideWarning(hit),false);
 hit.x=.5;hit.z=.5;assert.equal(insideWarning(hit),true);
 hit.inside=false;assert.equal(insideWarning(hit),false);
 hit.inside=true;hit.geometryRequired=false;assert.equal(insideWarning(hit),true);
});
