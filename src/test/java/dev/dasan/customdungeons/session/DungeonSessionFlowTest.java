package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.RunResult;
import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonSessionFlowTest {
    final UUID p1 = UUID.randomUUID();
    final Player player = mock(Player.class);
    final List<RunResult> results = new ArrayList<>();
    final List<Set<UUID>> survivors = new ArrayList<>();
    DungeonDef definition(int lives) { return definition(lives,false); }
    DungeonDef definition(int lives, boolean keepInventory) {
        var point = new Point("world", 0, 64, 0, 0, 0);
        var region = Region.of("world", new BlockPos(0, 60, 0), new BlockPos(4, 70, 4));
        var room = new RoomDef("first",region,point,region,UnlockMode.AUTOMATIC,null,List.of());
        var last = new RoomDef("last",region,point,null,UnlockMode.AUTOMATIC,null,List.of());
        return new DungeonDef("test","",true,point,new Point("world",99,64,0,0,0),1,2,0,lives,keepInventory,0,0,false,
            new ScalingDef(0.25,0.15),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room,last));
    }
    DungeonSession session(int lives) {
        when(player.getUniqueId()).thenReturn(p1);
        var s = new DungeonSession(definition(lives),false,new SessionServices() {});
        s.addListener(new SessionLifecycleListener() {
            public void onFinished(DungeonSession s, RunResult result, Set<UUID> alive) {
                results.add(result); survivors.add(alive);
            }
        });
        return s;
    }
    @Test void skipWaveZerosVirtualHealthBeforeNativeDeath() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var session=session(3);session.join(player);session.tick();session.enterRoom(0);
        when(player.hasPermission("customdungeons.admin.debug")).thenReturn(true);
        var entity=mock(org.bukkit.entity.Mob.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        var data=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(data);
        var key=dev.dasan.customdungeons.mob.MobKeys.VIRTUAL_HEALTH;
        var maxKey=dev.dasan.customdungeons.mob.MobKeys.VIRTUAL_MAX_HEALTH;
        var type=org.bukkit.persistence.PersistentDataType.DOUBLE;
        double[] hp={5000};
        when(data.has(maxKey,type)).thenReturn(true);
        when(data.get(maxKey,type)).thenReturn(5000d);
        when(data.get(key,type)).thenAnswer(c->hp[0]);
        doAnswer(c->{hp[0]=c.getArgument(2);return null;}).when(data).set(eq(key),eq(type),anyDouble());
        doAnswer(c->{assertEquals(0,hp[0],"PDC must be terminal before Paper dispatches death");return null;})
                .when(entity).setHealth(0);
        var template=new MobTemplate("zombie","ZOMBIE","",5000,1,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        session.track(new dev.dasan.customdungeons.runtime.ActiveMob(entity,template,session),"spawner");
        session.skipWave();
        verify(entity).setHealth(0);assertEquals(0,hp[0]);
    }
    @Test void testInvulnerabilityCancelsDamageAndIsRemovedOnLeave() {
        when(player.getUniqueId()).thenReturn(p1);
        var session=new DungeonSession(definition(3),true,new SessionServices() {});
        session.join(player); session.setInvulnerable(p1,true);
        assertTrue(session.isTestInvulnerable(p1));
        var manager=mock(SessionManager.class); when(manager.sessionOf(p1)).thenReturn(Optional.of(session));
        var listener=new SessionListener(manager);
        var damage=mock(org.bukkit.event.entity.EntityDamageEvent.class); when(damage.getEntity()).thenReturn(player);
        listener.protectTestAdmin(damage); verify(damage).setCancelled(true);
        session.setInvulnerable(p1,false); assertFalse(session.isTestInvulnerable(p1));
        var unprotected=mock(org.bukkit.event.entity.EntityDamageEvent.class); when(unprotected.getEntity()).thenReturn(player);
        listener.protectTestAdmin(unprotected); verify(unprotected,never()).setCancelled(true);
        session.setInvulnerable(p1,true); session.leave(p1); assertFalse(session.isTestInvulnerable(p1));
        verify(player,atLeastOnce()).setInvulnerable(false);
    }
    @Test void ordinaryParticipantCannotEnableTestProtectionButDebugAdminCan() {
        var session=session(3); session.join(player); session.setInvulnerable(p1,true);
        assertFalse(session.isTestInvulnerable(p1));
        when(player.hasPermission("customdungeons.admin.debug")).thenReturn(true);
        session.setInvulnerable(p1,true); assertTrue(session.isTestInvulnerable(p1));
        session.finish(false); assertFalse(session.isTestInvulnerable(p1));
    }
    @Test void lobbyThroughTwoRoomsCompletesWithSurvivor() {
        var s = session(3);
        assertEquals(JoinResult.OK,s.join(player));
        assertEquals(SessionState.LOBBY,s.state().state());
        s.tick();
        assertEquals(SessionState.RUNNING,s.state().state());
        s.enterRoom(0); s.tick();
        assertEquals(1,s.roomIndex());
        assertFalse(s.roomStarted());
        s.enterRoom(1); s.tick();
        assertEquals(List.of(RunResult.COMPLETED),results);
        assertEquals(Set.of(p1),survivors.getFirst());
        assertEquals(SessionState.FREE,s.state().state());
    }
    @Test void deathWithoutLivesEliminates() {
        var s = session(1); s.join(player); s.tick(); s.playerDied(p1);
        assertEquals(0,s.livesLeft(p1));
        assertFalse(s.survivors().contains(p1));
        assertEquals(List.of(RunResult.FAILED),results);
    }
    @Test void allLeaveFails() {
        var s = session(3); s.join(player); s.leave(p1);
        assertEquals(List.of(RunResult.FAILED),results);
        assertEquals(Set.of(),survivors.getFirst());
    }
    @Test void joinIsIdempotent() {
        var s = session(3);
        assertEquals(JoinResult.OK,s.join(player));
        assertEquals(JoinResult.ALREADY_IN,s.join(player));
        assertEquals(1,s.survivors().size());
        assertEquals(3,s.livesLeft(p1));
    }
    @Test void queuedSpawnsCountAsAliveAndAnyRemovalClearsRoom() {
        var base=definition(3);
        var spawner=new SpawnerDef("sp",base.lobby(),0,List.of(new WaveDef(List.of(new WaveEntry("zombie",3,0)),SpawnMode.SIMULTANEOUS,0,0)));
        var room=base.rooms().getFirst();
        var def=new DungeonDef(base.id(),"",true,base.lobby(),base.exit(),1,2,0,3,false,0,0,false,base.scaling(),Map.of(),base.reward(),
            List.of(new RoomDef(room.id(),room.region(),room.checkpoint(),room.door(),room.unlock(),null,List.of(spawner)),base.rooms().getLast()));
        List<dev.dasan.customdungeons.runtime.ActiveMob> spawned=new ArrayList<>();
        int[] removed={0};
        var services=new SessionServices() {
            public dev.dasan.customdungeons.runtime.ActiveMob spawn(DungeonSession session,String template,org.bukkit.Location at) {
                var entity=mock(org.bukkit.entity.Mob.class);
                when(entity.getUniqueId()).thenReturn(UUID.randomUUID()); when(entity.isValid()).thenReturn(true);
                var mob=new dev.dasan.customdungeons.runtime.ActiveMob(entity,new MobTemplate(template,"ZOMBIE","",20,1,0.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false),session);
                spawned.add(mob); return mob;
            }
            public void removed(DungeonSession session,dev.dasan.customdungeons.runtime.ActiveMob mob,org.bukkit.event.Event event) { removed[0]++; }
        };
        var s=new DungeonSession(def,false,services); s.maxAlive(1);
        when(player.getUniqueId()).thenReturn(p1); s.join(player); s.tick(); s.enterRoom(0); s.tick();
        assertEquals(1,spawned.size()); assertEquals(0,s.roomIndex());
        for (int i=0;i<3;i++) {
            var mob=spawned.get(i); s.mobRemoved(mob.entity().getUniqueId(),null);
            s.mobRemoved(mob.entity().getUniqueId(),null); // death plus Paper removal must count once
            s.tick();
            assertEquals(1,s.mobs().size()+ (i==2 ? 1 : 0));
        }
        assertEquals(3,spawned.size()); assertEquals(3,removed[0]); assertEquals(1,s.roomIndex());
    }
    @Test void failedLifecycleObserverDoesNotPreventCleanup() {
        var s=session(3);
        s.addListener(new SessionLifecycleListener() {
            public void onFinished(DungeonSession session,RunResult result,Set<UUID> survivors) { throw new IllegalStateException("observer"); }
        });
        s.join(player);
        assertDoesNotThrow(() -> s.leave(p1));
        assertEquals(SessionState.FREE,s.state().state());
    }

    @Test void tempBlocksWaitForJournalAndExpireFromPlacementTick() {
        var storage=mock(dev.dasan.customdungeons.storage.Storage.class);
        var block=mock(org.bukkit.block.Block.class);
        var world=mock(org.bukkit.World.class);
        var original=mock(org.bukkit.block.data.BlockData.class);
        var replacement=mock(org.bukkit.block.data.BlockData.class);
        when(block.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world");
        when(block.isEmpty()).thenReturn(true); when(block.getBlockData()).thenReturn(original);
        when(original.clone()).thenReturn(original); when(replacement.clone()).thenReturn(replacement);
        when(original.getAsString()).thenReturn("minecraft:air");
        when(replacement.getAsString()).thenReturn("minecraft:cobweb");
        var state=new java.util.concurrent.atomic.AtomicReference<>(original);
        when(block.getBlockData()).thenAnswer(i->state.get());
        doAnswer(i->{state.set(i.getArgument(0));return null;}).when(block).setBlockData(any(),eq(false));
        var saved=new java.util.concurrent.CompletableFuture<Void>();
        when(storage.addTempBlock(any())).thenReturn(saved);
        when(storage.markTempBlockRestored(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var temp=new SessionTempBlocks(storage,e -> fail(e));
        assertTrue(temp.place(block,replacement,2));
        temp.tick(10); verify(block,never()).setBlockData(any(),anyBoolean());
        saved.complete(null); temp.tick(11); verify(block).setBlockData(replacement,false);
        temp.tick(12); verify(block,never()).setBlockData(original,false);
        temp.tick(13); verify(block).setBlockData(original,false);
        verify(storage).markTempBlockRestored("world",0,0,0);
    }
    @Test void resettingBeforeJournalCompletesOrdersRestoredMarkerAfterInsertion() {
        var storage=mock(dev.dasan.customdungeons.storage.Storage.class);
        var block=mock(org.bukkit.block.Block.class); var world=mock(org.bukkit.World.class);
        var data=mock(org.bukkit.block.data.BlockData.class);
        when(block.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world");
        when(block.isEmpty()).thenReturn(true); when(block.getBlockData()).thenReturn(data);
        when(data.clone()).thenReturn(data); when(data.getAsString()).thenReturn("minecraft:air");
        var saved=new java.util.concurrent.CompletableFuture<Void>();
        when(storage.addTempBlock(any())).thenReturn(saved);
        when(storage.markTempBlockRestored(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var temp=new SessionTempBlocks(storage,e -> fail(e)); temp.place(block,data,Integer.MAX_VALUE); temp.restoreAll();
        verify(storage,never()).markTempBlockRestored(anyString(),anyInt(),anyInt(),anyInt());
        saved.complete(null); temp.tick(20);
        verify(storage).markTempBlockRestored("world",0,0,0); verify(block,never()).setBlockData(any(),anyBoolean());
    }

    @Test void tickerAlwaysAdvancesBossTransitionsIncludingFailureTick() {
        var plugin=mock(org.bukkit.plugin.Plugin.class); var server=mock(org.bukkit.Server.class);
        var scheduler=mock(org.bukkit.scheduler.BukkitScheduler.class); var task=mock(org.bukkit.scheduler.BukkitTask.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var session=mock(DungeonSession.class); var bosses=mock(dev.dasan.customdungeons.mob.BossController.class);
        var clock=mock(dev.dasan.customdungeons.runtime.TickScheduler.class);
        when(session.scheduler()).thenReturn(clock); when(clock.currentTick()).thenReturn(7L);
        when(session.def()).thenReturn(definition(3));
        var callback=org.mockito.ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.runTaskTimer(eq(plugin),callback.capture(),eq(1L),eq(1L))).thenReturn(task);
        new SessionTicker(plugin,session,bosses).start();
        callback.getValue().run(); verify(bosses).tickMusic(7L);
        doThrow(new IllegalStateException("tick failure")).when(session).tick();
        callback.getValue().run(); verify(bosses,times(2)).tickMusic(7L); verify(session).finish(false);
    }

    @Test void crossSessionResetCannotMarkNewBlockJournalRestored() {
        var storage=mock(dev.dasan.customdungeons.storage.Storage.class);
        var block=mock(org.bukkit.block.Block.class); var world=mock(org.bukkit.World.class);
        var data=mock(org.bukkit.block.data.BlockData.class);
        when(block.getWorld()).thenReturn(world); when(world.getName()).thenReturn("world");
        when(block.isEmpty()).thenReturn(true); when(block.getBlockData()).thenReturn(data);
        when(data.clone()).thenReturn(data); when(data.getAsString()).thenReturn("minecraft:air");
        var firstSave=new java.util.concurrent.CompletableFuture<Void>();
        var firstDelete=new java.util.concurrent.CompletableFuture<Void>();
        var secondSave=new java.util.concurrent.CompletableFuture<Void>();
        when(storage.addTempBlock(any())).thenReturn(firstSave,secondSave);
        when(storage.markTempBlockRestored(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(firstDelete,java.util.concurrent.CompletableFuture.completedFuture(null));
        var journal=new SessionTempBlocks.Journal();
        var first=new SessionTempBlocks(storage,e -> fail(e),journal);
        var second=new SessionTempBlocks(storage,e -> fail(e),journal);
        assertTrue(first.place(block,data,2));
        assertFalse(second.place(block,data,2)); // pending reservation is exclusive
        first.restoreAll(); assertTrue(second.place(block,data,2));
        verify(storage,times(1)).addTempBlock(any());
        firstSave.complete(null);
        verify(storage,times(1)).addTempBlock(any());
        firstDelete.complete(null);
        var order=inOrder(storage);
        order.verify(storage).addTempBlock(any());
        order.verify(storage).markTempBlockRestored("world",0,0,0);
        order.verify(storage).addTempBlock(any());
        secondSave.complete(null); second.tick(10);
        verify(block).setBlockData(data,false);
        second.restoreAll(); second.flushOnDisable();
    }

    @Test void deathScreenAfterSessionEndsRespawnsAtExit() throws Exception {
        var s=new DungeonSession(definition(3,true),false,new SessionServices() {});
        when(player.getUniqueId()).thenReturn(p1); s.join(player); s.tick();
        var manager=mock(SessionManager.class); when(manager.sessionOf(p1)).thenReturn(Optional.of(s));
        var runtime=mock(DungeonSessionRuntime.class);
        SessionRuntimeRegressionTest.field(runtime,"ambience",mock(SessionAmbience.class));
        when(manager.runtime(s)).thenReturn(runtime);
        var listener=new SessionListener(manager);
        var death=mock(org.bukkit.event.entity.PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player); when(death.getDrops()).thenReturn(new ArrayList<>());
        listener.death(death);
        assertEquals(2,s.livesLeft(p1)); assertEquals(s.checkpoint(),listener.pendingRespawn(p1));
        s.finish(false); when(manager.sessionOf(p1)).thenReturn(Optional.empty());
        assertEquals(s.def().exit(),listener.pendingRespawn(p1));
    }

    @Test void unloadedWorldRejectsBeforeLobbyCanBeOpened() {
        assertTrue(SessionManager.worldsReady(definition(3),"world"::equals));
        assertFalse(SessionManager.worldsReady(definition(3),world -> false));
    }

    @Test void projectileFromRemovedCasterCannotDamageOutsider() {
        var s=session(3); s.join(player); s.tick();
        var manager=mock(SessionManager.class); when(manager.byId(s.id().toString())).thenReturn(Optional.of(s));
        var caster=mock(org.bukkit.entity.Mob.class); var data=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(caster.getPersistentDataContainer()).thenReturn(data);
        when(data.get(dev.dasan.customdungeons.mob.MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(s.id().toString());
        var projectile=mock(org.bukkit.entity.Projectile.class); when(projectile.getShooter()).thenReturn(caster);
        var outsider=mock(Player.class); when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());
        var hit=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(hit.getDamager()).thenReturn(projectile); when(hit.getEntity()).thenReturn(outsider);
        new SessionListener(manager).hit(hit);
        verify(hit).setCancelled(true);
    }

    @Test void clearedKeyRoomCannotRestartWhileWaitingForDoor() {
        when(player.getUniqueId()).thenReturn(p1);
        var s=new DungeonSession(definition(3),false,new SessionServices() {
            @Override public void roomCleared(DungeonSession session) { }
        });
        s.join(player); s.tick(); s.enterRoom(0); s.tick();
        assertFalse(s.roomStarted());
        s.enterRoom(0);
        assertFalse(s.roomStarted());
        s.openDoor(); s.enterRoom(1);
        assertTrue(s.roomStarted());
    }

}
