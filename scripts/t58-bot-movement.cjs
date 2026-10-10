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

module.exports={normalRoomKiting,normalRoomWaypoint};
