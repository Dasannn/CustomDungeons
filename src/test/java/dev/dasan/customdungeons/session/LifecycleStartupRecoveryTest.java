package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleStartupRecoveryTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void startupAndLateChunksReturnPlayersWithoutPenaltyAndRemoveOnlyOwnedRemains(boolean modern) throws Exception {
        var lifecycle=new LootZoneLifecycleTest();var def=lifecycle.definition(modern);
        var interrupted=lifecycle.start(def,lifecycle.zones.player(1));lifecycle.activate(interrupted,modern);
        if(modern)interrupted.finish(true); // a persisted run can already be evacuating
        try(var t=new DisconnectRecoveryTest.Fixture(DisconnectMode.DIE_AND_DROP,false)) {
            when(t.storage.disconnect(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            when(t.f.definitions.dungeons()).thenReturn(Map.of(def.id(),def));
            var recovering=new java.util.concurrent.atomic.AtomicBoolean(true);
            var recoveryLoot=new LootChestService(lifecycle.chests.store,lifecycle.chests.blocks,
                    lifecycle.chests.store.loadLootBlocks().join(),()->{assertFalse(recovering.get(),"Startup must not roll loot");lifecycle.chests.draws++;return .25;},lifecycle.chests.failures::add);
            doReturn(recoveryLoot).when(t.manager).loot();
            var player=t.player;var inventory=mock(PlayerInventory.class);
            var retained=mock(ItemStack.class,RETURNS_DEEP_STUBS);when(inventory.getContents()).thenReturn(new ItemStack[]{retained});
            when(player.getInventory()).thenReturn(inventory);
            when(t.storage.abortUnfinishedRuns(any())).thenReturn(CompletableFuture.completedFuture(1));
            when(t.storage.loadTempBlocks()).thenReturn(CompletableFuture.completedFuture(List.of()));
            var active=new ActiveSessionRecord(interrupted.id(),def.id(),Set.of(player.getUniqueId()),t.pending.exit());
            var records=new ArrayList<>(List.of(active));
            when(t.storage.loadActive()).thenAnswer(c->CompletableFuture.completedFuture(List.copyOf(records)));
            when(t.storage.clearActive(any())).thenAnswer(c->{records.removeIf(r->r.sessionId().equals(c.getArgument(0)));return CompletableFuture.completedFuture(null);});
            when(t.storage.addPendingExit(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            var loaded=remnant(interrupted.id(),"ROOM:first:same");
            var lateZone=remnant(interrupted.id(),"ZONE:a:same");
            var lateMinion=remnant(interrupted.id(),"ZONE:b:same");
            var projectile=remnant(interrupted.id(),"ZONE:b:same");
            var ordinary=remnant(null,null);
            when(t.f.world.getEntities()).thenReturn(List.of(loaded,ordinary));
            t.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            var chunk=new CompletableFuture<Chunk>();when(t.f.world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(chunk);
            lifecycle.chests.blocks.ready=false;
            var recovery=new RecoveryService(t.f.plugin,t.storage,t.manager);recovery.recoverOnEnable();
            recoveryLoot.tick();assertEquals(modern,recoveryLoot.blocked(def.id()));
            verify(loaded).remove();verify(ordinary,never()).remove();
            verify(player,never()).teleport(any(Location.class));
            verify(t.storage).addPendingExit(player.getUniqueId(),t.pending.exit());
            verify(t.storage).clearActive(interrupted.id());
            // Entities were absent from getEntities() at startup and appear in later chunk events.
            var event=new EntitiesLoadEvent(mock(Chunk.class),List.of(lateZone,lateMinion,projectile,ordinary));
            recovery.entitiesLoaded(event);
            verify(lateZone).remove();verify(lateMinion).remove();verify(projectile).remove();verify(ordinary,never()).remove();
            when(t.f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            var available=mock(Chunk.class);when(available.getWorld()).thenReturn(t.f.world);
            chunk.complete(available);
            verify(player,times(1)).teleport(any(Location.class));
            verify(player,never()).setHealth(anyDouble());verify(inventory,never()).clear();verify(inventory,never()).setContents(any());
            verify(t.storage,never()).saveDisconnect(any());verify(t.storage,never()).setCooldown(any(),any(),any());
            lifecycle.chests.blocks.ready=true;recoveryLoot.chunksAvailable("world");recoveryLoot.tick();recoveryLoot.tick();
            assertFalse(recoveryLoot.blocked(def.id()));assertEquals(LootChestServiceTest.ORIGINAL,lifecycle.chests.blocks.data);
            assertNull(lifecycle.chests.blocks.contents[0]);
            // A repeated startup sees the acknowledged active journal and cannot duplicate a return.
            recovery.recoverOnEnable();recoveryLoot.tick();
            verify(t.storage,times(1)).addPendingExit(any(),any());verify(player,times(1)).teleport(any(Location.class));
            assertTrue(records.isEmpty());assertSame(retained,inventory.getContents()[0]);
            recovering.set(false);lifecycle.loot=recoveryLoot;lifecycle.assertFresh(def,modern);
        }
    }

    private Entity remnant(UUID session,String origin) {
        var entity=mock(Entity.class);var pdc=mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(session==null?null:session.toString());
        when(pdc.get(MobKeys.ORIGIN,PersistentDataType.STRING)).thenReturn(origin);
        return entity;
    }
}
