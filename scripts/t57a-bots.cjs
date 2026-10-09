#!/usr/bin/env node
'use strict';
// T57A acceptance on 25566. Lifecycle and fixtures belong to the shell runner.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const h=require('./t51-bot-support.cjs'),r=createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const id=process.env.T57A_ID,dir=process.env.T57A_RESULTS;
if(!process.argv.includes('--run')&&!process.argv.includes('--prepare')){console.log('T57a: genuine jump escape, teammate cage break, nonlethal bomb, 3 s immunity, reload cleanup, spark with five controlled bots.');process.exit(0);}
assert(id&&dir,'Use test-t57a-bots.sh --run');
const abilities=['drain_grab','levitation_cage','bomb_mark','roots','anchor_spear','soul_chain','grab_throw'];
const fixtures=abilities.map(a=>id+'-'+a).concat(id+'-zero',id+'-profile');
if(process.argv.includes('--prepare')) {
 for(const name of fixtures) {
  const ability=abilities.find(a=>name.endsWith('-'+a));
  const params=ability==='drain_grab'?{ticks:300,jumps:3,damage:0}:ability==='levitation_cage'?{ticks:300,damage:0}:ability==='bomb_mark'?{ticks:60,damage:40}:ability==='roots'?{ticks:200}:ability==='anchor_spear'?{ticks:400}:ability==='soul_chain'?{ticks:400,damage:0}:{ticks:100};
  const entries=name.endsWith('-zero')?[]:name.endsWith('-profile')?[{'ability-id':'roots',trigger:'EVERY_X_SECONDS','trigger-value':1,target:'ALL_IN_RADIUS',range:32,'cooldown-ticks':20,chance:1,'telegraph-ticks':0,params:{ticks:200}}]:[{'ability-id':ability,trigger:'ON_SPAWN',target:'ALL_IN_RADIUS',range:32,'cooldown-ticks':0,chance:1,'telegraph-ticks':0,params}];
  fs.writeFileSync(path.join(server,'plugins/CustomDungeons/mobs',name+'.yml'),JSON.stringify({'entity-type':'HUSK','display-name':name,'max-health':5000,attributes:{speed:0,damage:0},boss:true,abilities:entries,'world-boss':{world:'world','x-min':10300,'x-max':10316,'z-min':10300,'z-max':10316,'max-alive':1,radius:48,'minimum-damage':1,reward:{xp:0,items:[],commands:[]}}},null,2),{flag:'wx'});
 }
 process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'));
const log=path.join(server,'logs/latest.log'),mineflayer=r('mineflayer'),names=Array.from({length:5},(_,i)=>'T57A'+i+id.slice(-5));
const heartbeats=[];
const vehicles=Array(5).fill(null);
const attributes=Array.from({length:5},()=>new Map());
const bots=[],chat=names.map(()=>[]),packets=names.map(()=>[]),results=[];
let failure;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function pass(name,data){results.push({name,data});console.log('PASS '+name+' '+JSON.stringify(data??''));}
async function until(check,label,timeout=30000){const end=Date.now()+timeout;while(Date.now()<end){if(failure)throw Error(failure);if(check())return;await delay(100);}throw Error('Timeout: '+label);}
async function cmd(command) {
 assert(!/[\r\n]/.test(command));command=command.replace(/^(tp|kill|give|clear|gamemode|fill|damage|tag|data|summon|execute|effect|item) /,'minecraft:$1 ');command=command.replace(/ run (tp|kill|give|clear|damage|say|data) /g,' run minecraft:$1 ');
 for(let attempt=0;;attempt++){const result=spawnSync(path.join(root,'scripts/test-server.sh'),['cmd',command],{encoding:'utf8',env:{...process.env,CD_TARGET:'agents'}});if(result.status===0){await delay(180);return;}if(result.stderr.includes('Otra operación del servidor está en curso.')&&attempt<10){await delay(60000);await delay(60000);}else throw Error(result.stderr);}
}
let at,bossUuid;
async function spawn(template) {const from=chat[0].length;bots[0].chat('/customdungeon boss spawn '+template);await until(()=>chat[0].slice(from).some(m=>m.includes(template+' apareció en world:')),'spawn '+template);const text=chat[0].slice(from).find(m=>m.includes(template+' apareció en world:'));at=text.match(/world: (-?\d+), (-?\d+), (-?\d+)/).slice(1).map(Number);await cmd(`tag @e[type=husk,x=${at[0]+.5},y=${at[1]},z=${at[2]+.5},distance=..1,limit=1] add ${id}`);await cmd(`data merge entity @e[tag=${id},limit=1] {NoAI:1b}`);const uuidFrom=chat[0].length;bots[0].chat(`/minecraft:data get entity @e[tag=${id},limit=1] UUID`);
 await until(()=>chat[0].slice(uuidFrom).some(m=>/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/.test(m)),'owned boss UUID');
 const parts=chat[0].slice(uuidFrom).map(m=>m.match(/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/)).find(Boolean).slice(1).map(n=>(Number(n)>>>0).toString(16).padStart(8,'0')).join('');
 bossUuid=[parts.slice(0,8),parts.slice(8,12),parts.slice(12,16),parts.slice(16,20),parts.slice(20)].join('-');
}
async function despawn(template){bots[0].chat('/customdungeon boss despawn '+template);await delay(1200);}
function notices(i,needle,from=0){return packets[i].slice(from).filter(p=>p.type==='action_bar'||p.type==='system_chat').some(p=>JSON.stringify(p.data).includes(needle));}
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
  if(['look','position','position_look','flying'].includes(name)) {
   if(!b.realPhysics)return;
   const normalized={...value,flags:{onGround:!!(value.flags?.onGround??value.onGround),hasHorizontalCollision:false}};
   for(const key of ['x','y','z','yaw','pitch'])if(key in normalized)assert(Number.isFinite(normalized[key]),'finite physical '+key);
   write('tick_end',{});write(name,normalized);write('tick_end',{});return;
  }
  write(name,h.menuPacket(name,value));
 };
 b.rawMotion=(name,data)=>{for(const key of ['x','y','z'])if(key in data)assert(Number.isFinite(data[key]),'finite movement '+key);write('tick_end',{});write(name,data);write('tick_end',{});};
 b._client.on('set_passengers',data=>{if(b.entity&&data.passengers.includes(b.entity.id))vehicles[index]=data.entityId;else if(vehicles[index]===data.entityId)vehicles[index]=null;});
 b._client.on('entity_destroy',data=>{if(data.entityIds.includes(vehicles[index]))vehicles[index]=null;});
 b._client.on('entity_update_attributes',data=>{if(b.entity&&data.entityId===b.entity.id)for(const property of data.properties)attributes[index].set(property.key,property);});
 b.physicsEnabled=false;b.on('error',e=>failure=String(e));b.on('kicked',e=>failure='kicked '+JSON.stringify(e));b.on('messagestr',m=>chat[index].push(m));
 b._client.on('packet',(data,meta)=>{if(['boss_bar','sound_effect','stop_sound','action_bar','set_cooldown','system_chat','damage_event'].includes(meta.name))packets[index].push({type:meta.name,data:h.canonical(data),at:Date.now()});});
 await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Spawn timeout '+names[index])),60000);b.once('spawn',()=>{clearTimeout(timer);resolve();});b.once('kicked',reason=>{clearTimeout(timer);reject(Error('Kicked '+JSON.stringify(reason)));});b.once('error',error=>{clearTimeout(timer);reject(error);});});heartbeats.push(setInterval(()=>write('tick_end',{}),50));await delay(400);return b;}

async function checkConsole(command,marker,timeout=5000) {
 const from=chat[0].length;await cmd(command+` run say ${marker}`);await until(()=>chat[0].slice(from).some(m=>m.includes(marker)),marker,timeout);
}
async function arrange() {
 for(const name of names.slice(1))await cmd(`tp ${name} 10308 101 10308`);
 await delay(600);
}
async function mounted(index,expected) {
 await until(()=>((vehicles[index]!==null)===expected),'native passenger state '+index+'='+expected,8000);
}
async function noArtifacts(label) {
 const marker='T57_CLEAN_'+Date.now();
 await checkConsole(`execute unless entity @e[nbt={BukkitValues:{"customdungeons:control_auxiliary":1b}}]`,marker);
 for(let i=1;i<5;i++)await mounted(i,false);
 for(let i=1;i<5;i++)for(const property of attributes[i].values())assert(!property.modifiers.some(m=>m.uuid==='customdungeons:control_immobile'),label+' no live modifiers');
 pass(label+' leaves no helpers, modifiers or passengers');
}
(async()=>{
 let activeTemplate;
 try {
  for(let i=0;i<5;i++)await connect(i);
  for(const name of names)await cmd('op '+name);await cmd('gamemode creative '+names[0]);await cmd(`tp ${names[0]} 10308 104 10308`);
  bots[0].chat('/customdungeon boss list');await until(()=>chat[0].some(m=>m.includes(id+'-drain_grab:')),'definitions loaded',90000);
  await delay(5000);
  await checkConsole('execute if loaded 10296 100 10296 if loaded 10320 100 10320','T57_CHUNKS_LOADED_'+Date.now(),10000);
  await cmd('fill 10300 101 10300 10315 200 10315 air');await cmd('fill 10300 201 10300 10315 319 10315 air');await cmd('fill 10296 100 10296 10320 100 10320 stone');
  for(const name of names.slice(1))await cmd('gamemode survival '+name);
  await arrange();activeTemplate=id+'-drain_grab';let from=packets[1].length;await spawn(activeTemplate);
  await until(()=>notices(1,'¡Pulsa ESPACIO! 0/',from),'escape action bar');await mounted(1,true);
  // Real serverbound input changes, including a held key which must count just once.
  const b=bots[1];b._client.write('player_input',{inputs:{jump:true}});await delay(400);
  for(let i=0;i<12;i++)b._client.write('player_input',{inputs:{jump:true}});
  await mounted(1,true);
  for(let i=0;i<2;i++){b._client.write('player_input',{inputs:{jump:false}});await delay(150);b._client.write('player_input',{inputs:{jump:true}});await delay(150);}
  b._client.write('player_input',{inputs:{jump:false}});await mounted(1,false);pass('real jump rising edges escape draining grab');
  await despawn(activeTemplate);activeTemplate=null;await noArtifacts('drain retirement');
  await delay(3100);await arrange();activeTemplate=id+'-levitation_cage';from=packets[1].length;await spawn(activeTemplate);
  await until(()=>notices(1,'Jaula de levitación',from),'cage warning');await delay(1300);
  const hasLevitation='T57_LEV_'+Date.now();await checkConsole(`execute if entity @a[name=${names[1]},nbt={active_effects:[{id:"minecraft:levitation"}]}]`,hasLevitation);
  // An admin companion remains a legitimate target of this same host; release one player
  // before attacking to ensure the threshold is credited to somebody outside that control.
  await cmd(`damage @e[tag=${id},limit=1] 20 minecraft:player_attack by ${names[2]}`);
  await until(()=>notices(1,'inmunidad',from),'companion releases cage');
  const noLevitation='T57_NOLEV_'+Date.now();await checkConsole(`execute unless entity @a[name=${names[1]},nbt={active_effects:[{id:"minecraft:levitation"}]}]`,noLevitation);pass('teammate damage breaks levitation cage');
  // Reload starts a different control before the sixty-tick immunity has elapsed.
  const releasedAt=Date.now();
  activeTemplate=id+'-roots';await spawn(activeTemplate);await delay(1100);
  const protectedMarker='T57_IMMUNE_'+Date.now();await checkConsole(`execute unless entity @e[type=interaction,nbt={BukkitValues:{"customdungeons:control_auxiliary":1b}},distance=..100]`,protectedMarker);
  assert(Date.now()-releasedAt<3000,'immunity observation was inside the 3 s window');
  pass('released player rejects another control during 3 s immunity');await despawn(activeTemplate);await despawn(id+'-levitation_cage');activeTemplate=null;
  await delay(3300);await arrange();activeTemplate=id+'-roots';await spawn(activeTemplate);await delay(1200);
  const root='T57_ROOT_'+Date.now();await checkConsole(`execute if entity @e[type=interaction,nbt={BukkitValues:{"customdungeons:control_auxiliary":1b}}]`,root);await until(()=>[...attributes[1].values()].some(p=>p.modifiers.some(m=>m.uuid==='customdungeons:control_immobile')),'live root modifier');pass('control succeeds after immunity deadline');
  bots[0].chat('/customdungeon reload');await delay(1200);activeTemplate=null;await noArtifacts('reload during roots');
  await delay(3300);await arrange();for(const name of names.slice(1)){await cmd(`effect clear ${name}`);await cmd(`effect give ${name} minecraft:instant_health 1 10 true`);}
  activeTemplate=id+'-bomb_mark';from=packets[1].length;await spawn(activeTemplate);await delay(4500);
  for(let i=1;i<5;i++){assert(bots[i].health>=1,'full-health bomb victim survives');assert(bots[i].health<20,'bomb did apply damage');}
  pass('40 damage bomb is nonlethal from full health',{health:bots.slice(1).map(b=>b.health)});await despawn(activeTemplate);activeTemplate=null;
  await delay(3300);await arrange();activeTemplate=id+'-drain_grab';from=packets[1].length;await spawn(activeTemplate);await until(()=>notices(1,'¡Pulsa ESPACIO! 0/',from),'second active draining grab');await mounted(1,true);
  bots[0].chat('/customdungeon reload');await delay(1200);activeTemplate=null;await noArtifacts('reload during mounted control');
  // Same five survival bots, same stationary boss and arena, baseline then active roots.
  await cmd('gamemode survival '+names[0]);
  const sparkDir=path.join(server,'plugins/spark');
  for(const label of ['zero','profile']) {
   await delay(3300);for(const name of names){await cmd(`tp ${name} 10308 101 10308`);await cmd(`effect give ${name} minecraft:regeneration 999 2 true`);}
   activeTemplate=id+'-'+label;await spawn(activeTemplate);await delay(4000);
   const before=new Set(fs.readdirSync(sparkDir));await cmd('spark profiler cancel');await cmd('spark profiler start --timeout 60 --save-to-file');
   if(label==='profile'){const marker='T57_PROFILE_CONTROLS_'+Date.now();await checkConsole(`execute if entity @e[type=interaction,nbt={BukkitValues:{"customdungeons:control_auxiliary":1b}}]`,marker);}
   await until(()=>fs.readdirSync(sparkDir).some(n=>n.endsWith('.sparkprofile')&&!before.has(n)),'spark '+label,90000);
   const file=fs.readdirSync(sparkDir).find(n=>n.endsWith('.sparkprofile')&&!before.has(n));fs.copyFileSync(path.join(sparkDir,file),path.join(dir,label+'.sparkprofile'));pass('spark captured '+label,{bots:5,file});await cmd('spark tps');await despawn(activeTemplate);activeTemplate=null;
  }
 }catch(e){process.exitCode=1;failure=e.stack;console.error(failure);}
 finally {
  try{if(activeTemplate)await despawn(activeTemplate);for(const name of names)await cmd('deop '+name);}catch(e){console.error('cleanup: '+e.message);process.exitCode=1;}
  for(const timer of heartbeats)clearInterval(timer);
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({results,packets,chat,failure},null,2));for(const b of bots)if(b)b.quit();
 }
})().then(()=>setTimeout(()=>process.exit(process.exitCode||0),1000));
