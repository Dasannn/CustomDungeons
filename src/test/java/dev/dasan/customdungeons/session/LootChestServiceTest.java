package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootChestServiceTest {
    static final Point POSITION=new Point("world",1,64,1,0,0);
    static final String ORIGINAL="minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=true]";
    static class Store implements LootPersistence {
        final Map<Point,LootBlockRecord> records=new HashMap<>();
        CompletableFuture<Void> next;
        int writes;
        public CompletableFuture<Void> deleteLootBlock(Point point) {
            var result=next==null?CompletableFuture.<Void>completedFuture(null):next;next=null;
            return result.thenRun(()->records.remove(point));
        }
        public CompletableFuture<List<LootBlockRecord>> loadLootBlocks(){return CompletableFuture.completedFuture(List.copyOf(records.values()));}
        public CompletableFuture<Void> saveLootBlock(LootBlockRecord record) {
            writes++;var result=next==null?CompletableFuture.<Void>completedFuture(null):next;next=null;
            return result.thenRun(()->records.put(record.position(),record));
        }
    }
    static class Blocks implements LootChestService.Blocks {
        final Inventory inventory=mock(Inventory.class);
        final List<org.bukkit.entity.HumanEntity> viewers=new ArrayList<>();
        String data=ORIGINAL,ownedExpectation;
        boolean ready=true,failRestore,failSnapshot,failPlacement;
        int placements,restores;
        ItemStack[] contents=new ItemStack[27];
        Blocks() {
            when(inventory.getViewers()).thenReturn(viewers);
            var world=world();when(inventory.getLocation()).thenReturn(new Location(world,1,64,1));
            doAnswer(c->{contents=c.getArgument(0);return null;}).when(inventory).setContents(any(ItemStack[].class));
        }
        static World world(){var w=mock(World.class);when(w.getName()).thenReturn("world");return w;}
        public boolean ready(Point p){return ready;}
        public String snapshot(Point p){if(!ready || failSnapshot)throw new IllegalStateException();return ownedExpectation!=null && data.equals("minecraft:chest[type=single]")?ownedExpectation:data;}
        public String expectedChest(){return "managed:"+UUID.randomUUID();}
        public boolean matches(Point p,String snapshot,boolean allowContents) {
            if(snapshot.startsWith("managed:"))return snapshot.equals(ownedExpectation) && data.equals("minecraft:chest[type=single]")
                    && (allowContents || !failSnapshot && Arrays.stream(contents).allMatch(Objects::isNull));
            return !failSnapshot && ownedExpectation==null && data.equals(snapshot)
                    && (!data.equals("minecraft:chest[type=single]") || Arrays.stream(contents).allMatch(Objects::isNull));
        }
        public void chest(Point p,String expected){placements++;ownedExpectation=expected;data="minecraft:chest[type=single]";contents=new ItemStack[27];if(failPlacement)throw new IllegalStateException();}
        public Inventory inventory(Point p){return inventory;}
        public void restore(Point p,String original){if(failRestore)throw new IllegalStateException();restores++;ownedExpectation=original.startsWith("managed:")?original:null;data=ownedExpectation==null?original:"minecraft:chest[type=single]";contents=new ItemStack[27];}
    }
    final Store store=new Store();
    final Blocks blocks=new Blocks();
    final List<Throwable> failures=new ArrayList<>();
    int draws;
    final LootChestService loot=new LootChestService(store,blocks,List.of(),()->{draws++;return .25;},failures::add);
    final World world=Blocks.world();
    boolean intro,inside=true,resetReady=true;
    int doorCalls;
    DungeonSession session;
    final SessionServices services=new SessionServices() {
        public boolean prepareStart(DungeonSession s){return loot.prepare(s);}
        public boolean introTick(DungeonSession s){return intro;}
        public void roomEntered(DungeonSession s){loot.trigger(s,s.def().rooms().get(s.roomIndex()).id(),LootChestDef.Trigger.ROOM_ENTER);}
        public void roomCleared(DungeonSession s){loot.trigger(s,s.def().rooms().get(s.roomIndex()).id(),LootChestDef.Trigger.ROOM_CLEAR);doorCalls++;}
        public void finish(DungeonSession s){loot.finish(s);}
        public boolean reset(DungeonSession s){return resetReady && loot.reset(s);}
        public boolean inside(DungeonSession s,Player p){return inside && p.isOnline();}
        public void departed(DungeonSession s,Player p){loot.revoke(p.getUniqueId());}
        public void disconnected(DungeonSession s,Player p){loot.revoke(p.getUniqueId());}
        public void leave(DungeonSession s,Player p){loot.revoke(p.getUniqueId());}
    };
    ItemStack item(){var i=mock(ItemStack.class);when(i.clone()).thenReturn(i);when(i.getAmount()).thenReturn(64);return i;}
    LootChestDef chest(LootChestDef.Trigger trigger){return new LootChestDef("loot",POSITION,trigger,List.of(new LootEntryDef(item(),1)));}
    DungeonDef definition(LootChestDef.Trigger trigger,FinishMode finish,boolean cinematic) {
        var base=new DungeonSessionFlowTest().definition(2);
        var room=base.rooms().getFirst().withLootChests(List.of(chest(trigger)));
        return SpawnerPresets.withRooms(base,List.of(room)).withStart(StartMode.AUTO,List.of(),3,null,false,true,cinematic,5)
                .withFinish(finish,10,FinishDestination.EXIT,List.of());
    }
    Player player(){var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);
        when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.getLocation()).thenReturn(new Location(world,1,64,1));return p;}
    DungeonSession start(boolean test,LootChestDef.Trigger trigger,FinishMode finish,boolean cinematic,Player... players){
        session=new DungeonSession(definition(trigger,finish,cinematic),test,services);
        for(var p:players)session.join(p);session.forceStart();return session;
    }
    void generate(){loot.trigger(session,"first",LootChestDef.Trigger.ROOM_ENTER);}
    void reset(){inside=false;for(int i=0;i<12;i++)session.tick();}

    @Test void backupCompletesBeforeMutationAndTwoPlayersShareOneGeneration() {
        var saved=new CompletableFuture<Void>();store.next=saved;var p=player();var ally=player();
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,p,ally);
        assertEquals(0,blocks.placements);assertEquals(SessionState.LOBBY,session.state().state());
        saved.complete(null);session.forceStart();assertEquals(1,blocks.placements);assertEquals(SessionState.RUNNING,session.state().state());
        assertFalse(loot.allowed(POSITION,p,false));generate();assertTrue(loot.allowed(POSITION,p,true));assertTrue(loot.allowed(POSITION,ally,true));
        assertEquals(64,blocks.contents[0].getAmount());blocks.contents[0]=null;
        generate();session.enterRoom(0);generate();assertNull(blocks.contents[0]);assertEquals(1,draws);
    }
    @Test void failedBackupNeverMutatesAndDoesNotLeaveAReservation() {
        store.next=CompletableFuture.failedFuture(new IllegalStateException());inside=false;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.IMMEDIATE,false,player());
        assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);assertEquals(SessionState.FREE,session.state().state());assertFalse(failures.isEmpty());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void rejectedPreparationPreservesLaterBlockAndContentsThroughResetAndRecovery(boolean occupied) {
        var saved=new CompletableFuture<Void>();store.next=saved;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());
        var added=item();blocks.contents[0]=added;
        if(occupied)blocks.failSnapshot=true;else blocks.data="minecraft:diamond_block";
        String current=blocks.data;var cancelled=new CompletableFuture<Void>();store.next=cancelled;
        saved.complete(null);session.forceStart();reset();
        assertEquals(SessionState.RESETTING,session.state().state());assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);
        assertEquals(current,blocks.data);assertSame(added,blocks.contents[0]);assertFalse(failures.isEmpty());
        cancelled.complete(null);session.tick();assertEquals(SessionState.FREE,session.state().state());
        var recovering=new LootChestService(store,blocks,store.loadLootBlocks().join(),()->{fail("Cancelled preparation cannot roll");return 0;},failures::add);
        recovering.tick();recovering.tick();assertFalse(recovering.blocked("test"));
        assertEquals(current,blocks.data);assertSame(added,blocks.contents[0]);assertEquals(0,blocks.restores);assertEquals(0,draws);
    }
    @Test void failedCancellationReceiptRetriesWithoutRestoringTheRejectedSnapshot() {
        var saved=new CompletableFuture<Void>();store.next=saved;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());
        blocks.data="minecraft:diamond_block";saved.complete(null);store.next=CompletableFuture.failedFuture(new IllegalStateException());
        session.forceStart();var retry=new CompletableFuture<Void>();store.next=retry;reset();
        assertEquals(SessionState.RESETTING,session.state().state());assertEquals(0,blocks.restores);assertEquals(0,blocks.placements);
        retry.complete(null);session.tick();assertEquals(SessionState.FREE,session.state().state());
        assertEquals("minecraft:diamond_block",blocks.data);assertFalse(store.records.get(POSITION).sessionActive());
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"false,false","false,true","true,false","true,true"})
    void crashBeforeCancellationIsDurablePreservesLaterStateAndKeepsDungeonBlocked(boolean failed,boolean occupied) {
        var saved=new CompletableFuture<Void>();store.next=saved;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());
        blocks.data=occupied?"minecraft:chest[type=single]":"minecraft:diamond_block";
        var added=item();if(occupied){blocks.contents[0]=added;blocks.failSnapshot=true;}
        String current=blocks.data;saved.complete(null);
        store.next=failed?CompletableFuture.failedFuture(new IllegalStateException()):new CompletableFuture<>();
        session.forceStart();assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);
        var durable=store.loadLootBlocks().join();assertTrue(durable.getFirst().sessionActive());assertFalse(durable.getFirst().preparationCancelled());
        var recovering=new LootChestService(store,blocks,durable,()->{fail("Recovery cannot roll loot");return 0;},failures::add);
        recovering.tick();recovering.tick();recovering.tick();
        assertEquals(current,blocks.data);if(occupied)assertSame(added,blocks.contents[0]);
        assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);assertTrue(recovering.blocked("test"));
        assertEquals(durable,store.loadLootBlocks().join());
    }
    @Test void stoppingBeforePendingPreparationMutatesCancelsInsteadOfRestoring() {
        var saved=new CompletableFuture<Void>();store.next=saved;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());
        blocks.data="minecraft:diamond_block";session.finish(false,true);saved.complete(null);reset();
        assertEquals(SessionState.FREE,session.state().state());assertEquals("minecraft:diamond_block",blocks.data);
        assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);
    }
    @Test void crashAfterIntentBeforeMutationAcknowledgesAnUnchangedOriginalWithoutReplacingIt() {
        var saved=new CompletableFuture<Void>();store.next=saved;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());saved.complete(null);
        var recovering=new LootChestService(store,blocks,store.loadLootBlocks().join(),()->{fail("Recovery cannot roll");return 0;},failures::add);
        recovering.tick();recovering.tick();assertEquals(ORIGINAL,blocks.data);
        assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);assertFalse(recovering.blocked("test"));
        assertFalse(store.records.get(POSITION).sessionActive());
    }
    @Test void partiallyFailedPlacementStillRestoresTheOriginalAndKeepsItsBackup() {
        blocks.failPlacement=true;start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());
        assertEquals(1,blocks.placements);assertFalse(failures.isEmpty());reset();
        assertEquals(1,blocks.restores);assertEquals(ORIGINAL,blocks.data);assertEquals(SessionState.FREE,session.state().state());
        assertEquals(ORIGINAL,store.records.get(POSITION).sessionData());
    }
    @Test void currentRoomEntryWaitsForTheWholeCinematicAndDoesNotFireForFutureRoom() {
        intro=true;var p=player();start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,true,p);
        loot.trigger(session,"future",LootChestDef.Trigger.ROOM_ENTER);session.enterRoom(0);for(int i=0;i<20;i++)session.tick();assertEquals(0,draws);
        intro=false;session.tick();session.enterRoom(0);assertEquals(1,draws);session.enterRoom(0);assertEquals(1,draws);
    }
    @Test void clearGeneratesBeforeKeyAndFinalObserversAndNeverAtEvacuation() {
        start(false,LootChestDef.Trigger.ROOM_CLEAR,FinishMode.NONE,false,player());
        session.addListener(new SessionLifecycleListener(){public void onFinished(DungeonSession s,RunResult result,Set<UUID> alive){assertNotNull(blocks.contents[0]);}});
        session.enterRoom(0);assertEquals(0,draws);session.tick();assertEquals(1,draws);assertEquals(1,doorCalls);
        loot.trigger(session,"first",LootChestDef.Trigger.ROOM_CLEAR);assertEquals(1,draws);assertEquals(SessionState.COMPLETED,session.state().state());
    }
    @ParameterizedTest @EnumSource(value=FinishMode.class,names={"DELAYED","NONE"})
    void survivorsCanLootDuringEvacuationAndDeparturePermanentlyRevokes(FinishMode finish) {
        var p=player();start(false,LootChestDef.Trigger.ROOM_ENTER,finish,false,p);generate();session.finish(true);
        assertTrue(loot.allowed(POSITION,p,true));loot.tick();assertTrue(loot.allowed(POSITION,p,true));assertEquals(0,blocks.restores);
        loot.departing(p,new Location(world,100,64,1));assertFalse(loot.allowed(POSITION,p,true));
        when(p.getLocation()).thenReturn(new Location(world,1,64,1));assertFalse(loot.allowed(POSITION,p,true));
        reset();assertEquals(ORIGINAL,blocks.data);assertEquals(SessionState.FREE,session.state().state());assertNull(blocks.contents[0]);
    }
    @ParameterizedTest @ValueSource(strings={"death","quit","leave"})
    void completedSurvivorLosesAuthorizationOnEveryDeparture(String cause) {
        var p=player();start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,p);generate();session.finish(true);
        switch(cause){case "death"->loot.revoke(p.getUniqueId());case "quit"->session.disconnect(p.getUniqueId());case "leave"->session.leave(p.getUniqueId());}
        assertFalse(loot.allowed(POSITION,p,true));
    }
    @Test void failedSessionImmediatelyClosesViewsButRestoresOnlyInReset() {
        var p=player();blocks.viewers.add(p);start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,p);generate();session.finish(false);
        verify(p,atLeastOnce()).closeInventory();assertFalse(loot.allowed(POSITION,p,false));assertEquals(0,blocks.restores);
        reset();assertEquals(1,blocks.restores);assertEquals(ORIGINAL,blocks.data);
    }
    @Test void restoreFailureAndMarkerFailureBothKeepDungeonInReset() {
        var p=player();start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,p);generate();session.finish(false);
        blocks.failRestore=true;reset();assertEquals(SessionState.RESETTING,session.state().state());assertTrue(store.records.get(POSITION).sessionActive());
        blocks.failRestore=false;store.next=CompletableFuture.failedFuture(new IllegalStateException());session.tick();assertEquals(ORIGINAL,blocks.data);
        session.tick();assertEquals(SessionState.RESETTING,session.state().state());session.tick();assertEquals(SessionState.FREE,session.state().state());
        assertFalse(store.records.get(POSITION).sessionActive());
    }
    @Test void immediateModeAddsNoEvacuationDelayButWaitsForRestoreConfirmation() {
        var p=player();start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.IMMEDIATE,false,p);generate();inside=false;session.finish(true);
        assertFalse(loot.allowed(POSITION,p,true));assertEquals(1,blocks.restores);assertEquals(SessionState.RESETTING,session.state().state());
        session.tick();assertEquals(SessionState.FREE,session.state().state());
    }
    @Test void testCanViewButCannotTransferAndForeignSpectatorDeadAndOfflineAreDenied() {
        var p=player();start(true,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,p);generate();assertTrue(loot.allowed(POSITION,p,false));assertFalse(loot.allowed(POSITION,p,true));
        assertFalse(loot.allowed(POSITION,player(),false));
        when(p.getGameMode()).thenReturn(GameMode.SPECTATOR);assertFalse(loot.allowed(POSITION,p,false));when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p.isDead()).thenReturn(true);assertFalse(loot.allowed(POSITION,p,false));when(p.isDead()).thenReturn(false);
        when(p.isOnline()).thenReturn(false);assertFalse(loot.allowed(POSITION,p,false));
    }
    @ParameterizedTest @ValueSource(strings={"before","after","evacuation","restoring","restored"})
    void crashRecoveryDiscardsLeftoversWithoutAnotherRollAndIsIdempotent(String stage) {
        var p=player();start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,p);
        if(!stage.equals("before"))generate();if(stage.equals("evacuation") || stage.equals("restored") || stage.equals("restoring"))session.finish(true);
        if(stage.equals("restoring")){store.next=new CompletableFuture<>();reset();assertEquals(SessionState.RESETTING,session.state().state());}
        if(stage.equals("restored"))reset();int count=draws;
        var recovering=new LootChestService(store,blocks,store.loadLootBlocks().join(),()->{fail("Recovery must not roll");return 0;},failures::add);
        assertTrue(recovering.blocked("test"));blocks.ready=false;recovering.tick();assertTrue(recovering.blocked("test"));
        blocks.ready=true;recovering.chunksAvailable("world");recovering.tick();recovering.tick();assertEquals(ORIGINAL,blocks.data);assertFalse(recovering.blocked("test"));assertEquals(count,draws);
        new LootChestService(store,blocks,store.loadLootBlocks().join(),()->0,failures::add).tick();assertEquals(ORIGINAL,blocks.data);assertNull(blocks.contents[0]);
    }
    @Test void deletionResumesPausedReconciliationWithoutRetryingEveryTick() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));loot.tick();placed.join();
        store.next=CompletableFuture.failedFuture(new IllegalStateException("database offline"));
        var previous=loot.reconcile("test",List.of());for(int i=0;i<8;i++)loot.tick();
        assertFalse(previous.isDone());assertFalse(loot.pending());int pausedWrites=store.writes;
        for(int i=0;i<100;i++)loot.tick();assertEquals(pausedWrites,store.writes);assertEquals(0,blocks.restores);
        var deleted=loot.restoreForDeletion("test");assertSame(previous,deleted);
        for(int i=0;i<8;i++)loot.tick();deleted.join();
        assertEquals(ORIGINAL,blocks.data);assertEquals(1,blocks.restores);assertFalse(loot.blocked("test"));
        int completedWrites=store.writes;
        for(int i=0;i<100;i++)loot.tick();loot.restoreForDeletion("test").join();
        assertEquals(completedWrites,store.writes);assertEquals(1,blocks.restores);
    }
    @Test void deletionReconciliationRestoresExactBlockAndCanBeRepeated() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));loot.tick();placed.join();
        var deleted=loot.restoreForDeletion("test");for(int i=0;i<8;i++)loot.tick();deleted.join();
        assertEquals(ORIGINAL,blocks.data);assertEquals(1,blocks.restores);
        loot.restoreForDeletion("test").join();assertEquals(1,blocks.restores);
        var restarted=new LootChestService(store,blocks,store.loadLootBlocks().join(),()->0,failures::add);
        for(int i=0;i<8;i++)restarted.tick();assertEquals(ORIGINAL,blocks.data);
    }
    @Test void deletionReconciliationReportsIoFailureAndAllowsRetryWithoutDuplicateRestoration() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));loot.tick();placed.join();
        store.next=CompletableFuture.failedFuture(new IllegalStateException());
        var deleted=loot.restoreForDeletion("test");for(int i=0;i<8;i++)loot.tick();
        assertTrue(deleted.isCompletedExceptionally());assertEquals(0,blocks.restores);
        var retry=loot.restoreForDeletion("test");for(int i=0;i<8;i++)loot.tick();retry.join();
        assertEquals(ORIGINAL,blocks.data);assertEquals(1,blocks.restores);
    }
    @Test void placementOriginalSurvivesSessionsAndRemoveAndRepeatedRecovery() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));assertEquals(0,blocks.placements);loot.tick();placed.join();
        assertEquals(ORIGINAL,store.records.get(POSITION).placementData());String chestData=blocks.data;
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());generate();session.finish(true);reset();assertEquals(chestData,blocks.data);
        var removed=loot.remove(POSITION);loot.tick();removed.join();assertEquals(ORIGINAL,blocks.data);assertFalse(loot.contains(POSITION));
        var recovering=new LootChestService(store,blocks,store.loadLootBlocks().join(),()->0,failures::add);recovering.tick();recovering.tick();assertEquals(ORIGINAL,blocks.data);
    }
    @Test void placementWriteFailureAndConcurrentChangesNeverPlaceAChest() {
        store.next=CompletableFuture.failedFuture(new IllegalStateException());var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));loot.tick();
        assertThrows(CompletionException.class,placed::join);assertEquals(0,blocks.placements);
        var write=new CompletableFuture<Void>();store.next=write;placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));
        blocks.data="minecraft:dirt";write.complete(null);loot.tick();assertThrows(CompletionException.class,placed::join);assertEquals(0,blocks.placements);
    }
    @Test void rejectedAdministrativePlacementKeepsLaterContentsEvenIfCancellationWriteFails() {
        var saved=new CompletableFuture<Void>();store.next=saved;
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));
        var added=item();blocks.contents[0]=added;blocks.failSnapshot=true;saved.complete(null);
        store.next=CompletableFuture.failedFuture(new IllegalStateException());loot.tick();
        assertThrows(CompletionException.class,placed::join);loot.tick();loot.tick();
        loot.reconcile("test",List.of());loot.tick();loot.tick();
        assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);assertSame(added,blocks.contents[0]);assertFalse(loot.blocked("test"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void crashDuringAdministrativeCancellationKeepsTheForeignContainerAndDurableIntent(boolean failed) {
        var saved=new CompletableFuture<Void>();store.next=saved;
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));
        blocks.data="minecraft:chest[type=single]";var added=item();blocks.contents[0]=added;blocks.failSnapshot=true;
        saved.complete(null);store.next=failed?CompletableFuture.failedFuture(new IllegalStateException()):new CompletableFuture<>();
        loot.tick();assertThrows(CompletionException.class,placed::join);
        var durable=store.loadLootBlocks().join();assertTrue(durable.getFirst().sessionActive());
        var recovering=new LootChestService(store,blocks,durable,()->0,failures::add);
        recovering.tick();recovering.tick();assertTrue(recovering.blocked("test"));
        assertEquals(0,blocks.placements);assertEquals(0,blocks.restores);assertSame(added,blocks.contents[0]);
        assertEquals(durable,store.loadLootBlocks().join());
    }
    @Test void aSecondRunStartsEmptyAndGetsExactlyOneFreshRoll() {
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());generate();blocks.contents[0]=null;session.finish(true);reset();
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());assertNull(blocks.contents[0]);generate();generate();assertEquals(2,draws);assertNotNull(blocks.contents[0]);
    }
    @Test void placementAndRemovalStayBlockedUntilTheirRestorationReceiptIsDurable() {
        var write=new CompletableFuture<Void>();
        var placement=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));store.next=write;loot.tick();
        assertFalse(placement.isDone());assertTrue(loot.blocked("test"));write.complete(null);loot.tick();placement.join();assertFalse(loot.blocked("test"));
        write=new CompletableFuture<>();var removal=loot.remove(POSITION);store.next=write;loot.tick();assertEquals(ORIGINAL,blocks.data);assertFalse(removal.isDone());assertTrue(loot.blocked("test"));
        write.complete(null);loot.tick();removal.join();assertFalse(loot.blocked("test"));assertFalse(store.records.get(POSITION).sessionActive());
    }
    @Test void aLaterRunAfterAdministrativeRemovalBacksUpTheCurrentBlockInsteadOfAnOldPlacement() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));loot.tick();placed.join();
        var removed=loot.remove(POSITION);loot.tick();removed.join();
        blocks.data="minecraft:diamond_block";
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());generate();
        var recovering=new LootChestService(store,blocks,store.loadLootBlocks().join(),()->0,failures::add);recovering.tick();recovering.tick();
        assertEquals("minecraft:diamond_block",blocks.data);assertFalse(recovering.blocked("test"));
    }
    @Test void shutdownRestoresEvenDuringEvacuationAndKeepsTheJournal() {
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());generate();session.finish(true);loot.shutdown();
        assertEquals(ORIGINAL,blocks.data);assertNull(blocks.contents[0]);assertFalse(store.records.get(POSITION).sessionActive());assertEquals(SessionState.RESETTING,session.state().state());
    }

    @ParameterizedTest @ValueSource(strings={"minecraft:air",ORIGINAL})
    void authoringPlacementAndUndoRestoreExactBlockData(String original) {
        blocks.data=original;
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_CLEAR),()->true);loot.tick();placed.join();
        var undo=loot.remove(POSITION,()->true);loot.tick();undo.join();assertEquals(original,blocks.data);
        var undoRemoval=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_CLEAR),()->true);loot.tick();undoRemoval.join();
        var removed=loot.remove(POSITION,()->true);loot.tick();removed.join();assertEquals(original,blocks.data);
    }
    @Test void revokedAuthoringPermissionWhileBackupIsPendingNeverMutatesTheBlock() {
        var permitted=new java.util.concurrent.atomic.AtomicBoolean(true);var write=new CompletableFuture<Void>();store.next=write;
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_CLEAR),permitted::get);
        permitted.set(false);write.complete(null);loot.tick();assertTrue(placed.isCompletedExceptionally());assertEquals(ORIGINAL,blocks.data);assertEquals(0,blocks.placements);
    }
    @Test void removalRejectsForeignChangesInsteadOfOverwritingThem() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_CLEAR));loot.tick();placed.join();
        var write=new CompletableFuture<Void>();store.next=write;var removed=loot.remove(POSITION);
        blocks.data="minecraft:diamond_block";write.complete(null);loot.tick();
        assertTrue(removed.isCompletedExceptionally());assertEquals("minecraft:diamond_block",blocks.data);assertEquals(0,blocks.restores);
    }
    @Test void revokedPermissionWhileRemovalBackupIsPendingLeavesChestIntact() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_CLEAR));loot.tick();placed.join();
        var permitted=new java.util.concurrent.atomic.AtomicBoolean(true);var write=new CompletableFuture<Void>();store.next=write;
        var removed=loot.remove(POSITION,permitted::get);permitted.set(false);write.complete(null);loot.tick();
        assertTrue(removed.isCompletedExceptionally());assertEquals("minecraft:chest[type=single]",blocks.data);assertEquals(0,blocks.restores);
    }

    @Test void cancelledRemovalAfterSessionCanBeRetriedWithoutLosingPlacementWitness() {
        var placed=loot.place("test","first",chest(LootChestDef.Trigger.ROOM_ENTER));loot.tick();placed.join();
        start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player());generate();session.finish(true);reset();
        var permitted=new java.util.concurrent.atomic.AtomicBoolean(true);var write=new CompletableFuture<Void>();store.next=write;
        var removal=loot.remove(POSITION,permitted::get);permitted.set(false);write.complete(null);loot.tick();assertTrue(removal.isCompletedExceptionally());
        loot.tick();loot.tick();permitted.set(true);
        var retry=loot.remove(POSITION,permitted::get);loot.tick();retry.join();assertEquals(ORIGINAL,blocks.data);
    }
}
