package dev.dasan.customdungeons.session;
import dev.dasan.customdungeons.model.Point;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CinematicJournalTest {
    @TempDir Path root;
    CinematicJournal.Saved saved() {return new CinematicJournal.Saved(UUID.randomUUID(),UUID.randomUUID(),
        new Point("world",1.25,65.75,-9.125,179,-33),"CREATIVE",true,true,true,false);}
    @Test void backupSurvivesCrashAndRestoredRecordRetainsExactOriginalUntilConfirmed() {
        var s=saved();
        try(var j=new CinematicJournal(root,Runnable::run)) {j.backup(s).join();}
        try(var j=new CinematicJournal(root,Runnable::run)) {
            assertEquals(s,j.get(s.player(),s.token()).orElseThrow());
            j.restored(s).join();assertTrue(j.get(s.player(),s.token()).orElseThrow().restored());
        }
        try(var j=new CinematicJournal(root,Runnable::run)) {
            assertEquals(s.position(),j.get(s.player(),s.token()).orElseThrow().position());
            j.acknowledge(s.player(),s.token()).join();assertTrue(j.get(s.player(),s.token()).isEmpty());
        }
    }
    @Test void activeBackupCannotBeAcknowledgedAndOldGenerationCannotDeleteNew() {
        var s=saved();var other=new CinematicJournal.Saved(s.player(),UUID.randomUUID(),s.position(),s.mode(),false,false,false,false);
        try(var j=new CinematicJournal(root,Runnable::run)) {
            j.backup(s).join();assertThrows(CompletionException.class,()->j.acknowledge(s.player(),s.token()).join());
            j.backup(other).join();j.restored(s).join();j.acknowledge(s.player(),s.token()).join();
            assertEquals(other,j.get(other.player(),other.token()).orElseThrow());
        }
    }
    @Test void failedWriteNeverPublishesBackupAndQueueCanContinue() throws Exception {
        try(var j=new CinematicJournal(root,Runnable::run)) {
            var s=saved();Files.delete(root.resolve("cinematic-players"));Files.writeString(root.resolve("cinematic-players"),"blocked");
            assertThrows(CompletionException.class,()->j.backup(s).join());assertTrue(j.get(s.player(),s.token()).isEmpty());
            Files.delete(root.resolve("cinematic-players"));j.backup(s).join();assertEquals(s,j.get(s.player(),s.token()).orElseThrow());
        }
    }
    @Test void corruptedBackupFailsClosed() throws Exception {
        var s=saved();try(var j=new CinematicJournal(root,Runnable::run)){j.backup(s).join();}
        Files.write(root.resolve("cinematic-players").resolve(s.player()+"--"+s.token()+".bin"),new byte[]{1,2});
        assertThrows(IllegalStateException.class,()->new CinematicJournal(root,Runnable::run));
    }

    @Test void symlinkRecoveryDirectoryIsRejected() throws Exception {
        Path actual=java.nio.file.Files.createDirectory(root.resolve("actual"));
        java.nio.file.Files.createSymbolicLink(root.resolve("cinematic-players"),actual);
        assertThrows(IllegalStateException.class,()->new CinematicJournal(root,Runnable::run));
    }
}
