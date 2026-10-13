'use strict';
const assert=require('node:assert/strict'),path=require('node:path');
function recoveryPlan(files,server){
 assert(Array.isArray(files)&&files.length>0,'Missing fixture manifest');let nonce;
 const parents=[path.join(server,'plugins/CustomDungeons/mobs'),path.join(server,'plugins/CustomDungeons/dungeons')];
 for(const file of files){
  assert(typeof file==='string'&&file===path.resolve(file)&&parents.includes(path.dirname(file)),'Foreign fixture path');
  const match=/^(t58b-[0-9a-f]{6})-[a-z0-9_-]+\.yml$/.exec(path.basename(file));assert(match,'Foreign fixture name');
  nonce??=match[1];assert.equal(match[1],nonce,'Mixed fixture owners');
 }
 return {nonce,files:[...new Set(files)],names:Array.from({length:5},(_,i)=>'T58'+nonce.slice(5)+i)};
}
function validateSnapshot(saved,recipe,owner){
 assert(saved?.version===recipe.version&&saved.world===recipe.world&&saved.owner===owner&&/^t58b-[0-9a-f]{6}$/.test(owner),'Foreign terrain journal');
 assert(/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(saved.worldId),'Invalid world identity');
 const [x1,y1,z1,x2,y2,z2]=recipe.bounds,expected=(x2-x1+1)*(y2-y1+1)*(z2-z1+1)+25;
 assert(Array.isArray(saved.cells)&&saved.cells.length===expected,'Incomplete terrain journal');const seen=new Set();
 for(const {x,y,z,data} of saved.cells){
  assert([x,y,z].every(Number.isInteger),'Noninteger terrain cell');
  assert(x>=x1&&x<=x2&&y>=y1&&y<=y2&&z>=z1&&z<=z2||x>=54&&x<=58&&y===-1&&z>=-2&&z<=2,'Terrain outside fixture');
  assert(typeof data==='string'&&/^minecraft:[a-z0-9_]+(?:\[[a-z_]+=[a-z0-9_-]+(?:,[a-z_]+=[a-z0-9_-]+)*\])?$/.test(data),'Invalid block data');
  const key=x+','+y+','+z;assert(!seen.has(key),'Duplicate terrain cell');seen.add(key);
 }
 return owner;
}
module.exports={recoveryPlan,validateSnapshot};
if(require.main===module){
 const fs=require('node:fs'),zlib=require('node:zlib'),crypto=require('node:crypto');
 const [run,server,recipePath]=process.argv.slice(2);
 assert.equal(server,'/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes','Only the agents server');
 const plan=recoveryPlan(JSON.parse(fs.readFileSync(path.join(run,'created.json'),'utf8')),server);
 for(const file of plan.files){assert.equal(fs.realpathSync(path.dirname(file)),path.dirname(file),'Linked fixture directory');assert(!fs.existsSync(file)||!fs.lstatSync(file).isSymbolicLink(),'Linked fixture');}
 const journal=path.join(run,'arena-original.json.gz');
 if(fs.existsSync(journal))validateSnapshot(JSON.parse(zlib.gunzipSync(fs.readFileSync(journal),{maxOutputLength:20*1024*1024})),JSON.parse(fs.readFileSync(recipePath,'utf8')),plan.nonce);
 const probe=path.join(server,'plugins',plan.nonce+'-probe.jar'),hash=file=>crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
 if(fs.existsSync(probe))assert.equal(hash(probe),hash(path.join(run,'probe.jar')),'Probe ownership mismatch');
 // Validate all claims before removing anything; keep every unrelated plugin/op.
 for(const file of plan.files)fs.rmSync(file,{force:true});
 fs.rmSync(probe,{force:true});fs.rmSync(path.join(server,'plugins/.paper-remapped',path.basename(probe)),{force:true});
 const ops=path.join(server,'ops.json'),before=JSON.parse(fs.readFileSync(ops,'utf8')),after=before.filter(p=>!plan.names.includes(p.name));
 if(after.length!==before.length){
  const temporary=path.join(server,'.ops-'+plan.nonce+'.tmp'),fd=fs.openSync(temporary,'wx',fs.statSync(ops).mode&0o777);
  try{fs.writeFileSync(fd,JSON.stringify(after,null,2)+'\n');fs.fsyncSync(fd);}finally{fs.closeSync(fd);}
  fs.renameSync(temporary,ops);
 }
 process.stdout.write(plan.nonce+'\n');
}
