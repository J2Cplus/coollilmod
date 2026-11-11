import test from 'node:test';import assert from 'node:assert/strict';import http from 'node:http';import {once} from 'node:events';
import {ManagedRuntime} from '../managed-runtime.mjs';
const first='11111111-1111-4111-8111-111111111111',second='22222222-2222-4222-8222-222222222222',secret='a'.repeat(64);
function setup(enabled=true){let now=0,stops=0;const runtime=new ManagedRuntime({enabled,config:{provider:'ollama'},now:()=>now,shutdown:()=>stops++});return {runtime,advance:n=>now+=n,stops:()=>stops}}
test('manual companions never shut down from lease expiration',()=>{const x=setup(false);x.advance(999999);x.runtime.reap();assert.equal(x.stops(),0);assert.throws(()=>x.runtime.lease({clientId:first},secret),/manually/)});
test('two clients share services and last release shuts them down',()=>{const x=setup();x.runtime.lease({clientId:first},secret);x.runtime.lease({clientId:second},secret);x.runtime.release({clientId:first},secret);x.runtime.reap();assert.equal(x.stops(),0);x.runtime.release({clientId:second},secret);x.runtime.reap();assert.equal(x.stops(),1);x.runtime.reap();assert.equal(x.stops(),1)});
test('crashed clients expire while active heartbeat keeps service alive',()=>{const x=setup();x.runtime.lease({clientId:first},secret);x.runtime.lease({clientId:second},secret);x.advance(21000);x.runtime.lease({clientId:second},secret);x.runtime.reap();assert.equal(x.runtime.leases.size,1);assert.equal(x.stops(),0);x.advance(21000);x.runtime.reap();assert.equal(x.stops(),1)});
test('orphan startup exits and bad release secrets cannot stop another client',()=>{const x=setup();x.runtime.lease({clientId:first},secret);assert.throws(()=>x.runtime.release({clientId:first},'b'.repeat(64)),/Invalid/);assert.equal(x.runtime.leases.size,1);const orphan=setup();orphan.advance(31000);orphan.runtime.reap();assert.equal(orphan.stops(),1)});
test('owned Ollama alone is stopped; existing Ollama has no owned process to kill',()=>{const x=setup();x.runtime.stopOwned();let killed=0;const y=setup();y.runtime.ownedOllama={killed:false,kill:()=>killed++};y.runtime.stopOwned();assert.equal(killed,1)});
test('reuse running local Ollama and discover model without spawning a process',async()=>{
 const calls=[];const server=http.createServer(async(req,res)=>{let raw='';for await(const c of req)raw+=c;calls.push(req.url);res.setHeader('Content-Type','application/json');res.end(JSON.stringify(req.url==='/api/version'?{version:'test'}:req.url==='/api/tags'?{models:[{name:'local-test:7b',size:4e9}]}:{capabilities:['completion']}))});server.listen(0,'127.0.0.1');await once(server,'listening');let saved=false;
 try{const config={provider:'ollama',providerUrl:`http://127.0.0.1:${server.address().port}/api/chat`,modelId:''};const runtime=new ManagedRuntime({enabled:true,config,saveConfig:async()=>{saved=true}});await runtime.bootstrap();assert.equal(runtime.state,'ready');assert.equal(config.modelId,'local-test:7b');assert(saved);assert.equal(runtime.ownedOllama,null);assert.deepEqual(calls,['/api/version','/api/tags','/api/show']);runtime.stopOwned();}
 finally{server.closeAllConnections();await new Promise(r=>server.close(r))}
});

test('starts a missing Ollama daemon in background and cleans up only its own child',{skip:process.platform==='win32'},async()=>{
 const fs=await import('node:fs/promises');const os=await import('node:os');const path=await import('node:path');
 const dir=await fs.mkdtemp(path.join(os.tmpdir(),'luna owned service with spaces '));const executable=path.join(dir,'test-ollama');
 const probe=http.createServer();probe.listen(0,'127.0.0.1');await once(probe,'listening');const port=probe.address().port;await new Promise(r=>probe.close(r));
 const source=`#!/usr/bin/env node\nconst http=require('node:http');if(process.argv[2]!=='serve')process.exit(2);const address=new URL('http://'+process.env.OLLAMA_HOST);http.createServer((req,res)=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(req.url==='/api/version'?{version:'fixture'}:req.url==='/api/tags'?{models:[{name:'local-owned:7b',size:4e9}]}:{capabilities:['completion']}))}).listen(Number(address.port),address.hostname);\n`;
 await fs.writeFile(executable,source,{mode:0o700});const old=process.env.LUNA_OLLAMA_EXECUTABLE;process.env.LUNA_OLLAMA_EXECUTABLE=executable;
 const config={provider:'ollama',providerUrl:`http://127.0.0.1:${port}/api/chat`,modelId:''};const runtime=new ManagedRuntime({enabled:true,config});
 try{await runtime.bootstrap();assert.equal(runtime.state,'ready',runtime.error);assert.equal(config.modelId,'local-owned:7b');assert(runtime.ownedOllama?.pid);const exited=once(runtime.ownedOllama,'exit');runtime.stopOwned();await exited;assert(runtime.ownedOllama.exitCode!==null||runtime.ownedOllama.signalCode!==null);}
 finally{runtime.stopOwned();if(old===undefined)delete process.env.LUNA_OLLAMA_EXECUTABLE;else process.env.LUNA_OLLAMA_EXECUTABLE=old;await fs.rm(dir,{recursive:true,force:true});}
});
