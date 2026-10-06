package dev.dasan.customdungeons.gui.wizard;
import dev.dasan.customdungeons.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class WizardDraftStoreTest {
    @TempDir Path directory;
    static DungeonDef empty() {return new DungeonDef("draft","draft",false,null,null,1,0,30,3,false,0,0,false,
            new ScalingDef(.25,.15),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());}
    @Test void incompleteDraftResumesAcrossRestartsWithoutPublishingADungeon() {
        try(var store=new WizardDraftStore(directory,Runnable::run,s->{})) {
            store.save(new WizardDraftStore.Saved(empty(),0,0)).join();
        }
        try(var reloaded=new WizardDraftStore(directory,Runnable::run,s->{})) {
            assertEquals(empty(),reloaded.get("draft").orElseThrow().definition());
            assertEquals(0,reloaded.get("draft").orElseThrow().step());
            reloaded.delete("draft").join();
        }
        try(var reloaded=new WizardDraftStore(directory,Runnable::run,s->{})) {assertTrue(reloaded.get("draft").isEmpty());}
        assertFalse(Files.exists(directory.resolve("dungeons")));
    }
    @Test void progressIsSavedAndPathTraversalRejected() {
        try(var store=new WizardDraftStore(directory,Runnable::run,s->{})) {
            store.save(new WizardDraftStore.Saved(empty(),3,4)).join();
            assertThrows(IllegalArgumentException.class,()->store.delete("../escape"));
        }
        try(var reloaded=new WizardDraftStore(directory,Runnable::run,s->{})) {
            var saved=reloaded.get("draft").orElseThrow();assertEquals(3,saved.step());assertEquals(4,saved.completed());
        }
    }
    @Test void malformedFileDoesNotLoseTheOtherDrafts() throws Exception {
        try(var store=new WizardDraftStore(directory,Runnable::run,s->{})) {store.save(new WizardDraftStore.Saved(empty(),0,0)).join();}
        Files.writeString(directory.resolve("wizard-drafts/broken.yml"),"step: [\n");
        var warnings=new ArrayList<String>();
        try(var store=new WizardDraftStore(directory,Runnable::run,warnings::add)) {
            assertTrue(store.get("draft").isPresent());assertEquals(1,warnings.size());
        }
    }
}
