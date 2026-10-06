package dev.dasan.customdungeons.gui.wizard;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.model.DungeonDef;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;

/** Incomplete definitions live apart from playable dungeons. Writes are serialized and atomic. */
public final class WizardDraftStore implements AutoCloseable {
    public record Saved(DungeonDef definition,int step,int completed) {
        public Saved {new WizardState(step,completed);}
    }
    private final Path directory;
    private final Executor executor;
    private final Map<String,Saved> drafts=new HashMap<>();
    private CompletableFuture<Void> writes=CompletableFuture.completedFuture(null);
    private boolean closed;
    /** Called only at enable; gameplay reads exclusively from the in-memory cache. */
    public WizardDraftStore(Path root,Executor executor,Consumer<String> warning) {
        directory=root.resolve("wizard-drafts");this.executor=executor;
        try {
            safeDirectory();
            try(var files=Files.list(directory)) {
                for(var file:files.filter(p->p.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                    try {
                        if(Files.isSymbolicLink(file)) throw new IOException("Symlink draft");
                        String id=file.getFileName().toString().replaceFirst("\\.yml$",""); checkId(id);
                        var yaml=new YamlConfiguration();yaml.load(file.toFile());
                        var saved=new Saved(new DefinitionCodec().decodeDungeon(id,Objects.requireNonNull(yaml.getConfigurationSection("definition"))),
                                yaml.getInt("step"),yaml.getInt("completed"));drafts.put(id,saved);
                    } catch(Exception failure) {warning.accept(file.getFileName().toString());}
                }
            }
        } catch(IOException failure) {throw new IllegalStateException("Cannot load wizard drafts",failure);}
    }
    public synchronized Optional<Saved> get(String id) {return Optional.ofNullable(drafts.get(id));}
    public synchronized Map<String,Saved> all() {return Map.copyOf(drafts);}
    public synchronized CompletableFuture<Void> save(Saved saved) {
        String id=saved.definition().id();checkId(id);
        if(closed) return CompletableFuture.failedFuture(new IllegalStateException("Draft store closed"));
        // Snapshot Bukkit item serialization before crossing the thread boundary, just like DefinitionStore.
        var yaml=new YamlConfiguration();yaml.set("definition",new DefinitionCodec().encode(saved.definition()));
        yaml.set("step",saved.step());yaml.set("completed",saved.completed());String text=yaml.saveToString();
        drafts.put(id,saved);
        return enqueue(()->{
            Path target=target(id);Path tmp=Files.createTempFile(directory,".wizard-",".tmp");
            try {
                Files.writeString(tmp,text);
                try {Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException unavailable) {Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(tmp);}
        });
    }
    public synchronized CompletableFuture<Void> delete(String id) {
        checkId(id);
        if(closed) return CompletableFuture.failedFuture(new IllegalStateException("Draft store closed"));
        drafts.remove(id);return enqueue(()->Files.deleteIfExists(target(id)));
    }
    private CompletableFuture<Void> enqueue(IoAction action) {
        writes=writes.handle((value,failure)->null).thenRunAsync(()->{
            try {action.run();} catch(IOException failure) {throw new CompletionException(failure);}
        },executor);return writes;
    }
    private void safeDirectory() throws IOException {
        if(Files.isSymbolicLink(directory) || Files.isSymbolicLink(directory.getParent())) throw new IOException("Symlink draft directory");
        Files.createDirectories(directory);
    }
    private Path target(String id) throws IOException {
        safeDirectory();Path file=directory.resolve(id+".yml");
        if(Files.isSymbolicLink(file)) throw new IOException("Symlink draft file");return file;
    }
    private static void checkId(String id) {
        if(id==null || !id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("validation.id");
    }
    @FunctionalInterface private interface IoAction {void run() throws IOException;}
    @Override public void close() {
        CompletableFuture<Void> pending;
        synchronized(this) {closed=true;pending=writes;}
        pending.join();
    }
}
