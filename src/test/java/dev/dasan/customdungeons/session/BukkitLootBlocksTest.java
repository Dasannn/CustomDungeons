package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import dev.dasan.customdungeons.storage.LootBlockRecord;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitLootBlocksTest {
    final Point point=new Point("world",15,64,1,0,0);
    final World world=mock(World.class);
    final Block block=mock(Block.class);
    final Chest chest=mock(Chest.class);
    final Inventory inventory=mock(Inventory.class);
    final SessionManager manager=mock(SessionManager.class);
    final BukkitLootBlocks blocks=new BukkitLootBlocks(manager);
    MockedStatic<Bukkit> bukkit;
    BlockData current;
    @BeforeEach void setup() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        bukkit=mockStatic(Bukkit.class);bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getBlockAt(15,64,1)).thenReturn(block);
        var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);var tags=new HashMap<NamespacedKey,Object>();
        when(pdc.has(any(),any())).thenAnswer(c->{Object value=tags.get(c.getArgument(0));return ((org.bukkit.persistence.PersistentDataType<?,?>)c.getArgument(1)).getPrimitiveType().isInstance(value);});
        when(pdc.get(any(),any())).thenAnswer(c->{Object value=tags.get(c.getArgument(0));if(value!=null && !((org.bukkit.persistence.PersistentDataType<?,?>)c.getArgument(1)).getPrimitiveType().isInstance(value))throw new IllegalArgumentException("The found tag instance cannot store this type");return value;});
        when(pdc.getKeys()).thenAnswer(c->Set.copyOf(tags.keySet()));when(pdc.isEmpty()).thenAnswer(c->tags.isEmpty());
        doAnswer(c->{tags.put(c.getArgument(0),c.getArgument(2));return null;}).when(pdc).set(any(),any(),any());
        doAnswer(c->{tags.clear();return null;}).when(block).setType(Material.AIR,false);
        when(chest.getPersistentDataContainer()).thenReturn(pdc);when(chest.update(true,false)).thenReturn(true);
        when(block.getState()).thenReturn(chest);when(chest.getInventory()).thenReturn(inventory);when(chest.getBlockInventory()).thenReturn(inventory);
        var data=mock(org.bukkit.block.data.type.Chest.class);when(data.getType()).thenReturn(org.bukkit.block.data.type.Chest.Type.SINGLE);when(chest.getBlockData()).thenReturn(data);
        when(chest.getSnapshotInventory()).thenReturn(inventory);when(inventory.isEmpty()).thenReturn(true);
        when(inventory.getViewers()).thenReturn(List.of());
        current=mock(BlockData.class);when(current.getAsString()).thenReturn("minecraft:chest[type=single]");
        bukkit.when(()->Bukkit.createBlockData("minecraft:chest[type=single]")).thenReturn(current);
        when(block.getBlockData()).thenAnswer(c->current);
        doAnswer(c->{current=c.getArgument(0);return null;}).when(block).setBlockData(any(),eq(false));
    }
    @AfterEach void close(){bukkit.close();}
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void snapshotsAndMutationChecksRespectPaperTagTypesForLegacyAndNewOwnership(boolean legacy) {
        var neighbour=mock(Block.class);when(neighbour.getType()).thenReturn(Material.AIR);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(neighbour);when(world.getBlockAt(15,64,1)).thenReturn(block);
        when(world.getMaxHeight()).thenReturn(320);bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        var key=dev.dasan.customdungeons.config.BukkitLootBlockInspector.LOOT_CHEST;var pdc=chest.getPersistentDataContainer();
        String token=UUID.randomUUID().toString();
        if(legacy)pdc.set(key,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        else pdc.set(key,org.bukkit.persistence.PersistentDataType.STRING,token);
        if(legacy)assertThrows(IllegalArgumentException.class,()->pdc.get(key,org.bukkit.persistence.PersistentDataType.STRING));
        else assertThrows(IllegalArgumentException.class,()->pdc.get(key,org.bukkit.persistence.PersistentDataType.BYTE));
        String snapshot=assertDoesNotThrow(()->blocks.snapshot(point));
        assertEquals(legacy?"loot-chest-v1:minecraft:chest[type=single]":"loot-chest-v2:"+token+":minecraft:chest[type=single]",snapshot);
        assertTrue(assertDoesNotThrow(()->blocks.matches(point,snapshot,false)));
        assertEquals(!legacy,assertDoesNotThrow(()->blocks.matches(point,"loot-chest-v2:"+token+":minecraft:chest[type=single]",true)));
        blocks.restore(point,snapshot);assertTrue(blocks.matches(point,snapshot,false));
    }
    @Test void restoreClosesViewAndDiscardsLeftoversBeforeReplacingExactBlockDataWithoutPhysics() {
        var player=mock(org.bukkit.entity.Player.class);when(inventory.getViewers()).thenReturn(List.of(player));
        var original=mock(BlockData.class);String data="minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=true]";
        when(original.getAsString()).thenReturn(data);bukkit.when(()->Bukkit.createBlockData(data)).thenReturn(original);
        blocks.restore(point,data);
        var order=inOrder(player,inventory,block);order.verify(player).closeInventory();order.verify(inventory).clear();order.verify(block).setType(Material.AIR,false);order.verify(block).setBlockData(original,false);
        verify(world,never()).dropItem(any(),any());verify(world,never()).dropItemNaturally(any(),any());
        verify(block,never()).breakNaturally();assertSame(original,current);
        blocks.restore(point,data);assertSame(original,current);
    }
    @Test void preparingChestClearsOldContainerAndUsesAnActualSingleBlockInventory() {
        var data=mock(BlockData.class);when(data.getAsString()).thenReturn("minecraft:chest[type=single]");bukkit.when(()->Bukkit.createBlockData("minecraft:chest[type=single]")).thenReturn(data);
        blocks.chest(point);assertSame(inventory,blocks.inventory(point));verify(block).setType(Material.AIR,false);verify(block).setBlockData(data,false);verify(inventory,times(2)).clear();
    }
    @Test void restorationFailureIsReportedInsteadOfAcknowledged() {
        var original=mock(BlockData.class);var wrong=mock(BlockData.class);when(original.getAsString()).thenReturn("minecraft:stone");when(wrong.getAsString()).thenReturn("minecraft:dirt");
        bukkit.when(()->Bukkit.createBlockData("minecraft:stone")).thenReturn(original);
        doAnswer(c->{current=wrong;return null;}).when(block).setBlockData(original,false);
        assertThrows(IllegalStateException.class,()->blocks.restore(point,"minecraft:stone"));
    }
    @Test void restoredReceiptsOnlyReplayTheirOwnMutationAndPreserveLaterExternalEdits() {
        String token=UUID.randomUUID().toString(),expected="loot-chest-v2:"+token+":minecraft:chest[type=single]";
        var record=new LootBlockRecord(point,"test","first","loot",null,"minecraft:stone",false,false,expected);
        blocks.recover(record);verify(block,never()).setType(any(),anyBoolean());verify(inventory,never()).clear();
        chest.getPersistentDataContainer().set(dev.dasan.customdungeons.config.BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING,token);
        var data=mock(BlockData.class);when(data.getAsString()).thenReturn("minecraft:stone");bukkit.when(()->Bukkit.createBlockData("minecraft:stone")).thenReturn(data);
        blocks.recover(record);verify(block).setBlockData(data,false);verify(inventory).clear();
    }
    @Test void activeIntentCannotAuthorizeOverwritingAnUnmarkedOccupiedChest() {
        String expected="loot-chest-v2:"+UUID.randomUUID()+":minecraft:chest[type=single]";
        when(inventory.isEmpty()).thenReturn(false);
        var record=new LootBlockRecord(point,"test","first","loot",null,"minecraft:stone",false,true,expected);
        assertThrows(IllegalStateException.class,()->blocks.recover(record));
        verify(block,never()).setType(any(),anyBoolean());verify(inventory,never()).clear();
    }
    @Test void anotherMutationTokenCannotAuthorizeDiscardingItsContents() {
        String expected=blocks.expectedChest();blocks.chest(point);when(inventory.isEmpty()).thenReturn(false);clearInvocations(block,inventory);
        var record=new LootBlockRecord(point,"test","first","loot",null,"minecraft:stone",false,true,expected);
        assertThrows(IllegalStateException.class,()->blocks.recover(record));verify(block,never()).setType(any(),anyBoolean());verify(inventory,never()).clear();
    }
    @Test void legacyActiveIntentWithoutAMutationWitnessCannotDiscardContents() {
        chest.getPersistentDataContainer().set(dev.dasan.customdungeons.config.BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        when(inventory.isEmpty()).thenReturn(false);
        var record=new LootBlockRecord(point,"test","first","loot",null,"minecraft:stone",false,true);
        assertThrows(IllegalStateException.class,()->blocks.recover(record));
        verify(block,never()).setType(any(),anyBoolean());verify(inventory,never()).clear();
    }
    @Test void exactMutationTokenAllowsRestoringAnOwnedChestWithLeftovers() {
        String expected=blocks.expectedChest();blocks.chest(point,expected);when(inventory.isEmpty()).thenReturn(false);clearInvocations(block,inventory);
        var original=mock(BlockData.class);when(original.getAsString()).thenReturn("minecraft:stone");bukkit.when(()->Bukkit.createBlockData("minecraft:stone")).thenReturn(original);
        blocks.recover(new LootBlockRecord(point,"test","first","loot",null,"minecraft:stone",false,true,expected));
        verify(block).setBlockData(original,false);verify(inventory).clear();
    }
    @Test void unmarkedPartialPlacementStaysAmbiguousInsteadOfDeletingPossibleForeignContents() {
        String expected=blocks.expectedChest();when(chest.update(true,false)).thenReturn(false);
        assertThrows(IllegalStateException.class,()->blocks.chest(point,expected));
        when(chest.getPersistentDataContainer().get(dev.dasan.customdungeons.config.BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(null);
        clearInvocations(block,inventory);
        assertThrows(IllegalStateException.class,()->blocks.recover(new LootBlockRecord(point,"test","first","loot",null,"minecraft:stone",false,true,expected)));
        verify(block,never()).setType(any(),anyBoolean());verify(inventory,never()).clear();
    }
    @Test void snapshotOfManagedChestRestoresItsOwnershipAlongWithFacingAndWaterlogging() {
        var data=mock(BlockData.class);String original="minecraft:chest[facing=west,type=single,waterlogged=true]";
        when(data.getAsString()).thenReturn(original);bukkit.when(()->Bukkit.createBlockData(original)).thenReturn(data);
        blocks.restore(point,"loot-chest-v1:"+original);
        verify(chest.getPersistentDataContainer()).set(dev.dasan.customdungeons.config.BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        verify(chest).update(true,false);assertSame(data,current);
    }
    @Test void missingOrUnloadedNeighbourNeverUsesSynchronousChunkOrBlockAccess() {
        when(world.isChunkLoaded(1,0)).thenReturn(false);var loaded=new CompletableFuture<Chunk>();when(world.getChunkAtAsync(1,0)).thenReturn(loaded);
        assertFalse(blocks.ready(point));assertFalse(blocks.ready(point));verify(world,times(1)).getChunkAtAsync(1,0);
        verify(world,never()).getChunkAt(anyInt(),anyInt());verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        assertThrows(IllegalStateException.class,()->blocks.restore(point,"minecraft:stone"));
        bukkit.when(()->Bukkit.getWorld("world")).thenReturn(null);assertFalse(blocks.ready(point));
    }
    @Test void lateAsyncCompletionAfterShutdownDoesNotRetainTicketsOrMutateBlocks() {
        when(world.isChunkLoaded(1,0)).thenReturn(false);var loaded=new CompletableFuture<Chunk>();when(world.getChunkAtAsync(1,0)).thenReturn(loaded);
        var callbacks=new ArrayList<Runnable>();doAnswer(c->{callbacks.add(c.getArgument(0));return null;}).when(manager).main(any());
        blocks.ready(point);blocks.close();loaded.complete(mock(Chunk.class));callbacks.forEach(Runnable::run);
        verify(manager,never()).retainChunk(any());verifyNoInteractions(block);assertFalse(blocks.ready(point));
    }
    @Test void completionAfterItsOperationReleasedThePositionCannotAcquireALateTicket() {
        when(world.isChunkLoaded(1,0)).thenReturn(false);var loaded=new CompletableFuture<Chunk>();when(world.getChunkAtAsync(1,0)).thenReturn(loaded);
        doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(manager).main(any());
        blocks.ready(point);blocks.release(point);var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(world);loaded.complete(chunk);
        verify(manager,never()).retainChunk(any());
    }
    @Test void preparedAsyncChunkTicketIsReleasedAfterRestoration() {
        when(world.isChunkLoaded(1,0)).thenReturn(false);var loaded=new CompletableFuture<Chunk>();when(world.getChunkAtAsync(1,0)).thenReturn(loaded);
        doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(manager).main(any());
        blocks.ready(point);var chunk=mock(Chunk.class);when(chunk.getWorld()).thenReturn(world);loaded.complete(chunk);verify(manager).retainChunk(chunk);
        when(world.isChunkLoaded(1,0)).thenReturn(true);var data=mock(BlockData.class);when(data.getAsString()).thenReturn("minecraft:stone");bukkit.when(()->Bukkit.createBlockData("minecraft:stone")).thenReturn(data);
        blocks.restore(point,"minecraft:stone");verify(manager).releaseChunk(chunk);blocks.close();verify(manager,times(1)).releaseChunk(chunk);
    }
}
