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
        Fixture(DisconnectMode mode,boolean keep) {this(mode,keep,"");}
        Fixture(DisconnectMode mode,boolean keep,String respawnWorld) {
            super(respawnWorld);
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
    private static void realJoin(Fixture t) {
        var join=mock(org.bukkit.event.player.PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(t.player);t.listener.join(join);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void journalsRemainUntilAFreshLoginConfirmsThePersistedGeneration(boolean keepInventory) {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,keepInventory)) {
            t.killEvents();realJoin(t);
            verify(t.storage,never()).clearDisconnect(any(),any());
            verify(t.storage,never()).clearReturnTarget(any(),any());
            verify(t.storage,never()).takePendingExit(any());
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
            when(t.player.isDead()).thenReturn(false); // respawn does not confirm a vanilla save
            t.manager.connected(t.player); // plugin re-enable is also not a disk confirmation
            verify(t.storage,never()).clearDisconnect(any(),any());
            verify(t.player,times(1)).setHealth(0);
            t.manager.disconnected(t.player);realJoin(t);
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            verify(t.storage).clearReturnTarget(t.player.getUniqueId(),t.record.sessionId());
            assertEquals(JoinResult.DISABLED,t.manager.join(t.player,"missing"));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void roomEffectRecoveryRunsBeforeDisconnectPenaltyOnRealJoinAndReenable(boolean realLogin) {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var own=new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS,30,0,true,false,false);
            var foreign=new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED,600,2);
            var current=new HashMap<org.bukkit.potion.PotionEffectType,org.bukkit.potion.PotionEffect>();current.put(foreign.getType(),foreign);
            when(t.player.getPotionEffect(any())).thenAnswer(c->current.get(c.getArgument(0)));
            when(t.player.addPotionEffect(any())).thenAnswer(c->{org.bukkit.potion.PotionEffect effect=c.getArgument(0);current.put(effect.getType(),effect);return true;});
            doAnswer(c->{current.remove(c.getArgument(0));return null;}).when(t.player).removePotionEffect(any());
            new AmbienceEffects(t.player).apply(own);assertTrue(t.data.containsKey(new NamespacedKey("customdungeons","room_effects")));
            t.killEvents();if(realLogin)realJoin(t);else t.manager.connected(t.player);
            var order=inOrder(t.player);order.verify(t.player).removePotionEffect(own.getType());order.verify(t.player).setHealth(0);
            assertEquals(Map.of(foreign.getType(),foreign),current);
            assertFalse(t.data.containsKey(new NamespacedKey("customdungeons","room_effects")));
            assertEquals(t.record.id().toString(),t.data.get(new NamespacedKey("customdungeons","disconnect_applied")));
            verify(t.storage,never()).clearDisconnect(any(),any());
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        }
    }
    @Test void crashBeforeVanillaSaveReplaysTheStillDurablePenalty() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();realJoin(t);
            verify(t.storage,never()).clearDisconnect(any(),any());
            // Simulate reloading the old vanilla player-data after a crash before its save.
            t.data.clear();when(t.player.isDead()).thenReturn(false);realJoin(t);
            verify(t.player,times(2)).setHealth(0);
            verify(t.storage,never()).clearDisconnect(any(),any());
        }
    }
    @Test void confirmationIsCapturedBeforeTheAsynchronousDefinitionLoad() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var loaded=new CompletableFuture<Void>();when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(loaded);realJoin(t);
            t.data.put(new NamespacedKey("customdungeons","disconnect_applied"),t.record.id().toString());
            when(t.f.definitions.isReloading()).thenReturn(false);loaded.complete(null);
            verify(t.storage,never()).clearDisconnect(any(),any());
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        }
    }
    @Test void aPersistedMarkerFromAnotherGenerationCannotAcknowledgeThisPenalty() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.data.put(new NamespacedKey("customdungeons","disconnect_applied"),UUID.randomUUID().toString());
            t.killEvents();realJoin(t);verify(t.player).setHealth(0);
            verify(t.storage,never()).clearDisconnect(any(),any());
            assertEquals(t.record.id().toString(),t.data.get(new NamespacedKey("customdungeons","disconnect_applied")));
        }
    }
    @Test void failedConfirmationKeepsThePlayerBlockedAndNeverKillsTwice() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();realJoin(t);t.manager.disconnected(t.player);
            when(t.player.isDead()).thenReturn(false);
            when(t.storage.clearDisconnect(any(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("disk")));
            realJoin(t);verify(t.storage).clearDisconnect(any(),any());
            verify(t.player,times(1)).setHealth(0);
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        }
    }
    @Test void anOldAcknowledgementCannotReleaseANewerConnectionsGuard() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var acknowledgement=new CompletableFuture<Void>();
            when(t.storage.clearDisconnect(any(),any())).thenReturn(acknowledgement);
            t.data.put(new NamespacedKey("customdungeons","disconnect_applied"),t.record.id().toString());
            realJoin(t);t.manager.disconnected(t.player);
            t.data.clear();when(t.storage.disconnect(any())).thenReturn(new CompletableFuture<>());realJoin(t);
            acknowledgement.complete(null);
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        }
    }
    @Test void readFailureKeepsRecoveryBlockedWithoutConsumingItsJournal() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("disk")));
            realJoin(t);verify(t.storage,never()).clearDisconnect(any(),any());
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        }
    }
    @Test void constructionIsBlockedDuringJournalQueryDefinitionLoadChunkLoadAndPendingConfirmation() {
        for(int stage=0;stage<4;stage++)try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();
            if(stage==0)when(t.storage.disconnect(any())).thenReturn(new CompletableFuture<>());
            if(stage==1) {
                when(t.f.definitions.isReloading()).thenReturn(true);
                when(t.f.definitions.reloadCompletion()).thenReturn(new CompletableFuture<>());
            }
            if(stage==3) {
                when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
                when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(new CompletableFuture<>());
            }
            var plugin=mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
            when(plugin.sessionManager()).thenReturn(t.manager);
            when(plugin.messages()).thenReturn(mock(dev.dasan.customdungeons.text.Messages.class));
            when(t.player.hasPermission(anyString())).thenReturn(true);
            var journal=mock(dev.dasan.customdungeons.tool.construction.BuildJournal.class);
            var build=new dev.dasan.customdungeons.tool.BuildModeService(plugin,journal);
            try(var menus=mockStatic(dev.dasan.customdungeons.gui.MenuListener.class)) {
                menus.when(dev.dasan.customdungeons.gui.MenuListener::instance)
                        .thenReturn(mock(dev.dasan.customdungeons.gui.MenuListener.class));
                t.manager.connected(t.player);build.enter(t.player,"test");
                verify(journal,never()).backup(any());verify(journal,never()).save(any(),any());
                assertFalse(build.protects(t.player.getUniqueId()));
                verify(plugin.messages()).send(t.player,"build.recovery-pending");
            }
        }
    }
    @Test void penaltyDropsCannotMergeInEitherDirectionEvenAfterSessionCleanup() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var own=mock(org.bukkit.entity.Item.class);var foreign=mock(org.bukkit.entity.Item.class);
            var tagged=mock(PersistentDataContainer.class);var untagged=mock(PersistentDataContainer.class);
            when(own.getPersistentDataContainer()).thenReturn(tagged);when(foreign.getPersistentDataContainer()).thenReturn(untagged);
            when(tagged.has(new NamespacedKey("customdungeons","disconnect_drop"),PersistentDataType.STRING)).thenReturn(true);
            for(boolean source:List.of(true,false)) {
                var merge=mock(org.bukkit.event.entity.ItemMergeEvent.class);
                when(merge.getEntity()).thenReturn(source?own:foreign);when(merge.getTarget()).thenReturn(source?foreign:own);
                t.listener.merge(merge);verify(merge).setCancelled(true);
            }
            verify(foreign,never()).remove();
            var ordinary=mock(org.bukkit.event.entity.ItemMergeEvent.class);
            when(ordinary.getEntity()).thenReturn(foreign);when(ordinary.getTarget()).thenReturn(foreign);
            t.listener.merge(ordinary);verify(ordinary,never()).setCancelled(anyBoolean());
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
            verify(t.storage,never()).clearDisconnect(any(),any());realJoin(t);
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
            t.manager.connected(t.player);
            verify(t.storage,never()).clearDisconnect(any(),any());realJoin(t);t.assertReturned(99);
            verify(t.player,never()).setHealth(anyDouble());
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
        }
    }
    @Test void missingSavedWorldFallsBackToExitBeforeDeath() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var record=new DisconnectRecord(t.record.id(),t.record.player(),t.record.sessionId(),"test",
                    new Point("missing",2,64,3,0,0),t.record.exit(),t.record.mode(),false);
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(record)));
            t.killEvents();t.manager.connected(t.player);realJoin(t);t.assertReturned(99);verify(t.player).setHealth(0);
        }
    }
    @Test void respawnReplacesADungeonBedWithOutsideSpawnEvenAfterAnotherLogout() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();t.manager.connected(t.player);t.manager.disconnected(t.player);t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,1,64,1));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(argThat((Location at)->at.getX()==99));
            verify(t.player,times(1)).setHealth(0);
        }
    }
    @Test void respawnPreservesVanillaBedOutsideDungeon() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.isBedSpawn()).thenReturn(true);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,300,64,300));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(argThat((Location at)->at.getX()==300));
        }
    }
    @Test void noBedUsesExteriorExitEvenWhenVanillaDungeonSpawnIsOutsideRegions() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            var primary=mock(World.class);when(primary.getName()).thenReturn("primary");
            when(primary.getSpawnLocation()).thenReturn(new Location(primary,80,70,80));
            t.bukkit.when(Bukkit::getWorlds).thenReturn(List.of(primary,t.f.world));
            t.bukkit.when(()->Bukkit.getWorld("primary")).thenReturn(primary);
            t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,500,70,500));
            t.listener.respawn(respawn);
            verify(respawn).setRespawnLocation(argThat((Location at)->at.getWorld()==t.f.world && at.getX()==99));
        }
    }
    @Test void respawnPreservesValidVanillaAnchorOutsideDungeon() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();t.manager.connected(t.player);
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.isAnchorSpawn()).thenReturn(true);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,300,64,300));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(argThat((Location at)->at.getX()==300));
        }
    }
    @Test void alreadyDeadOnLoginArmsOutsideRespawnWithoutAnotherKillOrTeleport() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.player.isDead()).thenReturn(true);t.manager.connected(t.player);
            verify(t.player,never()).setHealth(anyDouble());verify(t.player,never()).teleport(any(Location.class));
            verify(t.storage,never()).clearDisconnect(any(),any());realJoin(t);
            verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,1,64,1));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(any());
        }
    }
    @Test void confirmedDeathScreenLoginKeepsItsOwnExitBeforeClearingTheJournal() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.player.isDead()).thenReturn(true);t.manager.connected(t.player);t.manager.disconnected(t.player);
            doReturn(new Point("world",700,70,700,0,0)).when(t.manager).outsideExit();
            realJoin(t);verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
            var respawn=mock(PlayerRespawnEvent.class);when(respawn.getPlayer()).thenReturn(t.player);
            when(respawn.getRespawnLocation()).thenReturn(new Location(t.f.world,1,64,1));
            t.listener.respawn(respawn);verify(respawn).setRespawnLocation(argThat((Location at)->at.getX()==99));
            verify(t.player,never()).setHealth(anyDouble());
        }
    }
    @Test void preparedRespawnChunkIsRetainedUntilRespawnAndReleasedOnShutdown() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.player.isDead()).thenReturn(true);
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);when(chunk.getX()).thenReturn(6);
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(CompletableFuture.completedFuture(chunk));
            t.manager.connected(t.player);verify(chunk).addPluginChunkTicket(t.f.plugin);
            verify(chunk,never()).removePluginChunkTicket(t.f.plugin);
            t.manager.shutdown();verify(chunk).removePluginChunkTicket(t.f.plugin);
        }
    }
    @Test void successfulAsyncChunkLoadPrecedesTheTeleportAndDeath() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            var load=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(0,0)).thenReturn(load);
            t.manager.connected(t.player);verify(t.player,never()).setHealth(anyDouble());
            var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(t.f.world);when(chunk.getX()).thenReturn(0);
            // The independently retained respawn exit is in chunk 6,0.
            load.complete(chunk);
            var order=inOrder(chunk,t.player);order.verify(chunk).addPluginChunkTicket(t.f.plugin);
            order.verify(t.player).teleport(any(Location.class));order.verify(t.player).setHealth(0);
            order.verify(chunk).removePluginChunkTicket(t.f.plugin);
        }
    }
    @Test void cancelledTeleportsRetainUnappliedRecord() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.player.teleport(any(Location.class))).thenReturn(false);t.manager.connected(t.player);
            verify(t.player,never()).setHealth(anyDouble());verify(t.storage,never()).clearDisconnect(any(),any());
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
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
            t.killEvents();realJoin(t);realJoin(t);
            verify(t.storage).clearDisconnect(any(),any());verify(t.player,times(1)).setHealth(0);
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
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
            var session=mock(DungeonSession.class);when(session.id()).thenReturn(t.record.sessionId());
            if(running) {
                var state=new SessionStateMachine();state.openLobby();state.start();
                when(session.state()).thenReturn(state);doReturn(Optional.of(session)).when(t.manager).byId(t.record.sessionId().toString());
            }
            var stack=mock(org.bukkit.inventory.ItemStack.class);when(stack.clone()).thenReturn(stack);
            var stackData=mock(PersistentDataContainer.class);var stackValues=new HashMap<NamespacedKey,String>();
            when(stack.getPersistentDataContainer()).thenReturn(stackData);
            when(stackData.get(any(),eq(PersistentDataType.STRING))).thenAnswer(c->stackValues.get(c.getArgument(0)));
            doAnswer(c->{stackValues.put(c.getArgument(0),c.getArgument(2));return null;}).when(stackData).set(any(),eq(PersistentDataType.STRING),anyString());
            doAnswer(c->{stackValues.remove(c.getArgument(0));return null;}).when(stackData).remove(any());
            when(stack.editPersistentDataContainer(any())).thenAnswer(c->{
                java.util.function.Consumer<PersistentDataContainer> edit=c.getArgument(0);edit.accept(stackData);return true;
            });
            when(item.getItemStack()).thenReturn(stack);
            var foreign=mock(org.bukkit.entity.Item.class);var foreignData=mock(PersistentDataContainer.class);
            when(foreign.getPersistentDataContainer()).thenReturn(foreignData);
            when(foreign.getWorld()).thenReturn(t.f.world);when(foreign.getLocation()).thenReturn(at);
            var foreignStack=mock(org.bukkit.inventory.ItemStack.class);
            when(foreignStack.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));when(foreign.getItemStack()).thenReturn(foreignStack);
            var death=mock(PlayerDeathEvent.class);when(death.getEntity()).thenReturn(t.player);
            when(death.getDrops()).thenReturn(new ArrayList<>(List.of(stack)));
            var spawn=mock(org.bukkit.event.entity.ItemSpawnEvent.class);when(spawn.getEntity()).thenReturn(item);
            var unrelated=mock(org.bukkit.event.entity.ItemSpawnEvent.class);when(unrelated.getEntity()).thenReturn(foreign);
            doAnswer(c->{t.listener.death(death);t.listener.drop(unrelated);t.listener.drop(spawn);when(t.player.isDead()).thenReturn(true);return null;}).when(t.player).setHealth(0);
            t.manager.connected(t.player);
            verify(itemData).set(dev.dasan.customdungeons.mob.MobKeys.SESSION,PersistentDataType.STRING,t.record.sessionId().toString());
            verify(itemData).set(new NamespacedKey("customdungeons","disconnect_drop"),PersistentDataType.STRING,t.record.id().toString());
            assertTrue(stackValues.isEmpty(),"The ownership bridge must not remain on collectible inventory items");
            verify(foreignData,never()).set(any(),any(),any());verify(foreign,never()).remove();
            if(running) {
                verify(item,never()).remove();
                when(itemData.get(dev.dasan.customdungeons.mob.MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(t.record.sessionId().toString());
                when(t.f.world.getEntities()).thenReturn(List.of(item,foreign));
                var runtime=t.f.runtime(t.manager,session);runtime.keys=mock(KeyService.class);
                runtime.finish(session);verify(item).remove();verify(foreign,never()).remove();
            } else verify(item).remove();
        }
    }
    @Test void hungSavedChunkTimesOutToExitAndIgnoresItsLateCompletion() throws Exception {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();doReturn(20L).when(t.manager).recoveryTimeoutMillis();
            var tasks=new ConcurrentLinkedQueue<Runnable>();doAnswer(c->{tasks.add(c.getArgument(0));return null;}).when(t.manager).main(any());
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenAnswer(c->(int)c.getArgument(0)==6);
            var hung=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(hung);
            var exitChunk=mock(Chunk.class);when(exitChunk.getWorld()).thenReturn(t.f.world);when(exitChunk.getX()).thenReturn(6);
            when(t.f.world.getChunkAtAsync(6,0)).thenReturn(CompletableFuture.completedFuture(exitChunk));
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void slowDefinitionPublicationOutlivesOperationTimeoutWithoutLosingRecovery(boolean penalty) throws Exception {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();
            if(!penalty) {
                when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
                when(t.storage.returnTarget(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
                when(t.storage.pendingExit(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            }
            var loaded=new CompletableFuture<Void>();
            when(t.f.definitions.reloadCompletion()).thenReturn(loaded);
            when(t.f.definitions.isReloading()).thenReturn(true);
            // Accelerate the production 10 s budget; publication is deliberately slower than it.
            doReturn(20L).when(t.manager).recoveryTimeoutMillis();
            var tasks=new ConcurrentLinkedQueue<Runnable>();
            doAnswer(c->{tasks.add(c.getArgument(0));return null;}).when(t.manager).main(any());
            realJoin(t);drain(tasks);
            Thread.sleep(100);drain(tasks);
            assertFalse(loaded.isDone(),"Waiting must not time out the publication future");
            assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
            assertEquals(JoinResult.RELOADING,t.manager.join(t.player,"test"));
            verify(t.storage,never()).disconnect(any());
            verify(t.storage,never()).takePendingExit(any());
            verify(t.player,never()).teleport(any(Location.class));
            verify(t.player,never()).setHealth(anyDouble());
            verify(t.logger,never()).warning(contains("definition load failed"));
            // Construction uses the same guard, before preparing a menu or touching inventory.
            when(t.f.plugin.sessionManager()).thenReturn(t.manager);
            var journal=mock(dev.dasan.customdungeons.tool.construction.BuildJournal.class);
            new dev.dasan.customdungeons.tool.BuildModeService(t.f.plugin,journal).enter(t.player,"test");
            verify(t.f.plugin.messages()).send(t.player,"build.recovery-pending");
            verifyNoInteractions(journal);verify(t.player,never()).getInventory();

            when(t.f.definitions.isReloading()).thenReturn(false);loaded.complete(null);
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"test"));
            drain(tasks);verify(t.storage).disconnect(t.player.getUniqueId());
            if(penalty) {
                verify(t.player).setHealth(0);verify(t.storage,never()).clearDisconnect(any(),any());
                assertTrue(t.manager.recoveryPending(t.player.getUniqueId()));
                // Only a later real login confirms and acknowledges the applied penalty.
                realJoin(t);drain(tasks);
                verify(t.storage).clearDisconnect(t.player.getUniqueId(),t.record.id());
                verify(t.player,times(1)).setHealth(0);
            } else {
                verify(t.player,never()).setHealth(anyDouble());
                verify(t.player,never()).teleport(any(Location.class));
            }
            assertFalse(t.manager.recoveryPending(t.player.getUniqueId()));
            assertEquals(JoinResult.DISABLED,t.manager.join(t.player,"missing"));
        }
    }
    private static void drain(Queue<Runnable> tasks) {
        Runnable task;while((task=tasks.poll())!=null)task.run();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void publicationAfterDisconnectOrShutdownCannotApplyPenalty(boolean shutdown) {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            t.killEvents();var loaded=new CompletableFuture<Void>();
            when(t.f.definitions.reloadCompletion()).thenReturn(loaded);
            when(t.f.definitions.isReloading()).thenReturn(true);realJoin(t);
            if(shutdown)t.manager.shutdown();else t.manager.disconnected(t.player);
            when(t.f.definitions.isReloading()).thenReturn(false);loaded.complete(null);
            verify(t.player,never()).setHealth(anyDouble());
            verify(t.storage,never()).disconnect(any());verify(t.storage,never()).clearDisconnect(any(),any());
        }
    }
    @Test void failedDefinitionLoadDoesNotApplyAnUnverifiablePenaltyOrConsumeItsRecord() {
        try(var t=new Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.f.definitions.isReloading()).thenReturn(true);
            when(t.f.definitions.reloadCompletion()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("load failed")));
            t.manager.connected(t.player);verify(t.player,never()).setHealth(anyDouble());
            verify(t.storage,never()).clearDisconnect(any(),any());
            verify(t.storage).returnTarget(t.player.getUniqueId());
            assertEquals(JoinResult.RELOADING,t.manager.join(t.player,"missing"));
            when(t.f.definitions.isReloading()).thenReturn(false);
            assertEquals(JoinResult.RESETTING,t.manager.join(t.player,"missing"));
        }
    }
}
