import test from 'node:test';import assert from 'node:assert/strict';import {spawn} from 'node:child_process';import fs from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import {once} from 'node:events';
const registry=Object.fromEntries(['stone_bricks','oak_planks','oak_log','bricks','glowstone','air'].map(n=>['minecraft:'+n,{blockEntity:false,properties:{}}]));
test('real HTTP pairing, preview, scheduling, pause, journal persistence, conditional undo, session isolation',async()=>{
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'luna-test-'));const config=path.join(dir,'config.json');const port=29843;await fs.writeFile(config,JSON.stringify({controlPort:port,placementsPerSecond:10}));
 const child=spawn(process.execPath,['server.mjs'],{cwd:new URL('..',import.meta.url),env:{...process.env,LUNA_DATA:dir,LUNA_CONFIG:config},stdio:['ignore','pipe','pipe']});let logs='';child.stdout.on('data',x=>logs+=x);child.stderr.on('data',x=>logs+=x);
 async function req(route,body={},key=''){if(route==='/host/poll')body={...body,botInWorld:true};const r=await fetch(`http://127.0.0.1:${port}${route}`,{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${key}`},body:JSON.stringify(body)});return r.json()}
 try{
  for(let n=0;n<100&&!logs.includes('listening');n++)await new Promise(r=>setTimeout(r,30));assert(logs.includes('listening'),logs);
  const key='a'.repeat(64);let s=await req('/host/session',{secret:key,world:'test-world',dimension:'minecraft:overworld',port:54321,registry});assert(s.pairCode);assert((await req('/action',{action:'demo'},'wrong')).error);
  const paired=await req('/bot/pair',{code:s.pairCode,name:'TestBuilder'});assert(paired.key);assert.equal(paired.port,54321);assert((await req('/action',{action:'demo'},paired.key)).error,'bot cannot perform host actions');
  await req('/action',{action:'demo',dimensions:[24,24,24]},key);
  for(let i=0;i<20;i++){s=await req('/host/poll',{},key);if(s.command?.type==='survey')break;await new Promise(r=>setTimeout(r,10))}assert.equal(s.command.type,'survey');
  const id=s.job.id;s=await req('/host/poll',{result:{id,type:'survey',origin:[0,64,0]}},key);assert.equal(s.phase,'Preview ready');assert.equal((await req('/bot/poll',{},paired.key)).task,null,'no mutations before preview approval');
  s=await req('/action',{action:'start'},key);assert.equal(s.phase,'Validating');
  const world=new Map();let first;
  for(let n=0;n<10;n++){
   s=await req('/host/poll',{},key);if(s.command?.type!=='inspect'||s.phase!=='Validating')break;
   const c=s.command;first??=c.positions[0];s=await req('/host/poll',{result:{id,type:'inspect',start:c.start,blocks:c.positions.map(p=>({p,state:'minecraft:air',replaceable:true,protected:false,supports:[[p[0],63,p[2]]]}))}},key);if(s.phase!=='Validating')break;
  }
  assert.equal(s.phase,'Traveling',s.message);s=await req('/host/poll',{},key);const c=s.command;
  s=await req('/host/poll',{result:{id,type:'inspect',start:c.start,blocks:[{p:c.positions[0],state:'minecraft:air',protected:false}]}},key);
  const task=(await req('/bot/poll',{},paired.key)).task;assert(task);assert.equal(task.world,'test-world');
  await req('/action',{action:'pause'},key);assert.equal((await req('/bot/poll',{},paired.key)).task,null);
  await req('/action',{action:'resume'},key);assert.equal((await req('/bot/poll',{},paired.key)).task,null,'resume requires authoritative reinspection');
  s=await req('/host/poll',{result:{id,type:'inspect',start:0,blocks:[{p:first,state:task.state,protected:false}]}},key);assert.equal(s.job.confirmed,1);
  const saved=JSON.parse(await fs.readFile(path.join(dir,'test-world.json'),'utf8'));assert.equal(saved.job.index,1);assert(saved.job.journal.length>200);
  await req('/action',{action:'cancel'},key);assert.equal((await req('/bot/poll',{},paired.key)).task,null);
  s=await req('/action',{action:'undo'},key);assert.equal(s.phase,'Undo checking');
  // A later player edit at the first position must not become an undo task.
  for(let n=0;n<10;n++){s=await req('/host/poll',{},key);if(s.command?.type!=='undoInspect')break;const c=s.command;s=await req('/host/poll',{result:{id,type:'undoInspect',start:c.start,blocks:c.positions.map(p=>({p,state:p.join(',')===first.join(',')?'minecraft:oak_planks':'minecraft:air',protected:false}))}},key);if(s.phase!=='Undo checking')break}
  assert.equal(s.phase,'Complete');assert.equal(s.job.total,0,'unchanged and later edited positions are not undone');
  await req('/host/end',{},key);assert((await req('/host/poll',{},key)).error);assert((await req('/bot/poll',{},paired.key)).error);assert((await req('/bot/pair',{code:s.pairCode,name:'TestBuilder'})).error);
 }finally{child.kill();await once(child,'exit');await fs.rm(dir,{recursive:true,force:true})}
});
