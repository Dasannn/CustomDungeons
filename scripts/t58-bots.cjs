#!/usr/bin/env node
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{createRequire}=require('node:module'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),server='/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
const h=require('./t51-bot-support.cjs'),r=createRequire(server+'/../bots/package.json');
const {normalRoomKiting,normalRoomWaypoint,arenaEdge,nearbyFloorAim,irregularRampWaypoint,attackEveryTicks,engagedGoal,returnRally,rangedClient,combatJump,silenceAttempt,wardenMeleeGoal,wardenAppleDue,unexplainedBackwards}=require('./t58-bot-movement.cjs');
const {walkingWaypoint}=require('./t58-navigation.cjs');
const {waitUntil}=require('./t58-wait.cjs');
h.assertAgentsTarget(process.env.CD_TARGET||'agents',25566);
const zones=require('./t58-zone-acceptance.cjs');
const {ParticleEvidence,insideWarning}=require('./t58-particle-evidence.cjs');
const particleEvidence=new ParticleEvidence(),particleFile=process.argv.includes('--run')?fs.createWriteStream(path.join(process.env.T58_RESULTS,'particles.jsonl')):null;
const ids=['grab_throw','levitation_cage','drain_grab','bomb_mark','tactical_summon','roots','anchor_spear','soul_chain','charge','decoys'];
const part=process.env.T58_RUN_PART||'warden',zoneRun=part==='zones';
const navigation=zoneRun&&process.argv.includes('--run')?createRequire(process.env.T58_NAV_PACKAGE)('mineflayer-pathfinder'):null;
const fixtureIds=zoneRun?zones.ids:ids;
const id=process.env.T58_ID,dir=process.env.T58_RESULTS,seconds=Number(process.env.T58_SECONDS||120);
if(process.argv.includes('--plan')){if(zoneRun){console.log('T58b: 18 habilidades x sala cubierta con bordes y prueba en vivo; 5 bots físicos, daño dentro del aviso, suelo interior, escudo, limpieza y spark. 52 escenarios de 120 s, incluidos 13 en arena irregular v1; avisos verificados en paquetes reales y mezcla de las nueve zonas.');process.exit(0);}console.log('T58a: 5 bots físicos en supervivencia; copia de warden-colosal con fases y combos en vivo, mazmorra y sala normal con retorno; 8 controles, embestida y señuelos; trazas cada 5 ticks y spark. Duración predeterminada: 120 s por escenario.');process.exit(0);}
assert(/^t58b?-[a-f0-9]{6}$/.test(id||''));assert(dir?.startsWith(root+'/.agent/'));
const manifest=path.join(dir,'created.json'),created=[];
function fixture(folder,name,data){const file=path.join(server,'plugins/CustomDungeons',folder,name+'.yml');assert(name.startsWith(id+'-'));fs.writeFileSync(file,JSON.stringify(data,null,2),{flag:'wx'});created.push(file);fs.writeFileSync(manifest,JSON.stringify(created));}
if(process.argv.includes('--remove-fixtures')){for(const file of JSON.parse(fs.readFileSync(manifest))){assert(file.startsWith(server+'/plugins/CustomDungeons/')&&path.basename(file).startsWith(id+'-'));fs.rmSync(file,{force:true});}process.exit(0);}
const center={x:12224,y:101,z:12224},world='cd_dungeons';
const point=(x,z)=>({world,x,y:center.y,z,yaw:0,pitch:0});
const region=(x1,z1,x2,z2)=>({world,min:{x:x1,y:94,z:z1},max:{x:x2,y:116,z:z2}});
function dungeon(name,template,radius=24){fixture('dungeons',name,{'display-name':name,enabled:true,lobby:point(center.x-3,center.z),exit:point(center.x+56,center.z),
 'min-players':5,'max-players':5,'lobby-countdown-seconds':5,lives:10,'keep-inventory':true,'time-limit-seconds':600,'cooldown-seconds':0,'require-permission':false,
 scaling:{'extra-mobs-per-player':0,'extra-health-per-player':0},area:region(center.x-radius,center.z-radius,center.x+radius,center.z+radius),'teleport-on-start':false,'finish-mode':'IMMEDIATE',
 rooms:[{id:'arena',region:region(center.x-radius,center.z-radius,center.x+radius,center.z+radius),checkpoint:point(center.x-3,center.z),unlock:'AUTOMATIC',spawners:[{id:'boss',location:point(center.x+2,center.z),radius:1,
 waves:[{entries:[{'template-id':template,count:1,'delay-ticks':0}],mode:'SIMULTANEOUS','pause-after-ticks':0}]}]}],reward:{items:[],money:0,xp:0,commands:[]}});}
if(process.argv.includes('--prepare')){
 const jars=[];function collect(folder){for(const item of fs.readdirSync(folder,{withFileTypes:true})){const file=path.join(folder,item.name);if(item.isDirectory())collect(file);else if(file.endsWith('.jar'))jars.push(file);}}
 collect('/home/dasan/.gradle/caches/modules-2/files-2.1');
 const reader=path.join(dir,'T58YamlReader.java');fs.writeFileSync(reader,'class T58YamlReader { public static void main(String[] args) throws Exception { var yaml=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions())); try(var input=java.nio.file.Files.newInputStream(java.nio.file.Path.of(args[0]))) { System.out.println(new com.google.gson.Gson().toJson((Object)yaml.load(input))); } } }');
 const parsed=spawnSync('/usr/lib/jvm/temurin-25-jdk-arm64/bin/java',['-XX:-UsePerfData','-cp',jars.join(':'),reader,path.join(server,'plugins/CustomDungeons/mobs/warden-colosal.yml')],{encoding:'utf8'});assert.equal(parsed.status,0,parsed.stderr);
 const original=JSON.parse(parsed.stdout);delete original['world-boss'];
 fixture('mobs',id+'-warden',original);dungeon(id+'-dungeon',id+'-warden');dungeon(id+'-room',id+'-warden',10);fs.writeFileSync(path.join(dir,'warden-copy.json'),JSON.stringify(original));
 fixture('mobs',id+'-ally',{'entity-type':'HUSK','display-name':id+'-ally','max-health':20,damage:1,speed:.23,abilities:[]});
 for(const ability of fixtureIds){const name=zones.fixtureName(id,ability);
  const params=ability==='tactical_summon'?{template:id+'-ally'}:{};
  fixture('mobs',name,{'entity-type':ability==='decoys'?'SKELETON':'HUSK','display-name':name,'max-health':zoneRun?4096:2048,damage:1,speed:.23,boss:true,
   abilities:[{'ability-id':ability,trigger:'EVERY_X_SECONDS','trigger-value':5,target:'NEAREST',range:16,'cooldown-ticks':100,chance:1,'telegraph-ticks':20,params}]});
  dungeon(name+'-d',name,zoneRun?10:24);
  if(zoneRun&&[...zones.zoneIds,'charge','boss_totem','decoys'].includes(ability))dungeon(name+'-i',name,24);
 }
 fixture('mobs',id+'-zero',{'entity-type':'HUSK','display-name':id+'-zero','max-health':zoneRun?4096:2048,damage:1,speed:.23,abilities:[]});dungeon(id+'-zero-d',id+'-zero',zoneRun?arenaEdge('zero',true):24);
 fixture('mobs',id+'-profile',{'entity-type':'HUSK','display-name':id+'-profile','max-health':zoneRun?4096:2048,damage:1,speed:.23,boss:true,
 abilities:(zoneRun?zones.zoneIds:['charge','levitation_cage','roots','drain_grab','decoys','soul_chain']).map(a=>({'ability-id':a,trigger:'EVERY_X_SECONDS','trigger-value':5,target:'NEAREST',range:16,'cooldown-ticks':100,chance:1,'telegraph-ticks':20,params:{}}))});dungeon(id+'-profile-d',id+'-profile',zoneRun?arenaEdge('profile',true):24);
 if(zoneRun)dungeon(id+'-irregular',id+'-warden',24);
 fixture('mobs',id+'-cold-trio',{'entity-type':'HUSK','display-name':id+'-cold-trio','max-health':2048,damage:1,speed:.23,boss:true,abilities:['charge','levitation_cage','grab_throw'].map(a=>({'ability-id':a,trigger:'EVERY_X_SECONDS','trigger-value':5,target:'NEAREST',range:16,'cooldown-ticks':100,chance:1,'telegraph-ticks':20,params:{}}))});dungeon(id+'-cold-trio-d',id+'-cold-trio');process.exit(0);
}
assert(process.argv.includes('--run')||process.argv.includes('--recover-arena'));assert(seconds>=20&&seconds<=180,'20..180 seconds; shortened runs are smoke checks');
const {ServerLog}=require('./t58-server-log.cjs');
const serverLog=new ServerLog(path.join(server,'logs/latest.log'),path.join(dir,'observed-server.log'));
const mf=r('mineflayer'),conv=r('mineflayer/lib/conversions'),bots=[],names=Array.from({length:5},(_,i)=>'T58'+id.slice(-6)+i);
const eventFile=fs.createWriteStream(path.join(dir,'events.jsonl')),sampleFile=fs.createWriteStream(path.join(dir,'client-samples.jsonl'));
let fightAt=0,hostBounds=null;let acting=false,scenario='setup',caster=null,tick=0,dialog=null,failure,finished=false,bossFraction=1,mobWidth=.6;
process.once('SIGINT',()=>{failure='Runner interrupted';});process.once('SIGTERM',()=>{failure='Runner terminated';});
const results=[];const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function event(type,data){eventFile.write(JSON.stringify({at:Date.now(),scenario,type,data:h.canonical(data)})+'\n');}
async function until(fn,label,ms=30000,checkFailure=true){return waitUntil(fn,{label,timeoutMs:ms,failure:()=>failure,checkFailure});}
async function operation(action,args=[]){for(let attempt=0;;attempt++){const p=spawnSync(root+'/scripts/test-server.sh',[action,...args],{encoding:'utf8',env:{...process.env,CD_TARGET:'agents'}});if(p.status===0)return p.stdout;const out=p.stdout+p.stderr;if(out.includes('Otra operación del servidor está en curso.')&&attempt<10){await delay(60000);await delay(60000);}else throw Error(out);}}
async function cmd(command){assert(!/[\r\n]/.test(command));event('console',command);await operation('cmd',[command]);await delay(140);}
function mob(b=bots[0]){return caster?Object.values(b.entities).find(e=>e.uuid===caster):null;}
function count(b,item){return b.inventory.items().filter(v=>v.name===item).reduce((n,v)=>n+v.count,0);}
async function equip(b,item,destination='hand') {
 if(destination==='off-hand')return true; // Console installs the shield before each encounter.
 const available=v=>v.name===item&&!(item==='potion'&&v.slot===44); // Slot 8 reserves the real strength refill.
 let found=b.inventory.items().find(v=>available(v)&&v.slot>=36&&v.slot<45);
 if(!found) {
  const spare=b.inventory.items().find(available);if(!spare)return false;
  // Shift-click carries no cursor item, so no guessed 26.1 component hashes are needed.
  await b.clickWindow(spare.slot,0,1);await delay(250);
  found=b.inventory.items().find(v=>v.name===item&&v.slot>=36&&v.slot<45);if(!found)return false;
 }
 b.setQuickBarSlot(found.slot-36);await delay(120);return b.heldItem?.name===item;
}
async function consume(b,item,slot) {
 if(b.using)return;b.using=true;
 try {let ready;if(slot!==undefined){b.setQuickBarSlot(slot);await delay(120);ready=b.heldItem?.name===item;}else ready=await equip(b,item);if(ready) {
  const before=count(b,item);if(acting){b.setControlState('forward',false);b.setControlState('back',true);}else b.clearControlStates();
  await b.consume();await until(()=>count(b,item)<before,'actual item consumed',3500);
  event('consume',{bot:b.username,item,health:b.health,remaining:count(b,item)});
  if(b.heldItem?.name==='glass_bottle') {b._client.write('block_dig',{status:4,location:{x:0,y:0,z:0},face:0,sequence:0});await delay(100);}
 }}catch(e){event('consume-failed',String(e));}finally{b.using=false;if(!acting)b.clearControlStates();}
}
async function connect(i){const b=mf.createBot({host:'127.0.0.1',port:25566,username:names[i],auth:'offline',version:'26.1',physicsEnabled:true});bots[i]=b;
 const write=b._client.write.bind(b._client),acks=new Set();b._client.write=(name,data)=>{
  if(name==='teleport_confirm'){if(acks.has(data.teleportId))return;acks.add(data.teleportId);write(name,data);write('tick_end',{});if(b.entity){const p=b.entity.position;write('position_look',{x:p.x,y:p.y,z:p.z,yaw:conv.toNotchianYaw(b.entity.yaw),pitch:conv.toNotchianPitch(b.entity.pitch),flags:{onGround:false,hasHorizontalCollision:false}});write('tick_end',{});}return;}
  if(['look','position','position_look','flying'].includes(name)){const normalized={...data,flags:{onGround:!!(data.flags?.onGround??data.onGround),hasHorizontalCollision:!!b.entity?.isCollidedHorizontally}};for(const k of ['x','y','z','yaw','pitch'])if(k in normalized)assert(Number.isFinite(normalized[k]));write('tick_end',{});write(name,normalized);write('tick_end',{});return;}write(name,h.menuPacket(name,data));};
 b.on('messagestr',m=>event('chat',{bot:i,text:m}));b.on('error',e=>{if(!finished)failure=String(e);});b.on('kicked',e=>{if(!finished)failure=JSON.stringify(e);});b.on('death',()=>{acting=false;failure='Player died during '+scenario;event('death',i);});
 b._client.on('world_particles',p=>{const at=Date.now();if(particleEvidence.receive(b.username,p,at))particleFile?.write(JSON.stringify({at,scenario,player:b.username,...p})+'\n');});
 b._client.on('show_dialog',p=>{const raw=p.dialog?.data??p.dialog??p;dialog=raw.type?r('prismarine-nbt').simplify(raw):raw;});
 b._client.on('packet',(p,m)=>{if(['action_bar','damage_event','entity_effect','remove_entity_effect','set_passengers','entity_status'].includes(m.name))event(m.name,{bot:i,...h.canonical(p)});});
 await new Promise((ok,no)=>{const timer=setTimeout(()=>no(Error('Spawn timeout')),60000);b.once('spawn',()=>{clearTimeout(timer);ok();});b.once('error',no);b.once('kicked',no);});
 if(navigation) {
  b.loadPlugin(navigation.pathfinder);
  const moves=new navigation.Movements(b);
  moves.canDig=false;moves.canOpenDoors=false;moves.maxDropDown=1;moves.allow1by1towers=false;moves.scafoldingBlocks=[];
  moves.allowParkour=false;moves.allowSprinting=false;moves.allowEntityDetection=false;
  for(const name of ['water','lava','fire','magma_block','cactus'])if(b.registry.blocksByName[name])moves.blocksToAvoid.add(b.registry.blocksByName[name].id);
  b.t58Movements=moves;b.pathfinder.setMovements(moves);b.pathfinder.enablePathShortcut=false;b.pathfinder.tickTimeout=10;
 }
 b.on('physicsTick',()=>{write('tick_end',{});if(i===0){tick++;if(tick%5===0)sampleFile.write(JSON.stringify({at:Date.now(),scenario,tick,players:bots.map(v=>({name:v.username,position:v.entity?.position,health:v.health,mode:v.game?.gameMode})),mob:mob()?h.canonical({position:mob().position,yaw:mob().yaw,velocity:mob().velocity}):null})+'\n');}
  if(!acting)return;const e=mob(b);if(!e)return;const age=tick/20,d=b.entity.position.distanceTo(e.position),edge=Math.max(Math.abs(b.entity.position.x-center.x),Math.abs(b.entity.position.z-center.z))>arenaEdge(scenario,zoneRun);
  if(['native-hole','native-corner','native-return','native-ramp'].includes(scenario)) {
    const phase=(Date.now()-fightAt)/1000,p=b.entity.position;
    const goal=scenario==='native-ramp'?{x:center.x+24.03,z:center.z+2.4}:scenario==='native-return'?i===0&&phase>=60?{x:center.x+3,z:center.z+2}:{x:center.x+23+Math.sin(phase/2+i)*.8,z:center.z+23+Math.cos(phase/2+i)*.8}:scenario==='native-corner'?{x:center.x-10.69,z:center.z+1.04}:{x:center.x+2+Math.sin(phase/2+i)*.8,z:center.z+13+Math.cos(phase/2+i)*.8};
    b.lookAt(p.offset(goal.x-p.x,0,goal.z-p.z),true).catch(()=>{});
    b.setControlState('forward',Math.hypot(p.x-goal.x,p.z-goal.z)>.2);b.setControlState('back',false);
    b.setControlState('left',false);b.setControlState('right',false);b.setControlState('sprint',false);b.setControlState('jump',!!b.entity.isCollidedHorizontally);
    const s=b.controlState;write('player_input',{inputs:{forward:!!s.forward,backward:false,left:false,right:false,jump:!!s.jump,shift:false,sprint:false}});return;
  }
  const nearby=Object.values(b.entities).filter(v=>v.uuid!==caster&&['mob','hostile'].includes(v.type)&&v.position.distanceTo(b.entity.position)<3.5).sort((a,c)=>a.position.distanceTo(b.entity.position)-c.position.distanceTo(b.entity.position))[0];
  if(nearby&&b.health>=19&&b.food>=15&&(!scenario.startsWith('warden')||Date.now()<b.refreshStrengthAt)&&tick%20===i&&!b.using){b.using=true;equip(b,'netherite_sword').then(()=>{b.lookAt(nearby.position.offset(0,1,0),true);b.attack(nearby);}).finally(()=>{b.using=false;});}
  const rainFight=zoneRun&&scenario.endsWith('arrow_rain');
  // Three defenders hold real shields while two moving attackers stay exposed.
  const ranged=rangedClient(zoneRun,scenario,i,d,bossFraction)&&tick%240>160;const bracing=zoneRun&&(rainFight?i<3:i===2&&tick%180<90);
  const incoming=rainFight&&bracing&&i<2?Object.values(b.entities).filter(v=>v.name==='arrow'&&v.position.y>b.entity.position.y&&Math.hypot(v.position.x-b.entity.position.x,v.position.z-b.entity.position.z)<4).sort((a,c)=>a.position.distanceTo(b.entity.position)-c.position.distanceTo(b.entity.position))[0]:null;
  const desired=b.using?9:ranged?9:bracing?1.4:(scenario.startsWith('warden')&&bossFraction<.30&&i!==Math.floor(tick/200)%5?6:2.4);
  // In a normal room, periodically kite through a real doorway and return on foot.
  const corridor=normalRoomKiting(scenario,tick,bossFraction);
  const waypoint=normalRoomWaypoint(e.position.x-center.x,e.position.z-center.z,i);
  const door=b.entity.position.offset(center.x+waypoint.x-b.entity.position.x,0,center.z+waypoint.z-b.entity.position.z);
  const followMob=corridor&&d>4;
  b.lookAt(incoming?incoming.position:followMob?e.position.offset(0,Math.min(1.5,e.height*.65),0):corridor?door:edge?b.entity.position.offset(center.x-b.entity.position.x,0,center.z-b.entity.position.z):e.position.offset(0,Math.min(1.5,e.height*.65),0),true).catch(()=>{});
  b.setControlState('forward',corridor||edge||d>desired+.5);b.setControlState('back',!corridor&&!edge&&d<desired-.4);b.setControlState('left',!bracing&&(followMob||!corridor&&!edge)&&Math.floor(age/2+i)%2===0);b.setControlState('right',!bracing&&(followMob||!corridor&&!edge)&&Math.floor(age/2+i)%2!==0);b.setControlState('sprint',!corridor&&d>4);b.setControlState('jump',combatJump(zoneRun,scenario,tick,i,bossFraction,!!b.entity.isCollidedHorizontally));
  if(zoneRun&&hostBounds&&!scenario.startsWith('warden')&&scenario!=='warden-irregular') {
    const phase=(Date.now()-fightAt)/1000;
    // Real walking: first fight normally, then keep a valid melee target beside edges and corners.
    if(phase>=12) {
      const corner=phase<45||phase>=75&&phase<100;
      const [minX,minZ,maxX,maxZ]=hostBounds;
      let goal={x:maxX-.65,z:corner?maxZ-.65:(minZ+maxZ)/2+Math.sin(phase/3)*2};
      if(scenario.startsWith('zones-irregular-')&&phase>=45&&phase<75) {
        const p=b.entity.position,waypoint=irregularRampWaypoint({x:p.x-center.x,z:p.z-center.z},b.rampStage||0,i);
        b.rampStage=waypoint.stage;goal={x:center.x+waypoint.x,z:center.z+waypoint.z};
      }else b.rampStage=0;
      const rally=returnRally(phase);
      goal=engagedGoal(i,d,goal,{x:e.position.x,z:e.position.z},{x:center.x+rally.x,z:center.z+rally.z});
      goal=walkingWaypoint(b,goal,Date.now(),navigation.goals.GoalXZ);
      const p=b.entity.position,dg=Math.hypot(p.x-goal.x,p.z-goal.z);
      b.lookAt(incoming?.position||p.offset(goal.x-p.x,0,goal.z-p.z),true).catch(()=>{});
      b.setControlState('forward',dg>.35);b.setControlState('back',false);
      b.setControlState('left',dg<=.35&&Math.floor(phase)%2===0);
      b.setControlState('right',dg<=.35&&Math.floor(phase)%2!==0);
      b.setControlState('sprint',false);b.setControlState('jump',!!b.entity.isCollidedHorizontally||goal.y>p.y+.45);
    }
  }
  if(zoneRun&&scenario==='warden-irregular'&&bossFraction>=.3) {
    const p=b.entity.position,requested=wardenMeleeGoal(e.position,i,b.using?8:Math.max(2.4,mobWidth/2+1.4));
    const goal=walkingWaypoint(b,requested,Date.now(),navigation.goals.GoalXZ);
    b.lookAt(p.offset(goal.x-p.x,0,goal.z-p.z),true).catch(()=>{});
    b.setControlState('forward',Math.hypot(p.x-goal.x,p.z-goal.z)>.35);b.setControlState('back',false);
    b.setControlState('left',false);b.setControlState('right',false);b.setControlState('sprint',false);
    b.setControlState('jump',combatJump(zoneRun,scenario,tick,i,bossFraction,!!b.entity.isCollidedHorizontally||goal.y>p.y+.45));
  }
  if(zoneRun&&scenario==='warden-irregular'&&bossFraction<.3&&(Date.now()-fightAt)/1000<95) {
    const p=b.entity.position,goal=walkingWaypoint(b,d>14?e.position:{x:center.x-8.5,z:center.z+2},Date.now(),navigation.goals.GoalXZ);
    b.lookAt(p.offset(goal.x-p.x,0,goal.z-p.z),true).catch(()=>{});
    b.setControlState('forward',Math.hypot(p.x-goal.x,p.z-goal.z)>.35);b.setControlState('back',false);
    b.setControlState('left',false);b.setControlState('right',false);b.setControlState('jump',!!b.entity.isCollidedHorizontally||goal.y>p.y+.45);
  }
  const state=b.controlState;write('player_input',{inputs:{forward:!!state.forward,backward:!!state.back,left:!!state.left,right:!!state.right,jump:!!state.jump,shift:false,sprint:!!state.sprint}});
  if(b.using)return;
  if(wardenAppleDue(zoneRun,scenario,Date.now(),b.refreshGoldenAt)) {
    b.refreshGoldenAt=Infinity;
    consume(b,'enchanted_golden_apple').finally(()=>{b.refreshGoldenAt=Date.now()+25000;});
  }
  else if(silenceAttempt(zoneRun,scenario,tick,i))consume(b,'golden_apple');else if(b.health<19&&tick%20===i)consume(b,'potion');else if((b.food<15||b.health<20)&&tick%40===i)consume(b,scenario.startsWith('warden')?'enchanted_golden_apple':'golden_apple');
  else if(scenario.startsWith('warden')&&Date.now()>=b.refreshStrengthAt&&bossFraction>.27){b.refreshStrengthAt=Infinity;consume(b,'potion',8);}
  else if(ranged){if(tick%40===i){b.using=true;equip(b,'bow').then(()=>{b.activateItem();setTimeout(()=>{b.deactivateItem();b.using=false;},1100).unref();});}}
  else {const sword=scenario.startsWith('warden')&&bossFraction>.27?'netherite_sword':'wooden_sword';if(b.heldItem?.name!==sword) {b.using=true;equip(b,sword).finally(()=>{b.using=false;});}if(zoneRun?bracing:i===2&&tick%100<30)b.activateItem(true);else b.deactivateItem();
   // Fight through both phases with normal attacks, then defend to sustain the two-minute encounter.
   if(!(rainFight&&bracing)&&(!scenario.startsWith('warden')||bossFraction>.14)&&d<3+mobWidth/2-.15&&tick%attackEveryTicks(zoneRun,scenario,bossFraction)===i*3&&!b.using){const cage=bots.some(v=>v.entity?.effects?.[v.registry.effectsByName?.levitation?.id]);if(!cage||tick%400>200)b.attack(e);}}
 });return b;}
async function click(slot){await bots[0].clickWindow(slot,0,0);await delay(250);}
async function live(template){if(bots[0].currentWindow)bots[0].closeWindow(bots[0].currentWindow);bots[0].chat('/customdungeon');await until(()=>bots[0].currentWindow?.slots[21]?.name==='book','main menu');await click(21);await until(()=>JSON.stringify(bots[0].currentWindow?.title).includes('Biblioteca de mobs'),'library');
 let entry;
 for(let page=0;page<10;page++) {
  const w=bots[0].currentWindow;const slot=w.slots.slice(9,w.inventoryStart-9).findIndex(v=>v&&JSON.stringify(h.canonical(v)).includes(template));
  if(slot>=0){entry=slot+9;break;}
  await click(w.inventoryStart-4);
 }
 assert(entry!==undefined,'fixture in mob library');await click(entry);
 if(zoneRun) {
  const offset=serverLog.checkpoint();let confirmed=false;
  for(let attempt=0;attempt<10&&!confirmed;attempt++) {
   await bots[0].lookAt(bots[0].entity.position.offset(2,-.5,0),true);
   // The 26.x bridge may ignore a rotation-only packet while the menu is open.
   // Send the same physical position with the requested view, then verify it.
   const e=bots[0].entity,p=e.position;
   const view={x:p.x,y:p.y,z:p.z,yaw:Math.fround(conv.toNotchianYaw(e.yaw)),pitch:Math.fround(conv.toNotchianPitch(e.pitch)),flags:{onGround:e.onGround,hasHorizontalCollision:e.isCollidedHorizontally}};
   bots[0]._client.write('position_look',view);event('live-floor-view',view);await bots[0].waitForTicks(5);
   await cmd('t58probe aim '+bots[0].username);
   confirmed=rows(offset,'T58_AIM ').filter(a=>a.player===bots[0].username).slice(-1).some(a=>nearbyFloorAim(a,center.y));
  }
  assert(confirmed,'server confirms a nearby interior floor before opening live test');
 }
 await click(38);bots[0].closeWindow(bots[0].currentWindow);}
async function preparePlayers(label){acting=false;for(const b of bots){b.clearControlStates();b.t58Path=null;b.rampStage=0;}for(let i=0;i<bots.length;i++){
 // Zone encounters open at ordinary melee reach, then the bots kite with physical input.
 const openingX=zoneRun&&label.startsWith('zones-')?center.x+i*.4:center.x-4+i;
 await cmd('minecraft:gamemode survival '+names[i]);await cmd('execute in minecraft:'+world+' run minecraft:tp '+names[i]+' '+openingX+' '+center.y+' '+center.z);
 await cmd('minecraft:clear '+names[i]);for(const part of ['head','chest','legs','feet']){const armor={head:'helmet',chest:'chestplate',legs:'leggings',feet:'boots'}[part];await cmd('minecraft:item replace entity '+names[i]+' armor.'+part+' with minecraft:netherite_'+armor+'[enchantments={"minecraft:protection":4}]');}
 await cmd('minecraft:give '+names[i]+' minecraft:wooden_sword');await cmd('minecraft:item replace entity '+names[i]+' weapon.offhand with minecraft:shield');await cmd('minecraft:give '+names[i]+(label.startsWith('warden')?' minecraft:netherite_sword[enchantments={"minecraft:sharpness":5}]':' minecraft:netherite_sword'));await cmd('minecraft:give '+names[i]+' minecraft:bow');await cmd('minecraft:give '+names[i]+' minecraft:arrow 64');await cmd('minecraft:give '+names[i]+' minecraft:golden_apple 64');await cmd('minecraft:give '+names[i]+' minecraft:enchanted_golden_apple 16');await cmd('minecraft:give '+names[i]+' minecraft:potion[potion_contents={potion:"minecraft:strong_healing"}] 24');
 await equip(bots[i],'wooden_sword');await equip(bots[i],'shield','off-hand');}
 // Health between fights is restored by consuming inventory items, never by commands.
 for(const b of bots){while(b.health<19){await consume(b,'potion');await delay(400);}await consume(b,'enchanted_golden_apple');b.refreshGoldenAt=Date.now()+25000;b.clearControlStates();}
 if(label.startsWith('warden'))for(const b of bots){await cmd('minecraft:item replace entity '+b.username+' hotbar.7 with minecraft:potion[potion_contents={potion:"minecraft:strong_strength"}]');await consume(b,'potion',7);await cmd('minecraft:item replace entity '+b.username+' hotbar.8 with minecraft:potion[potion_contents={potion:"minecraft:strong_strength"}]');b.refreshStrengthAt=Date.now()+45000;}
}
function rows(from,tag='T58_TRACE '){return serverLog.readFrom(from).split('\n').filter(line=>line.includes(tag)).map(line=>JSON.parse(line.split(tag)[1]));}
function traces(from){return rows(from);}
async function buildScene(label) {
 const mode=label==='native-hole'?'irregular-native':label==='warden-irregular'?'irregular-warden':label.startsWith('zones-irregular-')?'irregular':label.startsWith('warden')?'warden':label.startsWith('zones-room-')||['zero','profile'].includes(label)?'room':'live';
 const offset=serverLog.checkpoint();
 await cmd('t58probe arena '+id+' '+mode);
 await until(()=>rows(offset,'T58_ARENA_READY ').some(a=>a.owner===id&&a.mode===mode),'versioned arena ready',60000);
}
async function nativeHole() {
 const ramp=process.env.T58_NATIVE_RAMP==='1',returning=process.env.T58_NATIVE_RETURN==='1',corner=process.env.T58_NATIVE_CORNER==='1',label=ramp?'native-ramp':returning?'native-return':corner?'native-corner':'native-hole';
 await buildScene(corner||returning||ramp?'zones-irregular-falling_pillars':label);await preparePlayers('zones-irregular-rift');
 for(let i=0;i<names.length;i++)await cmd('execute in minecraft:'+world+' run minecraft:tp '+names[i]+' '+(center.x+(ramp?24.03:returning?23:corner?-10.69:2+i*.15))+' '+center.y+' '+(center.z+(ramp?2.4:returning?23:corner?1.04:13-i*.1)));
 const offset=serverLog.checkpoint();
 await cmd('t58probe '+(ramp?'native-ramp':returning?'native-return':corner?'native-corner':'native-husk')+' '+id+' '+names[0]);
 await until(()=>rows(offset,'T58_NATIVE_READY ').some(v=>v.owner===id),'unmarked native reference');
 const ready=rows(offset,'T58_NATIVE_READY ').find(v=>v.owner===id);
 caster=ready.uuid;scenario=label;fightAt=Date.now();acting=true;
 const end=fightAt+120000;while(Date.now()<end){if(failure)throw Error(failure);if(fs.existsSync(path.join(dir,'abort')))throw Error('Owned diagnostic aborted');if(Number(fs.readFileSync('/sys/class/thermal/thermal_zone0/temp'))>=82000)throw Error('Temperature reached 82 C');await delay(1000);}
 acting=false;for(const b of bots)b.clearControlStates();
 const data=rows(offset,'T58_NATIVE_TRACE ').filter(v=>v.owner===id);fs.writeFileSync(path.join(dir,label+'.json'),JSON.stringify(data));
 if(!corner&&!returning&&!ramp)assert(!ready.pathFound||ready.pathReachesTarget===false,'native planner also cannot reach the target');
 assert(data.length>=450&&data.every(v=>!v.marked&&v.ai),'vanilla reference with normal AI and no plugin marks');
 if(!corner&&!returning&&!ramp)assert(data.every(v=>v.mob.y<center.y),'the unmarked husk also cannot leave the three-block hole');
 if(returning) {
   const far=data.filter(v=>v.nearestPlayer>v.followRange);
   assert(far.length>=150&&far.some(v=>!v.target&&v.advance<.01),'unmarked native actor loses a target outside follow range');
   assert(data.some(v=>v.nearestPlayer<v.followRange&&v.target&&v.advance>.03),'native actor pursues after a real player walks back into range');
 }
 let begin=null,maxQuietTicks=0;for(const v of data){if(v.advance<.01&&!v.quietCause){begin??=v.tick;maxQuietTicks=Math.max(maxQuietTicks,v.tick-begin);}else begin=null;}
 const proof={diagnostic:label,acceptance:false,seconds:120,samples:data.length,maxQuietTicks,minY:Math.min(...data.map(v=>v.mob.y)),maxY:Math.max(...data.map(v=>v.mob.y)),ready};
 fs.writeFileSync(path.join(dir,'native-proof.json'),JSON.stringify(proof,null,2));console.log('NATIVE_REFERENCE '+JSON.stringify(proof));
 await cmd('t58probe native-stop '+id);caster=null;
}
async function fight(label,template,dungeonId){if(zoneRun)await buildScene(label);await preparePlayers(label);particleEvidence.clear();hostBounds=null;scenario=label;bossFraction=1;const before=new Set(Object.values(bots[0].entities).map(e=>e.uuid)),offset=serverLog.checkpoint();
 if(dungeonId){for(const name of names)await cmd('customdungeon join '+name+' '+dungeonId);}else await live(template);
 await until(()=>Object.values(bots[0].entities).some(e=>!before.has(e.uuid)&&['mob','hostile'].includes(e.type)&&e.position.distanceTo(bots[0].entity.position)<15),'new mob');
 caster=Object.values(bots[0].entities).filter(e=>!before.has(e.uuid)&&['mob','hostile'].includes(e.type)).sort((a,b)=>a.position.distanceTo(bots[0].entity.position)-b.position.distanceTo(bots[0].entity.position))[0].uuid;
 if(zoneRun&&!dungeonId)assert(mob().position.y===center.y&&mob().position.distanceTo(bots[0].entity.position)<4,'live caster starts on the nearby interior floor');
 const cold=seconds>=120&&((label==='zones-room-vortex'&&results.length===0)||(label==='warden-dungeon'&&results.length===0)||(label==='cold-trio'&&results.length===0));
 // Paper's default sampler targets Server thread; offline analysis requires that exact thread.
 if(cold)await cmd('spark profiler start --interval 1 --timeout 30 --save-to-file');
 else if(['zero','warden-dungeon','zones-room-vortex'].includes(label)||label==='profile'&&!zoneRun)await cmd('spark profiler start --interval 1 --timeout 60 --save-to-file');
 acting=true;const fightStarted=Date.now(),deadline=fightStarted+seconds*1000;fightAt=fightStarted;let nextWindow=31,stableStarted=false;

 let healthCursor=offset;while(Date.now()<deadline){
 if(zoneRun&&label==='profile'&&!stableStarted&&Date.now()-fightStarted>=60000){await cmd('spark profiler start --interval 1 --timeout 59 --save-to-file');stableStarted=true;}
 if(cold&&Date.now()-fightStarted>=nextWindow*1000){
   // Spark saves asynchronously; the next window must start after the previous timeout.
   if(nextWindow===91)await delay(350);
   await cmd('spark profiler start --interval 1 --timeout '+(nextWindow===31?60:Math.max(1,Math.floor((deadline-Date.now())/1000)))+' --save-to-file');
   nextWindow=nextWindow===31?91:Infinity;
 }
 const recent=traces(healthCursor);healthCursor=serverLog.size;const last=recent.filter(v=>v.template===template).at(-1);if(fs.existsSync(path.join(dir,'abort')))throw Error('Owned acceptance aborted; preserving evidence and cleaning up');if(last){hostBounds=last.area||hostBounds;bossFraction=last.healthFraction;mobWidth=last.mobWidth;if(last.players.some(p=>p.y<(zoneRun?94:center.y-.05)))throw Error('Player feet below the solid floor during '+label+' at tick '+last.tick);}if(failure)throw Error(failure);if(fs.existsSync('/sys/class/thermal/thermal_zone0/temp')&&Number(fs.readFileSync('/sys/class/thermal/thermal_zone0/temp','utf8'))>=82000)throw Error('Temperature reached 82 C; stopping owned server');await delay(1000);}acting=false;for(const b of bots)b.clearControlStates();const data=traces(offset).filter(v=>v.template===template),started=new Set(data.flatMap(v=>v.started.map(s=>s.split('#')[0])));
 assert(data.length>seconds*2,'server samples each 5 ticks');assert(data.every(v=>v.players.every(p=>p.health>0)),'living survival players');assert(data.every(v=>v.players.every(p=>p.y>=(zoneRun?94:center.y-.05))),'controls never seat players below the solid floor');
 const hits=rows(offset,'T58_HIT ').filter(v=>v.template===template),windows=new Map();let quietStart=null,maxQuietTicks=0;
 for(const v of data){if((v.advance||0)<.01&&!v.quietCause){quietStart??=v.tick;maxQuietTicks=Math.max(maxQuietTicks,v.tick-quietStart);}else quietStart=null;for(const effect of v.controlEffects)if(effect.applied){const key=effect.id+effect.ends+effect.players.join(',');const window=windows.get(key)||{id:effect.id,duration:effect.duration,first:v.tick,last:v.tick};window.last=v.tick;windows.set(key,window);}}
 const controls=[...windows.values()].map(v=>({...v,observedTicks:v.last-v.first}));
 for(const v of data)for(const s of v.motionSteps||[])if(s.advance>2&&s.teleport==='ability'){if(v.started.some(key=>key.startsWith('decoys#')))s.abilityTeleport='decoys';else if(v.started.some(key=>key.startsWith('blink_behind#')))s.abilityTeleport='blink_behind';}
 const snapshots=data.flatMap(v=>v.motionSteps||[]),physical=data.flatMap(v=>v.physicalSteps||[]);assert(physical.length>seconds*10,'physical motion samples at tick resolution');
 const summary={scenario:label,template,seconds,samples:data.length,motionSteps:snapshots.length,physicalSteps:physical.length,started:[...started],backwards:data.filter(v=>(v.physicalSteps||[]).some(s=>unexplainedBackwards(s))).length,
  unplannedTeleports:snapshots.filter(s=>s.teleport==='external'||s.advance>2&&!s.abilityTeleport&&s.teleport!=='host_return').length,abilityTeleports:snapshots.filter(s=>s.abilityTeleport).length,
  hostReturns:snapshots.filter(s=>s.teleport==='host_return'&&s.advance>2).length,rawBackwards:physical.filter(s=>s.advance>.03&&s.facingDot<0).length,verifiedRecoil:physical.filter(s=>s.advance>.03&&s.facingDot<0&&s.knockback).length,phases:[...new Set(data.map(v=>v.phaseIndex))],combosCompleted:data.flatMap(v=>v.comboCompleted),
  backwardsChord:data.filter(v=>unexplainedBackwards(v,.15)).length,
  backwardsCharge:data.filter(v=>v.combatEffects.some(e=>e.id==='charge'&&e.begun&&e.stun<=0)&&(v.motionSteps||[]).some(s=>s.advance>.03&&s.facingDot<0&&s.teleport!=='host_return')).length,
  controlSamples:data.filter(v=>v.controlling).length,decoySamples:data.filter(v=>v.decoys.length).length,maxQuietTicks,hits:hits.length,decoyHits:hits.filter(v=>v.decoy).length,controlDurations:controls};
 if(zoneRun) {
   summary.zoneHits=rows(offset,'T58_ZONE_HIT ').filter(v=>v.template===template).map(hit=>({...hit,inside:insideWarning(hit),warned:particleEvidence.warned(hit.player,hit),evidence:'world_particles'}));
   summary.particlePackets=particleEvidence.received;summary.arenaVersion=1;summary.terrainContacts=data.filter(v=>v.players.some(p=>p.onGround&&Math.abs(p.y-center.y)>.1)).length;
   const margins=data.flatMap(v=>v.area?v.players.map(p=>{const [a,b,c,d]=v.area;return {edge:Math.min(p.x-a,c-p.x,p.z-b,d-p.z),corner:Math.max(Math.min(p.x-a,c-p.x),Math.min(p.z-b,d-p.z))};}):[]);
   summary.borderVisits=margins.filter(p=>p.edge>=0&&p.edge<=1).length;summary.cornerVisits=margins.filter(p=>p.corner>=0&&p.corner<=1).length;
   summary.zoneEffects=rows(offset,'T58_ZONE_EFFECT ').filter(v=>v.template===template);
   summary.pillarPlayers=Math.max(0,...rows(offset,'T58_PILLARS ').filter(v=>v.template===template).map(v=>v.players));
 }
 fs.writeFileSync(path.join(dir,label+'.json'),JSON.stringify(data));results.push(summary);console.log('SCENARIO '+JSON.stringify({...summary,zoneHits:summary.zoneHits?.length,zoneEffects:summary.zoneEffects?.length}));
 if(dungeonId)await cmd('customdungeon stop '+dungeonId);else bots[0].chat('/customdungeon livetest stop');await delay(1200);
 if(zoneRun){await cmd('t58probe cleanup '+id);await until(()=>rows(offset,'T58_CLEANUP ').some(v=>v.prefix===id&&v.entities===0&&v.blocks===0&&v.zones===0&&v.combat===0),'complete owned cleanup',10000);}
 caster=null;
 const ability=label.startsWith('zones-')?label.replace(/^zones-(room|live|irregular)-/,''):null;
 const original=JSON.parse(fs.readFileSync(path.join(dir,'warden-copy.json')));const expected=ability?[ability]:label==='cold-trio'?['charge','levitation_cage','grab_throw']:label.startsWith('warden')?[...new Set([...original.abilities,...original.phases.flatMap(p=>p.abilities)].map(a=>a['ability-id']))]:label==='zero'?[]:label==='profile'?(zoneRun?zones.zoneIds:['charge','levitation_cage','roots','drain_grab','decoys','soul_chain']):[label];
 if(seconds>=120){for(const ability of expected)assert(started.has(ability),'ability started: '+ability+' in '+label);}else if(expected.length)assert(started.size>0,'at least one ability in a smoke encounter');
 assert.equal(summary.backwards,0,'no unexplained backwards movement');assert.equal(summary.backwardsCharge,0,'charge advances in its actual body direction');assert(maxQuietTicks<=20,'no unexplained stationary interval over 20 ticks');
 assert.equal(summary.unplannedTeleports,0,'no admin or unrelated teleports during the fight');
 if(label.startsWith('warden')&&seconds>=120){assert(summary.phases.includes(1),'both configured boss phases reached');assert(summary.combosCompleted.length>0,'at least one full configured combo completed');}
 if(label==='warden-room'&&seconds>=120)assert(summary.hostReturns>0,'normal room exercised at least one real boundary return');
 const speeds=data.filter(v=>!v.paused&&!v.warning&&!v.nativeStun&&!v.nativeRoar);assert(speeds.length>0,'effective speed sampled outside pauses');assert(speeds.every(v=>Math.abs(v.speed-(label.startsWith('warden')?.25:.23))<=.005),'configured speed survives vanilla base changes');
 assert(data.flatMap(v=>v.combatEffects).every(e=>e.stun<=0||e.id!=='charge'||e.wall),'charge stun requires a physical wall');
 if(['grab_throw','levitation_cage','drain_grab','roots','soul_chain','anchor_spear'].includes(label))assert(summary.controlSamples>0,'control applied');
 if(['levitation_cage','roots'].includes(label)&&seconds>=120)assert(controls.some(v=>v.id===label&&v.observedTicks>=v.duration-10),'at least one control lasts its configured duration');
 if(label==='cold-trio'&&seconds>=120) {
   assert(controls.some(v=>v.id==='levitation_cage'&&v.observedTicks>=v.duration-10),'cold trio includes a full configured cage');
   assert(controls.some(v=>v.id==='grab_throw'),'cold trio applies grab and throw');
   assert(data.some(v=>v.combatEffects.some(e=>e.id==='charge'&&e.begun)),'cold trio performs charge');
 }
 if(label==='tactical_summon')assert(data.some(v=>v.summons>0),'tactical summon produces minions');
 if(label==='bomb_mark')assert(hits.some(v=>v.rawDamage>1),'bomb causes effective damage');
 if(label==='drain_grab')assert(hits.some(v=>v.rawDamage>1),'drain causes a real damage pulse beyond melee damage');
 if(zoneRun&&seconds>=120&&(ability||label==='warden-irregular'))zones.validateCoverage(summary);
 if(ability)zones.validateScenario(summary,ability,seconds);
 if(label==='decoys'||ability==='decoys'){assert(summary.decoySamples>0,'decoys spawned');assert(data.flatMap(v=>v.decoys).every(v=>v.ai),'decoys have AI');assert(summary.decoyHits>0,'ranged-type decoys perform melee attacks');}
 return summary;
}
async function profile(label,template,dungeonId=template+'-d') {
 const spark=path.join(server,'plugins/spark'),before=new Set(fs.readdirSync(spark));
 const cold=seconds>=120&&results.length===0&&['warden-dungeon','cold-trio','zones-room-vortex'].includes(label);
 await cmd('spark profiler cancel');await fight(label,template,dungeonId);
 const files=()=>fs.readdirSync(spark).filter(f=>f.endsWith('.sparkprofile')&&!before.has(f)).sort((a,b)=>fs.statSync(path.join(spark,a)).mtimeMs-fs.statSync(path.join(spark,b)).mtimeMs);
 await until(()=>files().length===(cold?3:1),'complete spark windows',90000);await delay(1000);
 const measured=files();
 for(let i=0;i<measured.length;i++)fs.copyFileSync(path.join(spark,measured[i]),path.join(dir,cold?label+'-'+['0-30','31-91','91-end'][i]+'.sparkprofile':label+'.sparkprofile'));
 if(cold&&!zoneRun) {
   const library=path.join(server,'libraries/me/lucko/spark-paper');
   const jars=fs.readdirSync(library).flatMap(version=>fs.readdirSync(path.join(library,version)).filter(name=>name.endsWith('.jar')).map(name=>path.join(library,version,name))).sort();
   const analysis=spawnSync('/usr/lib/jvm/temurin-25-jdk-arm64/bin/java',['-cp',jars.at(-1),path.join(root,'scripts/T58SparkWindows.java'),...['0-30','31-91','91-end'].map(window=>path.join(dir,label+'-'+window+'.sparkprofile'))],{encoding:'utf8'});
   assert.equal(analysis.status,0,analysis.stderr);const windows=JSON.parse(analysis.stdout);
   fs.writeFileSync(path.join(dir,'cold-windows.json'),JSON.stringify(windows,null,2));
   console.log('COLD_WINDOWS '+JSON.stringify(windows));
   // The complete suite checks both cold workloads after all seventeen scenarios finish.
   if(!process.env.T58_ACCEPTANCE_RESULTS)require('./t58-profile-windows.cjs').validateColdWindows(windows);
 }

}

(async()=>{try{
 if(process.argv.includes('--recover-arena')) {
  const nonce=process.env.T58_RECOVER_NONCE,mode=process.env.T58_RECOVER_MODE;
  assert(/^t58b-[0-9a-f]{6}$/.test(nonce)&&['restore','restore-flat'].includes(mode));
  const at=serverLog.checkpoint();await cmd('t58probe arena '+nonce+' '+mode);
  await until(()=>rows(at,'T58_ARENA_RESTORED ').some(v=>v.owner===nonce&&v.differentBlocks===0),'recovered acceptance terrain',60000);
  console.log('T58_RECOVERY '+JSON.stringify({acceptance:false,mode,nonce,differentBlocks:0}));return;
 }
 for(let i=0;i<5;i++)await connect(i);for(const name of names)await cmd('op '+name);await cmd('execute in minecraft:'+world+' run minecraft:forceload add '+(center.x-80)+' '+(center.z-80)+' '+(center.x+80)+' '+(center.z+80));
 if(zoneRun&&(process.env.T58_NATIVE_HOLE==='1'||process.env.T58_NATIVE_CORNER==='1'||process.env.T58_NATIVE_RETURN==='1'||process.env.T58_NATIVE_RAMP==='1')){await nativeHole();return;}
 if(!zoneRun) {
 await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x-64)+' 100 '+(center.z-64)+' '+(center.x+64)+' 100 '+(center.z+64)+' minecraft:stone');
 await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x+54)+' 100 '+(center.z-2)+' '+(center.x+58)+' 100 '+(center.z+2)+' minecraft:stone');
 }
 const selected=(process.env.T58_SCENARIOS||(zoneRun?['zones-room-vortex','zones-room-rift','zones-live-rift','zero','profile',...[...zones.zoneIds,'boss_totem','decoys','charge'].map(a=>'zones-irregular-'+a),'warden-irregular',...zones.ids.filter(a=>!['vortex','rift'].includes(a)).map(a=>'zones-room-'+a),...zones.ids.filter(a=>a!=='rift').map(a=>'zones-live-'+a),'warden-live']:part==='trio'?['cold-trio','zero']:['warden-dungeon','warden-room','warden-live',...ids,'zero','profile']).join(',')).split(',');
 assert.equal(selected[0],zoneRun?'zones-room-vortex':part==='trio'?'cold-trio':'warden-dungeon','cold scenario is the first combat of the fresh JVM');
 for(const label of selected){if(label.startsWith('zones-')){const ability=label.replace(/^zones-(room|live|irregular)-/,'');assert(zones.ids.includes(ability));const template=zones.fixtureName(id,ability);if(label==='zones-room-vortex')await profile(label,template,template+'-d');else await fight(label,template,label.startsWith('zones-room-')?template+'-d':label.startsWith('zones-irregular-')?template+'-i':null);}else if(label==='warden-irregular'){await fight(label,id+'-warden',id+'-irregular');}else if(label==='warden-live'){await fight(label,id+'-warden');}else if(label==='warden-dungeon')await profile(label,id+'-warden',id+'-dungeon');else if(label==='warden-room'){if(!zoneRun)await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x-12)+' 101 '+(center.z-12)+' '+(center.x+12)+' 105 '+(center.z+12)+' minecraft:stone hollow');await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x-11)+' 101 '+(center.z-11)+' '+(center.x+11)+' 105 '+(center.z+11)+' minecraft:air');await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x-3)+' 101 '+(center.z+12)+' '+(center.x+3)+' 105 '+(center.z+12)+' minecraft:air');await fight(label,id+'-warden',id+'-room');if(!zoneRun)await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x-12)+' 101 '+(center.z-12)+' '+(center.x+12)+' 105 '+(center.z+12)+' minecraft:air');}else if(label==='zero'||label==='profile'||label==='cold-trio'){await profile(label,id+'-'+label);}else {assert(ids.includes(label));const template=zones.fixtureName(id,label);await fight(label,template,template+'-d');}}
 console.log('PASS T58 '+JSON.stringify({acceptance:seconds>=120&&results.length===15&&['warden-live','warden-dungeon','warden-room',...ids,'zero','profile'].every(label=>results.some(v=>v.scenario===label)),scenarios:results.map(s=>({...s,zoneHits:s.zoneHits?.length,zoneEffects:s.zoneEffects?.length}))}));
 }catch(e){console.error('FAIL '+e.stack);process.exitCode=1;}finally{acting=false;finished=true;for(const b of bots)b.clearControlStates();try{await cmd('customdungeon stop '+id+'-dungeon');await cmd('customdungeon stop '+id+'-room');for(const a of fixtureIds)await cmd('customdungeon stop '+zones.fixtureName(id,a)+'-d');await cmd('customdungeon stop '+id+'-zero-d');await cmd('customdungeon stop '+id+'-profile-d');await cmd('customdungeon stop '+id+'-cold-trio-d');if(bots[0])bots[0].chat('/customdungeon livetest stop');if(!zoneRun)await cmd('execute in minecraft:'+world+' run minecraft:fill '+(center.x-12)+' 101 '+(center.z-12)+' '+(center.x+12)+' '+(zoneRun?108:105)+' '+(center.z+12)+' minecraft:air');if(zoneRun){await cmd('t58probe native-stop '+id);for(const a of [...zones.zoneIds,'boss_totem','decoys','charge'])await cmd('customdungeon stop '+zones.fixtureName(id,a)+'-i');await cmd('customdungeon stop '+id+'-irregular');const restoredAt=serverLog.checkpoint();await cmd('t58probe arena '+id+' restore');await until(()=>rows(restoredAt,'T58_ARENA_RESTORED ').some(v=>v.owner===id&&v.differentBlocks===0),'exact block restoration',60000,false);}}catch(e){console.error('Cleanup: '+e.stack);process.exitCode=1;}
 for(const name of names)try{await cmd('deop '+name);}catch(e){console.error('Operator cleanup: '+e.stack);process.exitCode=1;}
 try{await cmd('execute in minecraft:'+world+' run minecraft:forceload remove '+(center.x-80)+' '+(center.z-80)+' '+(center.x+80)+' '+(center.z+80));}catch(e){console.error('Chunk cleanup: '+e.stack);process.exitCode=1;}
 fs.writeFileSync(path.join(dir,'summary.json'),JSON.stringify(results,null,2));for(const b of bots)b.quit();eventFile.end();sampleFile.end();particleFile?.end();try{serverLog.close();}catch(e){console.error('Log evidence: '+e.stack);process.exitCode=1;}}})();
