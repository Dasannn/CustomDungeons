package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BukkitLootBlockInspectorTest {
    @TempDir Path directory;
    final World world=mock(World.class);
    final Block target=mock(Block.class);
    final Block neighbour=mock(Block.class);
    MockedStatic<Bukkit> bukkit;
    @BeforeEach void setup() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        new Validator();
        bukkit=mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(()->Bukkit.getWorld("dungeons")).thenReturn(world);
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(neighbour);
        when(world.getBlockAt(1,64,2)).thenReturn(target);
        when(neighbour.getType()).thenReturn(Material.AIR);
        when(target.getState()).thenReturn(mock(BlockState.class));
        when(target.getType()).thenReturn(Material.STONE);
    }
    @AfterEach void close() {bukkit.close();}
    Validator.LootBlockInspector.Inspection inspect() {return new BukkitLootBlockInspector().inspect(OptionalLootConfigTest.POINT);}
    DungeonDef dungeon() {return OptionalLootConfigTest.chests(List.of(OptionalLootConfigTest.chest("c",OptionalLootConfigTest.POINT)));}
    @Test void airAndOrdinaryBlocksAreRestorableWithoutMutation() {
        assertTrue(inspect().restorable());
        when(target.getType()).thenReturn(Material.AIR);assertTrue(inspect().restorable());
        verify(target,never()).setBlockData(any(BlockData.class),anyBoolean());
        verify(target,never()).setType(any(Material.class),anyBoolean());
        verify(world,never()).getChunkAt(anyInt(),anyInt());
    }
    @Test void tileStatesWithAdditionalDataAreNotRestorableByBlockDataBackup() {
        when(target.getState()).thenReturn(mock(Sign.class));assertFalse(inspect().restorable());
        when(target.getState()).thenReturn(mock(CreatureSpawner.class));assertFalse(inspect().restorable());
    }
    @Test void pristineEmptySingleChestIsExactlyRepresentedByBlockDataButAdditionalStateIsRejected() {
        var chest=mock(Chest.class);var inventory=mock(Inventory.class);var data=mock(org.bukkit.block.data.type.Chest.class);
        var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(chest.getBlockData()).thenReturn(data);when(data.getType()).thenReturn(org.bukkit.block.data.type.Chest.Type.SINGLE);
        when(chest.getSnapshotInventory()).thenReturn(inventory);when(inventory.isEmpty()).thenReturn(true);
        when(chest.getPersistentDataContainer()).thenReturn(pdc);when(pdc.isEmpty()).thenReturn(true);when(target.getState()).thenReturn(chest);
        assertTrue(inspect().restorable());
        when(chest.isLocked()).thenReturn(true);assertFalse(inspect().restorable());when(chest.isLocked()).thenReturn(false);
        when(chest.customName()).thenReturn(net.kyori.adventure.text.Component.text("Named"));assertFalse(inspect().restorable());when(chest.customName()).thenReturn(null);
        when(chest.getSeed()).thenReturn(1L);assertFalse(inspect().restorable());when(chest.getSeed()).thenReturn(0L);
        when(pdc.isEmpty()).thenReturn(false);assertFalse(inspect().restorable());when(pdc.isEmpty()).thenReturn(true);
        when(pdc.isEmpty()).thenReturn(false);when(pdc.getKeys()).thenReturn(java.util.Set.of(BukkitLootBlockInspector.LOOT_CHEST));
        when(pdc.has(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn(true);
        when(pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn((byte)1);assertTrue(inspect().restorable());
        when(pdc.getKeys()).thenReturn(java.util.Set.of(BukkitLootBlockInspector.LOOT_CHEST,new NamespacedKey("other","field")));assertFalse(inspect().restorable());
        when(data.getType()).thenReturn(org.bukkit.block.data.type.Chest.Type.LEFT);assertFalse(inspect().restorable());
    }
    @Test void occupiedContainersAreRejectedWithoutReadingOrChangingLiveInventory() {
        var chest=mock(Chest.class);var inventory=mock(Inventory.class);
        when(chest.getSnapshotInventory()).thenReturn(inventory);when(inventory.isEmpty()).thenReturn(false);
        when(target.getState()).thenReturn(chest);assertFalse(inspect().restorable());
        verify(chest,never()).getInventory();verify(inventory,never()).clear();
    }
    @Test void perMutationOwnershipAcceptsCanonicalTokensAndRejectsMalformedTags() {
        var chest=mock(Chest.class);var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(chest.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(true);
        when(pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(UUID.randomUUID().toString());
        assertTrue(BukkitLootBlockInspector.ownedChest(chest));
        when(pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn("1-1-1-1-1");
        assertFalse(BukkitLootBlockInspector.ownedChest(chest));
        when(pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn("invalid");
        assertFalse(BukkitLootBlockInspector.ownedChest(chest));
    }
    @ParameterizedTest @ValueSource(strings={"byte","string","integer"})
    void ownershipChecksTypeBeforeReadingAPaperTag(String kind) {
        var chest=mock(Chest.class);var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);
        var type=switch(kind){case "byte"->org.bukkit.persistence.PersistentDataType.BYTE;case "string"->org.bukkit.persistence.PersistentDataType.STRING;default->org.bukkit.persistence.PersistentDataType.INTEGER;};
        Object value=switch(kind){case "byte"->(byte)1;case "string"->UUID.randomUUID().toString();default->42;};
        when(chest.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(any(),any())).thenAnswer(c->c.getArgument(1)==type);
        when(pdc.get(any(),any())).thenAnswer(c->{if(c.getArgument(1)!=type)throw new IllegalArgumentException("The found tag instance cannot store this type");return value;});
        if(!kind.equals("byte"))assertThrows(IllegalArgumentException.class,()->pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE));
        else assertThrows(IllegalArgumentException.class,()->pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING));
        assertEquals(!kind.equals("integer"),assertDoesNotThrow(()->BukkitLootBlockInspector.ownedChest(chest)));
    }
    @Test void emptyContainersStillRequireTileStateBackupSupport() {
        var barrel=mock(Barrel.class);var inventory=mock(Inventory.class);
        when(barrel.getSnapshotInventory()).thenReturn(inventory);when(inventory.isEmpty()).thenReturn(true);
        when(target.getState()).thenReturn(barrel);assertFalse(inspect().restorable());
    }
    @ParameterizedTest @ValueSource(strings={"CHEST","TRAPPED_CHEST"})
    void existingHorizontalChestNeighboursAreRejected(String material) {
        when(neighbour.getType()).thenReturn(Material.valueOf(material));assertTrue(inspect().doubleChest());
    }
    @Test void verticalAndDiagonalChestsDoNotFormDoubleChest() {
        var chest=mock(Block.class);when(chest.getType()).thenReturn(Material.CHEST);
        when(world.getBlockAt(1,65,2)).thenReturn(chest);when(world.getBlockAt(2,64,3)).thenReturn(chest);
        assertFalse(inspect().doubleChest());
    }
    @Test void missingWorldFailsClosed() {
        bukkit.when(()->Bukkit.getWorld("dungeons")).thenReturn(null);
        assertFalse(inspect().prepared());verifyNoInteractions(world);
    }
    @Test void unloadedTargetAndNeighbourChunksAreNotAccessed() {
        when(world.isChunkLoaded(0,0)).thenReturn(false);assertFalse(inspect().prepared());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        when(world.isChunkLoaded(0,0)).thenReturn(true);when(world.isChunkLoaded(1,0)).thenReturn(false);
        assertFalse(new BukkitLootBlockInspector().inspect(new Point("dungeons",15,64,2,0,0)).prepared());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void negativeChunkBoundaryPreparesNeighbourBeforeReadingBlocks() {
        when(world.isChunkLoaded(-2,0)).thenReturn(false);
        assertFalse(new BukkitLootBlockInspector().inspect(new Point("dungeons",-16,64,2,0,0)).prepared());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void worldHeightIsCheckedBeforeReadingBlocks() {
        assertFalse(new BukkitLootBlockInspector().inspect(new Point("dungeons",1,320,2,0,0)).restorable());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void inspectorRejectsWorkerBeforeAnyWorldAccess() {
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertThrows(IllegalStateException.class,this::inspect);verifyNoInteractions(world);
    }
    @Test void normalAuthoringValidationCallsRealInspector() {
        when(target.getState()).thenReturn(mock(Sign.class));
        var errors=new Validator().validate(dungeon(),OptionalLootConfigTest.MOBS);
        assertTrue(errors.stream().anyMatch(e->e.path().equals("rooms[0].loot-chests[0].location") && e.messageKey().equals("validation.loot-block")));
        when(target.getState()).thenReturn(mock(BlockState.class));when(neighbour.getType()).thenReturn(Material.CHEST);
        assertTrue(new Validator().validate(dungeon(),OptionalLootConfigTest.MOBS).stream().anyMatch(e->e.messageKey().equals("validation.loot-double")));
    }
    @Test void unpreparedChunksBlockAuthoringValidation() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertTrue(new Validator().validate(dungeon(),OptionalLootConfigTest.MOBS).stream().anyMatch(e->e.messageKey().equals("validation.loot-block-unprepared")));
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void loadingAndExplicitPureValidationNeverInspectWorldEvenOnMainThread() {
        assertTrue(new Validator().forLoading().validate(dungeon(),OptionalLootConfigTest.MOBS).isEmpty());
        assertTrue(new Validator().validate(dungeon(),OptionalLootConfigTest.MOBS,Map.of(),false).isEmpty());
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        assertTrue(new Validator().validate(dungeon(),OptionalLootConfigTest.MOBS).isEmpty());verifyNoInteractions(world);
    }
    @Test void directSaveChecksWorldOnCallerBeforeWorkerAndDoesNotPublishInvalidChest() {
        var config=new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new org.bukkit.configuration.file.YamlConfiguration());
        try(var store=new DefinitionStore(directory,config,Set.of("test"),w->{},Runnable::run)) {
            store.save(DefinitionCodecTest.mob()).join();
            when(target.getState()).thenReturn(mock(Sign.class));
            assertThrows(java.util.concurrent.CompletionException.class,()->store.save(dungeon()).join());
            assertTrue(store.dungeons().isEmpty());
            when(target.getState()).thenReturn(mock(BlockState.class));
            store.save(dungeon()).join();assertTrue(store.dungeons().get(dungeon().id()).enabled());
        }
    }
    @Test void saveInspectsPreparedBlocksBeforeQueueingAndWorkerDoesNotReadWorld() {
        var queued=new ArrayList<Runnable>();
        var config=new ConfigLoader(p->{},m->m==Material.IRON_BLOCK).load(new org.bukkit.configuration.file.YamlConfiguration());
        try(var store=new DefinitionStore(directory,config,Set.of("test"),w->{},queued::add)) {
            var mobSave=store.save(DefinitionCodecTest.mob());queued.removeFirst().run();mobSave.join();
            var save=store.save(dungeon());assertFalse(save.isDone());
            verify(target).getState();clearInvocations(world,target,neighbour);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            queued.removeFirst().run();save.join();
            verifyNoInteractions(world,target,neighbour);
            assertTrue(store.dungeons().get(dungeon().id()).enabled());
            assertThrows(java.util.concurrent.CompletionException.class,()->store.save(dungeon()).join());
            verifyNoInteractions(world,target,neighbour);
        }
    }

}
