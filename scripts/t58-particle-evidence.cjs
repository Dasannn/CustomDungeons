'use strict';
function contains(polygon,x,z) {
 if(polygon.length<3)return false;
 let inside=false;
 for(let i=0,j=polygon.length-1;i<polygon.length;j=i++) {
  const a=polygon[j],b=polygon[i],dx=b[0]-a[0],dz=b[2]-a[2];
  if(Math.abs((x-a[0])*dz-(z-a[2])*dx)<1e-8&&x>=Math.min(a[0],b[0])-1e-8&&x<=Math.max(a[0],b[0])+1e-8&&z>=Math.min(a[2],b[2])-1e-8&&z<=Math.max(a[2],b[2])+1e-8)return true;
  if((a[2]>z)!=(b[2]>z)&&x<dx*(z-a[2])/dz+a[0])inside=!inside;
 }
 return inside;
}
const key=(x,y,z)=>[x,y,z].map(n=>Math.round(n*1e5)).join(':');
class ParticleEvidence {
 constructor(){this.players=new Map();this.received=0;}
 clear(){this.players.clear();this.received=0;}
 receive(player,p,at=Date.now()) {
  if(!['enchant','portal'].includes(p.particle?.type)||p.amount<=0||![p.x,p.y,p.z].every(Number.isFinite))return false;
  let marks=this.players.get(player);if(!marks)this.players.set(player,marks=new Map());
  const k=key(p.x,p.y,p.z),times=marks.get(k)||[];times.push(at);marks.set(k,times);this.received++;
  return true;
 }
 warned(player,hit) {
  const marks=this.players.get(player);if(!marks||!hit.polygons?.length)return false;
  return hit.polygons.every(p=>p.length>=3&&p.every(([x,y,z])=>
   marks.get(key(x,y,z))?.some(at=>at>=hit.since&&at<=hit.at)));
 }
}
function insideWarning(hit) {
 return !!hit.inside&&(!hit.geometryRequired||hit.polygons?.some(p=>contains(p,hit.x,hit.z))===true);
}
module.exports={ParticleEvidence,contains,insideWarning};
