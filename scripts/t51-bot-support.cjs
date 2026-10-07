'use strict';
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const SCENARIOS = [
  ['published-button-command', 'published', 'button', 'command'],
  ['published-command-button', 'published', 'command', 'button'],
  ['published-button-disconnect', 'published', 'button', 'disconnect'],
  ['new-editor-button-button', 'editor-new', 'button', 'button'],
  ['new-editor-command-disconnect', 'editor-new', 'command', 'disconnect'],
  ['new-wizard-button-command', 'wizard-new', 'button', 'command'],
  ['new-wizard-command-button', 'wizard-new', 'command', 'button'],
  ['resume-editor-command-disconnect', 'editor-resume', 'command', 'disconnect'],
  ['resume-wizard-button-button', 'wizard-resume', 'button', 'button'],
  ['resume-published-command-command', 'published', 'command', 'command'],
  ['reload-published-button-command', 'reload-published', 'button', 'command'],
  ['reload-wizard-command-button', 'reload-wizard', 'command', 'button'],
  ['restart-published-button-disconnect', 'published', 'button', 'disconnect'],
  ['restart-editor-command-command', 'editor-resume', 'command', 'command'],
  ['restart-wizard-button-button', 'wizard-resume', 'button', 'button'],
  ['restart-published-command-button', 'published', 'command', 'button']
].map(([name, source, entry, exit], i) => ({ name, source, entry, exit, phase: i < 12 ? 'before' : 'after' }));
function canonical(value) {
  if (typeof value === 'bigint') return { bigint: value.toString() };
  if (Buffer.isBuffer(value)) return { bytes: [...value] };
  if (ArrayBuffer.isView(value)) return { array: [...value].map(canonical) };
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.keys(value).sort()
    .filter(k => value[k] !== undefined).map(k => [k, canonical(value[k])]));
  return value;
}
function inventorySnapshot(bot) {
  const componentOrder = (a, b) => String(a.type ?? a).localeCompare(String(b.type ?? b));
  return canonical({ slots: bot.inventory.slots.map(i => i ? {
    name: i.name, count: i.count, nbt: i.nbt,
    // Component patches are maps: wire ordering may change after Paper NBT restore.
    components: i.components && [...i.components].sort(componentOrder),
    removedComponents: i.removedComponents && [...i.removedComponents].sort(componentOrder)
  } : null), held: bot.quickBarSlot });
}
function inventoryMatches(bot, original) {
  return JSON.stringify(inventorySnapshot(bot)) === JSON.stringify(original);
}
function varInt(value) {
  assert(Number.isInteger(value) && value >= 0 && value <= 0x7fffffff);
  const bytes = []; do { let b = value & 0x7f; value >>>= 7; bytes.push(value ? b | 0x80 : b); } while (value);
  return Buffer.from(bytes);
}
function stringTag(name, value) {
  const key = Buffer.from(name), text = Buffer.from(value); assert(text.length <= 65535);
  const header = Buffer.alloc(3); header[0] = 8; header.writeUInt16BE(key.length, 1);
  const length = Buffer.alloc(2); length.writeUInt16BE(text.length);
  return Buffer.concat([header, key, length, text]);
}
// Paper 26.3 / client 26.1: the payload is a length-prefixed anonymous compound,
// not minecraft-data's optional-NBT encoding (T45 protocol evidence).
function dialogPacket(uuidInts, value, packetId = 0x44) {
  assert(Array.isArray(uuidInts) && uuidInts.length === 4);
  const ints = Buffer.alloc(16); uuidInts.forEach((v, i) => { assert(Number.isInteger(v)); ints.writeInt32BE(v | 0, i * 4); });
  const tag = Buffer.concat([Buffer.from([10, 11, 0, 2, 105, 100, 0, 0, 0, 4]), ints,
    value === undefined ? Buffer.alloc(0) : stringTag('value', value), Buffer.from([0])]);
  const action = Buffer.from('paper:dialog_click_callback');
  return Buffer.concat([varInt(packetId), varInt(action.length), action, varInt(tag.length), tag]);
}
function dialogCallback(data) {
  const candidates = [];
  function walk(value) {
    if (!value || typeof value !== 'object') return;
    const id = value.additions?.id;
    if ((Array.isArray(id) || ArrayBuffer.isView(id)) && id.length === 4) candidates.push([...id]);
    for (const child of Object.values(value)) walk(child);
  }
  walk(data.yes ?? data); assert(candidates.length >= 1, 'Missing Paper dialog callback UUID'); return candidates[0];
}
function menuPacket(name, data) {
  // These clicks target cancelling plugin menus, with an empty carried cursor.
  // Mineflayer predicts full item stacks; 26.1 instead expects hashed stacks.
  if (name === 'window_click') return { ...data, changedSlots: [], cursorItem: null };
  if (name === 'use_item' && typeof data.hand === 'number') return { ...data, hand: data.hand === 0 ? 'main_hand' : 'off_hand' };
  return data;
}
function definitionFingerprint(text) {
  const section = text.match(/^definition:\s*\n([\s\S]*?)(?=^baseline:)/m);
  assert(section, 'Missing definition/baseline in construction journal');
  // Map order can differ across JVMs. This checks our unchanged fixture values;
  // complete player inventory equality is checked independently, slot by slot.
  return crypto.createHash('sha256').update(section[1].split('\n').filter(Boolean).sort().join('\n')).digest('hex');
}
function assertAgentsTarget(target, port) {
  assert(target === 'agents', 'Only CD_TARGET=agents is supported'); assert(port === 25566, 'Only port 25566 is supported');
}
function roomMenuReady(window) {
  return !!window && window.slots[4]?.name === 'oak_door'
    && window.slots.slice(window.inventoryStart - 9, window.inventoryStart).some(i => i?.name === 'lime_dye');
}
module.exports = { SCENARIOS, canonical, inventorySnapshot, inventoryMatches, varInt, dialogPacket,
  dialogCallback, definitionFingerprint, assertAgentsTarget, menuPacket, roomMenuReady };
