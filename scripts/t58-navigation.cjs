'use strict';

function nextWalkingPoint(position,nodes,goal,reachesGoal=true) {
  while(nodes.length&&Math.hypot(position.x-nodes[0].x,position.z-nodes[0].z)<=.25
    &&Math.abs(position.y-nodes[0].y)<.75)nodes.shift();
  return nodes[0]||(reachesGoal?goal:position);
}

function walkingWaypoint(bot,goal,now,GoalXZ) {
  const key=Math.floor(goal.x)+','+Math.floor(goal.z),old=bot.t58Path;
  if(!old||old.key!==key||now-old.at>=1000||bot.entity.isCollidedHorizontally&&now-old.at>=250) {
    const result=bot.pathfinder.getPathTo(bot.t58Movements,new GoalXZ(goal.x,goal.z),30);
    bot.t58Path={key,at:now,nodes:result.path,status:result.status};
  }
  return nextWalkingPoint(bot.entity.position,bot.t58Path.nodes,goal,bot.t58Path.status==='success');
}

module.exports={nextWalkingPoint,walkingWaypoint};
