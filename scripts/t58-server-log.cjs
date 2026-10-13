'use strict';
const fs=require('node:fs');

// Paper rotates latest.log at midnight. Keep the old descriptor long enough to
// drain it, and give scenario cursors stable byte offsets in an owned evidence file.
class ServerLog {
  constructor(source,saved){this.source=source;this.output=fs.openSync(saved,'wx+');this.input=null;this.offset=0;this.size=0;this.pending=Buffer.alloc(0);this.closed=false;}
  drain(){
    if(this.input===null)return;
    const end=fs.fstatSync(this.input).size;
    if(end<this.offset)throw Error('Server log truncated; evidence is incomplete');
    while(this.offset<end){
      const buffer=Buffer.alloc(Math.min(1024*1024,end-this.offset));
      const count=fs.readSync(this.input,buffer,0,buffer.length,this.offset);if(!count)break;this.offset+=count;
      const bytes=Buffer.concat([this.pending,buffer.subarray(0,count)]),last=bytes.lastIndexOf(10);
      if(last>=0){const complete=bytes.subarray(0,last+1);let written=0;while(written<complete.length)written+=fs.writeSync(this.output,complete,written,complete.length-written,this.size+written);this.size+=written;}
      this.pending=bytes.subarray(last+1);
    }
  }
  sync(){
    if(this.closed)throw Error('Server log collector is closed');
    this.drain();let stat;
    try{stat=fs.statSync(this.source);}catch(error){if(error.code==='ENOENT')return;throw error;}
    const old=this.input===null?null:fs.fstatSync(this.input);
    if(old&&old.dev===stat.dev&&old.ino===stat.ino)return;
    let replacement;
    try{replacement=fs.openSync(this.source,'r');}catch(error){if(error.code==='ENOENT')return;throw error;}
    const fresh=fs.fstatSync(replacement);
    if(old&&old.dev===fresh.dev&&old.ino===fresh.ino){fs.closeSync(replacement);return;}
    this.drain();
    if(this.pending.length){fs.closeSync(replacement);throw Error('Server log rotated with an incomplete line');}
    if(this.input!==null)fs.closeSync(this.input);
    this.input=replacement;this.offset=0;this.drain();
  }
  checkpoint(){this.sync();return this.size;}
  readFrom(from){
    this.sync();if(!Number.isSafeInteger(from)||from<0||from>this.size)throw Error('Invalid server log checkpoint');
    const bytes=Buffer.alloc(this.size-from);let read=0;
    while(read<bytes.length){const count=fs.readSync(this.output,bytes,read,bytes.length-read,from+read);if(!count)throw Error('Evidence file unexpectedly shortened');read+=count;}
    return bytes.toString('utf8');
  }
  close(){if(this.closed)return;try{this.sync();}finally{if(this.input!==null)fs.closeSync(this.input);fs.closeSync(this.output);this.closed=true;}}
}
module.exports={ServerLog};
