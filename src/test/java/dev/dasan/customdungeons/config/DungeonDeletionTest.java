package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.gui.wizard.WizardDraftStore;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.construction.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DungeonDeletionTest {
    @TempDir Path root;
    DungeonDef definition() {return new DungeonDef("draft","draft",false,null,null,1,0,30,3,false,0,0,false,
            new ScalingDef(.25,.15),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());}
    DefinitionStore store(java.util.concurrent.Executor executor) {
        return new DefinitionStore(root,new ConfigLoader(p->{},m->true).load(new YamlConfiguration()),Set.of(),s->{},executor);
    }
    @Test void publishedAndUnpublishedDeletionRemoveAllDraftsButKeepInventoryRecoveryAndHistory() throws Exception {
        for(boolean published:List.of(false,true)) {
            var queue=new ArrayDeque<Runnable>();
            try(var definitions=store(queue::add);var wizard=new WizardDraftStore(root,Runnable::run,s->{});
                    var journal=new BuildJournal(root,Runnable::run)) {
                var d=definition();
                if(published) {
                    Files.createDirectories(root.resolve("dungeons"));
                    var yaml=new YamlConfiguration();new DefinitionCodec().encode(d).forEach(yaml::set);
                    Files.writeString(root.resolve("dungeons/draft.yml"),yaml.saveToString());definitions.loadAll();
                }
                UUID admin=UUID.randomUUID(),token=UUID.randomUUID();
                wizard.save(new WizardDraftStore.Saved(d,0,0)).join();
                journal.save(admin,new BuildState.Saved(d,d,0,0,List.of(),published)).join();
                journal.backup(new BuildJournal.Inventory(admin,token,new byte[]{1},new byte[]{2},0)).join();
                Path history=root.resolve("history.db");Files.writeString(history,"history and statistics");
                var files=new ArrayList<>(journal.deletionFiles(d.id()));files.addAll(wizard.deletionFiles(d.id()));
                assertTrue(definitions.beginDeletion(d.id()));assertFalse(definitions.beginDeletion(d.id()));
                assertThrows(IllegalStateException.class,definitions::reload);
                var result=definitions.deleteDungeon(d.id(),files,()->{journal.forgetDeleted(d.id());wizard.forgetDeleted(d.id());});
                assertFalse(result.isDone());assertTrue(wizard.get(d.id()).isPresent());assertTrue(journal.draft(admin,d.id()).isPresent());
                queue.remove().run();result.join();definitions.endDeletion(d.id());
                assertFalse(definitions.dungeons().containsKey(d.id()));assertTrue(wizard.get(d.id()).isEmpty());
                assertTrue(journal.draft(admin,d.id()).isEmpty());assertTrue(journal.inventory(admin,token).isPresent());
                assertEquals("history and statistics",Files.readString(history));
                try(var restarted=store(Runnable::run);var w=new WizardDraftStore(root,Runnable::run,s->{});var j=new BuildJournal(root,Runnable::run)) {
                    restarted.loadAll();assertFalse(restarted.dungeons().containsKey(d.id()));assertTrue(w.all().isEmpty());
                    assertTrue(j.draft(admin,d.id()).isEmpty());assertTrue(j.inventory(admin,token).isPresent());
                }
            }
        }
    }
    @Test void failedIoKeepsDefinitionAndDraftThenRetryIsIdempotent() throws Exception {
        try(var definitions=store(Runnable::run);var wizard=new WizardDraftStore(root,Runnable::run,s->{})) {
            wizard.save(new WizardDraftStore.Saved(definition(),0,0)).join();
            Files.createDirectories(root.resolve("dungeons"));
            Path bad=root.resolve("dungeons/draft.yml");
            var yaml=new YamlConfiguration();new DefinitionCodec().encode(definition()).forEach(yaml::set);
            Files.writeString(bad,yaml.saveToString());definitions.loadAll();var original=definitions.dungeons().get("draft");assertNotNull(original);
            Files.delete(bad);Files.createDirectory(bad);Files.writeString(bad.resolve("occupied"),"x");
            Runnable retire=()->wizard.forgetDeleted("draft");
            assertThrows(CompletionException.class,()->definitions.deleteDungeon("draft",wizard.deletionFiles("draft"),retire).join());
            assertSame(original,definitions.dungeons().get("draft"));
            assertTrue(wizard.get("draft").isPresent());assertTrue(Files.exists(wizard.deletionFiles("draft").getFirst()));
            Files.delete(bad.resolve("occupied"));Files.delete(bad);
            definitions.deleteDungeon("draft",wizard.deletionFiles("draft"),retire).join();
            definitions.deleteDungeon("draft",wizard.deletionFiles("draft"),retire).join();assertTrue(wizard.all().isEmpty());assertFalse(definitions.dungeons().containsKey("draft"));
        }
    }
}
