package dev.dasan.customdungeons.storage;

import dev.dasan.customdungeons.config.PluginConfig.DatabaseSettings;
import dev.dasan.customdungeons.model.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ReturnTargetTest {
    @TempDir Path folder;
    final Point before=new Point("previous-world",2,80,4,20,30),exit=new Point("exit",1,64,1,0,0);
    SqlStorage open(){return SqlStorage.create(new DatabaseSettings("sqlite","",0,"","","",4),folder);}
    @Test void previousAndFallbackSurviveCrashAndLaterSessionCannotBeClearedByOldCallback() {
        var player=UUID.randomUUID();var target=new ReturnTarget(UUID.randomUUID(),before,exit,FinishDestination.PREVIOUS);
        try(var storage=open()){ ((ExitPersistence)storage).saveReturnTarget(player,target).join(); }
        try(var storage=open()) {
            var journal=(ExitPersistence)storage;
            var restored=journal.returnTarget(player).join().orElseThrow();assertEquals(target,restored);
            assertEquals(before,restored.resolve(p->true));assertEquals(exit,restored.resolve(p->false));
            journal.clearReturnTarget(player,UUID.randomUUID()).join();assertTrue(journal.returnTarget(player).join().isPresent());
            journal.clearReturnTarget(player,target.sessionId()).join();assertTrue(journal.returnTarget(player).join().isEmpty());
        }
    }
    @Test void exitDestinationNeverUsesPrevious() {
        assertEquals(exit,new ReturnTarget(UUID.randomUUID(),before,exit,FinishDestination.EXIT).resolve(p->true));
    }
}
