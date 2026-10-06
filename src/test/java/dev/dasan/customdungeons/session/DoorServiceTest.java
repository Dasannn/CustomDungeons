package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DoorServiceTest {
    final World world = mock(World.class);
    final Storage storage = mock(Storage.class);
    final DungeonSession session = mock(DungeonSession.class);
    final PluginConfig config = mock(PluginConfig.class);
    final BlockData air = data("minecraft:air"), bars = data("minecraft:iron_bars[north=true]"), fill = data("minecraft:iron_block");
    final Map<Integer,AtomicReference<BlockData>> contents = new HashMap<>();
    final Map<Integer,Block> blocks = new HashMap<>();
    final SessionTempBlocks temp = new SessionTempBlocks(storage, e -> fail(e));
    final DoorService doors = new DoorService(session,temp,config);

    static BlockData data(String serialized) {
        var data = mock(BlockData.class); when(data.clone()).thenReturn(data); when(data.getAsString()).thenReturn(serialized); return data;
    }
    DoorServiceTest() {
        when(world.getName()).thenReturn("world");
        var def = new SessionRuntimeRegressionTest().definition(); var rooms = new ArrayList<>(def.rooms()); var r = rooms.getFirst();
        rooms.set(0,new RoomDef(r.id(),r.region(),r.checkpoint(),Region.of("world",new BlockPos(0,64,0),new BlockPos(0,65,0)),r.unlock(),r.keyCarrierTemplateId(),r.spawners()));
        when(session.def()).thenReturn(new DungeonDef(def.id(),def.displayName(),def.enabled(),def.lobby(),def.exit(),def.minPlayers(),def.maxPlayers(),def.lobbyCountdownSeconds(),def.lives(),def.keepInventory(),def.timeLimitSeconds(),def.cooldownSeconds(),def.requirePermission(),def.scaling(),def.hooks(),def.reward(),rooms));
        var material = mock(Material.class); when(material.createBlockData()).thenReturn(fill); when(config.doorMaterial()).thenReturn(material);
        when(storage.addTempBlock(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(storage.removeTempBlock(anyString(),anyInt(),anyInt(),anyInt())).thenReturn(CompletableFuture.completedFuture(null));
        for (int y : new int[]{64,65}) {
            var block = mock(Block.class); blocks.put(y,block);
            var state = new AtomicReference<>(y == 64 ? bars : air); contents.put(y,state);
            when(block.getWorld()).thenReturn(world); when(block.getY()).thenReturn(y);
            when(block.isEmpty()).thenAnswer(call -> state.get() == air);
            when(block.getBlockData()).thenAnswer(call -> state.get());
            doAnswer(call -> { state.set(call.getArgument(0)); return null; }).when(block).setBlockData(any(),eq(false));
            when(world.getBlockAt(0,y,0)).thenReturn(block);
        }
    }
    @Test void builtBarsAndFilledAirBothOpenAndResetToOriginalBlockData() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            doors.closeAll(); temp.tick(1);
            assertSame(bars,contents.get(64).get()); assertSame(fill,contents.get(65).get());
            doors.open(0); temp.tick(2);
            assertSame(air,contents.get(64).get()); assertSame(air,contents.get(65).get());
            verify(storage).addTempBlock(new TempBlockRecord("world",0,64,0,bars.getAsString()));
            verify(storage).addTempBlock(new TempBlockRecord("world",0,65,0,air.getAsString()));
            temp.restoreAll();
            assertSame(bars,contents.get(64).get()); assertSame(air,contents.get(65).get());
            verify(storage).removeTempBlock("world",0,64,0);
            verify(session).openDoor();
        }
    }
    @Test void openingBeforeInitialAirFillIsJournaledNeverClosesDoorLater() {
        var pending = new CompletableFuture<Void>(); when(storage.addTempBlock(any())).thenReturn(pending);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            doors.closeAll(); doors.open(0); temp.tick(1);
            assertSame(bars,contents.get(64).get()); // No mutation before the original is durable.
            pending.complete(null); temp.tick(2);
            assertSame(air,contents.get(64).get()); assertSame(air,contents.get(65).get());
            temp.restoreAll(); assertSame(bars,contents.get(64).get());
        }
    }
    @Test void resetBeforeDoorJournalCompletesLeavesBlocksUnchanged() {
        var pending = new CompletableFuture<Void>(); when(storage.addTempBlock(any())).thenReturn(pending);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkit.when(() -> Bukkit.createBlockData(Material.AIR)).thenReturn(air);
            doors.open(0); temp.restoreAll(); pending.complete(null); temp.tick(2);
            assertSame(bars,contents.get(64).get()); assertSame(air,contents.get(65).get());
            verify(blocks.get(64),never()).setBlockData(any(),anyBoolean());
        }
    }
}
