package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.config.PluginConfig.DatabaseSettings;
import dev.dasan.customdungeons.model.Point;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PendingExitPersistenceTest {
    @TempDir Path folder;
    final UUID player=UUID.randomUUID();
    final Point exit=new Point("world",99.5,64,-17.25,90,15);
    SqlStorage open(){return SqlStorage.create(new DatabaseSettings("sqlite","",0,"","","",2),folder);}
    @Test void readingNeverConsumesAnExitAndItSurvivesReopeningUntilAcknowledged() {
        PendingExitRecord record;
        try(var s=open()) {
            assertTrue(s.pendingExit(player).join().isEmpty());s.addPendingExit(player,exit).join();
            record=s.pendingExit(player).join().orElseThrow();assertEquals(exit,record.exit());
            assertEquals(record,s.pendingExit(player).join().orElseThrow());
        }
        try(var s=open()) {
            assertEquals(record,s.pendingExit(player).join().orElseThrow());
            s.clearPendingExit(player,record.id()).join();assertTrue(s.pendingExit(player).join().isEmpty());
        }
    }
    @Test void oldAcknowledgementCannotEraseANewGenerationEvenAtTheSameDestination() {
        try(var s=open()) {
            s.addPendingExit(player,exit).join();var old=s.pendingExit(player).join().orElseThrow();
            s.addPendingExit(player,exit).join();var latest=s.pendingExit(player).join().orElseThrow();
            assertNotEquals(old.id(),latest.id());s.clearPendingExit(player,old.id()).join();
            assertEquals(latest,s.pendingExit(player).join().orElseThrow());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void versionFourMigrationPreservesExitsAndResumesAnInterruptedColumnAddition(boolean interrupted) throws Exception {
        try(var s=open()){s.addPendingExit(player,exit).join();}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+folder.resolve("data.db"));var st=c.createStatement()) {
            st.executeUpdate("DELETE FROM schema_version WHERE version=5");
            if(interrupted)st.executeUpdate("UPDATE pending_exits SET id=NULL"); // ALTER was committed, backfill was not
            else st.executeUpdate("ALTER TABLE pending_exits DROP COLUMN id");
        }
        PendingExitRecord migrated;
        try(var s=open()){migrated=s.pendingExit(player).join().orElseThrow();assertEquals(exit,migrated.exit());}
        try(var s=open()){assertEquals(migrated,s.pendingExit(player).join().orElseThrow());}
    }
}
