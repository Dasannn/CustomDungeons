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
