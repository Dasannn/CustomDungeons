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
async function withReleasedItem(bot,work) {
  bot.deactivateItem();
  await bot.waitForTicks(1);
  try{return await work();}finally{bot.deactivateItem();}
}
module.exports={waitUntil,withReleasedItem};
