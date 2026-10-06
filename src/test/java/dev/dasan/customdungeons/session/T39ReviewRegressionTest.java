package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class T39ReviewRegressionTest {
    @Test void automaticRoomCommandDropMustNotSuppressNextRoomsKey() throws Exception {
        var fixture = new SessionRuntimeRegressionTest(); fixture.configure();
        var def = fixture.definition(); var first = def.rooms().getFirst();
        var door = Region.of("world",new BlockPos(14,64,1),new BlockPos(14,66,1));
        var auto = new RoomDef(first.id(),first.region(),first.checkpoint(),door,UnlockMode.AUTOMATIC,null,List.of());
        var next = new RoomDef("locked",first.region(),first.checkpoint(),door,UnlockMode.KEY,"*",List.of());
        var session = mock(DungeonSession.class); var id = UUID.randomUUID();
        when(session.id()).thenReturn(id);
        when(session.def()).thenReturn(SpawnerPresets.withRooms(def,List.of(auto,next,def.rooms().getLast())));
        var state = new SessionStateMachine(); state.openLobby(); state.start(); when(session.state()).thenReturn(state);
        var manager = mock(SessionManager.class); var player = mock(Player.class); var playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId); when(session.survivors()).thenReturn(Set.of(playerId));
        when(session.players()).thenReturn(List.of(player)); when(manager.sessionOf(playerId)).thenReturn(Optional.of(session));
        var inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(inventory.addItem(any(ItemStack.class))).thenAnswer(c -> new HashMap<>(Map.of(0,(ItemStack)c.getArgument(0))));
        var runtime = fixture.runtime(manager,session); when(manager.runtime(session)).thenReturn(runtime);
        var doors = mock(DoorService.class); runtime.doors = doors;
        when(doors.open(0)).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
        var old = mock(Item.class); var at = new Location(fixture.world,2,64,2);
        when(old.isValid()).thenReturn(true); when(old.getLocation()).thenReturn(at);
        when(old.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(fixture.world.dropItem(same(at),any(ItemStack.class))).thenAnswer(c -> {
            when(old.getItemStack()).thenReturn(c.getArgument(1)); return old;
        });
        var messages = fixture.plugin.messages();
        try (var bukkit = mockStatic(Bukkit.class); var staticRuntime = mockStatic(DungeonSessionRuntime.class);
             var staticDoors = mockStatic(DoorService.class);
             var stacks = mockConstruction(ItemStack.class,(stack,context) -> {
                 var meta = mock(org.bukkit.inventory.meta.ItemMeta.class); var data = mock(PersistentDataContainer.class);
                 var tokens = new HashMap<NamespacedKey,String>();
                 when(stack.getItemMeta()).thenReturn(meta); when(meta.getPersistentDataContainer()).thenReturn(data);
                 when(stack.getPersistentDataContainer()).thenReturn(data);
                 doAnswer(c -> {tokens.put(c.getArgument(0),c.getArgument(2));return null;}).when(data).set(any(),eq(PersistentDataType.STRING),anyString());
                 when(data.get(any(),eq(PersistentDataType.STRING))).thenAnswer(c -> tokens.get(c.getArgument(0)));
             })) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            staticRuntime.when(DungeonSessionRuntime::messages).thenReturn(messages);
            staticRuntime.when(() -> DungeonSessionRuntime.contains(any(),any())).thenCallRealMethod();
            staticDoors.when(() -> DoorService.keyRespawn(any())).thenReturn(at);
            assertEquals(KeyService.GiveResult.GIVEN,KeyService.give(manager,player,"test"));
            var original = old.getItemStack(); assertTrue(runtime.keys.matches(original));
            runtime.roomCleared(session); verify(doors).open(0);
            // DoorService.open advances the session; emulate only that boundary here.
            when(session.roomIndex()).thenReturn(1);
            runtime.roomCleared(session);
            assertFalse(runtime.keys.matches(original),"The tracked drop is now a foreign room key");
            assertEquals(2,stacks.constructed().size(),"A usable key must be created for the new KEY room");
        }
    }

    @Test void cleanupMustRemovePriorAutomaticRoomCommandCopies() throws Exception {
        var fixture = new SessionRuntimeRegressionTest(); fixture.configure();
        var def = fixture.definition(); var first = def.rooms().getFirst();
        var door = Region.of("world",new BlockPos(14,64,1),new BlockPos(14,66,1));
        var next = new RoomDef("locked",first.region(),first.checkpoint(),door,UnlockMode.KEY,"*",List.of());
        var session = mock(DungeonSession.class); var id = UUID.randomUUID();
        when(session.id()).thenReturn(id); when(session.def()).thenReturn(SpawnerPresets.withRooms(def,List.of(first,next,def.rooms().getLast())));
        when(session.roomIndex()).thenReturn(1);
        var player = mock(Player.class); var inventory = mock(PlayerInventory.class); var oldStack = mock(ItemStack.class);
        var data = mock(PersistentDataContainer.class);
        when(player.getInventory()).thenReturn(inventory); when(inventory.getSize()).thenReturn(41); when(inventory.getItem(0)).thenReturn(oldStack);
        when(oldStack.getPersistentDataContainer()).thenReturn(data); when(data.get(MobKeys.KEY_ITEM,PersistentDataType.STRING)).thenReturn(id+":first");
        when(session.players()).thenReturn(List.of(player));
        var keys = new KeyService(session,mock(DoorService.class));
        // create() switches the service to room 1; inventory copies of room 0 were not removed.
        SessionRuntimeRegressionTest.field(keys,"room",1);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            keys.clear();
        }
        verify(inventory).setItem(0,null);
    }
}
