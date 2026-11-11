const directions=[[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]];
const air=s=>/^minecraft:(air|cave_air|void_air)(\[|$)/.test(s);
const key=p=>p.join(',');
// Keep dependency-ordered components, then grow each component from real support.
export function schedule(tasks,supports){
 const solid=new Set(supports),groups=new Map(),out=[];
 for(const t of tasks){const g=t.component??'build';if(!groups.has(g))groups.set(g,[]);groups.get(g).push(t)}
 for(const group of groups.values()){
  const pending=new Map(group.map(t=>[key(t.p),t])),ready=[],queued=new Set();
  function enqueue(t){const k=key(t.p);if(queued.has(k))return;if(air(t.state)||solid.has(k)||directions.some(d=>solid.has(key(t.p.map((n,i)=>n+d[i]))))){ready.push(t);queued.add(k)}}
  for(const t of group)enqueue(t);
  for(let i=0;i<ready.length;i++){const t=ready[i],k=key(t.p);if(!pending.delete(k))continue;out.push(t);if(air(t.state))solid.delete(k);else solid.add(k);for(const d of directions){const near=pending.get(key(t.p.map((n,i)=>n+d[i])));if(near)enqueue(near)}}
  if(pending.size)throw Error(`Component ${group[0].component??'build'} has ${pending.size} unsupported placements. Revise with connected supports.`);
 }
 return out;
}
