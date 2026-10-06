package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalKeyTest {
    final SessionRuntimeRegressionTest fixture = new SessionRuntimeRegressionTest();
    DungeonDef puzzle() {
        var def = fixture.definition(); var r = def.rooms().getFirst();
        var room = new RoomDef(r.id(),r.region(),r.checkpoint(),
                Region.of("world",new BlockPos(10,64,10),new BlockPos(10,67,12)),
                UnlockMode.KEY,"*",List.of(),RoomDef.OpeningMode.EXTERNAL_KEY);
        return SpawnerPresets.withRooms(def,List.of(room,def.rooms().getLast()));
    }
    @Test void clearingPuzzleNeverOpensDoorOrCreatesKeyAndRepeatedTicksStayInRoom() {
        fixture.configure(); var manager = mock(SessionManager.class);
        var runtime = new DungeonSessionRuntime(fixture.plugin,manager,fixture.definitions,fixture.config,fixture.storage);
        // Real session progression; bypass chunk preparation and visual effects only.
        var services = new SessionServices() {
            @Override public void roomCleared(DungeonSession session) { runtime.roomCleared(session); }
        };
        var session = new DungeonSession(puzzle(),false,services); runtime.attach(session);
        runtime.keys = mock(KeyService.class); runtime.doors = mock(DoorService.class);
        var player = mock(Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        session.join(player); session.tick(); session.enterRoom(0);
        for (int i=0; i<20; i++) session.tick();
        assertEquals(SessionState.RUNNING,session.state().state()); assertEquals(0,session.roomIndex());
        assertFalse(session.roomStarted()); verify(runtime.keys).roomCleared();
        verifyNoMoreInteractions(runtime.keys); verifyNoInteractions(runtime.doors);
    }
    @Test void puzzleNeverRecordsCarrierOrCreatesMobKey() throws Exception {
        var session = mock(DungeonSession.class); when(session.def()).thenReturn(puzzle());
        var keys = new KeyService(session,mock(DoorService.class));
        var mob = mock(ActiveMob.class);
        keys.carrierDied(mob); keys.create();
        var field = KeyService.class.getDeclaredField("room"); field.setAccessible(true);
        assertEquals(-1,field.get(keys)); verifyNoInteractions(mob);
    }
    @Test void commandDeliveryRejectsMissingWrongLobbyAndEliminatedSessionsBeforeTouchingInventory() {
        var manager = mock(SessionManager.class); var player = mock(Player.class);
        UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        assertEquals(KeyService.GiveResult.NO_SESSION,KeyService.give(manager,player,null));
        var session = mock(DungeonSession.class); when(manager.sessionOf(id)).thenReturn(Optional.of(session));
        var state = new SessionStateMachine(); when(session.state()).thenReturn(state);
        when(session.def()).thenReturn(puzzle()); when(session.survivors()).thenReturn(Set.of(id));
        state.openLobby(); assertEquals(KeyService.GiveResult.NO_SESSION,KeyService.give(manager,player,"test"));
        state.start(); assertEquals(KeyService.GiveResult.NO_SESSION,KeyService.give(manager,player,"other"));
        when(session.survivors()).thenReturn(Set.of());
        assertEquals(KeyService.GiveResult.NO_SESSION,KeyService.give(manager,player,"test"));
        verify(player,never()).getInventory(); verify(manager,never()).runtime(any());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false","false,true","true,false","true,true"})
    void commandKeyUsesT28MaterialPdcAndOpensClearedPuzzleAtFourBlocks(boolean fullInventory, boolean issuedBeforeClear) throws Exception {
        fixture.configure(); var manager = mock(SessionManager.class); var session = mock(DungeonSession.class);
        when(session.def()).thenReturn(puzzle()); when(session.id()).thenReturn(UUID.randomUUID());
        var state = new SessionStateMachine(); state.openLobby(); state.start(); when(session.state()).thenReturn(state);
        var player = mock(Player.class); UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Ana"); when(session.survivors()).thenReturn(Set.of(id));
        when(session.players()).thenReturn(List.of(player));
        var inventory = mock(PlayerInventory.class); when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[0]); when(inventory.addItem(any(ItemStack.class))).thenAnswer(c -> fullInventory ? new HashMap<>(Map.of(0,(ItemStack)c.getArgument(0))) : new HashMap<>());
        when(manager.sessionOf(id)).thenReturn(Optional.of(session));
        var runtime = fixture.runtime(manager,session); when(manager.runtime(session)).thenReturn(runtime);
        var doors = mock(DoorService.class); var keys = new KeyService(session,doors); runtime.keys = keys;
        when(player.getLocation()).thenReturn(new Location(fixture.world,6,65,11));
        var meta = mock(ItemMeta.class); var pdc = mock(PersistentDataContainer.class);
        var tokens = new HashMap<NamespacedKey,String>(); when(meta.getPersistentDataContainer()).thenReturn(pdc);
        doAnswer(c -> { tokens.put(c.getArgument(0),c.getArgument(2)); return null; }).when(pdc).set(any(),eq(PersistentDataType.STRING),anyString());
        when(pdc.get(any(),eq(PersistentDataType.STRING))).thenAnswer(c -> tokens.get(c.getArgument(0)));
        when(doors.open(eq(0),any(Runnable.class))).thenAnswer(c -> {
            ((Runnable)c.getArgument(1)).run(); return java.util.concurrent.CompletableFuture.completedFuture(true);
        });
        var overflow=mock(Item.class); when(overflow.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        var fallback=new Location(fixture.world,6,65,11); when(fixture.world.dropItem(same(fallback),any())).thenReturn(overflow);
        var messages=fixture.plugin.messages();
        try (var bukkit = mockStatic(Bukkit.class); var staticRuntime = mockStatic(DungeonSessionRuntime.class); var staticDoors=mockStatic(DoorService.class);
             var stacks = mockConstruction(ItemStack.class,(stack,context) -> {
                 assertEquals(Material.TRIPWIRE_HOOK,context.arguments().getFirst());
                 when(stack.getItemMeta()).thenReturn(meta); when(stack.getPersistentDataContainer()).thenReturn(pdc);
             })) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            staticRuntime.when(DungeonSessionRuntime::messages).thenReturn(messages);
            staticDoors.when(() -> DoorService.keyRespawn(any())).thenReturn(fallback);
            if(!issuedBeforeClear) runtime.roomCleared(session);
            assertEquals(KeyService.GiveResult.GIVEN,KeyService.give(manager,player,"test"));
            ItemStack key = stacks.constructed().getFirst();
            verify(inventory).addItem(key); assertTrue(keys.matches(key));
            if(fullInventory) {
                verify(fixture.world).dropItem(fallback,key); verify(overflow).setUnlimitedLifetime(true);
            } else verify(fixture.world,never()).dropItem(any(),any());
            assertEquals(session.id()+":first",tokens.get(MobKeys.KEY_ITEM));
            verify(meta).displayName(fixture.plugin.messages().get("session.key")); verify(meta).setEnchantmentGlintOverride(true);
            if(issuedBeforeClear) {
                assertFalse(keys.use(player,null,key)); verifyNoInteractions(doors);
                runtime.roomCleared(session);
            }
            when(session.roomStarted()).thenReturn(true);
            assertFalse(keys.use(player,null,key)); verifyNoInteractions(doors);
            when(session.roomStarted()).thenReturn(false);
            assertTrue(keys.use(player,null,key)); verify(doors).open(eq(0),any(Runnable.class)); assertFalse(keys.matches(key));
            assertEquals(1,stacks.constructed().size());
        }
    }
    @Test void clearingRemovesAllCommandCopiesFromWorldButKeepsForeignItems() throws Exception {
        var session=mock(DungeonSession.class);when(session.def()).thenReturn(puzzle());when(session.id()).thenReturn(UUID.randomUUID());
        var keys=new KeyService(session,mock(DoorService.class));
        SessionRuntimeRegressionTest.field(keys,"room",0);
        var first=mock(Item.class);var second=mock(Item.class);var foreign=mock(Item.class);
        var firstStack=AnticipatedKeyLifecycleTest.key(session.id()+":first");
        var secondStack=AnticipatedKeyLifecycleTest.key(session.id()+":previous");
        var foreignStack=AnticipatedKeyLifecycleTest.key(UUID.randomUUID()+":first");
        when(first.getItemStack()).thenReturn(firstStack);when(second.getItemStack()).thenReturn(secondStack);when(foreign.getItemStack()).thenReturn(foreignStack);
        when(fixture.world.getEntitiesByClass(Item.class)).thenReturn(List.of(first,second,foreign));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(fixture.world));
            keys.clear();verify(first).remove();verify(second).remove();verify(foreign,never()).remove();
        }
    }

}
