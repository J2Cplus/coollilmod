// Declarative, deterministic plan compiler. AI output is never executed.
export const LIMIT=60000;
const banned=/(tnt|fire|lava|water|command_block|structure_|jigsaw|portal|spawner|bedrock|barrier|end_gateway|moving_piston|piston_head|light$|respawn_anchor)/;
const int=(n,min=-128,max=128)=>{if(!Number.isInteger(n)||n<min||n>max)throw Error(`Integer outside ${min}..${max}: ${n}`);return n};
export function transform(p,origin,rotation=0){let [x,y,z]=p;for(let i=0;i<rotation;i++)[x,z]=[-z,x];return [x+origin[0],y+origin[1],z+origin[2]]}
export function rotateState(s,r){let v=s;const dirs=['north','east','south','west'];v=v.replace(/facing=(north|east|south|west)/g,(_,d)=>`facing=${dirs[(dirs.indexOf(d)+r)%4]}`);if(r%2)v=v.replace(/axis=([xz])/g,(_,a)=>`axis=${a==='x'?'z':'x'}`);return v}
export function compile(plan,registry,{maxBlocks=LIMIT,maxDimensions=[96,96,96]}={}){
 if(!plan||plan.version!==1||!Array.isArray(plan.operations)||plan.operations.length>1000)throw Error('Expected plan version 1, at most 1000 operations');
 if(!plan.palette||typeof plan.palette!=='object')throw Error('Palette required');
 const states={};for(const [k,s] of Object.entries(plan.palette)){
  if(typeof s!=='string'||!/^minecraft:[a-z0-9_]+(\[[a-z0-9_=,]+\])?$/.test(s)||(banned.test(s.split('[')[0])||/waterlogged=true/.test(s)))throw Error(`Unsafe/invalid block ${s}`);
  const id=s.split('[')[0],def=registry[id];if(!def||def.blockEntity)throw Error(`Unknown or unsupported block entity: ${id}`);
  const props=s.includes('[')?s.slice(s.indexOf('[')+1,-1).split(','):[];
  const seen=new Set();for(const prop of props){let [a,b]=prop.split('=');if(seen.has(a)||!def.properties?.[a]?.includes(b))throw Error(`Invalid block state ${s}`);seen.add(a)}states[k]=s;
 }
 const ops=new Map();for(const op of plan.operations){if(!op.id||ops.has(op.id))throw Error('Unique operation IDs required');ops.set(op.id,op)}
 const order=[],seen=new Set(),active=new Set();function visit(op){if(active.has(op.id))throw Error('Dependency cycle');if(seen.has(op.id))return;active.add(op.id);for(const d of op.depends??[]){if(!ops.has(d))throw Error(`Missing dependency ${d}`);visit(ops.get(d))}active.delete(op.id);seen.add(op.id);order.push(op)}for(const op of ops.values())visit(op);
 const blocks=new Map();let expansions=0;
 function put(p,material,op){if(++expansions>LIMIT*4)throw Error('Expansion limit exceeded');if(!Array.isArray(p)||p.length!==3)throw Error('Coordinates require 3 integers');p.forEach(n=>int(n));const state=states[material];if(!state)throw Error(`Unknown palette key ${material}`);const key=p.join(',');const old=blocks.get(key);if(old&&old.state!==state&&!op.replace)throw Error(`Contradictory placement ${key}; explicit replace required`);blocks.set(key,{p:[...p],state,group:op.group??'shell',component:op.id});if(blocks.size>maxBlocks)throw Error('Block limit exceeded')}
 for(const op of order){const a=op.from,b=op.to;
  switch(op.type){
   case 'place':put(op.at,op.material,op);break;
   case 'fill':case 'floor':case 'wall':case 'hollow':{
    if(!Array.isArray(a)||!Array.isArray(b)||a.length!==3||b.length!==3)throw Error('from/to required');[...a,...b].forEach(n=>int(n));if(a.some((n,i)=>n>b[i]))throw Error('Reversed bounds');
    for(let x=a[0];x<=b[0];x++)for(let y=a[1];y<=b[1];y++)for(let z=a[2];z<=b[2];z++)if(op.type!=='hollow'||x===a[0]||x===b[0]||y===a[1]||y===b[1]||z===a[2]||z===b[2])put([x,y,z],op.material,op);break;}
   case 'pillar':for(let y=0;y<int(op.height,1,96);y++)put([op.at[0],op.at[1]+y,op.at[2]],op.material,op);break;
   case 'stairs':for(let i=0;i<int(op.length,1,96);i++)for(let w=0;w<int(op.width??1,1,32);w++)put([op.at[0]+w,op.at[1]+i,op.at[2]+i],op.material,op);break;
   case 'roof':{const [x,y,z]=op.at;const w=int(op.width,3,96),l=int(op.length,3,96);for(let i=0;i<Math.ceil(w/2);i++)for(let j=0;j<l;j++){put([x+i,y+i,z+j],op.material,op);put([x+w-1-i,y+i,z+j],op.material,op);if(i>0){put([x+i,y+i-1,z+j],op.material,op);put([x+w-1-i,y+i-1,z+j],op.material,op)}}break;}
   case 'path':{if(!Array.isArray(op.points)||op.points.length>128)throw Error('Invalid path');for(let n=1;n<op.points.length;n++){const a=op.points[n-1],b=op.points[n];[...a,...b].forEach(n=>int(n));let p=[...a];for(let i=0;i<768;i++){put(p,op.material,op);if(p.every((x,k)=>x===b[k]))break;const k=p.findIndex((x,k)=>x!==b[k]);p[k]+=Math.sign(b[k]-p[k])}}break;}
   default:throw Error(`Unsupported primitive ${op.type}`);
  }
 }
 const result=[...blocks.values()];if(!result.length)throw Error('Empty build');let min=[...result[0].p],max=[...min];for(const b of result)for(let i=0;i<3;i++){min[i]=Math.min(min[i],b.p[i]);max[i]=Math.max(max[i],b.p[i])}if(max.some((n,i)=>n-min[i]+1>maxDimensions[i]))throw Error('Build dimensions exceed selected limit');
 // Stable height order within each dependency-ordered component.
 const rank=new Map(order.map((o,i)=>[o.id,i]));result.sort((a,b)=>rank.get(a.component)-rank.get(b.component)||a.p[1]-b.p[1]||a.p[0]-b.p[0]||a.p[2]-b.p[2]);
 return {version:1,blocks:result,min,max,total:result.length};
}
export function fixture(){return {version:1,title:'OFFLINE DEMO: garden pavilion',palette:{base:'minecraft:stone_bricks',wood:'minecraft:oak_planks',post:'minecraft:oak_log',roof:'minecraft:bricks',light:'minecraft:glowstone'},operations:[{id:'foundation',type:'floor',from:[0,0,0],to:[8,0,8],material:'base',group:'foundation'},...[ [0,0],[0,8],[8,0],[8,8]].map(([x,z],i)=>({id:'post'+i,type:'pillar',at:[x,1,z],height:4,material:'post',depends:['foundation']})),{id:'beamA',type:'wall',from:[0,4,0],to:[8,4,0],material:'wood',replace:true,depends:['post0','post1','post2','post3']},{id:'beamB',type:'wall',from:[0,4,8],to:[8,4,8],material:'wood',replace:true,depends:['post0','post1','post2','post3']},{id:'roof',type:'roof',at:[-1,5,-1],width:11,length:11,material:'roof',depends:['beamA','beamB'],group:'roof',replace:true},{id:'lamp',type:'place',at:[4,8,4],material:'light',depends:['roof'],group:'interior'}]}}
