'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {ServerLog}=require('./t58-server-log.cjs');
function fixture(t){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'t58-log-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));const source=path.join(dir,'latest.log'),saved=path.join(dir,'observed.log');fs.writeFileSync(source,'ready\n');const log=new ServerLog(source,saved);t.after(()=>log.close());return {dir,source,saved,log};}
test('checkpoints refer to complete lines and UTF-8 byte offsets',t=>{
 const {source,log}=fixture(t),at=log.checkpoint();fs.appendFileSync(source,'suelo español\n');
 assert.equal(log.readFrom(at),'suelo español\n');assert.equal(log.checkpoint()-at,Buffer.byteLength('suelo español\n'));
});
test('an incomplete JSON line remains pending until its newline arrives',t=>{
 const {source,log}=fixture(t),at=log.checkpoint();fs.appendFileSync(source,'T58_TRACE {"tick":');
 assert.equal(log.readFrom(at),'');assert.equal(log.checkpoint(),at);fs.appendFileSync(source,'5}\n');assert.equal(log.readFrom(at),'T58_TRACE {"tick":5}\n');
});
test('midnight rotation preserves unread old lines and new lines without duplication',t=>{
 const {dir,source,saved,log}=fixture(t),at=log.checkpoint();fs.appendFileSync(source,'before midnight\n');
 fs.renameSync(source,path.join(dir,'yesterday.log'));fs.writeFileSync(source,'after midnight\n');
 assert.equal(log.readFrom(at),'before midnight\nafter midnight\n');assert.equal(log.readFrom(at),'before midnight\nafter midnight\n');
 assert.equal(fs.readFileSync(saved,'utf8'),'ready\nbefore midnight\nafter midnight\n');
});
test('rotation can briefly leave the source path missing while the old descriptor is drained',t=>{
 const {dir,source,log}=fixture(t),at=log.checkpoint();fs.appendFileSync(source,'old final\n');fs.renameSync(source,path.join(dir,'old.log'));
 assert.equal(log.readFrom(at),'old final\n');fs.writeFileSync(source,'new first\n');assert.equal(log.readFrom(at),'old final\nnew first\n');
});
test('unexpected same-file truncation fails rather than silently accepting incomplete evidence',t=>{
 const {source,log}=fixture(t);log.checkpoint();fs.truncateSync(source,0);assert.throws(()=>log.checkpoint(),/truncated/);assert.throws(()=>log.close(),/truncated/);
});
test('the evidence collector refuses to overwrite an existing artifact',t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'t58-log-existing-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));const source=path.join(dir,'latest.log'),saved=path.join(dir,'saved.log');fs.writeFileSync(source,'x\n');fs.writeFileSync(saved,'preserve');
 assert.throws(()=>new ServerLog(source,saved),/EEXIST/);assert.equal(fs.readFileSync(saved,'utf8'),'preserve');
});
