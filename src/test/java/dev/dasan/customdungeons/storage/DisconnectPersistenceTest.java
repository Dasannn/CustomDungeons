package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.config.PluginConfig.DatabaseSettings;
import dev.dasan.customdungeons.model.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DisconnectPersistenceTest {
    @TempDir Path folder;
    SqlStorage open(){return SqlStorage.create(new DatabaseSettings("sqlite","",0,"","","",2),folder);}
    DisconnectRecord record(UUID player,DisconnectMode mode,boolean keep) {
        return new DisconnectRecord(UUID.randomUUID(),player,UUID.randomUUID(),"dungeon",
                new Point("dungeons",-17.5,68.25,45.75,30.5f,-42.25f),new Point("world",90,64,90,0,0),mode,keep);
    }
    @Test void pendingRecordSurvivesReopenAndIsDeletedOnlyAfterApplication() {
        UUID uuid=UUID.randomUUID();var record=record(uuid,DisconnectMode.DIE_AND_DROP,true);
        try(var s=open()){assertTrue(s.disconnect(uuid).join().isEmpty());s.saveDisconnect(record).join();}
        try(var s=open()) {
            assertEquals(record,s.disconnect(uuid).join().orElseThrow());
            assertEquals(record,s.disconnect(uuid).join().orElseThrow(),"Reading must not consume an unapplied penalty");
            s.clearDisconnect(uuid,record.id()).join();assertTrue(s.disconnect(uuid).join().isEmpty());
        }
        try(var s=open()){assertTrue(s.disconnect(uuid).join().isEmpty());}
    }
    @Test void staleCleanupCannotEraseANewQuitAndRulesAreSnapshotted() {
        UUID uuid=UUID.randomUUID();var first=record(uuid,DisconnectMode.DIE_AND_DROP,false);
        var second=record(uuid,DisconnectMode.RETURN_TO_EXIT,true);
        try(var s=open()) {
            s.saveDisconnect(first).join();s.saveDisconnect(second).join();s.clearDisconnect(uuid,first.id()).join();
            assertEquals(second,s.disconnect(uuid).join().orElseThrow());
        }
    }
    @Test void versionThreeMigrationPreservesT38JournalAndCreatesNoPenaltyForCrash() throws Exception {
        UUID uuid=UUID.randomUUID();var record=record(uuid,DisconnectMode.DIE_AND_DROP,false);
        var original=new ReturnTarget(record.sessionId(),record.position(),record.exit(),FinishDestination.PREVIOUS);
        try(var s=open()){s.saveReturnTarget(uuid,original).join();s.markActive(new ActiveSessionRecord(record.sessionId(),"dungeon",Set.of(uuid),record.exit())).join();}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+folder.resolve("data.db"));var st=c.createStatement()) {
            st.executeUpdate("DROP TABLE disconnects");st.executeUpdate("DELETE FROM schema_version WHERE version >= 4");
        }
        try(var s=open()) {
            assertEquals(original,s.returnTarget(uuid).join().orElseThrow());assertEquals(1,s.loadActive().join().size());
            assertTrue(s.disconnect(uuid).join().isEmpty());
        }
    }
}
