#!/usr/bin/env node
'use strict';
// Reproducible RF-JEFES acceptance on the agents server only; lifecycle belongs to the shell runner.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const h=require('./t51-bot-support.cjs');
const r=createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const id=process.env.T55_ID,dir=process.env.T55_RESULTS,phase=process.env.T55_PHASE||'before',affected=process.env.T55_SUITE==='affected',projectiles=process.env.T55_SUITE==='projectiles';
if(!process.argv.includes('--run')&&!process.argv.includes('--prepare')) {
 console.log('T55 / 25566: 20 dry spawns in a half-water zone; maximum alive; two damage contributors; claim overflow; no reward on despawn/kill; leash; reload; restart and orphan PDC cleanup.');process.exit(0);
}
assert(id&&id.match(/^[a-z0-9_-]{1,32}$/)&&dir,'Use scripts/test-t55-bots.sh --run');
const fixture=path.join(server,'plugins/CustomDungeons/mobs',id+'.yml');
if(process.argv.includes('--prepare')) {
 const abilities=affected?[{'ability-id':'healer','trigger':'ON_DAMAGED','trigger-value':0,'target':'NEAREST','range':48,'cooldown-ticks':0,'chance':1,'telegraph-ticks':0,'params':{'radius':8,'heal':100}}]:[];
 fs.writeFileSync(fixture,JSON.stringify({'entity-type':projectiles?'SKELETON':'HUSK','display-name':id,'max-health':5000,'attributes':{'speed':0,'damage':0,...(affected?{'armor':0}:{})},'abilities':abilities,'boss':true,'boss-bar-color':'PURPLE',
  'world-boss':{'world':'world','x-min':10000,'x-max':10016,'z-min':10000,'z-max':10016,'max-alive':1,'radius':projectiles?16:48,'minimum-damage':5,
   'reward':{'items':[{'==':'org.bukkit.inventory.ItemStack','type':'DIAMOND','amount':4}],'xp':17,'commands':['give {player} minecraft:emerald 2']}}},null,2),{flag:'wx'});process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'),'Use the shell runner');
fs.mkdirSync(dir,{recursive:true});
const log=path.join(server,'logs/latest.log'),mineflayer=r('mineflayer'),names=['T55A','T55B','T55C'].map(n=>n+id.replace(/[^a-z0-9]/g,'').slice(-7));
const bots=[],chat=[[],[],[]],packets=[[],[],[]],results=[];
let failure;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function pass(name,data){results.push({name,data});console.log('PASS '+name+' '+JSON.stringify(data??''));}
async function until(check,label,timeout=30000){const end=Date.now()+timeout;while(Date.now()<end){if(failure)throw Error(failure);if(check())return;await delay(100);}throw Error('Timeout: '+label);}
async function cmd(command) {
 assert(!/[\r\n]/.test(command));
 command=command.replace(/^(tp|kill|give|clear|gamemode|fill|damage|tag|data|summon|execute) /,'minecraft:$1 ');
 command=command.replace(/ run (tp|kill|give|clear|fill|damage|tag|data|summon|say) /g,' run minecraft:$1 ');
 for(let attempt=0;;attempt++) {
  const result=spawnSync(path.join(root,'scripts/test-server.sh'),['cmd',command],{encoding:'utf8',env:{...process.env,CD_TARGET:'agents'}});
  if(result.status===0){await delay(250);return;}
  if(result.stderr.includes('Otra operación del servidor está en curso.')&&attempt<10){await delay(60000);await delay(60000);}
  else throw Error(result.stderr);
 }
}
async function spawn(){const from=chat[0].length;bots[0].chat('/customdungeon boss spawn '+id);await until(()=>chat[0].slice(from).some(m=>m.includes(id+' apareció en world:')),'spawn '+id);
 const text=chat[0].slice(from).find(m=>m.includes(id+' apareció en world:'));const coords=text.match(/world: (-?\d+), (-?\d+), (-?\d+)/);return coords.slice(1).map(Number);}
async function gone(label){
 const token='T55_CLEAN_'+label+'_'+Date.now();
 for(let i=0;i<30;i++) {
  await cmd('execute unless entity @e[tag='+id+'] run say '+token);
  if(fs.readFileSync(log,'utf8').includes(token))return;
  await delay(750);
 }
 throw Error('Timeout: '+label+' entity cleanup');
}
async function tag(at){await cmd(`tag @e[type=${projectiles?'skeleton':'husk'},x=${at[0]+.5},y=${at[1]},z=${at[2]+.5},distance=..0.8,limit=1] add ${id}`);await cmd(`data merge entity @e[tag=${id},limit=1] {NoAI:1b}`);}
function items(bot,name){return bot.inventory.items().filter(i=>i.name===name).reduce((n,i)=>n+i.count,0);}
async function connect(index){const b=mineflayer.createBot({host:'127.0.0.1',port:25566,username:names[index],auth:'offline',version:'26.1',physicsEnabled:false});bots[index]=b;
 const write=b._client.write.bind(b._client),acknowledged=new Set();
 b._client.write=(name,value)=>{
  if(name==='teleport_confirm') {
   if(acknowledged.has(value.teleportId))return;
   acknowledged.add(value.teleportId);
   const p=b.entity.position,conv=r('mineflayer/lib/conversions');
   const position={x:p.x,y:p.y,z:p.z,yaw:conv.toNotchianYaw(b.entity.yaw),pitch:conv.toNotchianPitch(b.entity.pitch),flags:{onGround:false,hasHorizontalCollision:false}};
   assert([position.x,position.y,position.z,position.yaw,position.pitch].every(Number.isFinite));
   write(name,value);write('position_look',position);return;
  }
  if(['look','position','position_look','flying'].includes(name))return;
  write(name,h.menuPacket(name,value));
 };
 b.physicsEnabled=false;b.on('error',e=>failure=String(e));b.on('kicked',e=>failure='kicked '+JSON.stringify(e));b.on('messagestr',m=>chat[index].push(m));
 b._client.on('packet',(data,meta)=>{if(['boss_bar','sound_effect','stop_sound'].includes(meta.name))packets[index].push({type:meta.name,data:h.canonical(data)});});
 await new Promise(resolve=>b.once('spawn',resolve));await delay(400);return b;}
(async()=>{
 try {
  await connect(0);await cmd('op '+names[0]);await cmd('gamemode creative '+names[0]);await cmd(`tp ${names[0]} 10008 103 10008`);
  bots[0].chat('/customdungeon boss list');await until(()=>chat[0].some(m=>m.includes(id+':')),'definitions loaded',90000);
  if(phase==='after') {
   await gone('restart');pass('restart and orphan PDC cleanup');return;
  }
  await cmd('fill 10000 101 10000 10015 200 10015 air');await cmd('fill 10000 201 10000 10015 319 10015 air');
  await cmd('fill 10000 100 10000 10015 100 10015 stone');await cmd('fill 10000 100 10000 10007 100 10015 water');
  if(projectiles) {
   await cmd('fill 10016 100 10000 10048 100 10048 stone');
   await connect(1);await connect(2);
   for(const name of names.slice(1)){await cmd('gamemode survival '+name);await cmd('clear '+name);}
   const at=await spawn();await tag(at);
   await cmd(`tp ${names[1]} ${at[0]+12.5} ${at[1]} ${at[2]+.5}`);
   await cmd(`tp ${names[2]} ${at[0]+20.5} ${at[1]} ${at[2]+.5}`);
   const offset=fs.readFileSync(log,'utf8').length;
   await cmd(`data get entity @e[tag=${id},type=skeleton,limit=1] BukkitValues."customdungeons:world_boss"`);
   await until(()=>/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}/.test(fs.readFileSync(log,'utf8').slice(offset)),'encounter UUID');
   const encounter=fs.readFileSync(log,'utf8').slice(offset).match(/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}/)[0];
   await cmd(`minecraft:item replace entity @e[tag=${id},type=skeleton,limit=1] weapon.mainhand with minecraft:bow`);
   await cmd(`minecraft:item replace entity @e[tag=${id},type=skeleton,limit=1] armor.head with minecraft:iron_helmet`);
   await cmd(`data merge entity @e[tag=${id},type=skeleton,limit=1] {NoAI:0b}`);
   // Capture an actual AI-fired arrow by its encounter PDC, then keep it in flight for deterministic damage/cleanup checks.
   const captured='T55_NATIVE_CAPTURED_'+Date.now();let found=false;
   for(let n=0;n<80;n++) {
    await cmd(`execute as @e[type=arrow,x=${at[0]},y=${at[1]},z=${at[2]},distance=..64] if data entity @s {BukkitValues:{"customdungeons:world_boss":"${encounter}"}} run data merge entity @s {Motion:[0.0d,0.0d,0.0d],NoGravity:1b,Tags:["${id}"]}`);
    await cmd(`execute if entity @e[tag=${id},type=arrow] run say ${captured}`);
    if(fs.readFileSync(log,'utf8').includes(captured)){found=true;break;}
   }
   assert(found,'Native skeleton arrow must inherit world_boss PDC at launch');
   pass('native skeleton arrow ownership',{encounter});
   await cmd(`data merge entity @e[tag=${id},type=skeleton,limit=1] {NoAI:1b}`);
   for(const name of names.slice(1))await cmd(`minecraft:effect give ${name} minecraft:instant_health 1 10 true`);
   await delay(1200);
   await cmd(`damage ${names[2]} 4 minecraft:arrow by @e[tag=${id},type=arrow,limit=1]`);
   const protectedToken='T55_NATIVE_PROTECTED_'+Date.now();
   await cmd(`execute as ${names[2]} if data entity @s {Health:20.0f} run say ${protectedToken}`);
   await until(()=>fs.readFileSync(log,'utf8').includes(protectedToken),'foreign player kept full HP');
   pass('native arrow cannot damage foreign player',{distance:20,radius:16,health:20});
   await cmd(`damage ${names[1]} 4 minecraft:arrow by @e[tag=${id},type=arrow,limit=1]`);
   const allowedToken='T55_NATIVE_ALLOWED_'+Date.now();
   await cmd(`execute as ${names[1]} if data entity @s {Health:16.0f} run say ${allowedToken}`);
   await until(()=>fs.readFileSync(log,'utf8').includes(allowedToken),'participant receives arrow damage');
   pass('native arrow can damage participant',{distance:12,health:16});
   bots[0].chat('/customdungeon boss despawn '+id);await gone('native_projectiles');
   const cleanToken='T55_NATIVE_REMOVED_'+Date.now();
   await cmd(`execute unless entity @e[type=arrow,x=${at[0]},y=${at[1]},z=${at[2]},distance=..64,nbt={BukkitValues:{"customdungeons:world_boss":"${encounter}"}}] run say ${cleanToken}`);
   await until(()=>fs.readFileSync(log,'utf8').includes(cleanToken),'all encounter arrows retired');
   pass('despawn removes native arrows and boss');await cmd('spark tps');return;
  }
  if(affected) {
   await connect(1);await connect(2);
   await cmd('gamemode survival '+names[1]);await cmd('gamemode adventure '+names[2]);
   for(const name of names.slice(1)){await cmd('clear '+name);await cmd(`tp ${name} 10010 101 10010`);}
   let at=await spawn();await tag(at);
   for(const name of names.slice(1))await cmd(`tp ${name} ${at[0]+1} ${at[1]} ${at[2]+1}`);
   async function hit(amount,type='minecraft:player_attack',player=names[1]){
    for(let attempt=0;attempt<20;attempt++){
     const offset=fs.readFileSync(log,'utf8').length;
     await cmd(`damage @e[tag=${id},limit=1] ${amount} ${type}${player?' by '+player:''}`);
     await until(()=>{const tail=fs.readFileSync(log,'utf8').slice(offset);return tail.includes(`Applied ${amount}.0 damage to ${id}`)||tail.includes('Target is invulnerable to the given damage type');},'damage command response',10000);
     if(fs.readFileSync(log,'utf8').slice(offset).includes(`Applied ${amount}.0 damage to ${id}`)){await delay(600);return;}
     await delay(1000);
    }
    throw Error('Damage did not pass native invulnerability after 20 attempts');
   }
   async function hp(expected,label){
    const token='T55_HEAL_'+label+'_'+Date.now();
    await cmd(`execute as @e[tag=${id},limit=1] if data entity @s {BukkitValues:{"customdungeons:virtual_health":${expected}.0d}} run say ${token}`);
    await until(()=>fs.readFileSync(log,'utf8').includes(token),'authoritative HP '+expected,10000);
    pass('self healing HP '+label,{expected});
   }
   await hit(1000,'minecraft:generic',null);await hp(4000,'initial');
   for(let n=0;n<2;n++){await hit(125);await hp(4000-25*(n+1),'hit_'+(n+1));}
   await hp(3950,'two_hits');
   await hit(50,'minecraft:player_attack',names[2]);await hp(4000,'below_threshold');
   // The environmental hit earns no player credit. The unqualified bot delivers the last 150 HP.
   await hit(4050,'minecraft:generic',null);await hp(50,'before_finish');
   await hit(150,'minecraft:player_attack',names[2]);
   await until(()=>items(bots[1],'emerald')===2,'250 actual HP credited despite 200 HP healed');await delay(800);
   assert.equal(items(bots[1],'diamond'),4);assert.equal(items(bots[2],'diamond'),0);assert.equal(items(bots[2],'emerald'),0);
   pass('self healing damage rewards',{qualifiedDamage:250,qualifiedNetLoss:50,killerDamage:200,minimum:250});await gone('healing_death');
   const before=names.slice(1).map((_,i)=>[items(bots[i+1],'diamond'),items(bots[i+1],'emerald')]);
   for(const mode of ['despawn','kill']){
    at=await spawn();await tag(at);for(const name of names.slice(1))await cmd(`tp ${name} ${at[0]+1} ${at[1]} ${at[2]+1}`);
    await hit(300);
    if(mode==='kill')await cmd(`kill @e[tag=${id}]`);else bots[0].chat('/customdungeon boss despawn '+id);
    await gone('affected_'+mode);await delay(600);
    assert.deepEqual(names.slice(1).map((_,i)=>[items(bots[i+1],'diamond'),items(bots[i+1],'emerald')]),before);
    pass('self healing no reward on '+mode);
   }
   await cmd('spark tps');return;
  }
  const positions=[];
  for(let i=0;i<20;i++) {
   const at=await spawn();assert(at[0]>=10008&&at[0]<10016&&at[2]>=10000&&at[2]<10016&&at[1]===101,'spawn must be on the dry half');positions.push(at);await tag(at);
   if(i===0){const from=chat[0].length;bots[0].chat('/customdungeon boss spawn '+id);await until(()=>chat[0].slice(from).some(m=>m.includes('máximo de vivos alcanzado')),'maximum alive');pass('maximum alive');}
   bots[0].chat('/customdungeon boss despawn '+id);await gone('despawn'+i);
  }
  pass('20 appearances never in water',positions);
  await connect(1);await connect(2);await cmd('gamemode survival '+names[1]);await cmd('gamemode adventure '+names[2]);
  await cmd('clear '+names[1]);await cmd('clear '+names[2]);
  for(const name of names.slice(1))await cmd(`tp ${name} 10010 101 10010`);
  let at=await spawn();await tag(at);await delay(1000);
  assert(packets[1].some(p=>p.type==='boss_bar')&&packets[2].some(p=>p.type==='boss_bar'),'both eligible bots must receive BossBar');pass('BossBar audience');
  await cmd(`tp @e[tag=${id},limit=1] 10030 101 10030`);await delay(1300);
  await cmd(`execute as @e[tag=${id},limit=1] unless entity @s[x=${at[0]},y=101,z=${at[2]},dx=1,dy=2,dz=1] run say T55_LEASH_FAILED`);
  assert(!fs.readFileSync(log,'utf8').includes('T55_LEASH_FAILED'));pass('leash');
  for(const name of names.slice(1))await cmd(`tp ${name} ${at[0]+1} ${at[1]} ${at[2]+1}`);
  // A real melee packet verifies player attribution; deterministic API damage then tests virtual HP.
  const target=Object.values(bots[1].entities).find(e=>e.name==='husk'&&e.position.distanceTo(bots[1].entity.position)<5);
  assert(target,'boss visible to bot');await bots[1].lookAt(target.position.offset(0,1,0),true);bots[1].attack(target);await delay(600);
  await cmd(`damage @e[tag=${id},limit=1] 300 minecraft:player_attack by ${names[1]}`);await delay(600);
  await cmd(`damage @e[tag=${id},limit=1] 100 minecraft:player_attack by ${names[2]}`);await delay(600);
  await cmd('spark tps');
  await cmd(`damage @e[tag=${id},limit=1] 10000 minecraft:player_attack by ${names[1]}`);
  await until(()=>items(bots[1],'emerald')===2,'qualified reward');await delay(800);
  assert.equal(items(bots[1],'diamond'),4);assert.equal(items(bots[2],'diamond'),0);assert.equal(items(bots[2],'emerald'),0);
  pass('only contributor above 5 percent rewarded',{qualified:chat[1].slice(-5),below:chat[2].slice(-5)});await gone('death');
  // Fill every inventory cell; all reward items (not commands) must survive through claim.
  await cmd('clear '+names[1]);await cmd('give '+names[1]+' stone 2304');at=await spawn();await tag(at);
  await cmd(`damage @e[tag=${id},limit=1] 10000 minecraft:player_attack by ${names[1]}`);
  await until(()=>chat[1].some(m=>m.includes('claim')),'pending overflow claim');await cmd('clear '+names[1]);bots[1].chat('/customdungeon claim');
  await until(()=>items(bots[1],'diamond')===4,'claim delivers overflow');pass('overflow survives in claim');
  const before=[items(bots[1],'diamond'),items(bots[1],'emerald')];
  for(const mode of ['despawn','kill']){
   at=await spawn();await tag(at);await cmd(`damage @e[tag=${id},limit=1] 300 minecraft:player_attack by ${names[1]}`);await delay(600);
   if(mode==='kill')await cmd(`kill @e[tag=${id}]`);else {bots[0].chat('/customdungeon boss despawn '+id);await delay(600);}await gone(mode);
   assert.deepEqual([items(bots[1],'diamond'),items(bots[1],'emerald')],before);pass('no reward on '+mode);
  }
  at=await spawn();await tag(at);bots[0].chat('/customdungeon reload');await gone('reload');
  await until(()=>chat[0].some(m=>m.includes('recargad')),'reload completion',90000);pass('reload cleanup');
  at=await spawn();await tag(at);
  await cmd(`summon husk 10010 101 10010 {NoAI:1b,Tags:["${id}"],BukkitValues:{"customdungeons:world_boss":"orphan"}}`);
  pass('living boss and persistent orphan prepared for restart');
 } catch(e){process.exitCode=1;console.error(e.stack);}
 finally {
  for(const name of names)await cmd('deop '+name).catch(()=>{});
  for(const b of bots)if(b)b.quit();
  fs.writeFileSync(path.join(dir,'results-'+phase+'.json'),JSON.stringify(h.canonical({results,chat,packets}),null,2));
 }
})().catch(e=>{console.error(e);process.exitCode=1;});
