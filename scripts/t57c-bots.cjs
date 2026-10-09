#!/usr/bin/env node
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const h=require('./t51-bot-support.cjs'),r=createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const id=process.env.T57C_ID,dir=process.env.T57C_RESULTS;
if(!process.argv.includes('--run')&&!process.argv.includes('--prepare')) {
 console.log('T57c: interrupción, tótem destructible, enlace roto por distancia, silencio/manzana/tótem, señuelos sin crédito/recompensa/restos; spark con cinco bots.');process.exit(0);
}
assert(id&&dir,'Use test-t57c-bots.sh --run');
const abilities=['charge','boss_totem','decoys','interruptible_ultimate','purge','buff_steal','silence','pain_link','blink_behind'];
if(process.argv.includes('--prepare')) {
 for(const a of abilities.concat('zero','profile')) {
  const onSpawn=(ability,params={})=>({'ability-id':ability,trigger:'ON_SPAWN',target:'NEAREST',range:48,'cooldown-ticks':0,chance:1,'telegraph-ticks':0,params});
  const params=a==='interruptible_ultimate'?{'charge-ticks':200}:a==='silence'?{ticks:200}:a==='boss_totem'?{health:5,protect:true}:a==='decoys'?{'damage-percent':0}:{};
  const entries=a==='zero'?[]:a==='profile'?[
   {...onSpawn('boss_totem',{protect:true}),trigger:'EVERY_X_SECONDS','trigger-value':4,'cooldown-ticks':80},
   {...onSpawn('decoys',{'damage-percent':0}),trigger:'EVERY_X_SECONDS','trigger-value':4,'cooldown-ticks':80},
   {...onSpawn('silence',{ticks:200}),trigger:'EVERY_X_SECONDS','trigger-value':3,'cooldown-ticks':60},
   {...onSpawn('pain_link'),trigger:'EVERY_X_SECONDS','trigger-value':3,'cooldown-ticks':60}]:[onSpawn(a,params)];
  assert((id+'-'+(a==='interruptible_ultimate'?'ultimate':a)).length<=32,'fixture ID within Validator limit');
  fs.writeFileSync(path.join(server,'plugins/CustomDungeons/mobs',id+'-'+(a==='interruptible_ultimate'?'ultimate':a)+'.yml'),JSON.stringify({
   'entity-type':'HUSK','display-name':id+'-'+a,'max-health':5000,attributes:{speed:0,damage:0},boss:true,abilities:entries,
   'world-boss':{world:'world','x-min':10420,'x-max':10428,'z-min':10420,'z-max':10428,'max-alive':1,radius:64,'minimum-damage':1,
    reward:{xp:0,items:[],commands:['give {player} minecraft:emerald 1']}}
  },null,2),{flag:'wx'});
 }
 process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'));
const log=path.join(server,'logs/latest.log'),mineflayer=r('mineflayer'),names=Array.from({length:5},(_,i)=>'T57C'+i+id.replace(/[^a-z0-9]/gi,'').slice(-5));
const heartbeats=[];
const vehicles=Array(5).fill(null);
const attributes=Array.from({length:5},()=>new Map());
const bots=[],chat=names.map(()=>[]),packets=names.map(()=>[]),results=[];
let failure,restarting=false;
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function pass(name,data){results.push({name,data});console.log('PASS '+name+' '+JSON.stringify(data??''));}
async function until(check,label,timeout=30000){const end=Date.now()+timeout;while(Date.now()<end){if(failure)throw Error(failure);if(check())return;await delay(100);}throw Error('Timeout: '+label);}
async function operation(action,args=[]) {
 for(let attempt=0;;attempt++) {
  const result=spawnSync(path.join(root,'scripts/test-server.sh'),[action,...args],{encoding:'utf8',env:{...process.env,CD_TARGET:'agents'}});
  if(result.status===0)return result.stdout;
  const output=result.stdout+result.stderr;
  if(output.includes('Otra operación del servidor está en curso.')&&attempt<10){await delay(60000);await delay(60000);}
  else throw Error(output);
 }
}
async function cmd(command) {
 assert(!/[\r\n]/.test(command));command=command.replace(/^(tp|kill|give|clear|gamemode|fill|damage|tag|data|summon|execute|effect|item) /,'minecraft:$1 ');command=command.replace(/ run (tp|kill|give|clear|damage|say|data) /g,' run minecraft:$1 ');
 await operation('cmd',[command]);await delay(180);
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
 b.physicsEnabled=false;b.on('error',e=>{if(!restarting)failure=String(e);});b.on('kicked',e=>{if(!restarting)failure='kicked '+JSON.stringify(e);});b.on('messagestr',m=>chat[index].push(m));
 b._client.on('packet',(data,meta)=>{if(['boss_bar','sound_effect','stop_sound','action_bar','set_cooldown','system_chat','damage_event'].includes(meta.name))packets[index].push({type:meta.name,data:h.canonical(data),at:Date.now()});});
 await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Spawn timeout '+names[index])),60000);b.once('spawn',()=>{clearTimeout(timer);resolve();});b.once('kicked',reason=>{clearTimeout(timer);reject(Error('Kicked '+JSON.stringify(reason)));});b.once('error',error=>{clearTimeout(timer);reject(error);});});heartbeats.push(setInterval(()=>write('tick_end',{}),50));await delay(400);return b;}

async function checkConsole(command,marker,timeout=5000) {
 const from=chat[0].length;await cmd(command+` run say ${marker}`);await until(()=>chat[0].slice(from).some(m=>m.includes(marker)),marker,timeout);
}
async function heal() {for(const name of names.slice(1)){await cmd(`effect clear ${name}`);await cmd(`effect give ${name} minecraft:instant_health 1 10 true`);}}
async function arrange() {for(const name of names.slice(1))await cmd(`tp ${name} 10424 101 10424`);await delay(500);}
(async()=>{
 let activeTemplate;
 try {
  for(let i=0;i<5;i++)await connect(i);
  for(const name of names)await cmd('op '+name);
  await cmd('gamemode creative '+names[0]);await cmd(`tp ${names[0]} 10424 104 10424`);
  let nextList=0;await until(()=>{if(Date.now()>=nextList){bots[0].chat('/customdungeon boss list');nextList=Date.now()+3000;}return chat[0].some(m=>m.includes(id+'-charge:'));},'definitions loaded',90000);await delay(3000);
  await cmd('fill 10396 100 10396 10452 100 10452 sea_lantern');
  for(let y=101;y<131;y+=10)await cmd(`fill 10400 ${y} 10400 10448 ${y+9} 10448 air`);
  // Isolate this fixture arena from natural mobs without touching encounter-owned entities.
  for(const wall of ['10395 101 10395 10395 105 10453','10453 101 10395 10453 105 10453','10396 101 10395 10452 105 10395','10396 101 10453 10452 105 10453'])await cmd('fill '+wall+' glass');
  await cmd('execute as @e[type=!player,x=10424,y=101,z=10424,distance=..80] unless data entity @s BukkitValues."customdungeons:session" run kill @s');
  for(const name of names.slice(1))await cmd('gamemode survival '+name);
  await arrange();await heal();activeTemplate=id+'-ultimate';await spawn(activeTemplate);await delay(1400);
  await checkConsole(`execute if entity @e[tag=${id},nbt={NoAI:1b}]`,'T57C_CHARGING');
  await cmd(`damage @e[tag=${id},limit=1] 1000 minecraft:player_attack by ${names[1]}`);await delay(2600);
  await checkConsole(`execute if entity @e[tag=${id}] unless entity @e[tag=${id},nbt={NoAI:1b}]`,'T57C_AI_RESTORED');
  await delay(7000);for(const b of bots.slice(1))assert.equal(b.health,20,'interrupted ultimate causes no hit');
  assert(notices(1,'Interrumpir'),'interruption progress shown');pass('ultimate interrupted, AI restored and attack cancelled');await despawn(activeTemplate);activeTemplate=null;

  await arrange();activeTemplate=id+'-boss_totem';await spawn(activeTemplate);await delay(1400);
  await checkConsole('execute if entity @e[type=interaction,nbt={BukkitValues:{"customdungeons:combat_auxiliary":1b}}]','T57C_TOTEM_ACTIVE');
  const helper=Object.values(bots[1].entities).find(e=>e.name==='interaction');assert(helper,'totem interaction visible');
  await cmd(`tp ${names[1]} ${helper.position.x+.8} ${helper.position.y} ${helper.position.z}`);
  await cmd(`give ${names[1]} minecraft:diamond_sword 1`);await delay(300);
  const sword=bots[1].inventory.items().find(i=>i.name==='diamond_sword');assert(sword);await bots[1].equip(sword,'hand');await delay(800);
  bots[1].attack(helper);await delay(600);
  await checkConsole('execute unless entity @e[type=interaction,nbt={BukkitValues:{"customdungeons:combat_auxiliary":1b}}]','T57C_TOTEM_DESTROYED');
  pass('totem destroyed by participant attack');await despawn(activeTemplate);activeTemplate=null;

  await arrange();await heal();activeTemplate=id+'-pain_link';await spawn(activeTemplate);await delay(1400);
  await cmd(`damage @e[tag=${id},limit=1] 10 minecraft:player_attack by ${names[2]}`);await delay(600);
  const linked=bots.findIndex((b,i)=>i>0&&b.health<20);assert(linked>0,'link reflects final boss damage');
  const before=bots[linked].health;await cmd(`tp ${names[linked]} 10450 101 10450`);await delay(400);
  const afterBreak=packets[linked].length;
  await cmd(`damage @e[tag=${id},limit=1] 10 minecraft:player_attack by ${names[2]}`);await delay(600);
  assert(bots[linked].health>=before,'distance breaks link; natural regeneration may heal');
  assert(!packets[linked].slice(afterBreak).some(p=>p.type==='damage_event'&&p.data.entityId===bots[linked].entity.id),'no reflected damage after distance break');
  pass('pain link broken by moving away',{before,after:bots[linked].health});await despawn(activeTemplate);activeTemplate=null;

  await arrange();await heal();activeTemplate=id+'-silence';await spawn(activeTemplate);await delay(1400);
  await cmd(`clear ${names[1]}`);await cmd(`give ${names[1]} minecraft:enchanted_golden_apple 1`);await delay(300);
  const apple=bots[1].inventory.items().find(i=>i.name==='enchanted_golden_apple');assert(apple);await bots[1].equip(apple,'hand');
  bots[1].activateItem();await delay(2300);bots[1].deactivateItem();
  assert(bots[1].inventory.items().some(i=>i.name==='enchanted_golden_apple'&&i.count===1),'silence blocked eating');
  await cmd(`item replace entity ${names[1]} weapon.offhand with minecraft:totem_of_undying`);await delay(250);
  await cmd(`damage ${names[1]} 40 minecraft:generic`);await delay(500);
  assert(bots[1].health>0,'totem resurrection works during silence');assert(!bots[1].inventory.items().some(i=>i.name==='totem_of_undying'),'totem consumed by resurrection');
  pass('silence blocks apple and permits totem resurrection',{health:bots[1].health});await despawn(activeTemplate);activeTemplate=null;

  await arrange();await heal();for(const name of names.slice(1))await cmd(`clear ${name}`);
  activeTemplate=id+'-decoys';await spawn(activeTemplate);await delay(1400);
  await checkConsole('execute if entity @e[type=husk,nbt={BukkitValues:{"customdungeons:decoy":1b}}]','T57C_DECOYS_ACTIVE');
  const barBefore=packets[1].filter(p=>p.type==='boss_bar').length;
  await cmd(`execute as @e[type=husk,nbt={BukkitValues:{"customdungeons:decoy":1b}},limit=1] run damage @s 100 minecraft:player_attack by ${names[1]}`);await delay(500);
  assert.equal(packets[1].filter(p=>p.type==='boss_bar').length,barBefore,'decoy hit leaves boss bar unchanged');
  await cmd(`damage @e[tag=${id},nbt=!{BukkitValues:{"customdungeons:decoy":1b}},limit=1] 6000 minecraft:player_attack by ${names[2]}`);await delay(1200);
  assert(!bots[1].inventory.items().some(i=>i.name==='emerald'),'decoy attacker receives no reward');
  assert(bots[2].inventory.items().some(i=>i.name==='emerald'),'actual contributor receives reward');
  await checkConsole('execute unless entity @e[nbt={BukkitValues:{"customdungeons:combat_auxiliary":1b}}]','T57C_NO_DEATH_REMNANTS');
  pass('decoys give no credit, boss bar changes or rewards; death cleans remnants');activeTemplate=null;
  await arrange();activeTemplate=id+'-decoys';await spawn(activeTemplate);await delay(1400);
  bots[0].chat('/customdungeon reload');await delay(2200);activeTemplate=null;
  await checkConsole('execute unless entity @e[nbt={BukkitValues:{"customdungeons:combat_auxiliary":1b}}]','T57C_NO_RELOAD_REMNANTS');pass('reload cleans all decoys');

  await arrange();await heal();await cmd('gamemode survival '+names[0]);const sparkDir=path.join(server,'plugins/spark');
  for(const label of ['zero','profile']) {
   await arrange();await heal();activeTemplate=id+'-'+label;await spawn(activeTemplate);await delay(2000);
   const before=new Set(fs.readdirSync(sparkDir));await cmd('spark profiler cancel');await cmd('spark profiler start --timeout 60 --save-to-file');
   await until(()=>fs.readdirSync(sparkDir).some(n=>n.endsWith('.sparkprofile')&&!before.has(n)),'spark '+label,90000);
   const file=fs.readdirSync(sparkDir).find(n=>n.endsWith('.sparkprofile')&&!before.has(n));
   await until(()=>fs.readFileSync(log,'utf8').includes('Data has been written to: plugins/spark/'+file),'spark save complete '+label,30000);
   fs.copyFileSync(path.join(sparkDir,file),path.join(dir,label+'.sparkprofile'));pass('spark captured '+label,{bots:5,file});await cmd('spark tps');await despawn(activeTemplate);activeTemplate=null;
  }
 } catch(error) {console.error(error.stack);process.exitCode=1;}
 finally {
  if(activeTemplate&&bots[0]){bots[0].chat('/customdungeon boss despawn '+activeTemplate);await delay(400);}
  for(const interval of heartbeats)clearInterval(interval);for(const b of bots)if(b)b.quit();
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify(results,null,2));
  fs.writeFileSync(path.join(dir,'bot-chat.json'),JSON.stringify(chat,null,2));
  fs.writeFileSync(path.join(dir,'bot-packets.json'),JSON.stringify(packets,null,2));
 }
})();
