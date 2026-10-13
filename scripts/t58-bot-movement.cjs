'use strict';

function normalRoomKiting(scenario,tick,healthFraction) {
  // Start with ordinary attacks; otherwise all players can leave the native follow range
  // before the first hit. A ten-second leg lets a moving boss actually reach the border.
  return scenario==='warden-room'&&healthFraction<.30&&tick%400<200;
}

function normalRoomWaypoint(mobX,mobZ,index) {
  // Align the real mob with the seven-block doorway while players are still inside.
  return {x:(index-2)*.35,z:Math.abs(mobX)>1||mobZ<6.5?8:14};
}

function arenaEdge(scenario,zoneRun) {
  if(scenario==='warden-room'||zoneRun&&(scenario.startsWith('zones-room-')||['zero','profile'].includes(scenario)))return 10;
  return zoneRun?24:22;
}

function nearbyFloorAim(aim,feetY) {
  return ['x','z','hitX','hitY','hitZ'].every(key=>Number.isFinite(aim[key]))
    &&aim.hitY===feetY-1&&Math.hypot(aim.hitX+.5-aim.x,aim.hitZ+.5-aim.z)<4;
}

function irregularRampWaypoint(position,stage,index) {
  const points=[{x:0,z:0},{x:-8,z:0},{x:-15,z:(index-2)*.6}];
  while(stage<2&&Math.hypot(position.x-points[stage].x,position.z-points[stage].z)<=.75)stage++;
  return {stage,...points[stage]};
}

function attackEveryTicks(zoneRun,scenario,healthFraction) {
  if(scenario.startsWith('warden'))return healthFraction>.27?20:100;
  return zoneRun?20:100;
}

function engagedGoal(index,distance,requested,enemy,rally) {
  // A real defender stays near the host's return origin. The remaining clients
  // pull the enemy toward edges without leaving a returned mob alone out of range.
  return index===0?rally:index>=3?requested:distance>15?enemy:requested;
}
function returnRally(phase) {
  // The fixture spawner is two blocks east, with a one-block return radius.
  return {x:2+Math.sin(phase/3)*.4,z:Math.cos(phase/3)*.4};
}
function rangedClient(zoneRun,scenario,index,distance,healthFraction) {
  if(zoneRun&&scenario.startsWith('warden')&&healthFraction>.35)return false;
  return index===4||zoneRun&&!scenario.startsWith('warden')&&index===3&&distance>5;
}
function combatJump(zoneRun,scenario,tick,index,healthFraction,collision) {
  if(collision)return true;
  if(zoneRun&&scenario.startsWith('warden')&&healthFraction>.35)return (tick-index*3+12)%20===0;
  return Math.floor(tick/10+index)%13===0;
}

function silenceAttempt(zoneRun,scenario,tick,index) {
  return zoneRun&&scenario.endsWith('silence')&&tick%20===index;
}

function wardenMeleeGoal(enemy,index,distance) {
  const angle=2*Math.PI*index/5;
  return {x:enemy.x+Math.cos(angle)*distance,z:enemy.z+Math.sin(angle)*distance};
}
function wardenAppleDue(zoneRun,scenario,now,next) {
  return zoneRun&&scenario.startsWith('warden')&&now>=next;
}

function unexplainedBackwards(sample,minAdvance=.03) {
  const tactical=sample.combatFacing===true&&Number.isFinite(sample.targetFacingDot)&&sample.targetFacingDot>=.8;
  return sample.advance>minAdvance&&sample.facingDot<0&&!sample.knockback&&!sample.retreat&&!tactical;
}
module.exports={normalRoomKiting,normalRoomWaypoint,arenaEdge,nearbyFloorAim,irregularRampWaypoint,attackEveryTicks,engagedGoal,returnRally,rangedClient,combatJump,silenceAttempt,wardenMeleeGoal,wardenAppleDue,unexplainedBackwards};
