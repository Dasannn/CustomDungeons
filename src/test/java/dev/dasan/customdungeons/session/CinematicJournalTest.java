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

    @Test void confirmingOlderRestorationCannotDeleteNewerUnconfirmedRestoredGenerationAfterRestart() {
        var first=saved();var second=new CinematicJournal.Saved(first.player(),UUID.randomUUID(),first.position(),first.mode(),false,false,false,false);
        try(var j=new CinematicJournal(root,Runnable::run)) {
            j.backup(first).join();j.restored(first).join();j.backup(second).join();j.restored(second).join();
        }
        try(var j=new CinematicJournal(root,Runnable::run)) {
            j.acknowledge(first.player(),first.token()).join();
            assertTrue(j.get(first.player(),first.token()).isEmpty());assertTrue(j.get(second.player(),second.token()).isPresent());
            j.acknowledge(second.player(),second.token()).join();assertTrue(j.get(second.player(),second.token()).isEmpty());
        }
    }

    @Test void originalBackupFormatStillLoadsAndIsRetiredByALaterConfirmedGeneration() throws Exception {
        var first=saved();var bytes=new java.io.ByteArrayOutputStream();
        try(var out=new java.io.DataOutputStream(bytes)) {
            out.writeInt(0x43444331);out.writeUTF(first.player().toString());out.writeUTF(first.token().toString());
            var p=first.position();out.writeUTF(p.world());out.writeDouble(p.x());out.writeDouble(p.y());out.writeDouble(p.z());out.writeFloat(p.yaw());out.writeFloat(p.pitch());
            out.writeUTF(first.mode());out.writeBoolean(first.invulnerable());out.writeBoolean(first.flying());out.writeBoolean(first.allowFlight());out.writeBoolean(true);
        }
        bytes.writeBytes(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        Files.createDirectories(root.resolve("cinematic-players"));
        Files.write(root.resolve("cinematic-players").resolve(first.player()+"--"+first.token()+".bin"),bytes.toByteArray());
        var second=new CinematicJournal.Saved(first.player(),UUID.randomUUID(),first.position(),first.mode(),false,false,false,false);
        try(var j=new CinematicJournal(root,Runnable::run)) {
            assertEquals(first.asRestored(),j.get(first.player(),first.token()).orElseThrow());
            j.backup(second).join();j.restored(second).join();j.acknowledge(second.player(),second.token()).join();
            assertTrue(j.get(first.player(),first.token()).isEmpty());assertTrue(j.get(second.player(),second.token()).isEmpty());
        }
    }
}
