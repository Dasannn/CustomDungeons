package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.gui.wizard.WizardDraftStore;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.construction.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LootDurableSourcesTest {
    @TempDir Path directory;
    DungeonDef definition() {
        var f=new LootChestServiceTest();var d=f.definition(LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false);
        return dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,List.of(d.rooms().getFirst().withLootChests(List.of(
                new LootChestDef("loot",LootChestServiceTest.POSITION,LootChestDef.Trigger.ROOM_ENTER,List.of())))));
    }
    @Test void zonesLootAndAdminInventorySurviveDraftReloadAndRepeatedRecoveryTogether() {
        var zoneFixture=new OptionalZoneRuntimeTest();
        var d=definition().withOptionalZones(zoneFixture.definition(1,false).optionalZones());
        var state=new BuildState(d);state.origin(dev.dasan.customdungeons.tool.EditOrigin.zone("a"));state.cyclePoint();
        state.change(d.withOptionalZones(List.of(d.optionalZones().getFirst())));
        var admin=UUID.randomUUID();var token=UUID.randomUUID();
        byte[] contents={1,0,-1,42},cursor={8,9};
        var f=new LootChestServiceTest();var placed=f.loot.place(d.id(),"first",d.rooms().getFirst().lootChests().getFirst());f.loot.tick();placed.join();
        try(var build=new BuildJournal(directory,Runnable::run);var wizard=new WizardDraftStore(directory,Runnable::run,p->fail(p))) {
            build.backup(new BuildJournal.Inventory(admin,token,contents,cursor,8)).join();
            build.save(admin,state.snapshot()).join();wizard.save(new WizardDraftStore.Saved(d,3,3)).join();
        }
        // No published definition or live editor remains after the crash.
        for(int restart=0;restart<2;restart++) {
            try(var build=new BuildJournal(directory,Runnable::run);var wizard=new WizardDraftStore(directory,Runnable::run,p->fail(p))) {
                var resumed=new BuildState(build.draft(admin,d.id()).orElseThrow());
                assertEquals(state.snapshot(),resumed.snapshot());assertTrue(resumed.undo());assertEquals(d,resumed.definition());
                assertEquals(List.of(d),wizard.durableDefinitions());
                var inventory=build.inventory(admin,token).orElseThrow();assertArrayEquals(contents,inventory.contents());assertArrayEquals(cursor,inventory.cursor());assertEquals(8,inventory.held());
                var durable=new ArrayList<>(build.durableDefinitions());durable.addAll(wizard.durableDefinitions());
                var recovered=new LootChestService(f.store,f.blocks,f.store.loadLootBlocks().join(),()->{fail("Authoring recovery cannot generate loot");return 0;},f.failures::add);
                recovered.reconcileAll(durable);for(int i=0;i<12;i++)recovered.tick();
                assertEquals("minecraft:chest[type=single]",f.blocks.data);assertNull(f.blocks.contents[0]);assertFalse(recovered.blocked(d.id()));
                assertEquals(LootChestServiceTest.ORIGINAL,f.store.records.get(LootChestServiceTest.POSITION).placementData());
                if(restart==1) {
                    // Removing the final durable references restores placement, without consuming the admin backup.
                    var removed=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,d.rooms().stream().map(r->r.withLootChests(List.of())).toList());
                    build.save(admin,new BuildState(removed).snapshot()).join();wizard.delete(d.id()).join();
                    recovered.reconcileAll(build.durableDefinitions());for(int i=0;i<12;i++)recovered.tick();
                    assertEquals(LootChestServiceTest.ORIGINAL,f.blocks.data);assertTrue(build.inventory(admin,token).isPresent());
                }
            }
        }
        assertTrue(f.failures.isEmpty(),f.failures::toString);
    }
    @Test void buildPlacementCountsOnlyAfterAtomicDraftWriteAndAfterReload() {
        var worker=new ArrayDeque<Runnable>();var d=definition();var admin=UUID.randomUUID();
        try(var journal=new BuildJournal(directory,worker::add)) {
            var write=journal.save(admin,new BuildState(d).snapshot());
            assertTrue(journal.durableDefinitions().isEmpty());worker.remove().run();write.join();
            assertEquals(List.of(d),journal.durableDefinitions());
            try(var loaded=new BuildJournal(directory,Runnable::run)) {assertEquals(List.of(d),loaded.durableDefinitions());}
        }
    }
    @Test void wizardPlacementCountsOnlyAfterConfirmedWriteAndDeletion() {
        var worker=new ArrayDeque<Runnable>();var d=definition();
        try(var journal=new WizardDraftStore(directory,worker::add,p->fail(p))) {
            var write=journal.save(new WizardDraftStore.Saved(d,3,3));assertTrue(journal.durableDefinitions().isEmpty());
            worker.remove().run();write.join();assertEquals(List.of(d),journal.durableDefinitions());
            try(var loaded=new WizardDraftStore(directory,Runnable::run,p->fail(p))) {assertEquals(List.of(d),loaded.durableDefinitions());}
            var deleted=journal.delete(d.id());assertEquals(List.of(d),journal.durableDefinitions());
            worker.remove().run();deleted.join();assertTrue(journal.durableDefinitions().isEmpty());
        }
    }
    @Test void failedBuildWriteDoesNotLegitimizeAnInMemoryDraft() throws Exception {
        var d=definition();try(var journal=new BuildJournal(directory,Runnable::run)) {
            var path=directory.resolve("build-drafts");Files.delete(path);Files.writeString(path,"blocked");
            assertTrue(journal.save(UUID.randomUUID(),new BuildState(d).snapshot()).isCompletedExceptionally());
            assertTrue(journal.durableDefinitions().isEmpty());
            // Drain the failed generation with a successful write before close.
            Files.delete(path);Files.createDirectory(path);journal.save(UUID.randomUUID(),new BuildState(d).snapshot()).join();
        }
    }
    @Test void publishedAndDurableIdentitiesSurviveReconciliationAndRoomReordering() {
        var d=definition();var f=new LootChestServiceTest();var room=d.rooms().getFirst();
        var placed=f.loot.place(d.id(),room.id(),room.lootChests().getFirst());f.loot.tick();placed.join();
        var untouched=new RoomDef("other",null,null,null,UnlockMode.AUTOMATIC,null,List.of());
        var reordered=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,List.of(untouched,room));
        f.loot.reconcileAll(List.of(reordered));for(int i=0;i<8;i++)f.loot.tick();
        assertEquals("minecraft:chest[type=single]",f.blocks.data);assertFalse(f.loot.blocked(d.id()));
        f.loot.reconcileAll(List.of());for(int i=0;i<8;i++)f.loot.tick();assertEquals(LootChestServiceTest.ORIGINAL,f.blocks.data);
    }
    @Test void failedWizardWriteKeepsPreviousDurableVersion() throws Exception {
        var d=definition();try(var journal=new WizardDraftStore(directory,Runnable::run,p->fail(p))) {
            journal.save(new WizardDraftStore.Saved(d,3,3)).join();
            var target=directory.resolve("wizard-drafts").resolve(d.id()+".yml");Files.delete(target);Files.createDirectory(target);Files.writeString(target.resolve("blocker"),"x");
            var removed=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,List.of());
            assertTrue(journal.save(new WizardDraftStore.Saved(removed,3,3)).isCompletedExceptionally());
            assertEquals(List.of(d),journal.durableDefinitions());
            Files.delete(target.resolve("blocker"));Files.delete(target);journal.save(new WizardDraftStore.Saved(d,3,3)).join();
        }
    }    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"publication","wizard","build"})
    void startupUsesPublishedAndReloadedDurableDraftsAsItsOnlyAuthority(String source) {
        var d=definition();var admin=UUID.randomUUID();
        try(var build=new BuildJournal(directory,Runnable::run);var wizard=new WizardDraftStore(directory,Runnable::run,p->fail(p))) {
            if(source.equals("build"))build.save(admin,new BuildState(d).snapshot()).join();
            if(source.equals("wizard"))wizard.save(new WizardDraftStore.Saved(d,3,3)).join();
        }
        try(var build=new BuildJournal(directory,Runnable::run);var wizard=new WizardDraftStore(directory,Runnable::run,p->fail(p))) {
            var plugin=org.mockito.Mockito.mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class,org.mockito.Mockito.RETURNS_DEEP_STUBS);
            var definitions=org.mockito.Mockito.mock(dev.dasan.customdungeons.config.DefinitionStore.class);
            var published=new HashMap<String,DungeonDef>();if(source.equals("publication"))published.put(d.id(),d);
            org.mockito.Mockito.when(definitions.dungeons()).thenReturn(published);
            var mode=org.mockito.Mockito.mock(dev.dasan.customdungeons.tool.BuildModeService.class);
            org.mockito.Mockito.when(mode.journal()).thenReturn(build);
            org.mockito.Mockito.when(plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.tool.BuildModeService.class)).thenReturn(mode);
            org.mockito.Mockito.when(plugin.getServer().getServicesManager().load(WizardDraftStore.class)).thenReturn(wizard);
            var f=new LootChestServiceTest();var room=d.rooms().getFirst();
            var placed=f.loot.place(d.id(),room.id(),room.lootChests().getFirst());f.loot.tick();placed.join();
            var restart=new LootChestService(f.store,f.blocks,List.copyOf(f.store.records.values()),()->0,f.failures::add);
            restart.reconcileAll(SessionManager.lootDefinitions(plugin,definitions));for(int tick=0;tick<20;tick++)restart.tick();
            assertEquals("minecraft:chest[type=single]",f.blocks.data);assertFalse(restart.blocked(d.id()));
            // Retire the last durable owner, then reconcile again without any Player or editor.
            published.clear();wizard.delete(d.id()).join();
            var empty=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,List.of());build.save(admin,new BuildState(empty).snapshot()).join();
            restart.reconcileAll(SessionManager.lootDefinitions(plugin,definitions));for(int tick=0;tick<20;tick++)restart.tick();
            assertEquals(LootChestServiceTest.ORIGINAL,f.blocks.data);assertFalse(restart.blocked(d.id()));
        }
    }
    @Test void publicationDuringRemovalBackupCancelsCleanupOfTheNowLegitimateChest() {
        var d=definition();var f=new LootChestServiceTest();var room=d.rooms().getFirst();
        var placed=f.loot.place(d.id(),room.id(),room.lootChests().getFirst());f.loot.tick();placed.join();
        var pending=new java.util.concurrent.CompletableFuture<Void>();f.store.next=pending;
        var result=f.loot.reconcile(d.id(),List.of());assertFalse(result.isDone());
        assertSame(result,f.loot.reconcile(d.id(),List.of(d)));pending.complete(null);
        for(int i=0;i<12;i++)f.loot.tick();result.join();
        assertEquals("minecraft:chest[type=single]",f.blocks.data);assertFalse(f.loot.blocked(d.id()));
        f.loot.reconcileAll(List.of());for(int i=0;i<12;i++)f.loot.tick();assertEquals(LootChestServiceTest.ORIGINAL,f.blocks.data);
    }
    @Test void legitimateChestInAnActiveSessionDoesNotCreateAPendingReconciliation() {
        var f=new LootChestServiceTest();var d=f.definition(LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false);
        var placed=f.loot.place(d.id(),"first",f.chest(LootChestDef.Trigger.ROOM_ENTER));f.loot.tick();placed.join();
        f.start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,f.player());f.generate();
        assertTrue(f.loot.reconcile(d.id(),List.of(d)).isDone());assertFalse(f.loot.blocked(d.id()));
    }
    @Test void orphanHeldByAnActiveSessionResumesCleanupAtSessionReset() {
        var f=new LootChestServiceTest();var d=f.definition(LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false);
        var placed=f.loot.place(d.id(),"first",f.chest(LootChestDef.Trigger.ROOM_ENTER));f.loot.tick();placed.join();
        f.start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,f.player());f.generate();
        var result=f.loot.reconcile(d.id(),List.of());assertFalse(result.isDone());
        int writes=f.store.writes;for(int i=0;i<30;i++)f.loot.tick();assertEquals(writes,f.store.writes);
        f.session.finish(true);f.reset();for(int i=0;i<12;i++)f.loot.tick();result.join();
        assertEquals(LootChestServiceTest.ORIGINAL,f.blocks.data);assertFalse(f.loot.blocked(d.id()));
    }

}
