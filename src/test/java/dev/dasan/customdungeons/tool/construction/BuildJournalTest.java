package dev.dasan.customdungeons.tool.construction;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BuildJournalTest {
    @TempDir Path directory;
    final UUID admin=UUID.randomUUID();
    @Test void simulatedCrashRestoresTheExactOpaqueInventoryAndHeldSlot() {
        var token=UUID.randomUUID();
        byte[] fullNbt={0,1,2,-1,4},cursor={9,8,7};
        var original=new BuildJournal.Inventory(admin,token,fullNbt,cursor,8);
        try(var store=new BuildJournal(directory,Runnable::run)) {store.backup(original).join();}
        fullNbt[0]=99;cursor[0]=99;
        try(var reloaded=new BuildJournal(directory,Runnable::run)) {
            var recovery=reloaded.inventory(admin,token).orElseThrow();
            assertArrayEquals(new byte[]{0,1,2,-1,4},recovery.contents());
            assertArrayEquals(new byte[]{9,8,7},recovery.cursor());assertEquals(8,recovery.held());
            recovery.contents()[0]=88;assertEquals(0,recovery.contents()[0]);
        }
    }
    @Test void resumedDraftKeepsUndoContextAndBaselineAndIsIsolatedPerAdmin() {
        var state=new BuildState(BuildStateTest.definition("base"));
        state.change(BuildStateTest.definition("draft"));state.cyclePoint();
        try(var store=new BuildJournal(directory,Runnable::run)) {store.save(admin,state.snapshot()).join();}
        try(var reloaded=new BuildJournal(directory,Runnable::run)) {
            assertEquals(state.snapshot(),reloaded.draft(admin,"draft").orElseThrow());
            assertTrue(reloaded.draft(UUID.randomUUID(),"draft").isEmpty());
            var resumed=new BuildState(reloaded.draft(admin,"draft").orElseThrow());
            assertTrue(resumed.undo());assertEquals("base",resumed.definition().displayName());
        }
        assertFalse(Files.exists(directory.resolve("dungeons")));
    }
    @Test void oldBackupSurvivesNewEntryUntilItsPlayerDataAcknowledgesRestoration() {
        var old=UUID.randomUUID();var next=UUID.randomUUID();
        try(var store=new BuildJournal(directory,Runnable::run)) {
            store.backup(new BuildJournal.Inventory(admin,old,new byte[]{1},new byte[0],0)).join();
            store.backup(new BuildJournal.Inventory(admin,next,new byte[]{2},new byte[0],1)).join();
            assertEquals(1,store.inventory(admin,old).orElseThrow().contents()[0]);
            store.restored(admin,next).join();store.acknowledge(admin,next).join();assertTrue(store.inventory(admin,next).isEmpty());
        }
        try(var store=new BuildJournal(directory,Runnable::run)) {assertTrue(store.inventory(admin,old).isPresent());}
    }
    @Test void sameTokenCannotOverwriteOriginalAndPathTraversalIsRejected() {
        var token=UUID.randomUUID();
        var store=new BuildJournal(directory,Runnable::run);
        store.backup(new BuildJournal.Inventory(admin,token,new byte[]{1},new byte[0],0)).join();
        assertThrows(Exception.class,()->store.backup(new BuildJournal.Inventory(admin,token,new byte[]{2},new byte[0],0)).join());
        assertThrows(IllegalArgumentException.class,()->store.draft(admin,"../escape"));
        assertThrows(java.util.concurrent.CompletionException.class,store::close);
        try(var reloaded=new BuildJournal(directory,Runnable::run)) {assertEquals(1,reloaded.inventory(admin,token).orElseThrow().contents()[0]);}
    }
    @Test void latestBackupUsesDurableOrderAcrossRestartAndAcknowledgementRetiresAllOlderTokens() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        try(var store=new BuildJournal(directory,Runnable::run)) {
            store.backup(new BuildJournal.Inventory(admin,first,new byte[]{1},new byte[0],0)).join();
            store.backup(new BuildJournal.Inventory(admin,second,new byte[]{2},new byte[0],0)).join();
        }
        try(var store=new BuildJournal(directory,Runnable::run)) {
            assertEquals(second,store.latestInventory(admin).orElseThrow().token());
            store.restored(admin,first).join();store.acknowledge(admin,first).join();
            store.restored(admin,second).join();store.acknowledge(admin,second).join();assertTrue(store.latestInventory(admin).isEmpty());
        }
        try(var store=new BuildJournal(directory,Runnable::run)) {assertTrue(store.latestInventory(admin).isEmpty());}
    }
    @Test void activeGenerationCannotBeRetiredWithoutRestoration() {
        UUID token=UUID.randomUUID();var store=new BuildJournal(directory,Runnable::run);
        store.backup(new BuildJournal.Inventory(admin,token,new byte[]{7},new byte[0],0)).join();
        assertThrows(java.util.concurrent.CompletionException.class,()->store.acknowledge(admin,token).join());
        assertTrue(store.inventory(admin,token).isPresent());
        store.restored(admin,token).join();store.close();
    }
    @Test void restorationStateSurvivesCrashWithoutRemovingOriginalBytes() {
        UUID token=UUID.randomUUID();
        try(var store=new BuildJournal(directory,Runnable::run)) {
            store.backup(new BuildJournal.Inventory(admin,token,new byte[]{7},new byte[0],0)).join();
            store.restored(admin,token).join();
        }
        try(var store=new BuildJournal(directory,Runnable::run)) {
            var original=store.inventory(admin,token).orElseThrow();
            assertEquals(BuildJournal.InventoryState.RESTORED,original.state());assertEquals(7,original.contents()[0]);
        }
    }
    @Test void corruptRecoveryRecordFailsClosedAndNeverDeletesTheOriginalFile() throws Exception {
        UUID token=UUID.randomUUID();
        try(var store=new BuildJournal(directory,Runnable::run)) {store.backup(new BuildJournal.Inventory(admin,token,new byte[]{1},new byte[0],0)).join();}
        Path file=directory.resolve("build-inventories/"+admin+"--"+token+".bin");byte[] bytes=Files.readAllBytes(file);bytes[10]^=1;Files.write(file,bytes);
        assertThrows(IllegalStateException.class,()->new BuildJournal(directory,Runnable::run));assertTrue(Files.exists(file));
    }
}
