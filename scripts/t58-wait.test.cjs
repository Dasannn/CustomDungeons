'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {waitUntil}=require('./t58-wait.cjs');
test('normal gameplay propagates a death before advancing another operation',async()=>{
 let reads=0;await assert.rejects(waitUntil(()=>{reads++;return true;},{failure:()=> 'Player died',label:'fight',timeoutMs:100}),/Player died/);assert.equal(reads,0);
});
test('cleanup still waits for verified restoration after a death without clearing the failure',async()=>{
 const failure='Player died';let reads=0;
 await waitUntil(()=>++reads===2,{failure:()=>failure,checkFailure:false,label:'restoration',timeoutMs:1000,intervalMs:0});assert.equal(reads,2);assert.equal(failure,'Player died');
});
test('a failed cleanup condition still times out rather than claiming restored terrain',async()=>{
 await assert.rejects(waitUntil(()=>false,{failure:()=> 'Player died',checkFailure:false,label:'restoration',timeoutMs:5,intervalMs:0}),/Timeout restoration/);
});
test('consumption releases the off-hand shield and lets the release reach the server first',async()=>{
 const {withReleasedItem}=require('./t58-wait.cjs');let active='shield',releaseQueued=false,completed=false;
 const bot={deactivateItem(){releaseQueued=true;},async waitForTicks(ticks){assert.equal(ticks,1);if(releaseQueued)active=null;}};
 await withReleasedItem(bot,async()=>{assert.equal(active,null,'the server must stop shielding before use_item for the main hand');active='potion';completed=true;});
 assert(completed);assert(releaseQueued);
});
test('failed consumption also releases the active item instead of blocking the next attempt',async()=>{
 const {withReleasedItem}=require('./t58-wait.cjs');let releases=0;
 const bot={deactivateItem(){releases++;},async waitForTicks(){}};
 await assert.rejects(withReleasedItem(bot,async()=>{throw Error('cancelled by cooldown');}),/cooldown/);
 assert.equal(releases,2);
});
