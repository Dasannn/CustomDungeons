package dev.dasan.customdungeons.reward;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.session.DungeonSession;
import dev.dasan.customdungeons.storage.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RewardServiceTest {
    final Storage storage = mock(Storage.class);
    final Messages messages = mock(Messages.class);
    final List<String> commands = new ArrayList<>();
    final RewardService rewards = new RewardService(storage, Optional.empty(), messages, Runnable::run,
            commands::add, Logger.getAnonymousLogger());
    ItemStack item() {
        var item=mock(ItemStack.class); when(item.clone()).thenReturn(item); return item;
    }
    Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getName()).thenReturn("Jugador"); when(p.isOnline()).thenReturn(true);
        when(p.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        return p;
    }
    DungeonSession session(Player... players) {
        var s = mock(DungeonSession.class); var def = mock(DungeonDef.class);
        when(s.def()).thenReturn(def); when(s.players()).thenReturn(List.of(players));
        when(def.reward()).thenReturn(new RewardDef(List.of(),0,42,List.of("give {player} diamond")));
        return s;
    }
    @Test void platformDeliveryUsesExistingOverflowClaimsXpAndCommands() {
        var p=player(); var awarded=item(); var rest=item();
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0,rest)));
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        rewards.deliver(p,new RewardDef(List.of(awarded),0,42,List.of("give {player} diamond")));
        verify(storage).addClaims(p.getUniqueId(),List.of(rest));
        verify(p).giveExp(42);
        assertEquals(List.of("give Jugador diamond"),commands);
        verify(messages).send(p,"reward.received"); verify(messages).send(p,"reward.pending");
    }
    @Test void platformDeliveryPreservesOfflineItemsAsClaims() {
        var p=player(); var awarded=item(); when(p.isOnline()).thenReturn(false);
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        rewards.deliver(p,new RewardDef(List.of(awarded),0,0,List.of()));
        verify(p.getInventory(),never()).addItem(any(ItemStack[].class));
        verify(storage).addClaims(p.getUniqueId(),List.of(awarded));
    }
    @Test void onlySurvivorsAreRewarded() {
        var alive = player(); var eliminated = player(); var s = session(alive,eliminated);
        rewards.onFinished(s,RunResult.COMPLETED,Set.of(alive.getUniqueId()));
        verify(alive).giveExp(42); verify(eliminated,never()).giveExp(anyInt());
        assertEquals(List.of("give Jugador diamond"),commands);
    }
    @Test void testModeGivesNothing() {
        var p = player(); var s = session(p); when(s.testMode()).thenReturn(true);
        rewards.onFinished(s,RunResult.COMPLETED,Set.of(p.getUniqueId()));
        verifyNoInteractions(storage); verify(p,never()).giveExp(anyInt()); assertTrue(commands.isEmpty());
    }
    @Test void failedRunGivesNothing() {
        var p = player(); rewards.onFinished(session(p),RunResult.FAILED,Set.of(p.getUniqueId()));
        verify(p,never()).giveExp(anyInt()); assertTrue(commands.isEmpty());
    }
    @Test void claimKeepsOverflowAndCountsItemAmounts() {
        var p = player(); var item = item(); var rest = item();
        when(item.getAmount()).thenReturn(10); when(rest.getAmount()).thenReturn(4);
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0,rest)));
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        assertEquals(6,rewards.claim(p).join()); verify(storage).addClaims(p.getUniqueId(),List.of(rest));
    }
    @Test void claimCountsBeforePaperMutatesInputStacks() {
        var p=player(); var item=item(); var copy=item();
        when(item.getAmount()).thenReturn(10); when(item.clone()).thenReturn(copy);
        int[] amount={10}; when(copy.getAmount()).thenAnswer(unused -> amount[0]);
        doAnswer(call -> { amount[0]=call.getArgument(0); return null; }).when(copy).setAmount(anyInt());
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenAnswer(call -> {
            ItemStack input=call.getArgument(0); input.setAmount(4);
            return new HashMap<>(Map.of(0,input));
        });
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        assertEquals(6,rewards.claim(p).join());
        assertEquals(10,item.getAmount()); verify(item).clone();
    }
    @Test void failedOverflowSaveKeepsClaimInProgressUntilRetrySucceeds() throws Exception {
        var p=player(); var item=item(); var rest=item();
        when(item.clone()).thenReturn(item); when(item.getAmount()).thenReturn(10); when(rest.getAmount()).thenReturn(4);
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0,rest)));
        var retried=new CompletableFuture<Void>();
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")))
                .thenReturn(retried);
        var result=rewards.claim(p);
        assertSame(result,rewards.claim(p));
        retried.complete(null);
        assertEquals(6,result.get(10,TimeUnit.SECONDS));
        verify(storage,times(2)).addClaims(p.getUniqueId(),List.of(rest));
        verify(storage,times(1)).takeClaims(p.getUniqueId());
    }
    @Test void exhaustedRetriesDropOnlyOverflowAndWarn() throws Exception {
        var p=player(); var item=item(); var rest=item();
        when(item.clone()).thenReturn(item); when(item.getAmount()).thenReturn(10); when(rest.getAmount()).thenReturn(4);
        when(rest.clone()).thenReturn(rest);
        var world=mock(org.bukkit.World.class); var at=new org.bukkit.Location(world,1,64,2);
        when(p.getLocation()).thenReturn(at); when(p.getWorld()).thenReturn(world);
        var warning=mock(Logger.class);
        var service=new RewardService(storage,Optional.empty(),messages,Runnable::run,commands::add,warning);
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0,rest)));
        when(storage.addClaims(any(),any())).thenAnswer(unused -> CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        var result=service.claim(p);
        assertThrows(ExecutionException.class,() -> result.get(10,TimeUnit.SECONDS));
        verify(storage,times(4)).addClaims(p.getUniqueId(),List.of(rest));
        verify(world).dropItemNaturally(at,rest);
        verify(warning).warning(anyString());
    }
    @Test void shutdownDropsOverflowWhenAllRetriesFailAndMainCallbackIsQueued() {
        var p=player(); var item=item(); var rest=item();
        when(item.getAmount()).thenReturn(10); when(rest.getAmount()).thenReturn(4);
        var world=mock(org.bukkit.World.class); var at=new org.bukkit.Location(world,1,64,2);
        when(p.getLocation()).thenReturn(at);
        var scheduled=new ArrayList<Runnable>(); var warning=mock(Logger.class);
        var service=new RewardService(storage,Optional.empty(),messages,scheduled::add,commands::add,warning);
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0,rest)));
        when(storage.addClaims(any(),any())).thenAnswer(unused -> CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
        var result=service.claim(p); scheduled.removeFirst().run();
        assertSame(result,service.claim(p));
        service.closeClaims();
        assertTrue(result.isCompletedExceptionally());
        scheduled.forEach(Runnable::run);
        verify(storage,times(4)).addClaims(p.getUniqueId(),List.of(rest));
        verify(storage,times(1)).takeClaims(p.getUniqueId());
        verify(world,times(1)).dropItemNaturally(at,rest);
        verify(warning).warning(anyString());
    }
    @Test void shutdownRetriesFailedSavesBeforeReleasingClaimItems() throws Exception {
        var p=player(); var item=item(); var rest=item();
        when(item.clone()).thenReturn(item); when(item.getAmount()).thenReturn(10); when(rest.getAmount()).thenReturn(4);
        var firstSave=new CompletableFuture<Void>();
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(p.getInventory().addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(Map.of(0,rest)));
        when(storage.addClaims(any(),any())).thenReturn(firstSave).thenReturn(CompletableFuture.completedFuture(null));
        var result=rewards.claim(p);
        firstSave.completeExceptionally(new IllegalStateException("database unavailable"));
        rewards.closeClaims();
        assertEquals(6,result.get(10,TimeUnit.SECONDS));
        verify(storage,times(2)).addClaims(p.getUniqueId(),List.of(rest));
    }
    @Test void disconnectedClaimIsReturnedToStorage() {
        var p = player(); var item = item(); when(p.isOnline()).thenReturn(false);
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        assertEquals(0,rewards.claim(p).join()); verify(storage).addClaims(p.getUniqueId(),List.of(item));
        verifyNoInteractions(p.getInventory());
    }
    @Test void simultaneousClaimsShareOneTake() {
        var p = player(); var pending = new CompletableFuture<List<ItemStack>>();
        when(storage.takeClaims(p.getUniqueId())).thenReturn(pending);
        var first = rewards.claim(p); assertSame(first,rewards.claim(p));
        pending.complete(List.of()); assertEquals(0,first.join()); verify(storage,times(1)).takeClaims(any());
    }
    @Test void shutdownReturnsTakenItemsBeforeScheduledDelivery() {
        var scheduled = new ArrayList<Runnable>();
        var service = new RewardService(storage,Optional.empty(),messages,scheduled::add,commands::add,Logger.getAnonymousLogger());
        var p=player(); var item=item();
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        when(storage.addClaims(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
        var result=service.claim(p); assertFalse(result.isDone());
        service.closeClaims(); assertTrue(result.isCompletedExceptionally());
        scheduled.forEach(Runnable::run);
        verify(storage,times(1)).addClaims(p.getUniqueId(),List.of(item)); verifyNoInteractions(p.getInventory());
    }
    @Test void shutdownDoesNotDeadlockOnQueuedClaimCompletion() {
        var scheduled = new ArrayList<Runnable>();
        var service = new RewardService(storage,Optional.empty(),messages,scheduled::add,commands::add,Logger.getAnonymousLogger());
        var p=player(); var item=item(); when(item.getAmount()).thenReturn(2);
        when(storage.takeClaims(p.getUniqueId())).thenReturn(CompletableFuture.completedFuture(List.of(item)));
        var result=service.claim(p); scheduled.removeFirst().run(); assertFalse(result.isDone());
        service.closeClaims(); assertEquals(2,result.join());
        scheduled.forEach(Runnable::run); verify(storage,never()).addClaims(any(),any());
    }

    @Test void registrationMakesRewardServiceAvailableToCommandsAfterRecovery() {
        var plugin=mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
        var server=mock(org.bukkit.Server.class); var plugins=mock(org.bukkit.plugin.PluginManager.class);
        var services=new org.bukkit.plugin.SimpleServicesManager();
        var manager=mock(dev.dasan.customdungeons.session.SessionManager.class);
        when(plugin.getServer()).thenReturn(server); when(server.getPluginManager()).thenReturn(plugins);
        when(server.getServicesManager()).thenReturn(services); when(plugin.storage()).thenReturn(storage);
        when(plugin.sessionManager()).thenReturn(manager); when(plugin.messages()).thenReturn(messages);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(storage.abortUnfinishedRuns(any())).thenReturn(CompletableFuture.completedFuture(0));
        when(storage.loadTempBlocks()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(storage.loadActive()).thenReturn(CompletableFuture.completedFuture(List.of()));
        try (var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getWorlds).thenReturn(List.of());
            bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(org.bukkit.Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(org.bukkit.Bukkit::getServer).thenReturn(server);
            assertNull(services.load(RewardService.class));
            RewardService.register(plugin);
            var registered=org.bukkit.Bukkit.getServicesManager().load(RewardService.class);
            assertNotNull(registered);
            assertSame(plugin,services.getRegistration(RewardService.class).getPlugin());
            verify(manager).addListener(registered); verify(storage).abortUnfinishedRuns(any());
        }
    }

    @org.junit.jupiter.api.Nested class Recording {
        final dev.dasan.customdungeons.session.SessionManager manager=mock(dev.dasan.customdungeons.session.SessionManager.class);
        final dev.dasan.customdungeons.session.RunRecorder recorder=new dev.dasan.customdungeons.session.RunRecorder(storage,manager,Logger.getAnonymousLogger());
        DungeonSession run(Player p) {
            var s=session(p); when(s.id()).thenReturn(UUID.randomUUID());
            var participants=Set.of(p.getUniqueId());
            when(s.survivors()).thenReturn(participants); when(s.def().id()).thenReturn("dungeon");
            when(s.def().cooldownSeconds()).thenReturn(30);
            when(s.def().exit()).thenReturn(new Point("world",1,64,2,0,0));
            when(storage.markActive(any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.startRun(anyString(),any(),any())).thenReturn(CompletableFuture.completedFuture(7L));
            when(storage.finishRun(anyLong(),any(),any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.setCooldown(any(),anyString(),any())).thenReturn(CompletableFuture.completedFuture(null));
            when(storage.clearActive(any())).thenReturn(CompletableFuture.completedFuture(null));
            return s;
        }
        @Test void finishWaitsForStartAndCachesOnlySurvivorCooldowns() {
            var alive=player(); var departed=player(); var s=run(alive);
            var participants=Set.of(alive.getUniqueId(),departed.getUniqueId());
            when(s.survivors()).thenReturn(participants);
            var start=new CompletableFuture<Long>(); when(storage.startRun(anyString(),any(),any())).thenReturn(start);
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.FREE,dev.dasan.customdungeons.session.SessionState.LOBBY);
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.LOBBY,dev.dasan.customdungeons.session.SessionState.RUNNING);
            recorder.onFinished(s,RunResult.COMPLETED,Set.of(alive.getUniqueId()));
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.RESETTING,dev.dasan.customdungeons.session.SessionState.FREE);
            verify(storage,never()).finishRun(anyLong(),any(),any(),any()); verify(storage,never()).clearActive(any());
            verify(manager).cacheCooldown(eq(alive.getUniqueId()),eq("dungeon"),any());
            verify(manager,never()).cacheCooldown(eq(departed.getUniqueId()),anyString(),any());
            start.complete(7L);
            var records=org.mockito.ArgumentCaptor.forClass(List.class);
            verify(storage).finishRun(eq(7L),eq(RunResult.COMPLETED),any(),records.capture());
            assertEquals(2,records.getValue().size());
            var ordered=inOrder(storage);
            ordered.verify(storage).finishRun(eq(7L),eq(RunResult.COMPLETED),any(),any());
            ordered.verify(storage).setCooldown(eq(alive.getUniqueId()),eq("dungeon"),any());
            ordered.verify(storage).clearActive(s.id());
        }
        @Test void eliminatedPlayerIsRemovedFromCrashExitSnapshotButKeptInStats() {
            var eliminated=player(); var alive=player(); var s=run(alive);
            var participants=Set.of(alive.getUniqueId(),eliminated.getUniqueId());
            when(s.survivors()).thenReturn(participants);
            when(s.livesLeft(eliminated.getUniqueId())).thenReturn(1);
            when(manager.sessionOf(eliminated.getUniqueId())).thenReturn(Optional.of(s));
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.FREE,dev.dasan.customdungeons.session.SessionState.LOBBY);
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.LOBBY,dev.dasan.customdungeons.session.SessionState.RUNNING);
            var event=mock(org.bukkit.event.entity.PlayerDeathEvent.class); when(event.getEntity()).thenReturn(eliminated);
            recorder.death(event);
            var snapshots=org.mockito.ArgumentCaptor.forClass(ActiveSessionRecord.class);
            verify(storage,times(3)).markActive(snapshots.capture());
            assertEquals(Set.of(alive.getUniqueId()),snapshots.getValue().players());
            recorder.onFinished(s,RunResult.COMPLETED,Set.of(alive.getUniqueId()));
            var records=org.mockito.ArgumentCaptor.forClass(List.class);
            verify(storage).finishRun(eq(7L),eq(RunResult.COMPLETED),any(),records.capture());
            assertTrue(records.getValue().contains(new RunPlayerRecord(eliminated.getUniqueId(),0,1,false,false)));
        }
        @Test void testModePersistsRecoveryWithoutHistoryOrCooldowns() {
            var p=player(); var s=run(p); when(s.testMode()).thenReturn(true);
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.FREE,dev.dasan.customdungeons.session.SessionState.LOBBY);
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.LOBBY,dev.dasan.customdungeons.session.SessionState.RUNNING);
            recorder.onFinished(s,RunResult.COMPLETED,Set.of(p.getUniqueId()));
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.RESETTING,dev.dasan.customdungeons.session.SessionState.FREE);
            verify(storage,atLeastOnce()).markActive(new ActiveSessionRecord(s.id(),"dungeon",Set.of(p.getUniqueId()),s.def().exit()));
            verify(storage).clearActive(s.id());
            verify(storage,never()).startRun(anyString(),any(),any());
            verify(storage,never()).finishRun(anyLong(),any(),any(),any());
            verify(storage,never()).setCooldown(any(),anyString(),any());
            verifyNoInteractions(manager);
        }
        @Test void failedRunHasNoCooldown() {
            var p=player(); var s=run(p);
            recorder.onStateChange(s,dev.dasan.customdungeons.session.SessionState.FREE,dev.dasan.customdungeons.session.SessionState.LOBBY);
            recorder.onFinished(s,RunResult.FAILED,Set.of());
            verify(storage).finishRun(eq(7L),eq(RunResult.FAILED),any(),any());
            verify(storage,never()).setCooldown(any(),anyString(),any()); verifyNoInteractions(manager);
        }
    }
}
