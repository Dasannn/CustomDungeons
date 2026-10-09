#!/usr/bin/env node
'use strict';
// T56 acceptance on 25566. Lifecycle and fixtures belong to the shell runner.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const h=require('./t51-bot-support.cjs'),r=createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const id=process.env.T56_ID,dir=process.env.T56_RESULTS;
if(!process.argv.includes('--run')&&!process.argv.includes('--prepare')){console.log('T56: repeated apples/criticals, variation, interrupted grounding, coordinated three-bot victory, spark level 0/5 with five nearby bots.');process.exit(0);}
assert(id&&dir,'Use test-t56-bots.sh --run');
const fixtures=[id,id+'-zero',id+'-fair'];
if(process.argv.includes('--prepare')) {
 for(const name of fixtures)fs.writeFileSync(path.join(server,'plugins/CustomDungeons/mobs',name+'.yml'),JSON.stringify({'entity-type':'HUSK','display-name':name,'max-health':name.endsWith('-fair')?1200:5000,'attributes':{'speed':name.endsWith('-fair')?.23:0,'damage':name.endsWith('-fair')?8:2,'armor':name.endsWith('-fair')?6:0,'armor-toughness':0},'boss':true,'intelligence':{'level':name.endsWith('-zero')?0:5,'weak-point':'back'},'world-boss':{'world':'world','x-min':10100,'x-max':10116,'z-min':10100,'z-max':10116,'max-alive':1,'radius':48,'minimum-damage':1,'reward':{'xp':0,'items':[],'commands':[]}}},null,2),{flag:'wx'});
 process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'));
const log=path.join(server,'logs/latest.log'),mineflayer=r('mineflayer'),names=Array.from({length:5},(_,i)=>'T56'+i+id.slice(-5));
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
let at,bossUuid;
async function spawn(template=id) {const from=chat[0].length;bots[0].chat('/customdungeon boss spawn '+template);await until(()=>chat[0].slice(from).some(m=>m.includes(template+' apareció en world:')),'spawn '+template);const text=chat[0].slice(from).find(m=>m.includes(template+' apareció en world:'));at=text.match(/world: (-?\d+), (-?\d+), (-?\d+)/).slice(1).map(Number);await cmd(`tag @e[type=husk,x=${at[0]+.5},y=${at[1]},z=${at[2]+.5},distance=..1,limit=1] add ${id}`);await cmd(`data merge entity @e[tag=${id},limit=1] {NoAI:1b}`);const uuidFrom=chat[0].length;bots[0].chat(`/minecraft:data get entity @e[tag=${id},limit=1] UUID`);
 await until(()=>chat[0].slice(uuidFrom).some(m=>/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/.test(m)),'owned boss UUID');
 const parts=chat[0].slice(uuidFrom).map(m=>m.match(/\[I;\s*(-?\d+),\s*(-?\d+),\s*(-?\d+),\s*(-?\d+)\]/)).find(Boolean).slice(1).map(n=>(Number(n)>>>0).toString(16).padStart(8,'0')).join('');
 bossUuid=[parts.slice(0,8),parts.slice(8,12),parts.slice(12,16),parts.slice(16,20),parts.slice(20)].join('-');
 for(const name of names.slice(1))await cmd(`tp ${name} ${at[0]+1.5} ${at[1]} ${at[2]+1.5}`);await delay(1000);}
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
  if(['look','position','position_look','flying'].includes(name)) {
   if(!b.realPhysics)return;
   const normalized={...value,flags:{onGround:!!(value.flags?.onGround??value.onGround),hasHorizontalCollision:false}};
   for(const key of ['x','y','z','yaw','pitch'])if(key in normalized)assert(Number.isFinite(normalized[key]),'finite physical '+key);
   write('tick_end',{});write(name,normalized);write('tick_end',{});return;
  }
  write(name,h.menuPacket(name,value));
 };
 b.rawMotion=(name,data)=>{for(const key of ['x','y','z'])if(key in data)assert(Number.isFinite(data[key]),'finite movement '+key);write('tick_end',{});write(name,data);write('tick_end',{});};
 b.physicsEnabled=false;b.on('error',e=>failure=String(e));b.on('kicked',e=>failure='kicked '+JSON.stringify(e));b.on('messagestr',m=>chat[index].push(m));
 b._client.on('packet',(data,meta)=>{if(['boss_bar','sound_effect','stop_sound','action_bar','set_cooldown','system_chat','damage_event'].includes(meta.name))packets[index].push({type:meta.name,data:h.canonical(data),at:Date.now()});});
 await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Spawn timeout '+names[index])),60000);b.once('spawn',()=>{clearTimeout(timer);resolve();});b.once('kicked',reason=>{clearTimeout(timer);reject(Error('Kicked '+JSON.stringify(reason)));});b.once('error',error=>{clearTimeout(timer);reject(error);});});heartbeats.push(setInterval(()=>write('tick_end',{}),50));await delay(400);return b;}
(async()=>{
 try {
  for(let i=0;i<5;i++)await connect(i);
  for(const name of names)await cmd('op '+name);await cmd('gamemode creative '+names[0]);await cmd(`tp ${names[0]} 10108 103 10108`);
  bots[0].chat('/customdungeon boss list');await until(()=>chat[0].some(m=>m.includes(id+':')),'definitions loaded',90000);
  await cmd('fill 10100 101 10100 10115 200 10115 air');await cmd('fill 10100 201 10100 10115 319 10115 air');await cmd('fill 10096 100 10096 10120 100 10120 stone');
  for(const name of names.slice(1))await cmd('gamemode survival '+name);
  if(!process.env.T56_FAIR_ONLY) {
  for(const name of names.slice(1))await cmd(`effect give ${name} minecraft:regeneration 999 1 true`);
  await spawn();let from=packets[1].length;await eat(1);await eat(1);await until(()=>notices(1,'consumibles',from),'repeated apples announced');await until(()=>cooldown(1,from).some(p=>p.data.cooldownTicks>0),'apple cooldown');pass('repeated apples cause temporary announced adaptation',cooldown(1,from));
  from=packets[1].length;await despawn();await until(()=>cooldown(1,from).some(p=>p.data.cooldownTicks===0),'despawn removes item cooldown');pass('despawn removes player modifications');
  await spawn();from=packets[1].length;await eat(1);await hit(2);await eat(1);await delay(1200);assert(!notices(1,'consumibles',from));pass('varied tactic resets consumption detector');await despawn();
  await spawn();from=packets[1].length;
  await cmd(`give ${names[1]} minecraft:diamond_sword`);await bots[1].equip(bots[1].inventory.items().find(x=>x.name==='diamond_sword'),'hand');await delay(1200);
  const target=()=>Object.values(bots[1].entities).find(e=>e.uuid===bossUuid&&e.position.distanceTo(bots[1].entity.position)<6);
  // Manual falling movements are genuine critical attacks, with a normal grounded hit for variation.
  async function attack(critical){const b=bots[1];
   await cmd(`tp ${names[1]} ${at[0]+1.5} ${at[1]+(critical?1.5:0)} ${at[2]+1.5}`);await delay(300);
   const p=b.entity.position.clone();if(critical){b.rawMotion('position',{x:p.x,y:p.y-.2,z:p.z,flags:{onGround:false,hasHorizontalCollision:false}});await delay(150);b.rawMotion('position',{x:p.x,y:p.y-.4,z:p.z,flags:{onGround:false,hasHorizontalCollision:false}});}
   else b.rawMotion('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:true,hasHorizontalCollision:false}});
   assert(target(),'visible target');b.attack(target());await delay(1300);
  }
  await attack(true);await attack(true);await until(()=>notices(1,'críticos',from),'repeated critical adaptation');pass('genuine repeated critical attacks adapt');await despawn();
  await spawn();from=packets[1].length;await attack(true);await attack(false);await attack(true);await delay(1000);assert(!notices(1,'críticos',from));pass('varying critical and normal hits prevents adaptation');await despawn();
  await spawn();from=packets[1].length;await cmd(`item replace entity ${names[1]} armor.chest with minecraft:elytra`);await cmd(`give ${names[1]} minecraft:firework_rocket 16`);
  const b=bots[1];await cmd(`tp ${names[1]} ${at[0]+1.5} ${at[1]+3} ${at[2]+1.5}`);await delay(300);const p=b.entity.position.clone();
  b.rawMotion('position',{x:p.x,y:p.y-.2,z:p.z,flags:{onGround:false,hasHorizontalCollision:false}});await delay(150);b.rawMotion('position',{x:p.x,y:p.y-.4,z:p.z,flags:{onGround:false,hasHorizontalCollision:false}});await delay(100);
  b._client.write('entity_action',{entityId:b.entity.id,actionId:'start_fall_flying',jumpBoost:0});await delay(100);
  const startedFlying='T56_GLIDE_'+Date.now();await cmd(`execute if entity @a[name=${names[1]},nbt={FallFlying:1b}] run say ${startedFlying}`);await until(()=>chat[0].some(m=>m.includes(startedFlying)),'genuine flight started');
  await b.equip(b.inventory.items().find(x=>x.name==='firework_rocket'),'hand');b.activateItem();await delay(150);b.deactivateItem();
  await until(()=>notices(1,'derribo',from),'grounding warning');await hit(400,2);await delay(1100);assert(!cooldown(1,from).some(p=>p.data.cooldownTicks>=120));const stillFlying='T56_FLY_'+Date.now();await cmd(`execute if entity @a[name=${names[1]},nbt={FallFlying:1b}] run say ${stillFlying}`);await until(()=>chat[0].some(m=>m.includes(stillFlying)),'grounding cancelled and player still gliding');pass('damage interrupts grounding warning');await despawn();
  await cmd('gamemode survival '+names[0]);
  // Five survival bots, same arena and boss attributes for both samples. Save raw spark files.
  const sparkDir=path.join(server,'plugins/spark');
  for(const [template,label] of [[id+'-zero','zero'],[id,'five']]) {
   await spawn(template);await delay(4000);const before=new Set(fs.readdirSync(sparkDir));await cmd('spark profiler cancel');await cmd('spark profiler start --timeout 60 --save-to-file');
   const pulse=setInterval(()=>{for(let i=0;i<5;i++){const e=Object.values(bots[i].entities).find(e=>e.uuid===bossUuid&&e.position.distanceTo(bots[i].entity.position)<6);if(e)bots[i].attack(e);}},1200);
   try{await until(()=>fs.readdirSync(sparkDir).some(n=>n.endsWith('.sparkprofile')&&!before.has(n)),'spark '+label,90000);}finally{clearInterval(pulse);}
   const file=fs.readdirSync(sparkDir).find(n=>n.endsWith('.sparkprofile')&&!before.has(n));fs.copyFileSync(path.join(sparkDir,file),path.join(dir,label+'.sparkprofile'));pass('spark captured '+label,{bots:5,file});await cmd('spark tps');await despawn(template);
  }
  }
  await cmd('gamemode creative '+names[0]);await cmd('gamemode creative '+names[4]);
  const fight=await require('./t56-fair-fight.cjs')({bots,names,cmd,spawn,despawn,at:()=>at,bossUuid:()=>bossUuid,id,delay,packets,chat,dir,failure:()=>failure});
  pass('unassisted level-5 three-bot combat recorded',fight);
 } catch(e){console.error(e.stack);process.exitCode=1;} finally {
  try{for(const name of names)await cmd("deop "+name);}catch(e){console.error("Deop cleanup: "+e.message);process.exitCode=1;}
  for(const timer of heartbeats)clearInterval(timer);
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({results,packets,chat,failure},null,2));for(const b of bots)if(b)b.quit();
 }
})().then(()=>setTimeout(()=>process.exit(process.exitCode||0),1000));
