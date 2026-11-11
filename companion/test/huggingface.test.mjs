import test from 'node:test';import assert from 'node:assert/strict';
import {generate} from '../provider.mjs';import {HUGGING_FACE,resolveConfig} from '../config.mjs';
import {fixture,compile} from '../compiler.mjs';
import fs from 'node:fs';
const registry=JSON.parse(fs.readFileSync(new URL('../registry-26.3.json',import.meta.url)));
test('Local defaults migrate empty legacy config while preserving custom providers',()=>{
 for(const old of [{},{providerUrl:'',modelId:'',apiKeyEnv:'LUNA_API_KEY',controlPort:29843}]){const c=resolveConfig(old);assert.equal(c.provider,'ollama');assert.equal(c.providerUrl,'http://127.0.0.1:11434/api/chat');assert.equal(c.modelId,'');assert.equal(c.apiKeyEnv,'')}
 const custom=resolveConfig({providerUrl:'http://localhost:8000/v1/chat/completions',modelId:'custom',apiKeyEnv:'LOCAL_KEY'});assert.equal(custom.provider,'custom');assert.equal(custom.apiKeyEnv,'LOCAL_KEY');assert.equal(custom.modelId,'custom');
});
test('HF token has an actionable missing-credential error',async()=>{const c={...HUGGING_FACE,apiKeyEnv:'LUNA_TEST_TOKEN_MISSING'};await assert.rejects(generate(c,{},registry,new AbortController().signal),/start-huggingface.cmd/)});
test('HF brief then JSON plan uses max_tokens and real schema validation',async()=>{
 const original=globalThis.fetch;const old=process.env.LUNA_TEST_HF_TOKEN;process.env.LUNA_TEST_HF_TOKEN='hf_test_only_not_a_real_credential';const sent=[];
 try{globalThis.fetch=async(url,options)=>{assert.equal(url.hostname,'router.huggingface.co');assert.equal(options.headers.Authorization,'Bearer hf_test_only_not_a_real_credential');const body=JSON.parse(options.body);sent.push(body);return Response.json({choices:[{message:{content:sent.length===1?'A connected timber pavilion with a supported roof.':JSON.stringify(fixture())}}],usage:{prompt_tokens:10,completion_tokens:20}})};
  const g=await generate({...HUGGING_FACE,maxOutputTokens:8192,apiKeyEnv:'LUNA_TEST_HF_TOKEN'},{prompt:'a pavilion'},registry,new AbortController().signal);const plan=await g.next();assert.equal(compile(plan,registry).total,332);assert.equal(sent.length,2);assert.equal(sent[0].max_tokens,1024);assert.equal(sent[1].max_tokens,8192);assert(!('max_completion_tokens' in sent[1]));assert.equal(sent[1].model,HUGGING_FACE.modelId);assert.equal(g.usage.length,2);
 }finally{globalThis.fetch=original;if(old===undefined)delete process.env.LUNA_TEST_HF_TOKEN;else process.env.LUNA_TEST_HF_TOKEN=old}
});
test('HF permission errors are useful and never echo provider body or token',async()=>{
 const original=globalThis.fetch;const old=process.env.LUNA_TEST_HF_TOKEN;process.env.LUNA_TEST_HF_TOKEN='hf_test_only_secret';
 try{globalThis.fetch=async()=>new Response('hf_test_only_secret: arbitrary provider diagnostics',{status:403});await assert.rejects(generate({...HUGGING_FACE,apiKeyEnv:'LUNA_TEST_HF_TOKEN'},{},registry,new AbortController().signal),e=>e.message.includes('Inference Providers')&&!e.message.includes('hf_test_only_secret'));}
 finally{globalThis.fetch=original;if(old===undefined)delete process.env.LUNA_TEST_HF_TOKEN;else process.env.LUNA_TEST_HF_TOKEN=old}
});
