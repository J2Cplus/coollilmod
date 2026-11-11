import test from 'node:test';import assert from 'node:assert/strict';import {schedule} from '../scheduler.mjs';
test('support order grows connected blocks without per-step AI',()=>{const a=[{p:[2,1,0],state:'minecraft:stone',component:'wall'},{p:[1,1,0],state:'minecraft:stone',component:'wall'},{p:[0,1,0],state:'minecraft:stone',component:'wall'}];assert.deepEqual(schedule(a,['0,0,0']).map(t=>t.p[0]),[0,1,2])});
test('unsupported components fail before building',()=>assert.throws(()=>schedule([{p:[0,50,0],state:'minecraft:stone',component:'roof'}],[]),/unsupported/));
test('air removals need no support',()=>assert.equal(schedule([{p:[0,50,0],state:'minecraft:air'}],[]).length,1));
