package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KeyUseTest {
    final World world = mock(World.class);
    final DungeonSession session = mock(DungeonSession.class);
    final DoorService doors = mock(DoorService.class);
    final Player player = mock(Player.class);
    final ItemStack key = mock(ItemStack.class);
    final KeyService keys = spy(new KeyService(session, doors));
    final Region door = Region.of("world", new BlockPos(10,64,10), new BlockPos(10,67,12));

    KeyUseTest() throws Exception {
        var def = new SessionRuntimeRegressionTest().definition();
        var rooms = new ArrayList<>(def.rooms());
        var r = rooms.getFirst();
        rooms.set(0, new RoomDef(r.id(), r.region(), r.checkpoint(), door, UnlockMode.KEY, "*", r.spawners()));
        when(session.def()).thenReturn(new DungeonDef(def.id(), def.displayName(), def.enabled(), def.lobby(), def.exit(),
                def.minPlayers(), def.maxPlayers(), def.lobbyCountdownSeconds(), def.lives(), def.keepInventory(), def.timeLimitSeconds(),
                def.cooldownSeconds(), def.requirePermission(), def.scaling(), def.hooks(), def.reward(), rooms));
        when(session.state()).thenReturn(mock(SessionStateMachine.class));
        when(session.state().state()).thenReturn(SessionState.RUNNING);
        when(world.getName()).thenReturn("world");
        UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id); when(session.survivors()).thenReturn(Set.of(id));
        SessionRuntimeRegressionTest.field(keys, "room", 0); keys.roomCleared();
        doReturn(true).when(keys).matches(key); doNothing().when(keys).clear();
        doAnswer(call -> { ((Runnable)call.getArgument(1)).run(); return java.util.concurrent.CompletableFuture.completedFuture(true); })
                .when(doors).open(anyInt(),any(Runnable.class));
    }
    @Test void unenteredRoomCannotConsumeCommandKey() throws Exception {
        var waiting=spy(new KeyService(session,doors));
        SessionRuntimeRegressionTest.field(waiting,"room",0);
        doReturn(true).when(waiting).matches(key); doNothing().when(waiting).clear();
        when(player.getLocation()).thenReturn(new Location(world,6,65,11));
        assertFalse(waiting.use(player,null,key));
        verifyNoInteractions(doors);
    }
    @Test void failedDoorPersistenceKeepsKeyAndAllowsRetry() throws Exception {
        var fixture=new DoorServiceTest();
        var player=mock(Player.class); var id=UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id); when(player.getLocation()).thenReturn(new Location(fixture.world,0,64,0));
        when(fixture.session.survivors()).thenReturn(Set.of(id));
        var keys=spy(new KeyService(fixture.session,fixture.doors));
        SessionRuntimeRegressionTest.field(keys,"room",0); keys.roomCleared(); doReturn(true).when(keys).matches(key); doNothing().when(keys).clear();
        var write=new java.util.concurrent.CompletableFuture<Void>();
        when(fixture.storage.addTempBlock(any())).thenReturn(write);
        var messages=mock(Messages.class);
        try(var bukkit=mockStatic(Bukkit.class); var runtime=mockStatic(DungeonSessionRuntime.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(fixture.world);
            bukkit.when(Bukkit::getLogger).thenReturn(mock(java.util.logging.Logger.class));
            bukkit.when(()->Bukkit.createBlockData(Material.AIR)).thenReturn(fixture.air);
            runtime.when(DungeonSessionRuntime::messages).thenReturn(messages);
            assertTrue(keys.use(player,null,key)); verify(keys,never()).clear();
            write.completeExceptionally(new IllegalStateException("disk failure")); fixture.temp.tick(1);
            verify(keys,never()).clear(); verify(fixture.session,never()).openDoor();
            verify(messages).send(player,"session.key-open-failed");
            when(fixture.storage.addTempBlock(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
            assertTrue(keys.use(player,null,key)); fixture.temp.tick(2);
            var ordered=inOrder(keys,fixture.session); ordered.verify(keys).clear(); ordered.verify(fixture.session).openDoor();
        }
    }
    @Test void rightClickOnUnrelatedBlockAtExactlyFourBlocksOpens() {
        when(player.getLocation()).thenReturn(new Location(world,6,65,11));
        Block clicked = mock(Block.class); when(clicked.getLocation()).thenReturn(new Location(world,3,65,11));
        assertTrue(keys.use(player,clicked,key)); verify(doors).open(eq(0),any(Runnable.class)); verify(keys).clear();
    }
    @Test void rightClickAirWithinRangeOpens() {
        when(player.getLocation()).thenReturn(new Location(world,11.5,65,11));
        assertTrue(keys.use(player,null,key)); verify(doors).open(eq(0),any(Runnable.class));
    }
    @Test void clickingDoorFromFarAwayDoesNotConsumeAndSendsHint() {
        when(player.getLocation()).thenReturn(new Location(world,5.99,65,11));
        Block clicked = mock(Block.class); when(clicked.getLocation()).thenReturn(new Location(world,10,65,11));
        var messages = mock(Messages.class);
        try (var runtime = mockStatic(DungeonSessionRuntime.class)) {
            runtime.when(DungeonSessionRuntime::messages).thenReturn(messages);
            runtime.when(() -> DungeonSessionRuntime.contains(any(),any())).thenCallRealMethod();
            assertFalse(keys.use(player,clicked,key));
            verify(messages).send(player,"session.key-too-far");
            verify(doors,never()).open(anyInt(),any(Runnable.class)); verify(keys,never()).clear();
        }
    }
    @Test void distanceUsesEveryFaceOfEntireRegionAndRejectsOtherWorlds() {
        assertTrue(KeyService.withinDoorRange(door,"world",15,65,11));
        assertFalse(KeyService.withinDoorRange(door,"world",15.01,65,11));
        assertTrue(KeyService.withinDoorRange(door,"world",10.5,72,11));
        assertFalse(KeyService.withinDoorRange(door,"world",10.5,72.01,11));
        assertTrue(KeyService.withinDoorRange(door,"world",10.5,65,17));
        assertFalse(KeyService.withinDoorRange(door,"world",10.5,65,17.01));
        assertFalse(KeyService.withinDoorRange(door,"world",6,65,6));
        assertFalse(KeyService.withinDoorRange(door,"other",10,65,11));
        assertFalse(KeyService.withinDoorRange(null,"world",10,65,11));
    }
    @Test void foreignKeyAndNonSurvivorCannotOpen() {
        doReturn(false).when(keys).matches(key); assertFalse(keys.use(player,null,key));
        doReturn(true).when(keys).matches(key); when(session.survivors()).thenReturn(Set.of());
        assertFalse(keys.use(player,null,key)); verifyNoInteractions(doors);
    }
}
