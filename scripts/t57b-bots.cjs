#!/usr/bin/env node
'use strict';
// T57B acceptance on 25566. Lifecycle and fixtures belong to the shell runner.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const h=require('./t51-bot-support.cjs'),r=createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const id=process.env.T57B_ID,dir=process.env.T57B_RESULTS;
const affected=process.env.T57B_AFFECTED==='1';
if(!process.argv.includes('--run')&&!process.argv.includes('--prepare')){console.log('T57b: fixed beam/sweep dodge, pool escape, pillar reload cleanup, rift half-water arena, native arrows, spark with five bots.');process.exit(0);}
assert(id&&dir,'Use test-t57b-bots.sh --run');
const abilities=['vortex','inverted_gravity','cracked_floor','sweep','falling_pillars','poison_pools','charged_beam','arrow_rain','rift'];
const fixtures=(affected?['control','dungeon-pillar']:abilities.concat('zero','profile')).map(a=>id+'-'+a);
if(process.argv.includes('--prepare')) {
 for(const name of fixtures) {
  const ability=abilities.find(a=>name.endsWith('-'+a));
  const params=ability==='charged_beam'?{warning:80}:ability==='falling_pillars'?{warning:80,'obstacle-ticks':400}:ability==='poison_pools'?{count:1,damage:2}:ability==='rift'?{'min-distance':8,'max-distance':16}:{};
  const onSpawn=(ability,params={},target='ALL_IN_RADIUS')=>({'ability-id':ability,trigger:'ON_SPAWN',target,range:48,'cooldown-ticks':0,chance:1,'telegraph-ticks':ability==='sweep'?80:0,params});
  const entries=name.endsWith('-control')?[onSpawn('roots',{ticks:200},'NEAREST'),onSpawn('inverted_gravity',{ticks:160,damage:2},'NEAREST')]:name.endsWith('-dungeon-pillar')?[onSpawn('falling_pillars',{count:1,damage:0,'obstacle-ticks':400})]:name.endsWith('-zero')?[]:name.endsWith('-profile')?[{'ability-id':'poison_pools',trigger:'EVERY_X_SECONDS','trigger-value':2,target:'ALL_IN_RADIUS',range:48,'cooldown-ticks':40,chance:1,'telegraph-ticks':0,params:{ticks:600,count:2,damage:0}}]:[onSpawn(ability,params)];
  const rift=ability==='rift';
  fs.writeFileSync(path.join(server,'plugins/CustomDungeons/mobs',name+'.yml'),JSON.stringify({'entity-type':'HUSK','display-name':name,'max-health':5000,attributes:{speed:0,damage:0},boss:true,abilities:entries,'world-boss':{world:'world','x-min':rift?10400:10420,'x-max':rift?10448:10428,'z-min':rift?10400:10420,'z-max':rift?10448:10428,'max-alive':1,radius:64,'minimum-damage':1,reward:{xp:0,items:[],commands:[]}}},null,2),{flag:'wx'});
 }
 if(affected) {
  const point=(world,x,y,z)=>({world,x,y,z,yaw:0,pitch:0});
  fs.writeFileSync(path.join(server,'plugins/CustomDungeons/dungeons',id+'-pillar.yml'),JSON.stringify({
   'display-name':id+'-pillar',enabled:true,lobby:point('cd_dungeons',10424.5,101,10424.5),exit:point('world',10440.5,101,10424.5),
   'min-players':1,'max-players':2,'lobby-countdown-seconds':30,lives:100,'keep-inventory':true,'time-limit-seconds':600,'cooldown-seconds':0,
   rooms:[{id:'pillar',region:{world:'cd_dungeons',min:{x:10420,y:100,z:10420},max:{x:10428,y:120,z:10428}},checkpoint:point('cd_dungeons',10424.5,101,10424.5),unlock:'AUTOMATIC',spawners:[{
    id:'pillar',location:point('cd_dungeons',10426.5,101,10424.5),radius:1,waves:[{mode:'SIMULTANEOUS',entries:[{'template-id':id+'-dungeon-pillar',count:1,'delay-ticks':0}]}]
   }]}]
  },null,2),{flag:'wx'});
 }
 process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'));
const log=path.join(server,'logs/latest.log'),mineflayer=r('mineflayer'),names=Array.from({length:5},(_,i)=>'T57B'+i+id.slice(-5));
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
  for(let i=0;i<(affected?2:5);i++)await connect(i);
  for(const name of names.slice(0,bots.length))await cmd('op '+name);await cmd('gamemode creative '+names[0]);await cmd(`tp ${names[0]} 10424 104 10424`);
  bots[0].chat('/customdungeon boss list');await until(()=>chat[0].some(m=>m.includes(id+(affected?'-control:':'-vortex:'))),'definitions loaded',90000);await delay(5000);
  await cmd('fill 10396 100 10396 10452 100 10452 stone');
  for(let y=101;y<161;y+=10)await cmd(`fill 10400 ${y} 10400 10448 ${y+9} 10448 air`);
  for(const name of names.slice(1,bots.length))await cmd('gamemode survival '+name);
  if(affected) {
   await cmd(`tp ${names[1]} 10424 101 10424`);activeTemplate=id+'-control';await spawn(activeTemplate);await delay(3000);
   await checkConsole('execute if entity @e[type=interaction,nbt={BukkitValues:{"customdungeons:control_auxiliary":1b}}]','T57B_ROOTS_ACTIVE');
   await checkConsole(`execute unless entity @a[name=${names[1]},nbt={active_effects:[{id:"minecraft:levitation"}]}]`,'T57B_ROOTS_NO_GRAVITY');
   pass('active roots exclude inverted gravity',{health:bots[1].health});
   bots[0].chat('/customdungeon reload');await delay(2000);activeTemplate=null;
   // A real dungeon pillar exercises the SQL journal, not the already-safe world-boss journal.
   await cmd(`execute as ${names[0]} in minecraft:cd_dungeons run tp @s 10424 105 10424`);await delay(1500);
   await cmd('execute in minecraft:cd_dungeons run fill 10420 100 10420 10428 100 10428 stone');
   await cmd('execute in minecraft:cd_dungeons run fill 10420 101 10420 10428 120 10428 air');
   bots[1].chat('/customdungeon join '+id+'-pillar');await delay(2000);await cmd('customdungeon start '+id+'-pillar');
   await delay(3000);
   await checkConsole('execute in minecraft:cd_dungeons if block 10424 101 10424 stone_bricks if block 10424 102 10424 stone_bricks if block 10424 103 10424 stone_bricks','T57B_SQL_PILLAR');
   await cmd('execute in minecraft:cd_dungeons run setblock 10424 102 10424 diamond_block');
   const journal=()=>{
    const result=spawnSync('python3',['-c','import sqlite3,json,sys; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(json.dumps(c.execute("SELECT y,original_block_data,placed_block_data FROM temp_blocks WHERE world=? AND x=10424 AND z=10424 AND y BETWEEN 101 AND 103 ORDER BY y",("cd_dungeons",)).fetchall()))',path.join(server,'plugins/CustomDungeons/data.db')],{encoding:'utf8'});
    assert.equal(result.status,0,result.stderr);return JSON.parse(result.stdout);
   };
   const records=journal();assert.equal(records.length,3);assert(records.every(r=>r[1]==='minecraft:air'&&r[2]==='minecraft:stone_bricks'));
   const savedFrom=fs.readFileSync(log,'utf8').length;await cmd('save-all flush');await until(()=>fs.readFileSync(log,'utf8').slice(savedFrom).includes('Saved the game'),'world saved before controlled crash',30000);
   assert(Object.keys(bots[0].players).every(name=>names.slice(0,2).includes(name)),'No other players may be present during controlled crash');
   const found=spawnSync('pgrep',['-f','[p]aper-26[.]3'],{encoding:'utf8'});assert.equal(found.status,0);const pids=found.stdout.trim().split(/\s+/);assert.equal(pids.length,1,'one owned Paper process');
   const pid=Number(pids[0]);assert(Number.isSafeInteger(pid)&&pid>1);assert.equal(fs.realpathSync('/proc/'+pid+'/cwd'),server);assert(fs.readFileSync('/proc/'+pid+'/cmdline','utf8').split('\0').includes('paper-26.3-157.jar'));
   fs.copyFileSync(log,path.join(dir,'before-crash-server.log'));restarting=true;for(const timer of heartbeats)clearInterval(timer);heartbeats.length=0;
   // Only this run's exact Paper PID, after an explicit disk save; no stop command or shutdown hook.
   process.kill(pid,'SIGKILL');for(const bot of bots)bot.quit();await delay(2000);
   fs.rmSync(process.env.CD_START_OWNER_FILE,{force:true});
   const restarted=await operation('start');fs.writeFileSync(path.join(dir,'restart.log'),restarted);
   bots.length=0;for(const list of chat)list.length=0;for(const list of packets)list.length=0;
   for(let i=0;i<2;i++)await connect(i);restarting=false;for(const name of names.slice(0,2))await cmd('op '+name);
   await cmd(`gamemode creative ${names[0]}`);await cmd(`execute as ${names[0]} in minecraft:cd_dungeons run tp @s 10424 105 10424`);await delay(1500);
   await checkConsole('execute in minecraft:cd_dungeons if block 10424 101 10424 air if block 10424 102 10424 diamond_block if block 10424 103 10424 air','T57B_CRASH_RESTORED_FOREIGN_PRESERVED');
   assert.deepEqual(journal(),[]);pass('SQL pillar recovered after SIGKILL; foreign middle block preserved and records discarded',{records});
   await cmd('execute in minecraft:cd_dungeons run setblock 10424 102 10424 air');
   return;
  }
  for(const ability of ['charged_beam','sweep']) {
   await arrange();await heal();activeTemplate=id+'-'+ability;await spawn(activeTemplate);
   // Direction is now fixed; put every participant behind the warning origin.
   let dx=10424.5-(at[0]+.5),dz=10424.5-(at[2]+.5),norm=Math.hypot(dx,dz)||1;dx/=norm;dz/=norm;
   // BossSpawner supplies yaw 0 at spawn; the sweep freezes this front before AI rotates.
   if(ability==='sweep'){dx=0;dz=1;}
   for(const name of names.slice(1))await cmd(`tp ${name} ${at[0]+.5-dx*4} 101 ${at[2]+.5-dz*4}`);
   await delay(4500);for(let i=1;i<5;i++)assert.equal(bots[i].health,20,ability+' dodged behind');
   pass(ability+' fixed warning can be dodged behind',{health:bots.slice(1).map(b=>b.health)});await despawn(activeTemplate);activeTemplate=null;
  }
  await arrange();await heal();activeTemplate=id+'-poison_pools';await spawn(activeTemplate);await delay(2200);
  assert(bots[1].health<20,'pool applies damage');const before=bots[1].health;
  for(const name of names.slice(1))await cmd(`tp ${name} 10434 101 10434`);
  await delay(2200);assert(bots[1].health>=before,'pool stopped damage after leaving');pass('pool can be escaped',{before,after:bots[1].health});await despawn(activeTemplate);activeTemplate=null;
  await arrange();await heal();activeTemplate=id+'-falling_pillars';await spawn(activeTemplate);await delay(4200);
  await checkConsole(`execute if block 10424 101 10424 stone_bricks`,'T57B_PILLAR_'+Date.now());
  bots[0].chat('/customdungeon reload');await delay(2000);activeTemplate=null;
  await checkConsole(`execute if block 10424 101 10424 air if block 10424 102 10424 air if block 10424 103 10424 air`,'T57B_RESTORED_'+Date.now());pass('pillar blocks restored after reload');
  // Half-water host area: terrain validator must never choose its wet half.
  await cmd('fill 10400 100 10400 10423 100 10448 water');
  for(let repetition=0;repetition<5;repetition++) {
   await arrange();for(let i=2;i<5;i++)await cmd(`tp ${names[i]} ${10424+(i-1)*3} 101 10424`);
   const initial=bots.slice(1).map(b=>({x:b.entity.position.x,z:b.entity.position.z}));
   activeTemplate=id+'-rift';await spawn(activeTemplate);await delay(1800);let moved=0;
   for(let i=1;i<5;i++) {
    const pos=bots[i].entity.position;
    assert(pos.x>=10424,'rift never goes into water');assert(pos.y>=101,'rift never goes into void');
    if(Math.hypot(pos.x-initial[i-1].x,pos.z-initial[i-1].z)>=7.5)moved++;
   }
   assert(moved>=1,'one actual rift target teleported away from the group');
   pass('rift half-water safe destination '+repetition,{positions:bots.slice(1).map(b=>({x:b.entity.position.x,y:b.entity.position.y,z:b.entity.position.z}))});await despawn(activeTemplate);activeTemplate=null;
  }
  await cmd('fill 10400 100 10400 10423 100 10448 stone');
  await arrange();activeTemplate=id+'-arrow_rain';await spawn(activeTemplate);await delay(1900);
  await checkConsole(`execute if entity @e[type=arrow,nbt={BukkitValues:{"customdungeons:zone_arrow":1b}}]`,'T57B_ARROWS_'+Date.now());
  bots[0].chat('/customdungeon reload');await delay(1800);activeTemplate=null;
  await checkConsole(`execute unless entity @e[type=arrow,nbt={BukkitValues:{"customdungeons:zone_arrow":1b}}]`,'T57B_NO_ARROWS_'+Date.now());pass('marked real arrows removed on reload');
  await cmd('gamemode survival '+names[0]);const sparkDir=path.join(server,'plugins/spark');
  for(const label of ['zero','profile']) {
   for(const name of names){await cmd(`tp ${name} 10424 101 10424`);await cmd(`effect give ${name} minecraft:regeneration 999 2 true`);}
   activeTemplate=id+'-'+label;await spawn(activeTemplate);await delay(4000);
   const before=new Set(fs.readdirSync(sparkDir));await cmd('spark profiler cancel');await cmd('spark profiler start --timeout 60 --save-to-file');
   await until(()=>fs.readdirSync(sparkDir).some(n=>n.endsWith('.sparkprofile')&&!before.has(n)),'spark '+label,90000);
   const file=fs.readdirSync(sparkDir).find(n=>n.endsWith('.sparkprofile')&&!before.has(n));
   // Spark creates the file before finishing its protobuf write. Wait for its completion message.
   await until(()=>fs.readFileSync(log,'utf8').includes('Data has been written to: plugins/spark/'+file),'spark save complete '+label,30000);
   fs.copyFileSync(path.join(sparkDir,file),path.join(dir,label+'.sparkprofile'));pass('spark captured '+label,{bots:5,file});await cmd('spark tps');await despawn(activeTemplate);activeTemplate=null;
  }
 }catch(e){process.exitCode=1;failure=e.stack;console.error(failure);}
 finally {
  try{if(activeTemplate)await despawn(activeTemplate);for(const name of names.slice(0,affected?2:5))await cmd('deop '+name);}catch(e){console.error('cleanup: '+e.message);process.exitCode=1;}
  for(const timer of heartbeats)clearInterval(timer);
  fs.writeFileSync(path.join(dir,'results.json'),JSON.stringify({results,packets,chat,failure},null,2));for(const b of bots)if(b)b.quit();
 }
})().then(()=>setTimeout(()=>process.exit(process.exitCode||0),1000));
