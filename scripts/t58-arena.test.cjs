'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const arena=require('./t58-arena-v1.json');
test('the versioned arena has every required irregular terrain feature',()=>{
 assert.equal(arena.version,1);assert.equal(arena.world,'cd_dungeons');
 assert.deepEqual(arena.features,['slopes','steps','slabs','stairs','drops-2-4','holes','water-edge','lava-edge','tall-grass','trees','low-ceilings']);
 const types=arena.fills.map(f=>f[6]);
 for(const prefix of ['stone_slab','stone_stairs','water','lava','tall_grass','oak_log','oak_leaves'])assert(types.some(t=>t.startsWith(prefix)));
 assert(arena.fills.some(f=>f[1]===-3&&f[6]==='air'));assert(arena.fills.some(f=>f[4]===3&&f[6]==='stone'));
});
test('every recipe mutation lies inside the complete restoration journal volume',()=>{
 const [a,b,c,d,e,f]=arena.bounds;
 for(const [x1,y1,z1,x2,y2,z2,data] of arena.fills) {
  assert([x1,y1,z1,x2,y2,z2].every(Number.isInteger));assert.equal(typeof data,'string');
  assert(a<=x1&&x1<=x2&&x2<=d&&b<=y1&&y1<=y2&&y2<=e&&c<=z1&&z1<=z2&&z2<=f);
 }
});
test('the three-block hole deliberately has no escape staircase',()=>{
 function floor(x,z) {
  const blocks=new Map(Array.from({length:7},(_,i)=>[-7+i,'stone']));
  for(const [a,b,c,d,e,f,type] of arena.fills)if(a<=x&&x<=d&&c<=z&&z<=f)for(let y=b;y<=e;y++)blocks.set(y,type);
  return Math.max(...[...blocks].filter(([,type])=>type==='stone').map(([y])=>y+1));
 }
 assert.deepEqual([-4,-3,-2,-1].map(x=>floor(x,9)),[-3,0,0,0]);
});
