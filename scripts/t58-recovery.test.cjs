'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {recoveryPlan,validateSnapshot}=require('./t58-recovery.cjs');
const server='/agents',owner='t58b-123abc',manifest=[server+'/plugins/CustomDungeons/mobs/'+owner+'-rift.yml',server+'/plugins/CustomDungeons/dungeons/'+owner+'-rift-i.yml'];
test('recovery claims only the manifest nonce and its five operators',()=>{
 const plan=recoveryPlan(manifest,server);assert.equal(plan.nonce,owner);assert.deepEqual(plan.names,Array.from({length:5},(_,i)=>'T58123abc'+i));
});
test('recovery refuses foreign plugins, sibling servers and noncanonical paths',()=>{
 for(const bad of ['/agents/plugins/Other.jar','/user/plugins/CustomDungeons/mobs/t58b-123abc-rift.yml','/agents/plugins/CustomDungeons/mobs/../mobs/t58b-123abc-rift.yml','/agents/plugins/CustomDungeons/mobs/t58b-123abc-rift.yml\n'])assert.throws(()=>recoveryPlan([bad],server));
});
test('recovery refuses a mixture of nonces and an empty manifest',()=>{
 assert.throws(()=>recoveryPlan([],server));assert.throws(()=>recoveryPlan([...manifest,manifest[0].replace('123abc','654def')],server));
});
const recipe={version:1,world:'cd_dungeons',bounds:[0,-1,0,1,0,1]};
function snapshot(){const cells=[];for(let x=0;x<=1;x++)for(let y=-1;y<=0;y++)for(let z=0;z<=1;z++)cells.push({x,y,z,data:'minecraft:air'});for(let x=54;x<=58;x++)for(let z=-2;z<=2;z++)cells.push({x,y:-1,z,data:'minecraft:stone'});return {version:1,owner,world:'cd_dungeons',worldId:'00000000-0000-0000-0000-000000000001',cells};}
test('a complete journal covers the arena and the exit without extending its ownership',()=>{
 assert.equal(validateSnapshot(snapshot(),recipe,owner),owner);
});
test('a journal cannot restore another world, owner or schema',()=>{
 for(const change of [{world:'world'},{owner:'t58b-654def'},{version:2},{worldId:'invalid'}])assert.throws(()=>validateSnapshot({...snapshot(),...change},recipe,owner));
});
test('missing and duplicated journal cells are rejected before mutation',()=>{
 const missing=snapshot();missing.cells.pop();assert.throws(()=>validateSnapshot(missing,recipe,owner));
 const duplicate=snapshot();duplicate.cells[0]=duplicate.cells[1];assert.throws(()=>validateSnapshot(duplicate,recipe,owner));
});
test('out-of-volume, fractional and invalid block-data records cannot enter recovery',()=>{
 for(const change of [{x:2},{y:1},{z:.5},{data:'minecraft:air\nstop'},{data:null}]){const bad=snapshot();bad.cells[0]={...bad.cells[0],...change};assert.throws(()=>validateSnapshot(bad,recipe,owner));}
});
