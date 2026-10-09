'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');

// Actual client physics, server AI and combat damage. All setup commands precede the fight.
// No health/effect command, teleport, forced damage or mob mutation is allowed while fighting.
module.exports=async function fight(ctx) {
 const {bots,names,spawn,despawn,id,delay,packets,chat,dir}=ctx;
 const fighters=[1,2,3],events=[],teleports=[],gear={armor:'diamond (no enchantments)',sword:'diamond (no enchantments)',bow:'unenchanted, 64 arrows',goldenApples:12,steak:32,shield:'off hand'};
 let fighting=false;
 async function setup(command){assert(!fighting,'Setup command during combat: '+command);teleports.push(command);await ctx.cmd(command);}
 for(const i of fighters) {
  await setup('effect clear '+names[i]);await setup('clear '+names[i]);
  for(const [slot,item] of [['head','diamond_helmet'],['chest','diamond_chestplate'],['legs','diamond_leggings'],['feet','diamond_boots']])await setup(`item replace entity ${names[i]} armor.${slot} with minecraft:${item}`);
  for(const [slot,item,count] of [[0,'diamond_sword',1],[1,'bow',1],[2,'golden_apple',12],[3,'cooked_beef',32],[4,'arrow',64]])await setup(`item replace entity ${names[i]} hotbar.${slot} with minecraft:${item} ${count}`);
  await setup(`item replace entity ${names[i]} weapon.offhand with minecraft:shield`);
 }
 async function serverUseFinished(bot) {
  await new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{bot._client.removeListener('entity_status',complete);bot.deactivateItem();reject(Error('No server consumption completion (cooldown or interrupted use)'));},5000);
   function complete(packet){if(packet.entityId!==bot.entity.id||packet.entityStatus!==9)return;clearTimeout(timer);bot._client.removeListener('entity_status',complete);resolve();}
   bot._client.on('entity_status',complete);bot.activateItem();
  });
  await delay(150);
 }
 await spawn(id+'-fair'); // Spawn helper pauses AI only during setup.
 const [x,y,z]=ctx.at();
 for(const i of fighters){await setup(`tp ${names[i]} ${x+([6,-6,0][i-1])+.5} ${y} ${z+([0,0,6][i-1])+.5}`);bots[i].setQuickBarSlot(3);if(bots[i].food<20)try{await serverUseFinished(bots[i]);}catch{}bots[i].setQuickBarSlot(0);}
 for(const i of fighters) {
  const b=bots[i];await b.waitForChunksToLoad();
  assert(b.blockAt(b.entity.position.offset(0,-1,0))?.name==='stone','Client must see the arena floor before combat');
 }
 const from=packets.map(p=>p.length),rewardFrom=chat.map(c=>c.length);
 await setup(`data merge entity @e[tag=${id},limit=1] {NoAI:0b}`);
 const started=Date.now(),duration=300000;
 const actor=fighters.map(i=>({index:i,name:names[i],initialHealth:bots[i].health,minimumHealth:bots[i].health,deaths:0,attacks:0,arrows:0,backAttempts:0,itemUseCompletions:0,lastAttack:0,lastBow:0,eating:false,drawing:false,nextHeal:0}));
 assert(actor.every(a=>a.initialHealth===20),'All fighters must start with full health');
 for(const a of actor){const b=bots[a.index];b.once('death',()=>{a.deaths++;a.minimumHealth=0;events.push({at:Date.now()-started,type:'death',name:a.name});b.clearControlStates();});}
 fighting=true;
 for(const a of actor){bots[a.index].realPhysics=true;bots[a.index].physicsEnabled=true;}
 let bossId,bossLowest=1,lastBar=[],lastSample=0;
 const combat=setInterval(()=>{
  const elapsed=Date.now()-started;
  for(const a of actor) {
   const b=bots[a.index];a.minimumHealth=Math.min(a.minimumHealth,b.health);if(a.deaths||b.health<=0||a.eating)continue;
   const target=bossId?b.entities[bossId]:Object.values(b.entities).find(e=>e.uuid===ctx.bossUuid());
   if(!target)continue;bossId=target.id;
   const phase=Math.floor(elapsed/10000)%3,tactic=elapsed<12000?'sword':phase===1?'bow':phase===2?'critical':'sword';
   // One engages in front; two circle behind the live facing of the mob.
   const yaw=target.yaw||0,angle=a.index===1?yaw+Math.PI:yaw+(a.index===2?-.35:.35),radius=tactic==='bow'?7:2.1;
   const destination=target.position.offset(Math.sin(angle)*radius,0,Math.cos(angle)*radius);
   const distance=b.entity.position.distanceTo(target.position),toDest=b.entity.position.distanceTo(destination);
   const aiming=tactic==='bow'||toDest<.8?target.position.offset(0,1.1,0):destination.offset(0,1.6,0);
   void b.lookAt(aiming,false).catch(()=>{});
   b.setControlState('forward',toDest>.6);b.setControlState('sprint',distance>4);b.setControlState('jump',tactic==='critical'&&distance<4);
   if(b.health<14&&elapsed>a.nextHeal&&b.inventory.items().some(i=>i.name==='golden_apple')) {
    a.eating=true;b.clearControlStates();b.setQuickBarSlot(2);
    (async()=>{try{await serverUseFinished(b);a.itemUseCompletions++;events.push({at:Date.now()-started,type:'item-use-finished',name:a.name,health:b.health});}catch(e){events.push({at:Date.now()-started,type:'consume-failed',name:a.name,reason:e.message});}finally{b.setQuickBarSlot(0);a.eating=false;a.nextHeal=Date.now()-started+4000;}})();continue;
   }
   if(tactic==='bow'&&distance<16&&!a.drawing&&elapsed-a.lastBow>1800) {
    a.drawing=true;a.lastBow=elapsed;b.setQuickBarSlot(1);b.activateItem();setTimeout(()=>{b.deactivateItem();a.arrows++;a.drawing=false;},1000);
   } else if(tactic!=='bow'&&!a.drawing&&distance<3.05&&elapsed-a.lastAttack>650) {
    b.setQuickBarSlot(0);b.attack(target);a.attacks++;a.lastAttack=elapsed;
    const dx=b.entity.position.x-target.position.x,dz=b.entity.position.z-target.position.z,length=Math.hypot(dx,dz);
    if(length>0&&(-Math.sin(yaw)*dx-Math.cos(yaw)*dz)/length<-.5)a.backAttempts++;
   }
  }
 },150);
 let outcome='timeout';
 try {
  while(Date.now()-started<duration) {
   if(ctx.failure())throw Error(ctx.failure());
   for(const a of actor) {
    const additions=packets[a.index].slice(from[a.index]);
    for(const p of additions.splice(lastBar[a.index]||0)){if(p.type==='boss_bar'&&p.data.action===2)bossLowest=Math.min(bossLowest,p.data.health??1);}
    lastBar[a.index]=packets[a.index].length-from[a.index];
   }
   if(fighters.some(i=>chat[i].slice(rewardFrom[i]).some(m=>m.includes('Has recibido las recompensas de '+id+'-fair')))){outcome='victory';break;}
   if(actor.every(a=>a.deaths>0)){outcome='defeat';break;}
   if(Date.now()-lastSample>3000){lastSample=Date.now();events.push({at:Date.now()-started,type:'sample',bossHealthFraction:bossLowest,bossPosition:bossId?bots[1].entities[bossId]?.position.toArray():undefined,players:actor.map(a=>({name:a.name,health:bots[a.index].health,pos:bots[a.index].entity.position.toArray(),deaths:a.deaths}))});}
   await delay(250);
  }
 } finally {
  clearInterval(combat);fighting=false;
  for(const a of actor){const b=bots[a.index];b.clearControlStates();b.deactivateItem();b.physicsEnabled=false;b.realPhysics=false;}
 }
 const adaptations=[];
 for(const a of actor)for(const p of packets[a.index].slice(from[a.index]))if(p.type==='action_bar')adaptations.push({name:a.name,notice:p.data,at:p.at-started});
 const credited=fighters.filter(i=>chat[i].slice(rewardFrom[i]).some(m=>m.includes('Has recibido las recompensas de '+id+'-fair'))).map(i=>names[i]);
 const result={outcome,credited,elapsedMs:Date.now()-started,itemUseEvidence:"server entity_status 9 (item use completion, not a consumed-item count)",boss:{health:1200,speed:.23,damage:8,armor:6,intelligence:5,uuid:ctx.bossUuid(),weakPoint:'back',weakPointBonus:25,adaptationDurationSeconds:15},gear,actors:actor,bossLowestHealthFraction:bossLowest,adaptations,events,setupCommands:teleports,combatCommands:[],grantedRegeneration:false,combatTeleports:0};
 fs.writeFileSync(path.join(dir,'fair-fight.json'),JSON.stringify(result,null,2));
 // Never alter the scenario or assert a victory that the clients did not earn.
 if(outcome!=='victory')console.log('OBSERVED '+outcome+': '+JSON.stringify({actors:actor,bossLowestHealthFraction:bossLowest}));
 await despawn(id+'-fair');return {outcome,credited,elapsedMs:result.elapsedMs,bossRemainingHP:outcome==='victory'?0:bossLowest*1200,actors:actor,notices:adaptations.length,combatTeleports:0,grantedRegeneration:false};
};
