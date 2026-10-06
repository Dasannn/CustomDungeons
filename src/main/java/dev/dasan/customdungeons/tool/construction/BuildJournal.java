package dev.dasan.customdungeons.tool.construction;

import dev.dasan.customdungeons.config.DefinitionCodec;
import dev.dasan.customdungeons.model.DungeonDef;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Serialized atomic, forced authoring writes. Inventory records are retained until player-data acknowledgement. */
public final class BuildJournal implements AutoCloseable {
    public record Inventory(UUID admin, UUID token, byte[] contents, byte[] cursor, int held,long sequence) {
        public Inventory(UUID admin,UUID token,byte[] contents,byte[] cursor,int held) {this(admin,token,contents,cursor,held,0);}
        public Inventory {
            Objects.requireNonNull(admin);Objects.requireNonNull(token);
            if(held<0||held>8||sequence<0) throw new IllegalArgumentException("Invalid inventory metadata");
            contents=contents.clone();cursor=cursor.clone();
        }
        @Override public byte[] contents() {return contents.clone();}
        @Override public byte[] cursor() {return cursor.clone();}
    }
    private record DraftKey(UUID admin,String id) {}
    private record InventoryKey(UUID admin,UUID token) {}
    private final Path draftsDirectory,inventoriesDirectory;
    private final Executor executor;
    private final Map<DraftKey,BuildState.Saved> drafts=new HashMap<>();
    private final Map<InventoryKey,Inventory> inventories=new LinkedHashMap<>();
    private CompletableFuture<Void> writes=CompletableFuture.completedFuture(null);
    private boolean closed;
    private long sequence;

    /** Enable only: a corrupt recovery record fails closed, without deleting any original. */
    public BuildJournal(Path root,Executor executor) {
        this.executor=executor;draftsDirectory=root.resolve("build-drafts");inventoriesDirectory=root.resolve("build-inventories");
        try {
            safeDirectory(draftsDirectory);safeDirectory(inventoriesDirectory);
            try(var files=Files.list(draftsDirectory)) {
                for(var file:files.filter(p->p.toString().endsWith(".yml")).toList()) {
                    safeFile(file);var name=file.getFileName().toString();
                    UUID admin=UUID.fromString(name.substring(0,36));String id=name.substring(38,name.length()-4);checkId(id);
                    var yaml=new YamlConfiguration();yaml.load(file.toFile());
                    var definition=decode(yaml,"definition",id);var baseline=decode(yaml,"baseline",id);
                    var undo=new ArrayList<DungeonDef>();
                    for(int i=0;i<yaml.getInt("undo-count");i++) undo.add(decode(yaml,"undo."+i,id));
                    drafts.put(new DraftKey(admin,id),new BuildState.Saved(definition,baseline,yaml.getInt("room"),yaml.getInt("point"),undo));
                }
            }
            try(var files=Files.list(inventoriesDirectory)) {
                for(var file:files.filter(p->p.toString().endsWith(".bin")).toList()) {
                    safeFile(file);var name=file.getFileName().toString();
                    UUID admin=UUID.fromString(name.substring(0,36)),token=UUID.fromString(name.substring(38,name.length()-4));
                    var recovery=decodeInventory(admin,token,Files.readAllBytes(file));
                    inventories.put(new InventoryKey(admin,token),recovery);
                    sequence=Math.max(sequence,recovery.sequence());
                }
            }
        } catch(Exception failure) {throw new IllegalStateException("Cannot load build recovery data",failure);}
    }
    private static DungeonDef decode(YamlConfiguration yaml,String path,String id) {
        return new DefinitionCodec().decodeDungeon(id,Objects.requireNonNull(yaml.getConfigurationSection(path)));
    }
    public synchronized Optional<BuildState.Saved> draft(UUID admin,String id) {checkId(id);return Optional.ofNullable(drafts.get(new DraftKey(admin,id)));}
    public synchronized CompletableFuture<Void> save(UUID admin,BuildState.Saved saved) {
        if(closed) return CompletableFuture.failedFuture(new IllegalStateException("Build journal closed"));
        String id=saved.definition().id();checkId(id);
        var yaml=new YamlConfiguration();var codec=new DefinitionCodec();
        yaml.set("definition",codec.encode(saved.definition()));yaml.set("baseline",codec.encode(saved.baseline()));
        yaml.set("room",saved.room());yaml.set("point",saved.point());yaml.set("undo-count",saved.undo().size());
        for(int i=0;i<saved.undo().size();i++) yaml.set("undo."+i,codec.encode(saved.undo().get(i)));
        byte[] bytes=yaml.saveToString().getBytes(StandardCharsets.UTF_8);
        drafts.put(new DraftKey(admin,id),saved);
        return enqueue(()->atomic(draftsDirectory.resolve(admin+"--"+id+".yml"),bytes,true));
    }
    public synchronized Optional<Inventory> inventory(UUID admin,UUID token) {return Optional.ofNullable(inventories.get(new InventoryKey(admin,token)));}
    public synchronized Optional<Inventory> latestInventory(UUID admin) {
        return inventories.values().stream().filter(i->i.admin().equals(admin)).max(Comparator.comparingLong(Inventory::sequence));
    }
    /** Reserve on the main thread, before asynchronous NBT serialization can reorder two entries. */
    public synchronized long reserveSequence() {
        if(closed) throw new IllegalStateException("Build journal closed");return ++sequence;
    }
    public synchronized CompletableFuture<Void> backup(Inventory inventory) {
        return enqueue(()->{
            Inventory ordered;
            synchronized(this) {
                long order=inventory.sequence()==0?++sequence:inventory.sequence();sequence=Math.max(sequence,order);
                ordered=new Inventory(inventory.admin(),inventory.token(),inventory.contents(),inventory.cursor(),inventory.held(),order);
            }
            atomic(inventoryPath(ordered.admin(),ordered.token()),encodeInventory(ordered),false);
            synchronized(this) {inventories.put(new InventoryKey(ordered.admin(),ordered.token()),ordered);}
        });
    }
    public synchronized CompletableFuture<Void> acknowledge(UUID admin,UUID token) {
        return enqueue(()->{
            var target=inventoryPath(admin,token);safeFile(target);Files.deleteIfExists(target);forceDirectory(inventoriesDirectory);
            synchronized(this) {inventories.remove(new InventoryKey(admin,token));}
        });
    }
    /** Confirmed restored player-data supersedes every older backup. Retire the newest record last. */
    public synchronized CompletableFuture<Void> acknowledgeAll(UUID admin) {
        return enqueue(()->{
            List<Inventory> owned;
            synchronized(this) {owned=inventories.values().stream().filter(i->i.admin().equals(admin)).sorted(Comparator.comparingLong(Inventory::sequence)).toList();}
            for(var recovery:owned) {
                var target=inventoryPath(admin,recovery.token());safeFile(target);Files.deleteIfExists(target);forceDirectory(inventoriesDirectory);
                synchronized(this) {inventories.remove(new InventoryKey(admin,recovery.token()));}
            }
        });
    }
    private Path inventoryPath(UUID admin,UUID token) {return inventoriesDirectory.resolve(admin+"--"+token+".bin");}
    private synchronized CompletableFuture<Void> enqueue(IoAction action) {
        if(closed) return CompletableFuture.failedFuture(new IllegalStateException("Build journal closed"));
        writes=writes.handle((v,e)->null).thenRunAsync(()->{
            try {action.run();} catch(Exception failure) {throw new CompletionException(failure);}
        },executor);return writes;
    }
    private static void safeDirectory(Path directory) throws IOException {
        // Reject symlinks throughout the ancestry, including a replaced plugin-data parent.
        for(Path p=directory.toAbsolutePath();p!=null;p=p.getParent()) if(Files.isSymbolicLink(p)) throw new IOException("Symlink build directory");
        Files.createDirectories(directory);
    }
    private static void safeFile(Path file) throws IOException {if(Files.isSymbolicLink(file)) throw new IOException("Symlink build file");}
    private static void atomic(Path target,byte[] bytes,boolean replace) throws IOException {
        safeDirectory(target.getParent());safeFile(target);
        if(!replace&&Files.exists(target)) throw new IOException("Inventory token already backed up");
        Path tmp=Files.createTempFile(target.getParent(),".build-",".tmp");
        try {
            try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)) {
                var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true);
            }
            // If atomic moves are unsupported, entry fails before touching the inventory.
            if(replace) Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            else Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(target.getParent());
        } finally {Files.deleteIfExists(tmp);}
    }
    private static void forceDirectory(Path directory) throws IOException {try(var channel=FileChannel.open(directory,StandardOpenOption.READ)) {channel.force(true);}}
    private static byte[] encodeInventory(Inventory inventory) throws IOException {
        var buffer=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(buffer)) {
            out.writeInt(0x43444231);out.writeInt(inventory.held());out.writeLong(inventory.sequence());
            out.writeInt(inventory.contents.length);out.write(inventory.contents);
            out.writeInt(inventory.cursor.length);out.write(inventory.cursor);
        }
        byte[] payload=buffer.toByteArray();buffer.writeBytes(digest(payload));return buffer.toByteArray();
    }
    private static Inventory decodeInventory(UUID admin,UUID token,byte[] bytes) throws IOException {
        if(bytes.length<48) throw new IOException("Truncated inventory backup");
        byte[] payload=Arrays.copyOf(bytes,bytes.length-32),checksum=Arrays.copyOfRange(bytes,bytes.length-32,bytes.length);
        if(!MessageDigest.isEqual(digest(payload),checksum)) throw new IOException("Corrupt inventory backup");
        try(var in=new DataInputStream(new ByteArrayInputStream(payload))) {
            if(in.readInt()!=0x43444231) throw new IOException("Unknown inventory backup");
            int held=in.readInt();long sequence=in.readLong();byte[] contents=readBytes(in),cursor=readBytes(in);
            if(in.available()!=0) throw new IOException("Trailing inventory data");return new Inventory(admin,token,contents,cursor,held,sequence);
        }
    }
    private static byte[] readBytes(DataInputStream in) throws IOException {
        int length=in.readInt();if(length<0||length>in.available()) throw new IOException("Invalid inventory length");
        var bytes=in.readNBytes(length);if(bytes.length!=length) throw new EOFException();return bytes;
    }
    private static byte[] digest(byte[] bytes) {try {return MessageDigest.getInstance("SHA-256").digest(bytes);} catch(NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}}
    private static void checkId(String id) {if(id==null||!id.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("validation.id");}
    @FunctionalInterface private interface IoAction {void run() throws Exception;}
    @Override public void close() {
        CompletableFuture<Void> pending;synchronized(this) {closed=true;pending=writes;}pending.join();
    }
}
