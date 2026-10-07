'use strict';
const test = require('node:test'), assert = require('node:assert/strict');
const h = require('./t51-bot-support.cjs');
test('inventory comparisons preserve armor, offhand, NBT and selected hotbar slot', () => {
  const bot = { inventory: { slots: Array(46).fill(null) }, quickBarSlot: 4 };
  bot.inventory.slots[5] = { name: 'iron_helmet', count: 1, nbt: { Damage: 3 } };
  bot.inventory.slots[45] = { name: 'shield', count: 1, components: [{ type: 'damage', value: 2 },
    { type: 'custom_data', value: { marker: 1 } }] };
  const original = h.inventorySnapshot(bot); assert(h.inventoryMatches(bot, original));
  bot.inventory.slots[45].components.reverse(); assert(h.inventoryMatches(bot, original));
  bot.inventory.slots[45].components.find(c => c.type === 'damage').value = 1; assert(!h.inventoryMatches(bot, original));
  bot.inventory.slots[45].components.find(c => c.type === 'damage').value = 2; bot.quickBarSlot = 0; assert(!h.inventoryMatches(bot, original));
  bot.quickBarSlot = 4;
  bot.inventory.slots[45].removedComponents = ['lore']; assert(!h.inventoryMatches(bot, original));
});
test('Paper callback uses the T45 anonymous-compound wire format and UTF8 input', () => {
  const uuid = [1, -2, 3, -4];
  const tag = Buffer.from('0a0b000269640000000400000001fffffffe00000003fffffffc00', 'hex');
  const action = Buffer.from('paper:dialog_click_callback');
  const expected = Buffer.concat([Buffer.from([0x44, action.length]), action, Buffer.from([tag.length]), tag]);
  assert.deepEqual(h.dialogPacket(uuid), expected);
  const input = h.dialogPacket(uuid, 't51_nuevo'); assert(input.includes(Buffer.from('value'))); assert(input.includes(Buffer.from('t51_nuevo')));
  assert.deepEqual(h.dialogCallback({ yes: { action: { additions: { id: uuid } } } }), uuid);
  assert.deepEqual(h.dialogCallback({ no: { action: { additions: { id: [4, 3, 2, 1] } } },
    yes: { action: { additions: { id: uuid } } } }), uuid);
  assert.throws(() => h.dialogCallback({}));
});
test('modern menu packets omit speculative item hashes and map the use-item hand', () => {
  assert.deepEqual(h.menuPacket('window_click', { slot: 47, changedSlots: [{ item: 'prediction' }], cursorItem: 'prediction' }),
    { slot: 47, changedSlots: [], cursorItem: null });
  assert.deepEqual(h.menuPacket('use_item', { hand: 0, sequence: 1 }), { hand: 'main_hand', sequence: 1 });
  assert.deepEqual(h.menuPacket('use_item', { hand: 1 }), { hand: 'off_hand' });
  assert.deepEqual(h.menuPacket('player_input', { inputs: { shift: true } }), { inputs: { shift: true } });
});
test('the script cannot target the user server', () => {
  assert.doesNotThrow(() => h.assertAgentsTarget('agents', 25566));
  assert.throws(() => h.assertAgentsTarget('user', 25565));
  assert.throws(() => h.assertAgentsTarget('agents', 25565));
});
test('acceptance matrix includes every required source, route, exit, reload and restart', () => {
  const s = h.SCENARIOS; assert.equal(s.length, 16);
  for (const source of ['published', 'editor-new', 'wizard-new']) {
    for (const entry of ['button', 'command']) assert(s.some(x => x.source === source && x.entry === entry));
  }
  for (const exit of ['button', 'command', 'disconnect']) assert(s.some(x => x.phase === 'before' && x.exit === exit));
  for (const source of ['published', 'editor-resume', 'wizard-resume']) assert(s.some(x => x.phase === 'after' && x.source === source));
  assert(s.some(x => x.source === 'reload-published')); assert(s.some(x => x.source === 'reload-wizard'));
});
test('room selector is ready only after its slot contents arrive', () => {
  const window = { inventoryStart: 27, slots: Array(63).fill(null) };
  assert.equal(h.roomMenuReady(null), false);
  assert.equal(h.roomMenuReady(window), false); // open_window precedes window_items
  window.slots[4] = { name: 'oak_door' };
  assert.equal(h.roomMenuReady(window), false);
  window.slots[20] = { name: 'lime_dye' };
  assert.equal(h.roomMenuReady(window), true); // empty/new dungeon also allows creation
  window.slots[13] = { name: 'oak_door' };
  assert.equal(h.roomMenuReady(window), true); // published dungeon with a room
});
function cleanupFixture(online, withState = true) {
  const fs = require('node:fs'), os = require('node:os'), path = require('node:path');
  const { spawnSync } = require('node:child_process');
  const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 't51-cleanup-'));
  const name = 'T51B1234abcd';
  const other = { uuid: 'owner-uuid', name: 'Admin', level: 4, bypassesPlayerLimit: true };
  fs.writeFileSync(path.join(fixture, 'ops.json'), JSON.stringify([other, { name, uuid: 'bot-uuid', level: 4 }]));
  if (withState) fs.writeFileSync(path.join(fixture, 'state.json'), JSON.stringify({ name }));
  const source = fs.readFileSync(path.join(__dirname, 'test-t51-bots.sh'), 'utf8');
  const start = source.includes('revoke_bot_op()') ? source.indexOf('revoke_bot_op()') : source.indexOf('cleanup()');
  const functions = source.slice(start, source.indexOf('trap cleanup EXIT'));
  const result = spawnSync('bash', ['-c', `set -euo pipefail
SERVER=$1; T51_RESULTS=$1; owned=false; bot_pid=
port_open() { ${online ? 'true' : 'false'}; }
pgrep() { return 1; }
operation() { printf '%s\\n' "$*" >> "$T51_RESULTS/operations"; }
no_foreign_player() { true; }
${functions}
cleanup`, '_', fixture], { encoding: 'utf8' });
  return { fs, fixture, name, other, result };
}
test('cleanup revokes the bot OP even when it does not own the running server', () => {
  const f = cleanupFixture(true);
  try {
    assert.equal(f.result.status, 0, f.result.stderr);
    assert.equal(f.fs.readFileSync(require('node:path').join(f.fixture, 'operations'), 'utf8'), 'cmd deop ' + f.name + '\n');
  } finally { f.fs.rmSync(f.fixture, { recursive: true, force: true }); }
});
test('cleanup after a failed restart removes only the bot persisted OP offline', () => {
  const f = cleanupFixture(false);
  try {
    assert.equal(f.result.status, 0, f.result.stderr);
    assert.deepEqual(JSON.parse(f.fs.readFileSync(require('node:path').join(f.fixture, 'ops.json'), 'utf8')), [f.other]);
  } finally { f.fs.rmSync(f.fixture, { recursive: true, force: true }); }
});
test('cleanup without a bot identity preserves all server operators', () => {
  const f = cleanupFixture(false, false);
  try {
    assert.equal(f.result.status, 0, f.result.stderr);
    assert.equal(JSON.parse(f.fs.readFileSync(require('node:path').join(f.fixture, 'ops.json'), 'utf8')).length, 2);
  } finally { f.fs.rmSync(f.fixture, { recursive: true, force: true }); }
});
