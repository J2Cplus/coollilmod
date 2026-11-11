import {spawn} from 'node:child_process';import path from 'node:path';import fs from 'node:fs/promises';
import {chooseOllamaModel} from './ollama.mjs';
// Managed lifecycle is enabled only by a mod-launched companion. Manual servers are never reaped.
export class ManagedRuntime {
 constructor({enabled=false,ownerPid=0,now=Date.now,shutdown=()=>{},config,saveConfig=async()=>{}}){Object.assign(this,{enabled,ownerPid,now,shutdown,config,saveConfig});this.leases=new Map();this.started=now();this.state='starting';this.error='';this.ownedOllama=null;this.stopping=false;}
 status(){return {managed:this.enabled,provider:this.config?.provider,clients:this.leases.size,ollamaState:this.state,ollamaError:this.error,modelId:this.config?.modelId??''};}
 lease(body,secret){if(!this.enabled)throw Error('Companion is manually managed');if(typeof body.clientId!=='string'||!/^[-a-f0-9]{36}$/.test(body.clientId)||typeof secret!=='string'||!/^[a-f0-9]{64}$/.test(secret))throw Error('Invalid runtime lease');const previous=this.leases.get(body.clientId);if(previous&&previous.secret!==secret)throw Error('Invalid runtime lease secret');if(!previous&&this.leases.size>=16)throw Error('Too many Minecraft clients');this.leases.set(body.clientId,{secret,last:this.now()});return this.status();}
 release(body,secret){const lease=this.leases.get(body.clientId);if(!lease||lease.secret!==secret)throw Error('Invalid runtime lease');this.leases.delete(body.clientId);if(!this.leases.size)this.lastRelease=this.now();return {released:true};}
 reap(){if(!this.enabled||this.stopping)return;for(const [id,l] of this.leases)if(this.now()-l.last>20000)this.leases.delete(id);if(this.leases.size)return;if(this.lastRelease!==undefined||this.now()-this.started>30000){this.stopping=true;this.shutdown();}}
 async bootstrap(){if(!this.enabled){this.state='manual';return;}try{
  if(this.config.provider!=='ollama'){this.state='custom';return;}
  const endpoint=new URL(this.config.providerUrl);if(!['127.0.0.1','localhost','[::1]'].includes(endpoint.hostname)||endpoint.protocol!=='http:')throw Error('Automatic Ollama startup requires a local HTTP endpoint');
  const available=async()=>{try{const r=await fetch(endpoint.origin+'/api/version',{redirect:'error',signal:AbortSignal.timeout(1500)});return r.ok}catch{return false}};
  if(!await available()){
   if(this.stopping)return;
   const candidates=[];if(process.env.LUNA_OLLAMA_EXECUTABLE)candidates.push(process.env.LUNA_OLLAMA_EXECUTABLE);
   if(process.platform==='win32'){if(process.env.LOCALAPPDATA)candidates.push(path.join(process.env.LOCALAPPDATA,'Programs','Ollama','ollama.exe'));if(process.env.ProgramFiles)candidates.push(path.join(process.env.ProgramFiles,'Ollama','ollama.exe'));}
   for(const dir of (process.env.PATH??'').split(path.delimiter))if(dir)candidates.push(path.join(dir,process.platform==='win32'?'ollama.exe':'ollama'));
   let executable;for(const p of candidates){try{if((await fs.stat(p)).isFile()){executable=p;break}}catch{}}
   if(!executable)throw Error('Ollama executable not found. Install/open Ollama, or set LUNA_OLLAMA_EXECUTABLE to its full path.');
   const env={...process.env,OLLAMA_HOST:endpoint.host};
   this.ownedOllama=spawn(executable,['serve'],{shell:false,windowsHide:true,stdio:'ignore',env});let launchError=false;this.ownedOllama.on('error',()=>{launchError=true});
   let ready=false;for(let i=0;i<30&&!this.stopping;i++){if(launchError)break;if(await available()){ready=true;break;}await new Promise(r=>setTimeout(r,500));}
   if(!ready)throw Error('Installed Ollama did not start. Open its app and check its local service; no model was downloaded.');
  }
  if(this.stopping)return;
  const selected=await chooseOllamaModel(this.config,AbortSignal.timeout(20000));this.config.modelId=selected.name;await this.saveConfig();this.state='ready';
 }catch(e){this.state='failed';this.error=String(e.message??e);}}
 stopOwned(){this.stopping=true;if(this.ownedOllama&&!this.ownedOllama.killed)this.ownedOllama.kill();}
}
