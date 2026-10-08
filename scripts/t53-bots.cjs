#!/usr/bin/env node
'use strict';
// T53: actual client packets and vanilla melee, on the agents server only.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {createRequire} = require('node:module');
const {spawnSync} = require('node:child_process');
const h = require('./t51-bot-support.cjs');
const {dimension, fixtureChunksReady, serverErrors} = require('./t52-bots.cjs');
module.exports = {serverErrors};
if (require.main === module) {
const root = path.resolve(__dirname, '..');
const data = '/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes/plugins/CustomDungeons';
const r = createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
h.assertAgentsTarget(process.env.CD_TARGET || 'agents', 25566);
if (!process.argv.includes('--run')) {
  console.log('T53 / 25566: two bots (admin + non-admin); live ability/phase/music/particles; creative negative target/damage assertions; non-admin ability damage; vanilla non-admin melee; Warden anger; session audience; music stop; cleanup.');
  process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'), 'Use scripts/test-t53-bots.sh --run');
const world = process.env.T53_WORLD || 'cd_dungeons', dim = dimension(world);
const logPath = path.resolve(data, '../../logs/latest.log');
const dir = process.env.T53_RESULTS || path.join(root, '.agent/t53-bots');
fs.mkdirSync(dir, {recursive:true});
const prefix = 't53-' + process.pid + '-' + Date.now().toString(36);
const names = ['T53A', 'T53B'].map(n => n + Date.now().toString(36));
const bots = [], packets = [[], []], chat = [[], []], files = [], heartbeats = [];
let failure, foreign = false, platform, heartbeatOn = false;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function cmd(command) {
  assert(!/[\r\n]/.test(command));
  for(let attempt=0;;attempt++) {
    const result=spawnSync(path.join(root,'scripts/test-server.sh'),['cmd',command],{encoding:'utf8',env:{...process.env,CD_TARGET:'agents'}});
    if(result.status===0) {await delay(180); return;}
    if(result.stderr.includes('Otra operación del servidor está en curso.') && attempt<10) {await delay(60000);await delay(60000);}
    else throw new Error(result.stderr);
  }
}
const execute = command => `minecraft:execute in ${dim} run minecraft:${command}`;
async function until(check,label,timeout=30000) {
  const end=Date.now()+timeout;
  while(Date.now()<end) {
    if(foreign) throw new Error('Another player joined; aborting shared-server test');
    if(failure) throw new Error(failure);
    if(check())return; await delay(100);
  }
  throw new Error('Timeout: '+label);
}
function fixture(kind,id,value) {
  const file=path.join(data,kind,id+'.yml'); fs.writeFileSync(file,JSON.stringify(value,null,2),{flag:'wx'});files.push(file);
}
const soundKey = packet => packet.sound?.data?.soundName || packet.sound?.data?.name
  // minecraft-protocol decodes registryEntryHolder as wire ID minus one;
  // minecraft-data's sound table for 26.1 uses the one-based wire IDs.
  || packet.sound?.soundName || r('minecraft-data')('26.1').sounds[packet.sound?.soundId + 1]?.name;
function heard(index,key,since=0) {return packets[index].slice(since).some(p=>p.type==='sound_effect' && soundKey(p.data)?.replace(/^minecraft:/,'')===key.replace(/^minecraft:/,''));}
async function click(slot) {await bots[0].clickWindow(slot,0,0);await delay(400);}
async function openLive(id) {
  const bot=bots[0]; bot.chat('/customdungeon');
  await until(()=>bot.currentWindow?.inventoryStart===36 && bot.currentWindow.slots[21]?.name==='book','main menu');
  await click(21);
  await until(()=>bot.currentWindow && bot.currentWindow.slots.slice(9,bot.currentWindow.inventoryStart-9).some(Boolean),'mob library');
  function find() {const w=bot.currentWindow;return w.slots.slice(9,w.inventoryStart-9).findIndex(i=>i && JSON.stringify(h.canonical(i)).includes(id))+9;}
  let slot=find();for(let page=0;slot<9 && page<15;page++) {await click(bot.currentWindow.inventoryStart-4);slot=find();}
  assert(slot>=9,'Fixture template missing: '+id);await click(slot);
  await until(()=>bot.currentWindow?.slots[38]?.name==='target','mob editor');
  await click(38); if(bot.currentWindow)bot.closeWindow(bot.currentWindow);
  await until(()=>chat[0].some(s=>/Prueba iniciada|Test started/i.test(s)),'live start');
}
async function assertCreativeSafe(entity,label,expectLightning=false) {
  await until(()=>bots[1].game.gameMode==='creative' && bots[0].game.gameMode==='survival','creative protection modes');
  const packetStart=packets[1].length,chatStart=chat[0].length,health=bots[1].health;
  const protectedAt=bots[1].entity.position.clone(),controlAt=bots[0].entity.position.clone();
  const forbidden=prefix+'-'+label+'-creative-target-forbidden',control=prefix+'-'+label+'-admin-target';
  // Query the vanilla target relation, with an admin-target positive control.
  // Observe more than two lightning cooldowns and multiple melee opportunities.
  for(let sample=0;sample<4;sample++) {
    await until(()=>true,'creative protection observation');
    await cmd(execute(`execute as ${entity} on target if entity @s[name=${names[1]}] run minecraft:say ${forbidden}`));
    await cmd(execute(`execute as ${entity} on target if entity @s[name=${names[0]}] run minecraft:say ${control}`));
    await delay(1000);
  }
  const observed=packets[1].slice(packetStart),messages=chat[0].slice(chatStart);
  assert(messages.some(s=>s.includes(control)),label+': active AI must target the survival admin');
  assert(!messages.some(s=>s.includes(forbidden)),label+': creative non-admin must never be the mob target');
  assert(!observed.some(p=>p.type==='damage_event' && p.data.entityId===bots[1].entity.id),
    label+': creative non-admin must receive no damage packet');
  assert.equal(bots[1].health,health,label+': creative health must be unchanged');
  if(expectLightning) {
    const lightningId=r('minecraft-data')('26.1').entitiesByName.lightning_bolt.id;
    const bolts=observed.filter(p=>p.type==='spawn_entity' && p.data.type===lightningId);
    const at=(packet,position)=>Math.hypot(packet.x-position.x,packet.z-position.z)<0.75 && Math.abs(packet.y-position.y)<1;
    assert(bolts.some(p=>at(p.data,controlAt)),'Lightning must execute against the survival admin during the negative window');
    assert(!bolts.some(p=>at(p.data,protectedAt)),'Lightning must never select the creative non-admin');
  }
  await cmd('minecraft:say '+prefix+'-'+label+'-creative-safe');
  console.log('PASS: creative non-admin is never targeted or damaged ('+label+', active admin control).');
}
async function main() {
  const mineflayer=r('mineflayer'),Vec3=r('vec3').Vec3;
  for(let i=0;i<2;i++) {
    const bot=mineflayer.createBot({host:'127.0.0.1',port:25566,username:names[i],auth:'offline',version:'26.1',physicsEnabled:false});bots.push(bot);
    const write=bot._client.write.bind(bot._client);
    const acknowledged = new Set();
    bot._client.write=(name,value)=>{
      if(name==='teleport_confirm') {
        if(acknowledged.has(value.teleportId))return;
        acknowledged.add(value.teleportId);
        const p=bot.entity.position,conv=r('mineflayer/lib/conversions');
        const position={x:p.x,y:p.y,z:p.z,yaw:conv.toNotchianYaw(bot.entity.yaw),
          pitch:conv.toNotchianPitch(bot.entity.pitch),flags:{onGround:false,hasHorizontalCollision:false}};
        assert([position.x,position.y,position.z,position.yaw,position.pitch].every(Number.isFinite));
        write(name,value);
        // ViaBackwards 26.3 completes the teleport from the following full position.
        write('position_look',position);
        fs.appendFileSync(path.join(dir,'teleports-'+i+'.log'),JSON.stringify({id:value.teleportId,...position})+'\n');
        return;
      }
      if(['look','position','position_look','flying'].includes(name))return;
      write(name,h.menuPacket(name,value));
    };
    bot.on('spawn',()=>{bot.physicsEnabled=false;});
    bot.on('playerJoined',p=>{if(!names.includes(p.username))foreign=true;});
    bot.on('messagestr',s=>{chat[i].push(s);fs.appendFileSync(path.join(dir,'chat-'+i+'.log'),s+'\n');});
    bot.on('error',e=>{failure=e.message;});bot.on('kicked',why=>{failure='kicked '+JSON.stringify(why);});
    for(const type of ['sound_effect','stop_sound','world_particles','damage_event','spawn_entity'])bot._client.on(type,p=>{packets[i].push({type,data:p});});
    await until(()=>bot.entity && bot.inventory,'bot '+i+' spawn',45000);
    // Physics is disabled for stationary bots; keep the client tick heartbeat.
    heartbeats.push(setInterval(()=>{
      if(!heartbeatOn || !bot.entity)return;
      write('tick_end',{});
    },50));
  }
  assert(Object.values(bots[0].players).every(p=>names.includes(p.username)),'Agents server occupied');
  await until(()=>/Definiciones cargadas:|Definitions loaded:/.test(fs.readFileSync(logPath,'utf8')),'definitions',90000);
  await cmd('minecraft:op '+names[0]);await cmd('minecraft:deop '+names[1]);
  for(const name of names)await cmd('minecraft:gamemode creative '+name);
  const x=40000+(process.pid%500)*32,z=40000;
  for(let i=0;i<2;i++) {
    await cmd(execute(`tp ${names[i]} ${x+i*2} 65 ${z} 0 0`));
  }
  // Multiverse applies the destination world's mode when teleporting between worlds.
  for(const name of names)await cmd('minecraft:gamemode creative '+name);
  await until(()=>fixtureChunksReady(p=>bots[0].blockAt(new Vec3(p.x,p.y,p.z)),x,z),'fixture chunks');
  for(let px=x-8;px<=x+8;px++)for(let pz=z-8;pz<=z+8;pz++)assert(['air','cave_air','void_air'].includes(bots[0].blockAt(new Vec3(px,64,pz))?.name),'Platform must be empty');
  platform=[x-8,64,z-8,x+8,64,z+8].join(' ');await cmd(execute(`fill ${platform} minecraft:glass replace minecraft:air`));
  for(const bot of bots)bot._client.write('player_loaded',{});
  heartbeatOn = true;
  const template={'entity-type':'HUSK','display-name':prefix,'max-health':100,damage:1,
    attributes:{speed:0,'follow-range':32},boss:true,'music-key':'minecraft:music_disc.13',
    abilities:[{'ability-id':'dragon_roar',trigger:'EVERY_X_SECONDS','trigger-value':1,target:'CURRENT_TARGET',range:32,params:{knockback:0,up:0}},
      {'ability-id':'lightning',trigger:'EVERY_X_SECONDS','trigger-value':2,target:'ALL_IN_RADIUS',range:32,params:{damage:2}}],
    phases:[{'health-threshold':0.5,'sound-key':'minecraft:block.anvil.land','music-key':'minecraft:music_disc.cat','invulnerable-ticks':10}]};
  fixture('mobs',prefix,template);
  fixture('mobs',prefix+'-melee',{'entity-type':'HUSK','display-name':prefix+' melee','max-health':100,damage:1,attributes:{speed:0.23,'follow-range':32}});
  fixture('mobs',prefix+'-warden',{'entity-type':'WARDEN','display-name':prefix+' warden',attributes:{speed:0}});
  const point=(px,pz)=>({world,x:px,y:65,z:pz});
  fixture('dungeons',prefix,{'display-name':prefix,enabled:true,lobby:point(x,z),exit:point(x+40,z),
    'min-players':2,'max-players':2,'lobby-countdown-seconds':5,lives:3,'keep-inventory':true,'intro-cinematic':false,
    scaling:{'extra-mobs-per-player':0,'extra-health-per-player':0},
    rooms:[{id:'test',region:{world,min:{x:x-8,y:60,z:z-8},max:{x:x+8,y:80,z:z+8}},checkpoint:point(x,z),
      spawners:[{id:'test',location:point(x+3,z),radius:1,waves:[{mode:'SIMULTANEOUS',entries:[{'template-id':prefix,count:1}]}]}]}]});
  const offset=fs.statSync(logPath).size;await cmd('customdungeon reload');
  await until(()=>/recargad|reloaded/i.test(fs.readFileSync(logPath).subarray(offset).toString()),'reload',90000);
  await cmd('minecraft:gamemode survival '+names[0]);
  await cmd('minecraft:effect give '+names[0]+' minecraft:resistance 300 4 true');
  await openLive(prefix);
  await until(()=>[0,1].every(i=>heard(i,'music_disc.13') && heard(i,'entity.ender_dragon.growl')),'live music and ability to both clients');
  const mob=`@e[type=minecraft:husk,nbt={BukkitValues:{"customdungeons:template":"${prefix}"}},limit=1]`;
  await cmd(execute(`damage ${mob} 60 minecraft:generic`));
  await until(()=>[0,1].every(i=>heard(i,'block.anvil.land') && heard(i,'music_disc.cat') && packets[i].some(p=>p.type==='world_particles' && p.data.amount===30)),'live phase sound/music/particles');
  console.log('PASS: live ability, phase, transition particles and music reach the non-admin and admin.');
  await assertCreativeSafe(mob,'ability',true);
  // Freeze admin in creative; let vanilla AI choose and actually hit the other player.
  await cmd('minecraft:gamemode creative '+names[0]);
  await cmd('minecraft:gamemode survival '+names[1]);await cmd('minecraft:effect give '+names[1]+' minecraft:resistance 30 1 true');
  await until(()=>bots[1].game.gameMode==='survival' && bots[0].game.gameMode==='creative',
    'combat client modes: admin creative, non-admin survival');
  const huskId = r('minecraft-data')('26.1').entitiesByName.husk.id;
  // An immobile mob six blocks away cannot melee; attributed damage here is the ability.
  await cmd(execute(`tp ${mob} ${x+8} 65 ${z}`));
  let attacker;
  await until(()=>{
    attacker=Object.values(bots[1].entities).find(e=>e.entityType===huskId
      && e.position.distanceTo(bots[1].entity.position)>4 && e.position.distanceTo(bots[1].entity.position)<10);
    return !!attacker;
  },'immobile ability caster beyond melee range');
  const abilityStart=packets[1].length;
  await until(()=>packets[1].slice(abilityStart).some(p=>p.type==='damage_event' && p.data.entityId===bots[1].entity.id
    && p.data.sourceDirectId===attacker.id+1),'non-admin lightning ability damage',15000);
  assert(attacker.position.distanceTo(bots[1].entity.position)>4,'Ability damage must occur beyond melee range');
  console.log('PASS: non-admin receives attributed lightning ability damage beyond melee range.');
  bots[0].chat('/customdungeon livetest stop');
  await until(()=>packets[1].some(p=>p.type==='stop_sound' && p.data.sound==='minecraft:music_disc.cat'),'music stops on close');
  // A separate template has no abilities, so its damage packet proves vanilla melee.
  await cmd('minecraft:gamemode creative '+names[1]);
  await cmd('minecraft:gamemode survival '+names[0]);
  chat[0].length=0;await openLive(prefix+'-melee');
  const meleeMob=`@e[type=minecraft:husk,nbt={BukkitValues:{"customdungeons:template":"${prefix}-melee"}},limit=1]`;
  await assertCreativeSafe(meleeMob,'melee');
  await cmd('minecraft:gamemode survival '+names[1]);
  await cmd('minecraft:gamemode creative '+names[0]);
  await cmd(execute(`tp ${meleeMob} ${x+2} 65 ${z}`));
  await until(()=>{
    attacker=Object.values(bots[1].entities).find(e=>e.entityType===huskId
      && e.position.distanceTo(bots[1].entity.position)<3);
    return !!attacker;
  },'vanilla-only husk next to non-admin');
  const start=packets[1].length;
  await until(()=>packets[1].slice(start).some(p=>p.type==='damage_event' && p.data.entityId===bots[1].entity.id
    && p.data.sourceDirectId===attacker.id+1),'vanilla non-admin melee',45000);
  console.log('PASS: vanilla mob chooses and attacks the nearby non-admin (actual damage_event, no abilities).');
  bots[0].chat('/customdungeon livetest stop');await delay(500);
  await cmd('minecraft:gamemode creative '+names[1]);
  await cmd('minecraft:gamemode survival '+names[0]);
  chat[0].length=0;await openLive(prefix+'-warden');
  await cmd('minecraft:gamemode creative '+names[0]);
  const warden=`@e[type=minecraft:warden,nbt={BukkitValues:{"customdungeons:template":"${prefix}-warden"}},limit=1]`;
  const marker=prefix+'-no-forced-anger';
  await delay(1500);
  await cmd(execute(`execute unless entity @e[type=minecraft:warden,nbt={BukkitValues:{"customdungeons:template":"${prefix}-warden"},anger:{suspects:[{anger:150}]}},limit=1] if entity ${warden} run minecraft:say ${marker}`));
  await until(()=>chat[0].some(s=>s.includes(marker)),'Warden no forced anger');
  console.log('PASS: live Warden has no forced 150 anger towards admin.');
  bots[0].chat('/customdungeon livetest stop');await delay(500);
  for(let i=0;i<2;i++)fs.writeFileSync(path.join(dir,'live-packets-'+i+'.json'),JSON.stringify(h.canonical(packets[i]),null,2));
  for(const list of packets)list.length=0;
  for(const name of names) {
    await cmd('minecraft:gamemode survival '+name);
    await cmd('minecraft:effect give '+name+' minecraft:resistance 300 4 true');
  }
  await cmd('customdungeon join '+names[0]+' '+prefix);await cmd('customdungeon join '+names[1]+' '+prefix);
  await until(()=>[0,1].every(i=>heard(i,'music_disc.13') && heard(i,'entity.ender_dragon.growl')),'session sounds to both participants',45000);
  await cmd(execute(`damage ${mob} 60 minecraft:generic`));
  await until(()=>[0,1].every(i=>heard(i,'block.anvil.land') && heard(i,'music_disc.cat') && packets[i].some(p=>p.type==='world_particles' && p.data.amount===30)),'session phase effects to both');
  heartbeatOn=false;
  await cmd('customdungeon stop '+prefix);
  await until(()=>[0,1].every(i=>packets[i].some(p=>p.type==='stop_sound' && p.data.sound==='minecraft:music_disc.cat')),'session music stop');
  console.log('PASS: session ability/phase/music/particles reach both participants and music stops.');
}
main().catch(e=>{console.error(e.stack);process.exitCode=1;}).finally(async()=>{
  heartbeatOn = false;
  for(const timer of heartbeats)clearInterval(timer);
  if(!foreign) {
    if(bots[0]?.entity) {bots[0].chat('/customdungeon livetest stop');await delay(300);}
    try {await cmd('customdungeon stop '+prefix);}catch{}
    if(platform)try{await cmd(execute(`fill ${platform} minecraft:air replace minecraft:glass`));}catch{}
  }
  for(const file of files)fs.unlinkSync(file);
  for(let i=0;i<2;i++)fs.writeFileSync(path.join(dir,'packets-'+i+'.json'),JSON.stringify(h.canonical(packets[i]),null,2));
  try{await cmd('minecraft:deop '+names[0]);await cmd('customdungeon reload');}catch{}
  for(const bot of bots)bot.quit();
  const errors=serverErrors(fs.readFileSync(logPath,'utf8'));if(errors.length){console.error(errors.join('\n'));process.exitCode=1;}
});
}
