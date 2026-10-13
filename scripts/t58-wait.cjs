'use strict';
async function waitUntil(predicate,{label,timeoutMs=30000,intervalMs=100,failure=()=>null,checkFailure=true}) {
  const end=Date.now()+timeoutMs;
  while(Date.now()<end){
    const error=checkFailure&&failure();if(error)throw Error(error);
    if(predicate())return;
    await new Promise(resolve=>setTimeout(resolve,intervalMs));
  }
  throw Error('Timeout '+label);
}
module.exports={waitUntil};
