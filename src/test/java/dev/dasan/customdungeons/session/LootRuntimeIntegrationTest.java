package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootRuntimeIntegrationTest {
    @Test void testCommandDefersUnloadedLootInspectionToAsyncSessionPreparation() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var validator=new dev.dasan.customdungeons.config.Validator();
        var f=new SessionRuntimeRegressionTest();f.configure();var fixture=new LootChestServiceTest();
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            when(f.world.getMinHeight()).thenReturn(-64);when(f.world.getMaxHeight()).thenReturn(320);when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
            when(f.world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(c->new CompletableFuture<Chunk>());
            var base=new OptionalZoneRuntimeTest().definition(1,false);
            var first=base.rooms().getFirst();
            var source=first.spawners().getFirst();
            var room=new RoomDef(first.id(),first.region(),first.checkpoint(),null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef(source.id(),source.location(),1,source.waves())))
                    .withLootChests(List.of(new LootChestDef("loot",LootChestServiceTest.POSITION,LootChestDef.Trigger.ROOM_ENTER,List.of())));
            var def=new DungeonDef("test","",true,base.lobby(),base.exit(),1,2,5,3,false,0,0,false,new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room));
            var mob=new MobTemplate("zombie","ZOMBIE","",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
            when(f.definitions.dungeons()).thenReturn(Map.of("test",def));when(f.definitions.mobs()).thenReturn(Map.of("zombie",mob));
            when(f.storage.addPendingExit(any(),any())).thenReturn(CompletableFuture.completedFuture(null));
            var errors=validator.validate(def,f.definitions.mobs());
            assertFalse(errors.isEmpty());assertTrue(errors.stream().allMatch(e->e.messageKey().equals("validation.loot-block-unprepared")),errors.toString());
            var manager=new SessionManager(f.plugin,f.definitions,f.config,f.storage);var player=fixture.player();
            when(player.getInventory()).thenReturn(mock(org.bukkit.inventory.PlayerInventory.class));manager.startTest(player,"test");
            assertEquals(SessionState.LOBBY,manager.session("test").orElseThrow().state().state());
            verify(f.world,atLeastOnce()).getChunkAtAsync(anyInt(),anyInt());verify(f.world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
            manager.shutdown();
        }
    }
    @Test void positionIndexTracksPublishedDefinitionsAndIgnoresIncompleteDisabledLocations() {
        var f=new SessionRuntimeRegressionTest();f.configure();var fixture=new LootChestServiceTest();
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            var def=f.definition();var room=def.rooms().getFirst().withLootChests(List.of(fixture.chest(LootChestDef.Trigger.ROOM_ENTER),new LootChestDef("incomplete",null,LootChestDef.Trigger.ROOM_CLEAR,List.of())));
            when(f.definitions.dungeons()).thenReturn(Map.of("test",SpawnerPresets.withRooms(def,List.of(room))));
            var manager=new SessionManager(f.plugin,f.definitions,f.config,f.storage);
            assertTrue(manager.lootRegistered(LootChestServiceTest.POSITION));assertTrue(manager.lootRegistered(LootChestServiceTest.POSITION));
            when(f.definitions.dungeons()).thenReturn(Map.of("test",def));assertFalse(manager.lootRegistered(LootChestServiceTest.POSITION));
            manager.shutdown();
        }
    }
    @Test void realRuntimeTriggersClearBeforeCreatingKeyAndFinalLifecycleNotification() {
        var f=new SessionRuntimeRegressionTest();f.configure();
        var lootFixture=new LootChestServiceTest();
        var manager=mock(SessionManager.class);when(manager.loot()).thenReturn(lootFixture.loot);when(manager.blockJournal()).thenReturn(new SessionTempBlocks.Journal());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(f.world));when(f.world.getEntities()).thenReturn(List.of());
            when(f.world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(call->{
                var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(f.world);when(chunk.getX()).thenReturn(call.getArgument(0));when(chunk.getZ()).thenReturn(call.getArgument(1));
                return CompletableFuture.completedFuture(chunk);
            });
            var base=f.definition();var first=base.rooms().getFirst();
            first=new RoomDef(first.id(),first.region(),first.checkpoint(),first.door(),UnlockMode.KEY,null,List.of())
                    .withLootChests(List.of(lootFixture.chest(LootChestDef.Trigger.ROOM_CLEAR)));
            var def=SpawnerPresets.withRooms(base,List.of(first,base.rooms().getLast()));
            var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);
            var session=new DungeonSession(def,false,runtime);runtime.attach(session);
            var p=lootFixture.player();session.join(p);session.forceStart();
            assertEquals(SessionState.RUNNING,session.state().state());
            runtime.keys=mock(KeyService.class);
            doAnswer(c->{assertNotNull(lootFixture.blocks.contents[0]);return null;}).when(runtime.keys).create();
            session.enterRoom(0);session.tick();verify(runtime.keys).create();assertEquals(1,lootFixture.draws);
            session.addListener(new SessionLifecycleListener(){public void onFinished(DungeonSession s,dev.dasan.customdungeons.storage.RunResult result,Set<UUID> survivors){assertNotNull(lootFixture.blocks.contents[0]);}});
            session.openDoor();session.enterRoom(1);session.tick();assertEquals(SessionState.COMPLETED,session.state().state());assertEquals(1,lootFixture.draws);
        }
    }
    @Test void realRuntimeFinalRoomClearPrecedesCompletionAndDoesNotRestoreDuringNoneEvacuation() {
        var f=new SessionRuntimeRegressionTest();f.configure();var fixture=new LootChestServiceTest();
        var manager=mock(SessionManager.class);when(manager.loot()).thenReturn(fixture.loot);when(manager.blockJournal()).thenReturn(new SessionTempBlocks.Journal());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(f.world);bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
            when(f.world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(c->{var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(f.world);return CompletableFuture.completedFuture(chunk);});
            var def=SpawnerPresets.withRooms(f.definition(),List.of(f.definition().rooms().getFirst().withLootChests(List.of(fixture.chest(LootChestDef.Trigger.ROOM_CLEAR)))))
                    .withFinish(FinishMode.NONE,60,FinishDestination.EXIT,List.of());
            var runtime=new DungeonSessionRuntime(f.plugin,manager,f.definitions,f.config,f.storage);var session=new DungeonSession(def,false,runtime);runtime.attach(session);runtime.keys=mock(KeyService.class);
            session.addListener(new SessionLifecycleListener(){public void onFinished(DungeonSession s,dev.dasan.customdungeons.storage.RunResult result,Set<UUID> survivors){assertEquals(1,fixture.draws);assertNotNull(fixture.blocks.contents[0]);}});
            session.join(fixture.player());session.forceStart();session.enterRoom(0);session.tick();
            assertEquals(SessionState.COMPLETED,session.state().state());assertEquals(0,fixture.blocks.restores);
        }
    }
}
