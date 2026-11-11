import {OLLAMA} from './ollama.mjs';
export const HUGGING_FACE = Object.freeze({
 provider: 'huggingface',
 modelLabel: 'Luna · Hugging Face Qwen',
 providerUrl: 'https://router.huggingface.co/v1/chat/completions',
 modelId: 'Qwen/Qwen3.6-35B-A3B',
 apiKeyEnv: 'HF_TOKEN'
});
export const DEFAULT_CONFIG = Object.freeze({
 ...OLLAMA, ollamaContextTokens:32768, controlPort:28763, timeoutMs:180000, maxOutputTokens:8192,
 placementsPerSecond:4, botExecutable:'', botArgs:[]
});
export function resolveConfig(saved={}) {
 const config={...DEFAULT_CONFIG,...saved};
 // Migrate the original unconfigured package without reusing another provider's credential.
 if(!saved.providerUrl&&!saved.modelId)Object.assign(config,OLLAMA);
 // A previously configured custom endpoint remains custom until explicitly switched.
 else if(!saved.provider&&saved.providerUrl!==HUGGING_FACE.providerUrl)config.provider=saved.providerUrl===OLLAMA.providerUrl?'ollama':'custom';
 return config;
}
