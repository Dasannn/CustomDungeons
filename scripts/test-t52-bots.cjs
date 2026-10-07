'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
let imported = false;
process.on('exit', () => { if (!imported) { console.error('Runner must be importable without exiting the test process'); process.exitCode = 1; } });
const h = require('./t52-bots.cjs');
imported = true;

test('fixtures use the loaded laboratory world and the codec enum, without scaling or cinematics', () => {
  assert.equal(h.DEFAULT_WORLD, 'cd_dungeons');
  const fixture = h.dungeonFixture('t52-test', h.DEFAULT_WORLD, 24000, 24000);
  assert.equal(h.dimension('cd_dungeons'), 'minecraft:cd_dungeons');
  assert.equal(fixture.lobby.world, 'cd_dungeons');
  assert.equal(fixture.rooms[0].region.world, 'cd_dungeons');
  assert.equal(fixture.rooms[0].spawners[0].waves[0].mode, 'SIMULTANEOUS');
  assert.equal(fixture.rooms[0].spawners[0].waves[0].entries.length, 3);
  assert.equal(fixture['intro-cinematic'], false);
  assert.deepEqual(fixture.scaling, {'extra-mobs-per-player': 0, 'extra-health-per-player': 0});
});
test('the fixture needs every corner loaded, not just the southeast chunk', () => {
  assert.equal(h.fixtureChunksReady(() => null, 24000, 24000), false);
  assert.equal(h.fixtureChunksReady(p => p.x === 24008 && p.z === 24008 ? {} : null, 24000, 24000), false);
  assert.equal(h.fixtureChunksReady(() => ({name: 'air'}), 24000, 24000), true);
});
test('log checks ignore only the offline Mojang discovery timeout record', () => {
  const timeout = '[11:01:00] [Minecraft Services Discovery/ERROR]: Could not get public keys\ncom.mojang.authlib.exceptions.MinecraftClientException: Failed\nCaused by: java.net.SocketTimeoutException: Read timed out\n';
  assert.deepEqual(h.serverErrors(timeout), []);
  assert.equal(h.serverErrors(timeout + '[11:01:01] [Server thread/ERROR]: Plugin failure\njava.lang.IllegalStateException: broken\n').length, 1);
  assert.equal(h.serverErrors('[11:01:00] [Minecraft Services Discovery/ERROR]: Failure\njava.lang.IllegalStateException: broken\n').length, 1);
  assert.equal(h.serverErrors('[11:01:00] [Server thread/ERROR]: java.net.SocketTimeoutException: unrelated\n').length, 1);
});
