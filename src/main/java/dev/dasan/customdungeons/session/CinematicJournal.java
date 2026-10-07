package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Forced atomic backups; serialized writes off-thread, loaded only on enable/reload. */
final class CinematicJournal implements AutoCloseable {
    record Saved(UUID player,UUID token,Point position,String mode,boolean invulnerable,boolean flying,boolean allowFlight,boolean restored) {
        Saved {
            Objects.requireNonNull(player);Objects.requireNonNull(token);Objects.requireNonNull(position);
            if(!Set.of("SURVIVAL","CREATIVE","ADVENTURE","SPECTATOR").contains(mode)
                    || !Double.isFinite(position.x()) || !Double.isFinite(position.y()) || !Double.isFinite(position.z())
                    || !Float.isFinite(position.yaw()) || !Float.isFinite(position.pitch()))throw new IllegalArgumentException("Invalid cinematic backup");
        }
        Saved asRestored() {return new Saved(player,token,position,mode,invulnerable,flying,allowFlight,true);}
    }
    private record Key(UUID player,UUID token) {}
    private record Stored(Saved saved,long order) {}
    private final Path directory;
    private final Executor executor;
    private final Map<Key,Saved> records=new HashMap<>();
    private final Map<Key,Long> orders=new HashMap<>();
    private long sequence;
    private CompletableFuture<Void> writes=CompletableFuture.completedFuture(null);
    private boolean closed;
    CinematicJournal(Path root,Executor executor) {
        this.executor=executor;directory=root.resolve("cinematic-players");
        try {
            safeDirectory();
            try(var files=Files.list(directory)) {
                for(Path file:files.filter(p->p.toString().endsWith(".bin")).toList()) {
                    safeFile(file);Stored stored=decode(Files.readAllBytes(file));Saved s=stored.saved();
                    if(!file.getFileName().toString().equals(s.player()+"--"+s.token()+".bin"))throw new IOException("Mismatched cinematic backup");
                    var key=new Key(s.player(),s.token());records.put(key,s);orders.put(key,stored.order());sequence=Math.max(sequence,stored.order());
                }
            }
        } catch(Exception failure) {throw new IllegalStateException("Cannot load cinematic recovery",failure);}
    }
    synchronized Optional<Saved> get(UUID player,UUID token) {return Optional.ofNullable(records.get(new Key(player,token)));}
    synchronized CompletableFuture<Void> backup(Saved s) {
        return enqueue(()->{long order;synchronized(this){order=++sequence;}
            atomic(s,false,order);synchronized(this){var key=new Key(s.player(),s.token());records.put(key,s);orders.put(key,order);}});
    }
    synchronized CompletableFuture<Void> restored(Saved s) {
        return enqueue(()->{Saved old; synchronized(this){old=records.get(new Key(s.player(),s.token()));}
            if(old==null)return;Saved restored=old.asRestored();long order;synchronized(this){order=orders.get(new Key(s.player(),s.token()));}
            atomic(restored,true,order);
            synchronized(this){records.put(new Key(s.player(),s.token()),restored);}});
    }
    synchronized CompletableFuture<Void> pending(Saved saved) {
        return enqueue(()->{Key key=new Key(saved.player(),saved.token());Saved old;long order;
            synchronized(this){old=records.get(key);order=orders.getOrDefault(key,0L);}
            if(old==null || !old.restored())return;
            Saved pending=new Saved(old.player(),old.token(),old.position(),old.mode(),old.invulnerable(),old.flying(),old.allowFlight(),false);
            atomic(pending,true,order);synchronized(this){records.put(key,pending);}});
    }
    synchronized CompletableFuture<Void> acknowledge(UUID player,UUID token) {
        return enqueue(()->{Key key=new Key(player,token);synchronized(this){var old=records.get(key);
            if(old!=null && !old.restored())throw new IOException("Unconfirmed cinematic restoration");}
            // A real login of the latest restored marker also confirms already superseded restores.
            // The serial queue fences later backups; active generations are never eligible.
            List<Key> confirmed;synchronized(this){long order=orders.getOrDefault(key,0L);confirmed=records.entrySet().stream()
                    .filter(e->e.getKey().player().equals(player) && e.getValue().restored()
                            && (e.getKey().equals(key) || order>0 && orders.get(e.getKey())<order)).map(Map.Entry::getKey)
                    .sorted(Comparator.comparingLong(k->orders.get(k))).toList();}
            safeDirectory();
            for(Key confirmedKey:confirmed) {
                // Persist predecessor deletion before deleting the only remaining PDC reference.
                if(confirmedKey.equals(key))forceDirectory();
                Path file=path(confirmedKey.player(),confirmedKey.token());safeFile(file);Files.deleteIfExists(file);
                synchronized(this){records.remove(confirmedKey);orders.remove(confirmedKey);}
            }
            forceDirectory();});
    }
    private synchronized CompletableFuture<Void> enqueue(IoAction action) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Cinematic journal closed"));
        writes=writes.handle((v,e)->null).thenRunAsync(()->{try{action.run();}catch(Exception e){throw new CompletionException(e);}},executor);
        return writes;
    }
    private Path path(UUID player,UUID token) {return directory.resolve(player+"--"+token+".bin");}
    private void safeDirectory() throws IOException {
        for(Path p=directory.toAbsolutePath();p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symlink cinematic directory");
        Files.createDirectories(directory);
    }
    private static void safeFile(Path file) throws IOException {if(Files.isSymbolicLink(file))throw new IOException("Symlink cinematic backup");}
    private void atomic(Saved s,boolean replace,long order) throws IOException {
        safeDirectory();Path target=path(s.player(),s.token());safeFile(target);
        if(!replace && Files.exists(target))throw new IOException("Cinematic generation exists");
        Path tmp=Files.createTempFile(directory,".cinematic-",".tmp");
        try {
            try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)) {
                ByteBuffer bytes=ByteBuffer.wrap(encode(s,order));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
            }
            if(replace)Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            else Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
            forceDirectory();
        } finally {Files.deleteIfExists(tmp);}
    }
    private void forceDirectory() throws IOException {try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}}
    private static byte[] encode(Saved s,long order) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) {
            out.writeInt(0x43444332);out.writeLong(order);out.writeUTF(s.player().toString());out.writeUTF(s.token().toString());
            var p=s.position();out.writeUTF(p.world());out.writeDouble(p.x());out.writeDouble(p.y());out.writeDouble(p.z());out.writeFloat(p.yaw());out.writeFloat(p.pitch());
            out.writeUTF(s.mode());out.writeBoolean(s.invulnerable());out.writeBoolean(s.flying());out.writeBoolean(s.allowFlight());out.writeBoolean(s.restored());
        }
        byte[] payload=bytes.toByteArray();bytes.writeBytes(digest(payload));return bytes.toByteArray();
    }
    private static Stored decode(byte[] bytes) throws IOException {
        if(bytes.length<36)throw new IOException("Truncated cinematic backup");
        byte[] payload=Arrays.copyOf(bytes,bytes.length-32);
        if(!MessageDigest.isEqual(digest(payload),Arrays.copyOfRange(bytes,bytes.length-32,bytes.length)))throw new IOException("Corrupt cinematic backup");
        try(var in=new DataInputStream(new ByteArrayInputStream(payload))) {
            int format=in.readInt();
            if(format!=0x43444331 && format!=0x43444332)throw new IOException("Unknown cinematic backup");
            long order=format==0x43444332?in.readLong():0;
            if(order<0)throw new IOException("Invalid cinematic generation order");
            UUID player=UUID.fromString(in.readUTF()),token=UUID.fromString(in.readUTF());
            Point position=new Point(in.readUTF(),in.readDouble(),in.readDouble(),in.readDouble(),in.readFloat(),in.readFloat());
            var s=new Saved(player,token,position,in.readUTF(),in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readBoolean());
            if(in.available()!=0)throw new IOException("Trailing cinematic backup");return new Stored(s,order);
        }
    }
    private static byte[] digest(byte[] bytes) {try{return MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    @FunctionalInterface private interface IoAction {void run() throws Exception;}
    public void close() {CompletableFuture<Void> pending;synchronized(this){closed=true;pending=writes;}pending.join();}
}
