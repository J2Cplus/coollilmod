import {fileURLToPath} from 'node:url';import fs from 'node:fs/promises';import path from 'node:path';
export const OLLAMA=Object.freeze({provider:'ollama',modelLabel:'Luna · local Ollama',providerUrl:'http://127.0.0.1:11434/api/chat',modelId:'',apiKeyEnv:''});
function base(endpoint){const u=new URL(endpoint);if(u.protocol!=='http:'||!['127.0.0.1','localhost','[::1]'].includes(u.hostname)||u.username||u.password||u.search||u.hash||u.pathname!=='/api/chat')throw Error('Ollama must use a local loopback /api/chat endpoint');return u.origin;}
const cloud=m=>!!m.remote_host||!!m.remote_model||/(?:[:\-]cloud)(?:$|:)/i.test(m.name??m.model??'');
async function read(url,options={}){let r;try{r=await fetch(url,{...options,redirect:'error',signal:options.signal??AbortSignal.timeout(10000)})}catch(e){if(options.signal?.aborted)throw e;throw Error('Cannot reach local Ollama. Open the Ollama app on this PC, then run start-ollama.cmd.')}if(!r.ok)throw Error(`Ollama HTTP ${r.status}: check that Ollama is running and the selected model is installed`);return r.json();}
export async function discoverOllama(config,signal){const origin=base(config.providerUrl);const data=await read(origin+'/api/tags',{signal});if(!Array.isArray(data.models)||data.models.length>500)throw Error('Ollama returned an invalid installed-model list');return data.models.filter(m=>typeof(m.name??m.model)==='string'&&!cloud(m)).map(m=>({...m,name:m.name??m.model}));}
export async function chooseOllamaModel(config,signal){
 const all=await discoverOllama(config,signal);const candidates=all.filter(m=>!/(embed|nomic|bge-|rerank)/i.test(m.name));
 function score(m){const gb=(m.size??0)/1e9;return (gb>=2&&gb<=8?0:20)+(gb>8?gb:0)+(gb<2?10:0)+(/qwen.*coder/i.test(m.name)?0:/qwen/i.test(m.name)?1:/llama|gemma|mistral/i.test(m.name)?3:5)}
 candidates.sort((a,b)=>score(a)-score(b)||(a.size??0)-(b.size??0)||a.name.localeCompare(b.name));
 if(config.modelId){const exact=candidates.find(m=>m.name===config.modelId);if(!exact)throw Error('Selected Ollama model is not installed locally or is a cloud/embedding model. Run start-ollama.cmd to choose an installed local model.');candidates.splice(0,candidates.length,exact);}
 for(const model of candidates.slice(0,32)){const details=await read(base(config.providerUrl)+'/api/show',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({model:model.name}),signal});if(!cloud(details)&&Array.isArray(details.capabilities)&&details.capabilities.includes('completion'))return {name:model.name,capabilities:details.capabilities};}
 throw Error('No installed local chat model with completion support was found. Cloud and embedding-only models are excluded; no models are downloaded automatically.');
}
if(process.argv[1]&&fileURLToPath(import.meta.url)===path.resolve(process.argv[1])){
 try{const root=path.dirname(fileURLToPath(import.meta.url));const file=process.env.LUNA_CONFIG??path.join(root,'config.json');let old={};try{old=JSON.parse(await fs.readFile(file,'utf8'))}catch(e){if(e.code!=='ENOENT')throw e}const config={controlPort:28763,timeoutMs:180000,maxOutputTokens:8192,ollamaContextTokens:32768,placementsPerSecond:4,botExecutable:'',botArgs:[],...old,...OLLAMA};
  const selected=await chooseOllamaModel(config);config.modelId=selected.name;await fs.writeFile(file,JSON.stringify(config,null,2),{mode:0o600});console.log(`Local Ollama configured: ${selected.name}. No API key, paid provider or model download is used.`);
 }catch(e){console.error(e.message);process.exitCode=1}
}
