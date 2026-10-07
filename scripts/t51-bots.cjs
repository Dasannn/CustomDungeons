#!/usr/bin/env node
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const { spawnSync } = require('node:child_process');
const h = require('./t51-bot-support.cjs');
const ROOT = path.resolve(__dirname, '..');
const SERVER = '/home/dasan/Desktop/Proyectos/plugins/servidor/Servidor-agentes';
const DATA = path.join(SERVER, 'plugins/CustomDungeons');
const requireBots = createRequire('/home/dasan/Desktop/Proyectos/plugins/servidor/bots/package.json');
const args = process.argv.slice(2);
h.assertAgentsTarget(process.env.CD_TARGET || 'agents', 25566);
if (!args.length || args.includes('--plan')) {
  console.log(JSON.stringify({ target: 'agents', port: 25566, scenarios: h.SCENARIOS }, null, 2));
} else if (args.includes('--self-check')) {
  for (const name of ['mineflayer', 'vec3', 'prismarine-nbt', 'minecraft-data']) requireBots(name);
  const data = requireBots('minecraft-data')('26.1');
  assert.equal(data.protocol.play.toServer.types.packet[1][0].type[1].mappings['0x44'], 'custom_click_action');
  const serializer = requireBots('minecraft-protocol').createSerializer({ state: 'play', isServer: false, version: '26.1' });
  for (const [name, params] of [
    ['window_click', { windowId: 1, stateId: 0, slot: 47, mouseButton: 0, mode: 0, changedSlots: [], cursorItem: null }],
    ['use_item', { hand: 0, sequence: 1, rotation: { x: 0, y: 0 } }],
    ['player_input', { inputs: { shift: true } }]
  ]) assert(serializer.createPacketBuffer({ name, params: h.menuPacket(name, params) }).length > 0);
  console.log('Dependencies and T45 dialog protocol available; no connection made.');
} else if (args.includes('--run') && args.includes('--server-owned-by-runner')) {
  main().catch(error => { console.error(error.stack); process.exitCode = 1; });
} else throw new Error('Use scripts/test-t51-bots.sh --run (or --plan/--self-check).');

async function main() {
  const mf = requireBots('mineflayer'), nbt = requireBots('prismarine-nbt');
  const resultDir = path.resolve(process.env.T51_RESULTS || path.join(ROOT, '.agent/t51-bots'));
  fs.mkdirSync(resultDir, { recursive: true });
  const phase = args[args.indexOf('--phase') + 1]; assert(['before', 'after'].includes(phase));
  const stateFile = path.join(resultDir, 'state.json');
  let state = phase === 'after' ? JSON.parse(fs.readFileSync(stateFile)) : {
    name: 'T51B' + Math.floor(Date.now() / 1000).toString(16), ids: {}, passed: []
  };
  assert(/^T51B[0-9a-f]{8}$/.test(state.name));
  let bot, dialog, failure, shuttingDown = false, restarting = false;
  const logFile = path.join(resultDir, phase + '.jsonl');
  const log = (event, data) => fs.appendFileSync(logFile, JSON.stringify(h.canonical({ at: new Date().toISOString(), event, data })) + '\n');
  const persist = () => fs.writeFileSync(stateFile, JSON.stringify(state, null, 2));
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  async function until(check, label, timeout = 15000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) { if (failure) throw failure; if (check()) return; await delay(100); }
    throw new Error('Timeout: ' + label);
  }
  async function cmd(command) {
    assert(!/[\r\n]/.test(command));
    for (let attempt = 0; ; attempt++) {
      const r = spawnSync(path.join(ROOT, 'scripts/test-server.sh'), ['cmd', command], {
        cwd: ROOT, encoding: 'utf8', env: { ...process.env, CD_TARGET: 'agents' }
      });
      if (r.status === 0) return;
      if (r.stderr.includes('Otra operación del servidor está en curso.') && attempt < 10) {
        log('console-busy-retry', attempt + 1); await delay(60000); await delay(60000);
      } else throw new Error('Console command failed: ' + r.stderr.trim());
    }
  }
  async function connect() {
    dialog = null; failure = null;
    bot = mf.createBot({ host: '127.0.0.1', port: 25566, username: state.name, auth: 'offline', version: '26.1', physicsEnabled: false });
    const write = bot._client.write.bind(bot._client);
    bot._client.write = (name, data) => {
      if (['look', 'position', 'position_look', 'flying'].includes(name)) return;
      write(name, h.menuPacket(name, data));
    };
    bot.on('spawn', () => { bot.physicsEnabled = false; });
    bot.on('messagestr', text => log('chat', text));
    bot.on('windowOpen', w => log('window', { title: w.title, size: w.inventoryStart }));
    bot.on('error', error => { failure = error; });
    bot.on('death', () => { failure = new Error('Test fixture died (gameMode=' + bot.game.gameMode + ')'); });
    bot.on('kicked', reason => {
      if (restarting) log('restart-disconnect', reason);
      else failure = new Error('Bot kicked: ' + JSON.stringify(reason));
    });
    bot.on('playerJoined', p => {
      if (p.username !== state.name) {
        fs.writeFileSync(path.join(resultDir, 'foreign-player'), p.username);
        failure = new Error('Another player joined; no restart/stop is permitted.');
      }
    });
    bot._client.on('show_dialog', packet => {
      const raw = packet.dialog?.data ?? packet.dialog ?? packet;
      dialog = raw.type ? nbt.simplify(raw) : raw;
    });
    await until(() => bot.entity && bot.inventory, 'bot spawn', 45000);
    const others = Object.values(bot.players).filter(p => p.username !== state.name);
    if (others.length) fs.writeFileSync(path.join(resultDir, 'foreign-player'), others.map(p => p.username).join(','));
    assert.equal(others.length, 0, 'The agents server is occupied');
    log('connected', { gameMode: bot.game.gameMode });
    // The installed world/permissions plugins may force survival on a real join.
    // The GUI fixture must remain protected without changing mobs or gamerules.
    await cmd('minecraft:gamemode creative ' + state.name);
    await until(() => bot.game.gameMode === 'creative', 'creative GUI fixture');
    await delay(1500);
  }
  async function chat(text) { bot.chat(text); await delay(600); }
  async function click(slot, button = 0) {
    assert(bot.currentWindow, 'Missing menu for slot ' + slot);
    await bot.clickWindow(slot, button, 0); await delay(500);
  }
  async function closeWindow() { if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await delay(200); }
  function findEntry(id) {
    const w = bot.currentWindow; if (!w) return -1;
    return w.slots.slice(9, w.inventoryStart - 9).findIndex(i => i && JSON.stringify(h.canonical(i)).includes(id)) + 9;
  }
  async function openList() {
    await chat('/customdungeon'); await until(() => bot.currentWindow?.inventoryStart === 27
      && bot.currentWindow.slots[10]?.name === 'bookshelf', 'loaded main menu');
    await click(10); await until(() => bot.currentWindow?.inventoryStart === 54
      && bot.currentWindow.slots[4]?.name === 'bookshelf', 'loaded dungeon list');
  }
  async function selectEditor(id, wizard = false) {
    await openList();
    let slot = findEntry(id);
    for (let page = 0; slot < 9 && page < 10; page++) { await click(50); slot = findEntry(id); }
    assert(slot >= 9, 'Missing dungeon in list: ' + id);
    await click(slot, wizard ? 1 : 0);
    await until(() => bot.currentWindow?.inventoryStart === 54 && bot.currentWindow.slots[47]?.name === 'bricks', 'source editor button 47');
  }
  async function newDefinition(kind, number) {
    const id = 't51_' + state.name.toLowerCase() + '_' + kind[0] + number;
    assert(!fs.existsSync(path.join(DATA, 'dungeons', id + '.yml')));
    assert(!fs.existsSync(path.join(DATA, 'wizard-drafts', id + '.yml')));
    dialog = null;
    if (kind === 'editor') {
      await openList(); await click(49);
    } else {
      await chat('/customdungeon'); await until(() => bot.currentWindow?.inventoryStart === 27
        && bot.currentWindow.slots[12]?.name === 'lime_dye', 'loaded main menu');
      await click(12);
    }
    await until(() => dialog !== null, 'new dungeon dialog');
    bot._client.writeRaw(h.dialogPacket(h.dialogCallback(dialog), id));
    await until(() => bot.currentWindow?.inventoryStart === 54 && (kind === 'editor'
      ? bot.currentWindow.slots[47]?.name === 'bricks' : bot.currentWindow.slots[49]?.name === 'book'), 'loaded new editor/assistant');
    if (kind === 'wizard') {
      await until(() => fs.existsSync(path.join(DATA, 'wizard-drafts', id + '.yml')), 'assistant persisted', 30000);
      await click(49); // Pause the actual assistant before its full-editor route.
    } else assert.equal(bot.currentWindow.slots[47]?.name, 'bricks');
    state.ids[kind] ??= id; persist(); return id;
  }
  function toolIndex(item) {
    if (!item) return -1;
    let simplified;
    if (item.nbt) simplified = nbt.simplify(item.nbt);
    else {
      const custom = item.components?.find(c => c.type === 'custom_data' || c.type === 'minecraft:custom_data');
      if (custom?.data) simplified = custom.data.type ? nbt.simplify(custom.data) : custom.data;
    }
    let result = -1;
    function visit(value) {
      if (!value || typeof value !== 'object') return;
      if (Number.isInteger(value['customdungeons:build-tool'])) result = value['customdungeons:build-tool'];
      for (const child of Object.values(value)) visit(child);
    }
    visit(simplified); return result;
  }
  function hasTools() { return Array.from({ length: 9 }, (_, i) => toolIndex(bot.inventory.slots[36 + i])).every((v, i) => v === i); }
  function draft(id) {
    const dir = path.join(DATA, 'build-drafts');
    const file = fs.readdirSync(dir).find(f => f === bot.player.uuid + '--' + id + '.yml');
    assert(file, 'Missing admin construction journal for ' + id);
    return fs.readFileSync(path.join(dir, file), 'utf8');
  }
  function rememberedDraft(id) {
    try { return h.definitionFingerprint(draft(id)); } catch (error) {
      if (error.code === 'ENOENT' || error.message.startsWith('Missing admin construction')) return null;
      throw error;
    }
  }
  async function enter(id, entry, wizard = false, currentEditor = false) {
    const saved = rememberedDraft(id);
    if (entry === 'button') {
      // Leaving a new, unsaved editor would correctly request discard confirmation.
      // Exercise its actual button directly, preserving the only source definition.
      if (!currentEditor) await selectEditor(id, wizard);
      else assert.equal(bot.currentWindow?.slots[47]?.name, 'bricks');
      await click(47);
    }
    else await chat('/customdungeon build ' + id);
    await until(hasTools, 'nine tools for ' + id, 30000);
    await until(() => { try { return !saved || h.definitionFingerprint(draft(id)) === saved; } catch { return false; } }, 'resume retains saved definition');
    assert.equal(bot.inventory.slots.filter(Boolean).length, 9, 'Only the nine build tools may remain');
    log('entered', { id, entry, tools: bot.inventory.slots.slice(36, 45).map(toolIndex),
      priorDraftFound: !!saved, savedDefinitionPreserved: saved ? true : null });
  }
  async function useTools(id) {
    // Room selector / creation (7), point cycle and placement (6), undo (8), editor (9).
    const originalDefinition = h.definitionFingerprint(draft(id));
    // Distinct positions make a point edit observable on every repetition.
    state.edits = (state.edits || 0) + 1; persist();
    await cmd('minecraft:tp ' + state.name + ' ' + (0.5 + state.edits * 2) + ' 100 0.5');
    await delay(300);
    const previousWindow = bot.currentWindow;
    bot.setQuickBarSlot(6); await delay(200); bot.activateItem();
    await until(() => bot.currentWindow !== previousWindow && h.roomMenuReady(bot.currentWindow), 'loaded room selector');
    log('room-selector-ready', { size: bot.currentWindow.inventoryStart,
      slots: bot.currentWindow.slots.slice(0, bot.currentWindow.inventoryStart).map(i => i?.name ?? null) });
    const room = bot.currentWindow.slots.slice(9, bot.currentWindow.inventoryStart - 9).findIndex(i => i?.name === 'oak_door');
    if (room < 0) {
      const w = bot.currentWindow;
      const create = w.slots.slice(w.inventoryStart - 9, w.inventoryStart).findIndex(i => i?.name === 'lime_dye');
      assert(create >= 0, 'Missing create-room control');
      await click(w.inventoryStart - 9 + create);
      await until(() => h.definitionFingerprint(draft(id)) !== originalDefinition, 'room creation persists');
    } else await click(room + 9);
    await closeWindow();
    const beforePoint = h.definitionFingerprint(draft(id));
    bot.setQuickBarSlot(5); await delay(200); bot.setControlState('sneak', true); await delay(200);
    bot.activateItem(); await delay(300); bot.setControlState('sneak', false); await delay(200);
    bot.activateItem(); await delay(500);
    await until(() => h.definitionFingerprint(draft(id)) !== beforePoint, 'point tool changes real draft');
    bot.setQuickBarSlot(7); await delay(200); bot.activateItem(); await delay(500);
    await until(() => h.definitionFingerprint(draft(id)) === beforePoint, 'undo restores previous definition');
    // Leave one real point edit in the private draft to detect a lost resume.
    bot.setQuickBarSlot(5); await delay(200); bot.activateItem(); await delay(500);
    await until(() => h.definitionFingerprint(draft(id)) !== beforePoint, 'saved edit remains for resume');
    bot.setQuickBarSlot(8); await delay(200); bot.activateItem();
    await until(() => bot.currentWindow?.slots[51]?.name === 'barrier', 'construction editor');
    log('tools-used', { id, tools: [7, 6, 8, 9], definitionFingerprint: h.definitionFingerprint(draft(id)) });
  }
  async function exit(kind) {
    if (kind === 'button') await click(51);
    else if (kind === 'command') await chat('/customdungeon build exit');
    else {
      const ended = new Promise(resolve => bot.once('end', resolve)); bot.quit(); await ended;
      await connect();
    }
    await until(() => h.inventoryMatches(bot, state.original), 'exact 46-slot inventory and selected slot restored');
    log('inventory-restored', { exit: kind, snapshot: h.inventorySnapshot(bot) });
  }
  function checkLog() {
    const text = fs.readFileSync(path.join(SERVER, 'logs/latest.log'), 'utf8');
    const errors = text.split('\n').filter(l => /generated an exception|Exception|\bERROR\b|Build mode entry failed/.test(l))
      // Mojang key lookup times out offline (online-mode=false); unrelated to the plugin.
      .filter(l => !/Minecraft Services Discovery|com\.mojang\.authlib|SocketTimeoutException/.test(l));
    assert.equal(errors.length, 0, 'Server exceptions found (see saved server log)');
  }
  process.on('SIGTERM', () => { shuttingDown = true; bot?.quit(); });
  try {
    assert(fs.existsSync(path.join(DATA, 'dungeons/coloso-abismal.yml')), 'Published coloso-abismal fixture is required');
    assert(!fs.existsSync(path.join(DATA, 'wizard-drafts/coloso-abismal.yml')), 'A wizard draft would shadow the published fixture; resolve it before the suite');
    if (phase === 'before') { persist(); await cmd('op ' + state.name); }
    await connect();
    if (phase === 'before') {
      await cmd('minecraft:tp ' + state.name + ' 0.5 100 0.5');
      await cmd('minecraft:give ' + state.name + ' diamond[custom_data={t51_original:1}] 7');
      await cmd('minecraft:give ' + state.name + ' iron_sword[damage=9,custom_data={t51_original:2}] 1');
      await cmd('minecraft:item replace entity ' + state.name + ' armor.head with iron_helmet[damage=3]');
      await cmd('minecraft:item replace entity ' + state.name + ' weapon.offhand with shield[damage=2]');
      await until(() => bot.inventory.slots.filter(Boolean).length === 4, 'seed items');
      bot.setQuickBarSlot(4); await delay(500); state.original = h.inventorySnapshot(bot); persist();
    } else {
      await until(() => h.inventoryMatches(bot, state.original), 'restart inventory recovery', 45000);
      log('restart-inventory-restored', h.inventorySnapshot(bot));
    }
    for (const scenario of h.SCENARIOS.filter(s => s.phase === phase)) {
      assert(!shuttingDown, 'Suite interrupted'); log('scenario-start', scenario);
      let id = 'coloso-abismal', wizard = false;
      if (scenario.source === 'editor-new') id = await newDefinition('editor', state.passed.length);
      else if (scenario.source === 'wizard-new') { id = await newDefinition('wizard', state.passed.length); wizard = true; }
      else if (scenario.source.includes('editor')) id = state.ids.editor;
      else if (scenario.source.includes('wizard')) { id = state.ids.wizard; wizard = true; }
      assert(id, 'Missing fixture for ' + scenario.source);
      if (scenario.source.startsWith('reload-')) {
        await enter(id, 'command', wizard); await useTools(id);
        await chat('/customdungeon reload');
        await until(() => h.inventoryMatches(bot, state.original), 'reload restores active inventory');
        log('reload-inventory-restored', h.inventorySnapshot(bot));
        // Publication completion is observable through a reopened main menu, without
        // assuming a duration for the Pi's asynchronous definition loading.
        for (let retry = 0; retry < 60; retry++) {
          await chat('/customdungeon'); if (bot.currentWindow?.inventoryStart === 27) break;
          await delay(500);
        }
        assert.equal(bot.currentWindow?.inventoryStart, 27, 'Reload did not complete');
      }
      await enter(id, scenario.entry, wizard, scenario.source === 'editor-new'); await useTools(id); await exit(scenario.exit);
      checkLog(); state.passed.push(scenario.name); persist(); log('scenario-pass', scenario.name);
    }
    checkLog(); fs.copyFileSync(path.join(SERVER, 'logs/latest.log'), path.join(resultDir, phase + '-server.log'));
    if (phase === 'before') {
      await enter('coloso-abismal', 'command'); await useTools('coloso-abismal');
      checkLog(); fs.copyFileSync(path.join(SERVER, 'logs/latest.log'), path.join(resultDir, phase + '-server.log'));
      restarting = true;
      persist(); fs.writeFileSync(path.join(resultDir, 'ready-to-restart'), state.name);
      log('restart-with-active-lease', state.name);
      await new Promise(resolve => bot.once('end', resolve));
      if (failure) throw failure;
    } else {
      assert.equal(state.passed.length, h.SCENARIOS.length); log('suite-pass', { scenarios: state.passed.length });
    }
  } catch (error) {
    log('suite-fail', { message: error.message });
    log('failure-client-state', { inventory: bot && h.inventorySnapshot(bot), window: bot?.currentWindow && {
      size: bot.currentWindow.inventoryStart, slots: bot.currentWindow.slots.map(i => i?.name ?? null)
    }});
    if (fs.existsSync(path.join(SERVER, 'logs/latest.log'))) fs.copyFileSync(path.join(SERVER, 'logs/latest.log'), path.join(resultDir, phase + '-server.log'));
    throw error;
  } finally { bot?.quit(); }
}
