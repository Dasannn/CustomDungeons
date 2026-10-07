package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisconnectRecoveryTest {
    static class Fixture extends ReturnRecoveryRegressionTest.Fixture {
        final PersistentDataContainer pdc=mock(PersistentDataContainer.class);
        final Map<NamespacedKey,Object> data=new HashMap<>();
        final DisconnectRecord record;
        final SessionListener listener;
        final List<org.bukkit.inventory.ItemStack> drops=new ArrayList<>();
        PlayerDeathEvent death;
        Fixture(DisconnectMode mode,boolean keep) {
            record=new DisconnectRecord(UUID.randomUUID(),player.getUniqueId(),UUID.randomUUID(),"test",
                    new Point("world",2,64,3,0,0),target.exit(),mode,keep);
            when(f.world.getMinHeight()).thenReturn(-64);when(f.world.getMaxHeight()).thenReturn(320);
            when(player.getWorld()).thenReturn(f.world);
            when(storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(record)));
            when(storage.clearDisconnect(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            when(player.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(any(),any())).thenAnswer(c->data.get(c.getArgument(0)));
            when(pdc.has(any(),any())).thenAnswer(c->data.containsKey(c.getArgument(0)));
            doAnswer(c->{data.put(c.getArgument(0),c.getArgument(2));return null;}).when(pdc).set(any(),any(),any());
            doAnswer(c->{data.remove(c.getArgument(0));return null;}).when(pdc).remove(any());
            try {SessionRuntimeRegressionTest.field(manager,"disconnects",new DisconnectService(storage,manager));}
            catch(Exception error){throw new AssertionError(error);}
            listener=new SessionListener(manager);
        }
        void killEvents() {
            death=mock(PlayerDeathEvent.class);when(death.getEntity()).thenReturn(player);when(death.getDrops()).thenReturn(drops);
            doAnswer(c->{listener.death(death);when(player.isDead()).thenReturn(true);return null;}).when(player).setHealth(0);
        }
    }
    @Test void deathAtSavedPositionWhileOriginalGameContinues() throws Exception {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var original=new DungeonSession(t.f.definitions.dungeons().get("test"),false,new SessionServices() {});
            var teammate=mock(Player.class);when(teammate.getUniqueId()).thenReturn(UUID.randomUUID());
            original.join(teammate);original.forceStart();
            SessionRuntimeRegressionTest.field(t.manager,"sessions",new HashMap<>(Map.of("test",original)));
            t.killEvents();t.manager.connected(t.player);
            assertEquals(SessionState.RUNNING,original.state().state());assertEquals(Set.of(teammate.getUniqueId()),original.survivors());
            var order=inOrder(t.player,t.storage);
            order.verify(t.player).teleport(argThat((Location at)->at.getX()==2 && at.getZ()==3));
            order.verify(t.player).setHealth(0);
            order.verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            verify(t.death).setKeepInventory(false);verify(t.death).setKeepLevel(false);
            verify(t.storage).clearReturnTarget(t.player.getUniqueId(),t.record.sessionId());
            verify(t.player,never()).teleport(argThat((Location at)->at.getX()==20));
        }
    }
    @Test void deathAlsoAppliesWithNoOriginalSessionAndRespectsKeepInventory() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,true)) {
            t.killEvents();t.drops.add(mock(org.bukkit.inventory.ItemStack.class));
            assertTrue(t.manager.session("test").isEmpty());t.manager.connected(t.player);
            verify(t.player).setHealth(0);verify(t.death).setKeepInventory(true);
            verify(t.death).setKeepLevel(true);verify(t.death).setDroppedExp(0);assertTrue(t.drops.isEmpty());
        }
    }
    @Test void returnModeUsesExitEvenWhenPreviousDestinationWasConfigured() {
        try(var t=new Fixture(DisconnectMode.RETURN_TO_EXIT,false)) {
            t.manager.connected(t.player);t.assertReturned(99);
            verify(t.player,never()).setHealth(anyDouble());
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
        }
    }
    @Test void missingSavedWorldFallsBackToExitBeforeDeath() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var record=new DisconnectRecord(t.record.id(),t.record.player(),t.record.sessionId(),"test",
                    new Point("missing",2,64,3,0,0),t.record.exit(),t.record.mode(),false);
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(record)));
            t.killEvents();t.manager.connected(t.player);t.assertReturned(99);verify(t.player).setHealth(0);
        }
    }
    @Test void respawnReplacesADungeonBedWithOutsideSpawnEvenAfterAnotherLogout() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();t.manager.connected(t.player);t.manager.disconnected(t.player);t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,1,64,1));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(argThat((Location at)->at.getX()==500));
            verify(t.player,times(1)).setHealth(0);
        }
    }
    @Test void respawnPreservesVanillaBedOutsideDungeon() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,300,64,300));
            t.listener.respawn(respawn);verify(respawn,never()).setRespawnLocation(any());
        }
    }
    @Test void alreadyDeadOnLoginArmsOutsideRespawnWithoutAnotherKillOrTeleport() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.player.isDead()).thenReturn(true);t.manager.connected(t.player);
            verify(t.player,never()).setHealth(anyDouble());verify(t.player,never()).teleport(any(Location.class));
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,1,64,1));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(any());
        }
    }
    @Test void successfulAsyncChunkLoadPrecedesTheTeleportAndDeath() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            var load=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(0,0)).thenReturn(load);
            t.manager.connected(t.player);verify(t.player,never()).setHealth(anyDouble());
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);load.complete(chunk);
            var order=inOrder(chunk,t.player);order.verify(chunk).addPluginChunkTicket(t.f.plugin);
            order.verify(t.player).teleport(any(Location.class));order.verify(t.player).setHealth(0);
            order.verify(chunk).removePluginChunkTicket(t.f.plugin);
        }
    }
    @Test void cancelledTeleportsRetainUnappliedRecord() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.player.teleport(any(Location.class))).thenReturn(false);t.manager.connected(t.player);
            verify(t.player,never()).setHealth(anyDouble());verify(t.storage,never()).clearDisconnect(any(),any());
            assertEquals(JoinResult.DISABLED,t.manager.join(t.player,"missing"));
        }
    }
    @Test void aLateChunkCompletionCannotKillADisconnectedOrNewConnection() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            var loaded=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(loaded);
            t.manager.connected(t.player);t.manager.disconnected(t.player);loaded.complete(mock(Chunk.class));
            verify(t.player,never()).setHealth(anyDouble());verify(t.storage,never()).clearDisconnect(any(),any());
        }
    }
    @Test void appliedGenerationDoesNotKillTwiceWhenDeletionFailed() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.clearDisconnect(any(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("disk")));
            t.killEvents();t.manager.connected(t.player);t.manager.connected(t.player);
            verify(t.player,times(1)).setHealth(0);
        }
    }
    @Test void cancelledDeathRetainsTheRecordWithoutArmingAnUnrelatedRespawn() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();
            doAnswer(c->{t.listener.death(t.death);return null;}).when(t.player).setHealth(0);
            t.manager.connected(t.player);
            assertFalse(t.data.containsKey(new NamespacedKey("customdungeons","disconnect_respawn")));
            verify(t.storage,never()).clearDisconnect(any(),any());
        }
    }
    @Test void deathDropsBelongToOriginalSessionAndLateDropsAreCleaned() {
        for(boolean running:List.of(true,false))try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var item=mock(org.bukkit.entity.Item.class);var itemData=mock(PersistentDataContainer.class);
            when(item.getPersistentDataContainer()).thenReturn(itemData);when(item.getWorld()).thenReturn(t.f.world);
            var at=t.player.getLocation();when(item.getLocation()).thenReturn(at);
            var uuid=t.player.getUniqueId();t.bukkit.when(()->Bukkit.getPlayer(uuid)).thenReturn(t.player);
            if(running) {
                var session=mock(DungeonSession.class);var state=new SessionStateMachine();state.openLobby();state.start();
                when(session.state()).thenReturn(state);doReturn(Optional.of(session)).when(t.manager).byId(t.record.sessionId().toString());
            }
            var death=mock(PlayerDeathEvent.class);when(death.getEntity()).thenReturn(t.player);when(death.getDrops()).thenReturn(new ArrayList<>());
            var spawn=mock(org.bukkit.event.entity.ItemSpawnEvent.class);when(spawn.getEntity()).thenReturn(item);
            doAnswer(c->{t.listener.death(death);t.listener.drop(spawn);when(t.player.isDead()).thenReturn(true);return null;}).when(t.player).setHealth(0);
            t.manager.connected(t.player);
            verify(itemData).set(dev.dasan.customdungeons.mob.MobKeys.SESSION,PersistentDataType.STRING,t.record.sessionId().toString());
            if(running)verify(item,never()).remove();else verify(item).remove();
        }
    }
    @Test void hungSavedChunkTimesOutToExitAndIgnoresItsLateCompletion() throws Exception {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();doReturn(20L).when(t.manager).recoveryTimeoutMillis();
            var tasks=new ConcurrentLinkedQueue<Runnable>();doAnswer(c->{tasks.add(c.getArgument(0));return null;}).when(t.manager).main(any());
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==6);
            var hung=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(hung);
            t.manager.connected(t.player);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(System.nanoTime()<deadline && !t.player.isDead()) {
                Runnable task;while((task=tasks.poll())!=null)task.run();Thread.sleep(2);
            }
            assertTrue(t.player.isDead());verify(t.player).teleport(argThat((Location at)->at.getX()==99));
            hung.complete(mock(Chunk.class));Runnable task;while((task=tasks.poll())!=null)task.run();
            verify(t.player,times(1)).setHealth(0);
        }
    }
    @Test void dungeonDropRuleOverridesVanillaKeepInventoryWithoutLosingTheItems() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();when(t.death.getKeepInventory()).thenReturn(true);
            var inventory=mock(org.bukkit.inventory.PlayerInventory.class);when(t.player.getInventory()).thenReturn(inventory);
            var item=mock(org.bukkit.inventory.ItemStack.class);var material=mock(Material.class);
            when(item.getType()).thenReturn(material);when(item.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(item.clone()).thenReturn(item);
            when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[]{item,null});
            t.manager.connected(t.player);assertEquals(List.of(item),t.drops);verify(t.death).setKeepInventory(false);
        }
    }
    @Test void initialDefinitionLoadFinishesBeforeApplyingThePenalty() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var loaded=new CompletableFuture<Void>();when(t.f.definitions.reloadCompletion()).thenReturn(loaded);
            when(t.f.definitions.isReloading()).thenReturn(true);t.manager.connected(t.player);
            verify(t.player,never()).setHealth(anyDouble());
            when(t.f.definitions.isReloading()).thenReturn(false);loaded.complete(null);verify(t.player).setHealth(0);
        }
    }
    @Test void failedDefinitionLoadDoesNotApplyAnUnverifiablePenaltyOrConsumeItsRecord() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("load failed")));
            t.manager.connected(t.player);verify(t.player,never()).setHealth(anyDouble());
            verify(t.storage,never()).clearDisconnect(any(),any());
            verify(t.storage).returnTarget(t.player.getUniqueId());
        }
    }
}
