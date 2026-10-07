#!/usr/bin/env node
'use strict';
// Paper event acceptance with one connected client; only the agents server, using temporary unique fixtures.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const { spawnSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const data = '/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes/plugins/CustomDungeons';
const requireBots = createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const run = process.argv.includes('--run');
assert.equal(process.env.CD_TARGET || 'agents', 'agents');
if (!run) {
  console.log('T52 / 25566: connect bot; spawn 5000 HP mobs; receive 4999 + 1 damage; melee override 3000 leaves 2000 HP; /kill; clean fixtures.');
  process.exit(0);
}
assert(process.argv.includes('--server-owned-by-runner'), 'Use scripts/test-t52-bots.sh --run');
const delay = ms => new Promise(r => setTimeout(r, ms));
const prefix = 't52-' + process.pid + '-' + Date.now().toString(36);
const name = 'T52B' + Date.now().toString(36);
const files = [];
let bot, foreign = false, platform = null;
const dir = process.env.T52_RESULTS || path.join(root, '.agent/t52-bots');
fs.mkdirSync(dir, { recursive: true });
fs.writeFileSync(path.join(dir, 'bot-name'), name);
async function cmd(command) {
  assert(!/[\r\n]/.test(command));
  for (let attempt = 0; ; attempt++) {
    const result = spawnSync(path.join(root, 'scripts/test-server.sh'), ['cmd', command], { encoding: 'utf8', env: { ...process.env, CD_TARGET: 'agents' } });
    if (result.status === 0) { await delay(150); return; }
    if (result.stderr.includes('Otra operación del servidor está en curso.') && attempt < 10) {
      await delay(60000); await delay(60000);
    } else throw new Error(result.stderr);
  }
}
async function until(check, label, timeout = 30000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { if (foreign) throw new Error('Another player joined'); if (check()) return; await delay(100); }
  throw new Error('Timeout: ' + label);
}
function fixture(kind, id, text) {
  const file = path.join(data, kind, id + '.yml');
  fs.writeFileSync(file, text, { flag: 'wx' }); files.push(file);
}
async function main() {
  const mineflayer = requireBots('mineflayer');
  bot = mineflayer.createBot({ host: '127.0.0.1', port: 25566, username: name, auth: 'offline', version: '26.1', physicsEnabled: false });
  const write = bot._client.write.bind(bot._client);
  bot._client.write = (packet, payload) => {
    if (['look', 'position', 'position_look', 'flying'].includes(packet)) return;
    write(packet, payload);
  };
  bot.on('spawn', () => { bot.physicsEnabled = false; });
  bot.on('playerJoined', p => { if (p.username !== name) { foreign = true; fs.writeFileSync(path.join(dir, 'foreign-player'), p.username); } });
  const chat = [];
  bot.on('messagestr', message => { chat.push(message); fs.appendFileSync(path.join(dir, 'chat.log'), message + '\n'); });
  bot.on('error', e => console.error(e.message));
  await until(() => bot.entity && bot.inventory, 'bot spawn', 45000);
  assert(Object.values(bot.players).every(p => p.username === name), 'Agents server occupied');
  await cmd('minecraft:gamemode creative ' + name);
  const x = 24000 + (process.pid % 500) * 32, z = 24000;
  const point = (px, pz) => `{world: dungeons, x: ${px}, y: 66, z: ${pz}}`;
  for (const id of ['health', 'attacker', 'victim']) fixture('mobs', prefix + '-' + id,
    `entity-type: HUSK\ndisplay-name: T52 ${id}\nmax-health: 5000\ndamage: ${id === 'attacker' ? 3000 : 1}\nattributes: {armor: 0, armor-toughness: 0, speed: 0, gravity: 0}\n`);
  fixture('dungeons', prefix,
    `display-name: T52 acceptance\nenabled: true\nlobby: ${point(x, z)}\nexit: ${point(x + 40, z)}\nmin-players: 1\nmax-players: 1\nlobby-countdown-seconds: 5\nlives: 3\nkeep-inventory: true\nrooms:\n  - id: test\n    region: {world: dungeons, min: {x: ${x - 8}, y: 60, z: ${z - 8}}, max: {x: ${x + 8}, y: 80, z: ${z + 8}}}\n    checkpoint: ${point(x, z)}\n    spawners:\n      - id: test\n        location: ${point(x + 3, z)}\n        radius: 1\n        waves:\n          - mode: SIMULTANEO\n            entries:\n${['health', 'attacker', 'victim'].map(id => `              - {template-id: ${prefix}-${id}, count: 1, delay-ticks: 0}`).join('\n')}\n`);
  const Vec3 = requireBots('vec3').Vec3;
  await cmd(`minecraft:execute in dungeons run tp ${name} ${x} 66 ${z}`);
  await until(() => Math.abs(bot.entity.position.x-x)<1 && bot.blockAt(new Vec3(x+8,64,z+8)), 'fixture chunks');
  for(let px=x-8;px<=x+8;px++) for(let pz=z-8;pz<=z+8;pz++)
    assert(['air','cave_air','void_air'].includes(bot.blockAt(new Vec3(px,64,pz))?.name), 'Fixture platform is not empty');
  platform = [x - 8, 64, z - 8, x + 8, 64, z + 8].join(' ');
  await cmd('minecraft:execute in dungeons run fill ' + platform + ' minecraft:glass replace minecraft:air');
  const logPath = path.resolve(data, '../../logs/latest.log');
  const logOffset = fs.statSync(logPath).size;
  await cmd('customdungeon reload');
  // The Pi loads definitions asynchronously; wait for the translated completion signal.
  await until(() => /recargad|reloaded/i.test(fs.readFileSync(logPath).subarray(logOffset).toString('utf8')), 'definition reload', 90000);
  await cmd('customdungeon join ' + name + ' ' + prefix);
  const mob = id => `@e[type=minecraft:husk,nbt={BukkitValues:{"customdungeons:template":"${prefix}-${id}"}},limit=1]`;
  const execute = command => 'minecraft:execute in dungeons run ' + command;
  await delay(9000);
  await cmd(execute('data merge entity ' + mob('attacker') + ' {NoAI:1b}'));
  await cmd(execute('data merge entity ' + mob('victim') + ' {NoAI:1b}'));
  await cmd(execute('data merge entity ' + mob('health') + ' {NoAI:1b}'));
  async function exists(id, expected, label) {
    const marker = prefix + '-' + label;
    await cmd(execute(`execute ${expected ? 'if' : 'unless'} entity ${mob(id)} run say ${marker}`));
    await until(() => chat.some(s => s.includes(marker)), label, 10000);
  }
  await exists('health', true, 'spawned');
  // Four equal hits plus 999 must leave the mob alive; the last 1 is lethal.
  for (let i = 0; i < 4; i++) { await cmd(execute('damage ' + mob('health') + ' 1000 minecraft:generic')); await delay(700); }
  await cmd(execute('damage ' + mob('health') + ' 999 minecraft:generic'));await delay(700);
  await exists('health', true, 'alive-after-4999');
  await cmd(execute('damage ' + mob('health') + ' 1 minecraft:generic'));await delay(1000);
  await exists('health', false, 'dead-after-5000');
  console.log('PASS: 5000 HP survives 4999 and dies on the last 1.');
  // Public damage command creates a real ENTITY_ATTACK event with the configured mob as the damager.
  await cmd(execute('damage ' + mob('victim') + ' 1 minecraft:mob_attack by ' + mob('attacker')));await delay(700);
  await exists('victim', true, 'melee-survived');
  await cmd(execute('data get entity ' + mob('victim') + ' BukkitValues."customdungeons:virtual_health"'));
  // This remaining-health probe also proves all 3000 were applied, beyond Minecraft's 2048 cap.
  await cmd(execute('damage ' + mob('victim') + ' 1999 minecraft:generic'));await delay(700);
  await exists('victim', true, 'alive-after-4999-melee');
  await cmd(execute('damage ' + mob('victim') + ' 1 minecraft:generic'));await delay(1000);
  await exists('victim', false, 'dead-after-3000-plus-2000');
  console.log('PASS: melee override applies 3000; target has exactly 2000 remaining.');
  await cmd(execute('kill ' + mob('attacker')));await delay(1000);
  await exists('attacker', false, 'kill-bypasses-virtual');
  console.log('PASS: /kill bypasses virtual health.');
  await cmd('customdungeon stop ' + prefix);
  await cmd('minecraft:execute in dungeons run fill ' + platform + ' minecraft:air replace minecraft:glass');
  platform=null;
}
main().catch(error => { console.error(error.stack); process.exitCode = 1; }).finally(async () => {
  try { await cmd('customdungeon stop ' + prefix); } catch {}
  if(platform) try { await cmd('minecraft:execute in dungeons run fill ' + platform + ' minecraft:air replace minecraft:glass'); } catch {}
  for (const file of files) fs.unlinkSync(file);
  try { await cmd('customdungeon reload'); } catch {}
  if (bot) bot.quit();
});
