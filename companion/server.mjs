import {ManagedRuntime} from './managed-runtime.mjs';
import {resolveConfig} from './config.mjs';
import http from 'node:http';import crypto from 'node:crypto';import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath} from 'node:url';import {spawn} from 'node:child_process';
import {schedule} from './scheduler.mjs';
import {compile,fixture,transform,rotateState} from './compiler.mjs';import {generate} from './provider.mjs';
const root=path.dirname(fileURLToPath(import.meta.url));const data=path.resolve(process.env.LUNA_DATA??path.join(root,'data'));await fs.mkdir(data,{recursive:true,mode:0o700});
let savedConfig={};try{savedConfig=JSON.parse(await fs.readFile(process.env.LUNA_CONFIG??path.join(root,'config.json'),'utf8'))}catch(e){if(e.code!=='ENOENT')throw e}
let config=resolveConfig(savedConfig);
if(process.env.LUNA_CONTROL_PORT)config.controlPort=Number(process.env.LUNA_CONTROL_PORT);
if(!Number.isInteger(config.controlPort)||config.controlPort<1024||config.controlPort>65535)throw Error('Invalid control port');
let stopping=false;
const managed=new ManagedRuntime({enabled:process.env.LUNA_MANAGED==='1',ownerPid:Number(process.env.LUNA_OWNER_PID??0),config,shutdown:()=>shutdown(),saveConfig:async()=>{await fs.writeFile(process.env.LUNA_CONFIG??path.join(root,'config.json'),JSON.stringify(config,null,2),{mode:0o600})}});
const sessions=new Map();const token=()=>crypto.randomBytes(32).toString('hex');let child;
const same=(a,b)=>a?.split('[')[0]===b?.split('[')[0]&&(!b?.includes('[')||b.slice(b.indexOf('[')+1,-1).split(',').every(p=>a.slice(a.indexOf('[')+1,-1).split(',').includes(p)));
const natural=/^minecraft:(air|cave_air|void_air|grass_block|dirt|stone|sand|gravel|short_grass|tall_grass|fern|snow|.*_leaves)$/;
const writes=new Map();
async function save(s){if(!s.job)return;const j={...s.job};delete j.controller;const f=path.join(data,s.world+'.json');const payload=JSON.stringify({world:s.world,dimension:s.dimension,job:j});const next=(writes.get(f)??Promise.resolve()).catch(()=>{}).then(async()=>{await fs.writeFile(f+'.tmp',payload,{mode:0o600});await fs.rename(f+'.tmp',f)});writes.set(f,next);await next}
function status(s){const j=s.job;return {pairCode:s.code,phase:j?.phase??'Connecting',message:s.message??'Ready',botConnected:!!s.botInWorld&&Date.now()-(s.botSeen??0)<3000,botName:s.botName??'',port:s.port,job:j?{id:j.id,title:j.plan?.title??'',prompt:j.prompt,confirmed:j.index??0,total:j.tasks?.length??j.compiled?.total??0,phase:j.phase,etaSeconds:j.index>1&&j.lastConfirmedAt>j.firstConfirmedAt?Math.ceil((j.tasks.length-j.index)*(j.lastConfirmedAt-j.firstConfirmedAt)/(j.index-1)/1000):null,brief:j.brief??'',origin:j.origin??null,rotation:j.rotation??0,usage:j.usage??[],preview:(j.compiled?.blocks??[]).filter(b=>!b.state.startsWith('minecraft:air')).slice(0,6000),recent:(j.history??[]).slice(-5),undo:j.undo??false}:null}}
function fail(s,e){if(s.job){s.job.phase='Failed';s.job.controller?.abort();s.job.epoch++}s.message=String(e.message??e);save(s).catch(()=>{});console.error('LunaBuilder:',s.message.replace(/Bearer\s+\S+/gi,'Bearer [redacted]'))}
function auth(req,s,bot=false){return req.headers.authorization===`Bearer ${bot?s.botKey:s.key}`}
function tasks(j){return j.compiled.blocks.map(b=>({p:transform(b.p,j.origin,j.rotation),state:rotateState(b.state,j.rotation),group:b.group,component:b.component}))}
function inspectCommand(s){const j=s.job;if(!j)return null;
 if(j.phase==='Verifying')return {type:'finalInspect',id:j.id,start:j.verifyIndex,positions:j.tasks.slice(j.verifyIndex,j.verifyIndex+128).map(t=>t.p)};
 if(j.phase==='Surveying')return {type:'survey',id:j.id,compiled:{min:j.compiled.min,max:j.compiled.max},prepare:j.options.prepare,offset:j.offset??[0,0],rotation:j.rotation,anchor:j.anchor??null};
 if(j.phase==='Validating'&&j.tasks)return {type:'inspect',id:j.id,start:j.checkIndex,positions:j.tasks.slice(j.checkIndex,j.checkIndex+128).map(t=>t.p)};
 if(['Building','Verifying','Preparing site','Traveling'].includes(j.phase)&&j.tasks[j.index])return {type:'inspect',id:j.id,start:j.index,positions:[j.tasks[j.index].p]};
 return null}
async function result(s,r){const j=s.job;if(!j||r?.id!==j.id)return;
 if(r.error)throw Error(r.error);
 if(r.type==='finalInspect'&&j.phase==='Verifying'&&r.start===j.verifyIndex){for(const old of r.blocks){const t=j.tasks[j.verifyIndex++];if(old.protected||!same(old.state,t.state))throw Error('Final verification mismatch at '+t.p)}if(j.verifyIndex===j.tasks.length){j.phase='Complete';s.message=j.undo?'Undo complete; later edits were preserved.':'All final block states verified by the integrated server.'}await save(s);return;}
 if(r.type==='survey'&&j.phase==='Surveying'){
  if(!r.origin)throw Error('No safe loaded site found. Move to flat empty land and generate again.');j.origin=r.origin;j.phase='Preview ready';s.message='Preview ready. Review footprint, then Start building.';await save(s);
 }else if(r.type==='inspect'&&Array.isArray(r.blocks)){
  if(j.phase==='Validating'){
   if(r.start!==j.checkIndex)return;
   for(let k=0;k<r.blocks.length;k++){const old=r.blocks[k],t=j.tasks[j.checkIndex];if(!t||old.p.join(',')!==t.p.join(','))throw Error('Inspection position mismatch');if(old.protected)throw Error('Site contains an inventory, block entity, excluded position, or unloaded chunk');
    if(!same(old.state,t.state)&&!old.replaceable&&!(j.options.prepare&&natural.test(old.state.split('[')[0])&&t.p[1]<=j.origin[1]))throw Error(`Occupied site at ${t.p}. Move preview; existing builds are protected.`);
    j.journal.push({p:t.p,before:old.state,after:t.state});j.supports??=[];for(const p of old.supports??[])j.supports.push(p.join(','));j.checkIndex++;
   }
   if(j.checkIndex===j.tasks.length){const entries=new Map(j.journal.map(e=>[e.p.join(','),e]));j.tasks=schedule(j.tasks,j.supports??[]);j.journal=j.tasks.map(t=>entries.get(t.p.join(',')));j.phase='Traveling';j.index=0;j.observed=null;s.message='Site validated. Waiting for paired builder.'}await save(s);
  }else if(['Building','Traveling','Preparing site','Verifying'].includes(j.phase)&&r.start===j.index){
   const old=r.blocks[0],t=j.tasks[j.index];if(!old||!t)return;if(old.protected)throw Error('Placement became protected or unloaded');
   if(same(old.state,t.state)){j.journal[j.index].confirmed=true;j.firstConfirmedAt??=Date.now();j.lastConfirmedAt=Date.now();j.index++;j.observed=null;j.lastObserved=null;j.failures=0;j.readyAt=Date.now()+1000/Math.max(1,Math.min(10,config.placementsPerSecond));if(j.index>=j.tasks.length){j.phase='Verifying';j.verifyIndex=0;s.message='Checking every final block state'}else j.phase='Building';await save(s)}
   else{if(!same(old.state,j.journal[j.index]?.before)&&!same(old.state,j.observed))throw Error('Player changed an active placement; pause and inspect');j.observed=old.state;j.lastObserved=old.state;j.phase='Building'}
  }
 }
}
async function action(s,b){let j=s.job;
 switch(b.action){
 case 'generate':case 'demo':{
  if(j&&!['Complete','Failed','Cancelled','Preview ready'].includes(j.phase))throw Error('Cancel the current job first');
  if(j){await save(s);await fs.copyFile(path.join(data,s.world+'.json'),path.join(data,s.world+'-'+j.id+'.json')).catch(()=>{});}
  const prompt=String(b.prompt??'').slice(0,12000);if(!prompt&&b.action!=='demo')throw Error('Enter a build prompt');
  j=s.job={id:crypto.randomUUID(),prompt,phase:'Generating',options:{prepare:!!b.prepare,interior:b.interior!==false,style:String(b.style??'').slice(0,200),size:b.size??'Medium',detail:String(b.detail??'Detailed').slice(0,30),dimensions:b.dimensions??[48,48,48]},index:0,rotation:0,epoch:1,journal:[],history:[],controller:new AbortController()};s.message=b.action==='demo'?'OFFLINE DEMO — no AI request':'Generating with '+config.modelLabel;
  (async()=>{try{
   if(b.action==='demo'){j.plan=fixture();j.brief='Offline fixture: open garden pavilion. This is a deterministic test, not AI generation.';j.compiled=compile(j.plan,s.registry,{maxDimensions:j.options.dimensions})}
   else{const g=await generate(config,{prompt,...j.options},s.registry,j.controller.signal);j.brief=g.brief;for(let a=0,feedback='';a<3;a++){try{j.plan=await g.next(feedback);j.compiled=compile(j.plan,s.registry,{maxDimensions:j.options.dimensions});break}catch(e){feedback=e.message;if(a===2)throw e}}j.usage=g.usage}
   if(s.job!==j||j.controller.signal.aborted)return;j.phase='Surveying';s.message='Surveying loaded terrain for a safe footprint';await save(s);
  }catch(e){if(s.job===j&&j.phase!=='Cancelled')fail(s,e)}})();break;}
 case 'start':if(j?.phase!=='Preview ready')throw Error('Generate and review a preview first');if(!s.botInWorld||Date.now()-(s.botSeen??0)>3000)throw Error('Wait for the authenticated second Fabric client to join this world');j.tasks=tasks(j);j.checkIndex=0;j.journal=[];j.phase='Validating';j.epoch++;s.message='Checking every target before first mutation';break;
 case 'pause':if(!j||!['Building','Traveling','Validating','Verifying','Undo checking'].includes(j.phase))throw Error('No active build');j.resumePhase=j.phase;j.phase='Paused';j.epoch++;break;
 case 'resume':if(j?.phase!=='Paused')throw Error('No paused job');j.phase=j.resumePhase??'Building';j.epoch++;j.observed=null;break;
 case 'cancel':if(j){j.controller?.abort();j.phase='Cancelled';j.epoch++;s.message='Cancelled. Confirmed changes remain available for undo.'}break;
 case 'select':if(j?.phase!=='Preview ready')throw Error('Select a site only before building');if(!Array.isArray(b.anchor)||b.anchor.length!==3||b.anchor.some(n=>!Number.isInteger(n)||Math.abs(n)>30000000))throw Error('Invalid selected position');j.anchor=b.anchor;j.offset=[0,0];j.phase='Surveying';break;
 case 'rotate':case 'move':if(j?.phase!=='Preview ready')throw Error('Move/rotate only before building');j.rotation=(j.rotation+(b.action==='rotate'?1:0))%4;if(b.action==='move'){j.offset??=[0,0];j.offset[0]+=Number(b.dx??0);j.offset[1]+=Number(b.dz??0);if(j.offset.some(n=>!Number.isInteger(n)||Math.abs(n)>96))throw Error('Offset exceeds loaded survey radius')}j.phase='Surveying';break;
 case 'undo':{
  if(!j?.journal?.length||!['Complete','Cancelled','Failed'].includes(j.phase))throw Error('Finish or cancel the build before undo');
  j.undo=true;j.phase='Undo checking';j.epoch++;j.undoSource=j.journal.filter((e,i)=>!same(e.before,e.after)&&(e.confirmed||i===j.dispatchedIndex)).reverse();j.undoIndex=0;j.undoTasks=[];s.message='Checking journal; positions changed by you will be skipped';break;}
 case 'launch':if(!config.botExecutable)throw Error('Configure botExecutable and botArgs for your authenticated second Minecraft profile, or launch it manually');if(child&&!child.killed)throw Error('Builder launcher is already running');child=spawn(config.botExecutable,config.botArgs??[],{shell:false,stdio:'ignore',windowsHide:false});child.on('error',()=>{s.message='Could not launch configured builder executable'});break;
 default:throw Error('Unknown action');
 }await save(s);return status(s)
}
const server=http.createServer(async(req,res)=>{res.setHeader('Content-Type','application/json');res.setHeader('Cache-Control','no-store');try{
 if(req.headers.origin)throw Error('Browser origins are not permitted');if(!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress))throw Error('Loopback only');
 if(req.method==='GET'&&req.url==='/health'){res.end(JSON.stringify({name:'LunaBuilder',version:1,...managed.status()}));return}
 if(req.method!=='POST')throw Error('POST required');let bytes=0,chunks=[];for await(const c of req){bytes+=c.length;if(bytes>4_000_000)throw Error('Request too large');chunks.push(c)}const b=JSON.parse(Buffer.concat(chunks).toString('utf8'));let out;
 if(req.url==='/runtime/lease'){out=managed.lease(b,(req.headers.authorization??'').replace(/^Bearer /,''));}
 else if(req.url==='/runtime/release'){out=managed.release(b,(req.headers.authorization??'').replace(/^Bearer /,''));}
 else if(req.url==='/host/session'){
  if(typeof b.world!=='string'||!/^[-a-zA-Z0-9_]{1,100}$/.test(b.world)||typeof b.secret!=='string'||b.secret.length!==64||!Number.isInteger(b.port)||b.port<1||b.port>65535||!b.registry||Object.keys(b.registry).length>10000)throw Error('Invalid session');
  for(const old of sessions.values())if(old.world===b.world){old.job?.controller?.abort();sessions.delete(old.key)}
  const s={key:b.secret,code:crypto.randomBytes(6).toString('hex'),world:b.world,dimension:b.dimension,port:b.port,registry:b.registry,lastSeen:Date.now(),message:'Host paired. Enter pairing code in the second Fabric client.'};sessions.set(s.key,s);
  try{const saved=JSON.parse(await fs.readFile(path.join(data,s.world+'.json'),'utf8'));if(saved.dimension===s.dimension&&saved.job?.journal?.length){s.job=saved.job;s.job.phase='Paused';s.job.resumePhase=saved.job.phase==='Complete'?'Complete':saved.job.phase==='Validating'?'Validating':saved.job.phase==='Verifying'?'Verifying':'Building';s.job.epoch++;s.job.observed=null;s.message='Recovered journal. Resume rechecks actual blocks; undo available after cancel.'}}catch{}out=status(s);
 }else if(req.url==='/bot/pair'){
  const s=[...sessions.values()].find(s=>s.code===b.code&&Date.now()-s.lastSeen<5000);if(!s)throw Error('Pairing code expired or host disconnected');s.botKey=token();s.botName=String(b.name??'').slice(0,32);if(!s.botName)throw Error('Bot username required');s.botSeen=Date.now();out={key:s.botKey,port:s.port,world:s.world};
 }else{
  const s=[...sessions.values()].find(s=>auth(req,s)||auth(req,s,true));if(!s)throw Error('Unauthorized or expired session');const bot=auth(req,s,true);
  if(bot&&req.url!=='/bot/poll')throw Error('Bot credentials cannot control host');
  if(req.url==='/host/end'){s.job?.controller?.abort();if(s.job){s.job.phase='Paused';s.job.epoch++}await save(s);sessions.delete(s.key);out={ended:true}}
  else if(req.url==='/settings'){
   if(b.update){const v=b.update;if(typeof v.providerUrl!=='string'||typeof v.modelId!=='string'||v.providerUrl.length>2048||v.modelId.length>200)throw Error('Invalid provider settings');if(v.providerUrl){const u=new URL(v.providerUrl);if(u.username||u.password||u.search||u.hash)throw Error('Put credentials in the environment variable, never in the URL');if(u.protocol!=='https:'&&!(u.protocol==='http:'&&['localhost','127.0.0.1'].includes(u.hostname)))throw Error('HTTPS or local HTTP required')}config.providerUrl=v.providerUrl;config.modelId=v.modelId;config.placementsPerSecond=Math.max(1,Math.min(10,Number(v.placementsPerSecond)||4));await fs.writeFile(process.env.LUNA_CONFIG??path.join(root,'config.json'),JSON.stringify(config,null,2),{mode:0o600});}
   out={provider:config.provider,apiKeyRequired:config.provider!=='ollama',providerUrl:config.providerUrl,modelId:config.modelId,modelLabel:config.modelLabel,apiKeyEnv:config.apiKeyEnv,apiKeyPresent:!!process.env[config.apiKeyEnv],placementsPerSecond:config.placementsPerSecond};
  }else if(req.url==='/plans'){
   const files=(await fs.readdir(data)).filter(f=>f===s.world+'.json'||f.startsWith(s.world+'-')&&f.endsWith('.json')).slice(-100);const plans=[];for(const f of files){try{const x=JSON.parse(await fs.readFile(path.join(data,f),'utf8'));if(x.world===s.world&&x.job?.plan)plans.push({id:x.job.id,title:x.job.plan.title,prompt:x.job.prompt,phase:x.job.phase})}catch{}}out={plans};
  }else if(req.url==='/load'){
   if(typeof b.id!=='string'||!/^[-a-f0-9]{36}$/.test(b.id))throw Error('Invalid saved plan ID');if(s.job&&!['Complete','Cancelled','Failed','Preview ready'].includes(s.job.phase))throw Error('Cancel the active job before loading');
   let x;for(const f of [s.world+'-'+b.id+'.json',s.world+'.json']){try{const candidate=JSON.parse(await fs.readFile(path.join(data,f),'utf8'));if(candidate.world===s.world&&candidate.job.id===b.id){x=candidate.job;break}}catch{}}if(!x?.plan)throw Error('Saved plan not found in this world');
   if(s.job){await save(s);await fs.copyFile(path.join(data,s.world+'.json'),path.join(data,s.world+'-'+s.job.id+'.json'));}
   const compiled=compile(x.plan,s.registry,{maxDimensions:x.options.dimensions});s.job={id:crypto.randomUUID(),prompt:x.prompt,plan:x.plan,compiled,options:x.options,brief:x.brief,phase:'Surveying',index:0,rotation:0,epoch:1,journal:[],history:[]};await save(s);out=status(s);
  }else if(req.url==='/action'){out=await action(s,b)}
  else if(req.url==='/host/poll'){
   s.lastSeen=Date.now();s.botInWorld=b.botInWorld===true;try{if(b.result)await result(s,b.result)}catch(e){fail(s,e)}
   const j=s.job;let command=inspectCommand(s);
   if(j?.phase==='Undo checking'){
    if(b.result?.type==='undoInspect'&&b.result.id===j.id&&b.result.start===j.undoIndex){for(let k=0;k<b.result.blocks.length;k++){const old=b.result.blocks[k],entry=j.undoSource[j.undoIndex++];if(!old.protected&&same(old.state,entry.after)){j.undoTasks.push({p:entry.p,state:entry.before});}}}
    if(j.undoIndex>=j.undoSource.length){j.tasks=j.undoTasks;j.journal=j.tasks.map(t=>({p:t.p,before:j.undoSource.find(e=>e.p.join(',')===t.p.join(','))?.after,after:t.state}));j.index=0;j.observed=null;j.phase=j.tasks.length?'Building':'Complete';await save(s);command=inspectCommand(s)}else command={type:'undoInspect',id:j.id,start:j.undoIndex,positions:j.undoSource.slice(j.undoIndex,j.undoIndex+128).map(e=>e.p)};
   }
   out={...status(s),command,allowedTarget:j&&['Building','Traveling'].includes(j.phase)&&j.observed!=null?j.tasks[j.index]?.p:null,authorizeBot:s.botName??null};
  }else if(req.url==='/bot/poll'){
   if(!bot)throw Error('Bot credentials required');s.botSeen=Date.now();const j=s.job;
   if(b.error&&j&&b.task===`${j.id}:${j.epoch}:${j.index}`){j.history.push(String(b.error).slice(0,300));if((j.failures=(j.failures??0)+1)>3)fail(s,Error('Builder placement failed after retries: '+String(b.error).slice(0,200)))}
   let task=null;if(j&&['Building','Traveling'].includes(j.phase)&&j.observed!=null&&Date.now()>=(j.readyAt??0))task={id:`${j.id}:${j.epoch}:${j.index}`,p:j.tasks[j.index].p,state:j.tasks[j.index].state,before:j.observed,world:s.world};if(task&&j.dispatchedIndex!==j.index){j.dispatchedIndex=j.index;await save(s)}out={task,phase:j?.phase??'Connecting',alive:true};
  }else throw Error('Unknown route');
 }
 res.end(JSON.stringify(out));
 }catch(e){res.statusCode=400;res.end(JSON.stringify({error:String(e.message??e)}))}});
server.requestTimeout=10000;server.headersTimeout=10000;server.listen(config.controlPort,'127.0.0.1',()=>console.log(`LunaBuilder companion listening on 127.0.0.1:${config.controlPort}. Model: ${config.modelLabel} (${config.modelId||(config.provider==='ollama'?'automatic local selection':'not configured')}).`));
setInterval(()=>{for(const [key,s] of sessions)if(Date.now()-s.lastSeen>10000){s.job?.controller?.abort();if(s.job){s.job.phase='Paused';s.job.epoch++;save(s).catch(()=>{})}sessions.delete(key)}},1000).unref();
async function shutdown(){if(stopping)return;stopping=true;for(const s of sessions.values()){s.job?.controller?.abort();if(s.job){s.job.phase='Paused';s.job.epoch++;await save(s).catch(()=>{});}}child?.kill();managed.stopOwned();server.close(()=>process.exit(0));setTimeout(()=>process.exit(0),2000).unref();}
if(managed.enabled){setInterval(()=>managed.reap(),1000).unref();managed.bootstrap();}
process.on('SIGTERM',shutdown);process.on('SIGINT',shutdown);process.on('exit',()=>managed.stopOwned());
