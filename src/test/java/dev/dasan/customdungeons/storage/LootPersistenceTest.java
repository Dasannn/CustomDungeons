package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.config.PluginConfig.DatabaseSettings;
import dev.dasan.customdungeons.model.Point;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LootPersistenceTest {
    @TempDir Path directory;
    SqlStorage open(){return SqlStorage.create(new DatabaseSettings("sqlite","",0,"","","",2),directory);}
    final Point position=new Point("dungeons",-16,64,32,0,0);
    @Test void independentPlacementAndRunOriginalsSurviveResetAndReopening() {
        var placement=new LootBlockRecord(position,"dungeon","room","loot","minecraft:oak_stairs[facing=east]",null,true,false);
        try(var storage=open()) {
            storage.saveLootBlock(placement).join();var run=placement.prepared("minecraft:chest[facing=west,type=single,waterlogged=true]");
            storage.saveLootBlock(run).join();assertEquals(List.of(run),storage.loadLootBlocks().join());
            storage.saveLootBlock(run.restored()).join();
        }
        try(var storage=open()) {
            var record=storage.loadLootBlocks().join().getFirst();assertEquals(placement.placementData(),record.placementData());
            assertEquals("minecraft:chest[facing=west,type=single,waterlogged=true]",record.sessionData());assertFalse(record.sessionActive());
            storage.saveLootBlock(record.removed()).join();
        }
        try(var storage=open()){var record=storage.loadLootBlocks().join().getFirst();assertEquals(placement.placementData(),record.placementData());assertNull(record.sessionData());assertFalse(record.placed());}
    }
    @Test void oldSchemaMigratesWithoutTouchingExistingBlockRecordsAndInterruptedDdlCanResume() throws Exception {
        try(var storage=open()){storage.addTempBlock(new TempBlockRecord("world",1,2,3,"minecraft:stone","minecraft:air")).join();}
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("data.db"));var statement=connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_version WHERE version>=7"); // Simulates DDL committed, version receipt absent.
        }
        try(var storage=open()){assertTrue(storage.loadLootBlocks().join().isEmpty());assertEquals(1,storage.loadTempBlocks().join().size());}
    }
    @Test void expectedMutationSurvivesReopeningCancellationAndRestorationReceipts() {
        String expected="loot-chest-v2:"+UUID.randomUUID()+":minecraft:chest[type=single]";
        var intent=new LootBlockRecord(position,"dungeon","room","loot",null,"minecraft:stone",false,true,expected);
        try(var storage=open()){storage.saveLootBlock(intent).join();}
        try(var storage=open()) {
            assertEquals(List.of(intent),storage.loadLootBlocks().join());
            storage.saveLootBlock(intent.cancelPreparation()).join();
        }
        try(var storage=open()) {
            assertEquals(List.of(intent.cancelPreparation()),storage.loadLootBlocks().join());
            storage.saveLootBlock(intent.restored()).join();
        }
        try(var storage=open()){assertEquals(List.of(intent.restored()),storage.loadLootBlocks().join());}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void schemaSevenMigrationPreservesLegacyIntentAndResumesAfterCommittedDdl(boolean interrupted) throws Exception {
        var legacy=new LootBlockRecord(position,"dungeon","room","loot",null,"minecraft:stone",false,true);
        try(var storage=open()){storage.saveLootBlock(legacy).join();}
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+directory.resolve("data.db"));var statement=connection.createStatement()) {
            statement.executeUpdate("DELETE FROM schema_version WHERE version=8");
            if(!interrupted)statement.executeUpdate("ALTER TABLE loot_blocks DROP COLUMN expected_block_data");
        }
        try(var storage=open()) {
            assertEquals(List.of(legacy),storage.loadLootBlocks().join());
            var witnessed=new LootBlockRecord(position,"dungeon","room","loot",null,"minecraft:stone",false,true,"new-witness");
            storage.saveLootBlock(witnessed).join();assertEquals(List.of(witnessed),storage.loadLootBlocks().join());
        }
    }
    @Test void savesOnlyReplaceTheirOwnPositionAndClosedStorageRejectsMutation() {
        var first=new LootBlockRecord(position,"dungeon","room","loot",null,"minecraft:stone",false,true);
        var second=new LootBlockRecord(new Point("other",-16,64,32,0,0),"dungeon","room","other",null,"minecraft:dirt",false,true);
        var storage=open();storage.saveLootBlock(first).join();storage.saveLootBlock(second).join();storage.saveLootBlock(first.restored()).join();
        assertEquals(Set.of(first.restored(),second),new HashSet<>(storage.loadLootBlocks().join()));storage.close();
        assertTrue(storage.saveLootBlock(first).isCompletedExceptionally());
    }
    @Test void administrativeDiscardDeletesOnlyTheExactJournalPositionAndSurvivesReopening() {
        var first=new LootBlockRecord(position,"dungeon","room","loot",null,"minecraft:stone",false,true);
        var other=new LootBlockRecord(new Point("other",position.x(),position.y(),position.z(),0,0),"other","room","loot",null,"minecraft:dirt",false,true);
        try(var storage=open()){storage.saveLootBlock(first).join();storage.saveLootBlock(other).join();storage.deleteLootBlock(position).join();storage.deleteLootBlock(position).join();}
        try(var storage=open()){assertEquals(List.of(other),storage.loadLootBlocks().join());}
    }
}
