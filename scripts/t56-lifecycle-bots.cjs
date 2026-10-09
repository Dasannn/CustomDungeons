#!/usr/bin/env node
'use strict';
// T56 acceptance on 25566. Lifecycle and fixtures belong to the shell runner.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const h=require('./t51-bot-support.cjs'),r=createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const id=process.env.T56_ID,dir=process.env.T56_RESULTS;
if(!process.argv.includes('--run')&&!process.argv.includes('--prepare')){console.log('T56 reviewer lifecycle regressions: fall after retirement and live-test cooldown after reload.');process.exit(0);}
assert(id&&dir,'Use test-t56-bots.sh --run');
const fixtures=[id,id+'-zero',id+'-fair'];
if(process.argv.includes('--prepare')) {
 for(const name of fixtures)fs.writeFileSync(path.join(server,'plugins/CustomDungeons/mobs',name+'.yml'),JSON.stringify({'entity-type':'HUSK','display-name':name,'max-health':name.endsWith('-fair')?180:5000,'attributes':{'speed':0,'damage':2,'armor':0,'armor-toughness':0},'boss':true,'intelligence':{'level':name.endsWith('-zero')?0:5,'weak-point':'back'},'world-boss':{'world':'world','x-min':10100,'x-max':10116,'z-min':10100,'z-max':10116,'max-alive':1,'radius':48,'minimum-damage':1,'reward':{'xp':0,'items':[],'commands':[]}}},null,2),{flag:'wx'});
 process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'));
const log=path.join(server,'logs/latest.log'),mineflayer=r('mineflayer'),names=Array.from({length:2},(_,i)=>'T56'+i+id.slice(-5));
const heartbeats=[];
const bots=[],chat=names.map(()=>[]),packets=names.map(()=>[]),results=[];
let failure;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function pass(name,data){results.push({name,data});console.log('PASS '+name+' '+JSON.stringify(data??''));}
async function until(check,label,timeout=30000){const end=Date.now()+timeout;while(Date.now()<end){if(failure)throw Error(failure);if(check())return;await delay(100);}throw Error('Timeout: '+label);}
async function cmd(command) {
 assert(!/[\r\n]/.test(command));command=command.replace(/^(tp|kill|give|clear|gamemode|fill|damage|tag|data|summon|execute|effect|item) /,'minecraft:$1 ');command=command.replace(/ run (tp|kill|give|clear|damage|say|data) /g,' run minecraft:$1 ');
 for(let attempt=0;;attempt++){const result=spawnSync(path.join(root,'scripts/test-server.sh'),['cmd',command],{encoding:'utf8',env:{...process.env,CD_TARGET:'agents'}});if(result.status===0){await delay(180);return;}if(result.stderr.includes('Otra operación del servidor está en curso.')&&attempt<10){await delay(60000);await delay(60000);}else throw Error(result.stderr);}
}
let at;
async function spawn(template=id) {const from=chat[0].length;bots[0].chat('/customdungeon boss spawn '+template);await until(()=>chat[0].slice(from).some(m=>m.includes(template+' apareció en world:')),'spawn '+template);const text=chat[0].slice(from).find(m=>m.includes(template+' apareció en world:'));at=text.match(/world: (-?\d+), (-?\d+), (-?\d+)/).slice(1).map(Number);await cmd(`tag @e[type=husk,x=${at[0]+.5},y=${at[1]},z=${at[2]+.5},distance=..1,limit=1] add ${id}`);await cmd(`data merge entity @e[tag=${id},limit=1] {NoAI:1b}`);for(const name of names.slice(1))await cmd(`tp ${name} ${at[0]+1.5} ${at[1]} ${at[2]+1.5}`);await delay(1000);}
async function despawn(template=id){bots[0].chat('/customdungeon boss despawn '+template);await delay(1200);}
function notices(i,needle,from=0){return packets[i].slice(from).filter(p=>p.type==='action_bar'||p.type==='system_chat').some(p=>JSON.stringify(p.data).includes(needle));}
function cooldown(i,from=0){return packets[i].slice(from).filter(p=>p.type==='set_cooldown');}
async function hit(amount,player=1,type='minecraft:player_attack'){await cmd(`damage @e[tag=${id},limit=1] ${amount} ${type} by ${names[player]}`);await delay(450);}
async function eat(i,item='golden_apple') {await cmd(`give ${names[i]} minecraft:${item} 3`);await delay(400);await bots[i].equip(bots[i].inventory.items().find(x=>x.name===item),'hand');await bots[i].consume();await delay(150);}
async function connect(index){const b=mineflayer.createBot({host:'127.0.0.1',port:25566,username:names[index],auth:'offline',version:'26.1',physicsEnabled:false});bots[index]=b;
 const write=b._client.write.bind(b._client),acknowledged=new Set();
 b._client.write=(name,value)=>{
  if(name==='teleport_confirm') {
   if(acknowledged.has(value.teleportId))return;
   acknowledged.add(value.teleportId);
   const p=b.entity.position,conv=r('mineflayer/lib/conversions');
   const position={x:p.x,y:p.y,z:p.z,yaw:conv.toNotchianYaw(b.entity.yaw),pitch:conv.toNotchianPitch(b.entity.pitch),flags:{onGround:false,hasHorizontalCollision:false}};
   assert([position.x,position.y,position.z,position.yaw,position.pitch].every(Number.isFinite));
   write(name,value);write('tick_end',{});write('position_look',position);write('tick_end',{});return;
  }
  if(['look','position','position_look','flying'].includes(name))return;
  write(name,h.menuPacket(name,value));
 };
 b.rawMotion=(name,data)=>{for(const key of ['x','y','z'])if(key in data)assert(Number.isFinite(data[key]),'finite movement '+key);write('tick_end',{});write(name,data);write('tick_end',{});};
 b.physicsEnabled=false;b.on('error',e=>failure=String(e));b.on('kicked',e=>failure='kicked '+JSON.stringify(e));b.on('messagestr',m=>chat[index].push(m));
 b._client.on('packet',(data,meta)=>{if(['boss_bar','sound_effect','stop_sound','action_bar','set_cooldown','system_chat','damage_event'].includes(meta.name))packets[index].push({type:meta.name,data:h.canonical(data)});});
 await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Spawn timeout '+names[index])),60000);b.once('spawn',()=>{clearTimeout(timer);resolve();});b.once('kicked',reason=>{clearTimeout(timer);reject(Error('Kicked '+JSON.stringify(reason)));});b.once('error',error=>{clearTimeout(timer);reject(error);});});heartbeats.push(setInterval(()=>write('tick_end',{}),50));await delay(400);return b;}

async function click(slot) {await bots[0].clickWindow(slot,0,0);await delay(400);}
async function openLive() {
 const b=bots[0];b.chat('/customdungeon');await until(()=>b.currentWindow?.slots[21]?.name==='book','main menu');await click(21);
 await until(()=>b.currentWindow,'library');
 const find=()=>{const w=b.currentWindow;return w.slots.slice(9,w.inventoryStart-9).findIndex(i=>i&&JSON.stringify(h.canonical(i)).includes(id))+9;};
 let slot=find();for(let n=0;slot<9&&n<15;n++){await click(b.currentWindow.inventoryStart-4);slot=find();}
 assert(slot>=9);await click(slot);await until(()=>b.currentWindow?.slots[38]?.name==='target','editor');await click(38);if(b.currentWindow)b.closeWindow(b.currentWindow);
 await until(()=>chat[0].some(m=>/Prueba iniciada|Test started/.test(m)),'live test started');
}
(async()=>{
 try {
  for(let i=0;i<2;i++)await connect(i);
  assert(Object.values(bots[0].players).every(p=>names.includes(p.username)),"agents server occupied");
  for(const n of names)await cmd('op '+n);
  await cmd('gamemode creative '+names[0]);await cmd(`tp ${names[0]} 10108 103 10108`);await cmd('gamemode survival '+names[1]);await cmd('effect clear '+names[1]);
  await spawn();const from=packets[1].length;
  await cmd(`item replace entity ${names[1]} armor.chest with minecraft:elytra`);await cmd(`give ${names[1]} minecraft:firework_rocket 16`);await cmd(`tp ${names[1]} ${at[0]+1.5} ${at[1]+25} ${at[2]+1.5}`);
  const b=bots[1];await delay(300);const start=b.entity.position.clone();
  b.rawMotion('position',{x:start.x,y:start.y-.2,z:start.z,flags:{onGround:false,hasHorizontalCollision:false}});await delay(150);
  b.rawMotion('position',{x:start.x,y:start.y-.4,z:start.z,flags:{onGround:false,hasHorizontalCollision:false}});await delay(100);
  b._client.write('entity_action',{entityId:b.entity.id,actionId:'start_fall_flying',jumpBoost:0});await delay(150);
  await b.equip(b.inventory.items().find(x=>x.name==='firework_rocket'),'hand');b.activateItem();await delay(150);b.deactivateItem();
  await until(()=>notices(1,'derribo',from),'ground warning');await until(()=>cooldown(1,from).some(p=>p.data.cooldownTicks>=120),'ground response actually applied');
  assert.equal(b.health,20);await cmd(`damage ${names[1]} 100 minecraft:fall`);await delay(500);assert.equal(b.health,20);pass('fall guard positive control while grounded adaptation exists');
  await despawn();await cmd(`damage ${names[1]} 100 minecraft:fall`);await delay(700);assert.equal(b.health,20);assert(!chat[0].some(m=>m.includes(names[1]+" hit the ground too hard")));pass('regression: fall safety survives boss retirement',{healthBefore:20,healthAfter:b.health});
  // Respawn and check reload during an actual live test and item adaptation.
  await delay(1200);await cmd(`tp ${names[1]} 10109 101 10109`);await cmd(`tp ${names[0]} 10108 101 10108`);
  await openLive();await delay(1200);let consumption=packets[1].length;await eat(1);await eat(1);await until(()=>cooldown(1,consumption).some(p=>p.data.cooldownTicks===160),'live apple cooldown');
  const reloadFrom=packets[1].length;bots[0].chat('/customdungeon reload');await until(()=>chat[0].some(m=>/recargad|reloaded/i.test(m)),'reload completed');await delay(300);
  const zeroBeforeStop=cooldown(1,reloadFrom).filter(p=>p.data.cooldownTicks===0);assert(zeroBeforeStop.length>0,'Reload must restore live-test cooldown before stop');pass('regression: reload rolls back actual live-test cooldown',zeroBeforeStop);
  bots[0].chat('/customdungeon livetest stop');await until(()=>cooldown(1,reloadFrom).some(p=>p.data.cooldownTicks===0),'live stop clears cooldown');pass('live stop after reload is harmless');
 } catch(e) {console.error(e.stack);process.exitCode=1;} finally {
  try {for(const n of names)await cmd('deop '+n);}catch(e){console.error(e.message);}
  for(const t of heartbeats)clearInterval(t);fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({results,packets,chat,failure},null,2));for(const b of bots)if(b)b.quit();
 }
})().then(()=>setTimeout(()=>process.exit(process.exitCode||0),1000));
