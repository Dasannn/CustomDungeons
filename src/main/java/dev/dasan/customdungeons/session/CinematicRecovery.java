package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Shared journal and vanilla acknowledgement, independent of the session's lifetime. */
final class CinematicRecovery {
    static final NamespacedKey MARKER=Objects.requireNonNull(NamespacedKey.fromString("customdungeons:cinematic_recovery"));
    private final CinematicJournal journal;
    private final BiPredicate<Player,Point> teleport;
    private final Consumer<Runnable> main;
    private final Consumer<Throwable> errors;
    private final Consumer<Chunk> retain,release;
    private final Function<Player,Point> exit;
    private final Map<UUID,GameMode> forcedModes=new HashMap<>();
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,Consumer<Throwable> errors) {
        this(journal,teleport,main,errors,chunk->{},chunk->{});
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,
            Consumer<Throwable> errors,Consumer<Chunk> retain,Consumer<Chunk> release) {
        this(journal,teleport,main,errors,retain,release,player->null);
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,
            Consumer<Throwable> errors,Consumer<Chunk> retain,Consumer<Chunk> release,Function<Player,Point> exit) {
        this.journal=journal;this.teleport=teleport;this.main=main;this.errors=errors;this.retain=retain;this.release=release;this.exit=exit;
    }
    boolean forcingMode(Player player,GameMode mode) {return mode!=null && forcedModes.get(player.getUniqueId())==mode;}
    static boolean pending(Player player) {
        var data=player.getPersistentDataContainer();
        String marker=data==null?null:data.get(MARKER,PersistentDataType.STRING);
        return marker!=null && marker.startsWith("active:");
    }
    static CinematicJournal.Saved capture(Player player) {
        Location at=player.getLocation();
        return new CinematicJournal.Saved(player.getUniqueId(),UUID.randomUUID(),
                new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch()),
                player.getGameMode().name(),player.isInvulnerable(),player.isFlying(),player.getAllowFlight(),false);
    }
    CompletableFuture<Void> backup(CinematicJournal.Saved saved) {return journal.backup(saved);}
    void activate(Player player,CinematicJournal.Saved saved) {
        // Backup must already be durable. Vanilla saves this marker together with the modified state.
        player.getPersistentDataContainer().set(MARKER,PersistentDataType.STRING,"active:"+saved.token());
        player.setInvulnerable(true);player.setGameMode(GameMode.SPECTATOR);
        if(player.getGameMode()!=GameMode.SPECTATOR)throw new IllegalStateException("Cinematic spectator mode cancelled");
        player.setSpectatorTarget(null);player.setAllowFlight(true);player.setFlying(true);
    }
    void restore(Player player,CinematicJournal.Saved saved) {
        restore(player,saved,false);
    }
    void forceRestore(Player player,CinematicJournal.Saved saved) {
        restore(player,saved,true);
    }
    private boolean active(Player player,CinematicJournal.Saved saved) {
        return ("active:"+saved.token()).equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING));
    }
    private void restore(Player player,CinematicJournal.Saved saved,boolean force) {
        String marker=player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING);
        if(("active:"+saved.token()).equals(marker)) {
            var failures=new ArrayList<RuntimeException>();
            // Safety attributes precede ALL world resolution, chunk loading and teleport attempts.
            attempt(failures,()->restoreAttributes(player,saved,force));
            attempt(failures,()->{if(!teleport.test(player,saved.position()))throw new IllegalStateException("Cinematic restore teleport cancelled");});
            throwFailures(failures);
        } else if(marker!=null && !("restored:"+saved.token()).equals(marker)) {
            throw new IllegalStateException("Cinematic generation changed during restoration");
        }
        confirm(player,saved);
    }
    void restoreAttributes(Player player,CinematicJournal.Saved saved,boolean force) {
        if(!active(player,saved))return;
        var failures=new ArrayList<RuntimeException>();
        attempt(failures,()->{if(player.getGameMode()==GameMode.SPECTATOR)player.setSpectatorTarget(null);});
        GameMode mode=GameMode.valueOf(saved.mode());
        attempt(failures,()->{
            if(force)forcedModes.put(player.getUniqueId(),mode);
            try{player.setGameMode(mode);}finally{if(force)forcedModes.remove(player.getUniqueId());}
        });
        attempt(failures,()->player.setAllowFlight(saved.allowFlight()));
        attempt(failures,()->player.setFlying(saved.flying()));
        attempt(failures,()->player.setInvulnerable(saved.invulnerable()));
        if(!attributesMatch(player,saved))failures.add(new IllegalStateException("Cinematic state restoration cancelled"));
        throwFailures(failures);
    }
    boolean attributesMatch(Player player,CinematicJournal.Saved saved) {
        return player.getGameMode()==GameMode.valueOf(saved.mode()) && player.getAllowFlight()==saved.allowFlight()
                && player.isFlying()==saved.flying() && player.isInvulnerable()==saved.invulnerable();
    }
    private void confirm(Player player,CinematicJournal.Saved saved) {
        if(active(player,saved)) {
            if(!attributesMatch(player,saved))throw new IllegalStateException("Cinematic attributes changed during return");
            player.getPersistentDataContainer().set(MARKER,PersistentDataType.STRING,"restored:"+saved.token());
        }
        observe(journal.restored(saved));
    }
    boolean confirmCurrentPosition(Player player,CinematicJournal.Saved saved) {
        Location at=player.getLocation();Point expected=saved.position();
        if(at.getWorld()==null || !at.getWorld().getName().equals(expected.world()) || at.getX()!=expected.x()
                || at.getY()!=expected.y() || at.getZ()!=expected.z() || at.getYaw()!=expected.yaw() || at.getPitch()!=expected.pitch())return false;
        confirm(player,saved);return true;
    }
    private static void throwFailures(List<RuntimeException> failures) {
        if(failures.isEmpty())return;
        var failure=failures.removeFirst();failures.forEach(failure::addSuppressed);throw failure;
    }
    private static void attempt(List<RuntimeException> failures,Runnable action) {try{action.run();}catch(RuntimeException error){failures.add(error);}}
    private void observe(CompletableFuture<?> operation) {operation.exceptionally(error->{errors.accept(error);return null;});}
    /** Capture the vanilla marker before any asynchronous work. Only a real login acknowledges it. */
    void recover(Player player,boolean realJoin,BooleanSupplier connected,Runnable done,Runnable failed) {
        if(!connected.getAsBoolean())return;
        var pdc=player.getPersistentDataContainer();
        String marker=pdc==null?null:pdc.get(MARKER,PersistentDataType.STRING);
        if(marker==null){done.run();return;}
        CinematicJournal.Saved previous=null;
        try {
            String[] parts=marker.split(":",2);UUID token=UUID.fromString(parts[1]);
            if(parts[0].equals("restored")) {
                if(!realJoin){done.run();return;}
                var saved=journal.get(player.getUniqueId(),token);
                var updated=saved.map(journal::restored).orElseGet(()->CompletableFuture.completedFuture(null));
                updated.thenCompose(v->journal.acknowledge(player.getUniqueId(),token)).whenComplete((v,error)->main.accept(()->{
                    if(!connected.getAsBoolean())return;
                    if(error!=null){errors.accept(error);failed.run();return;}
                    if(marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))
                        player.getPersistentDataContainer().remove(MARKER);
                    done.run();
                }));return;
            }
            if(!parts[0].equals("active"))throw new IllegalStateException("Invalid cinematic marker");
            var saved=journal.get(player.getUniqueId(),token).orElseThrow(()->new IllegalStateException("Missing cinematic backup"));
            previous=saved;
            try{restoreAttributes(player,saved,false);}catch(RuntimeException cancelled) {
                errors.accept(cancelled);restoreAttributes(player,saved,true);
            }
            recoverPosition(player,saved,marker,connected,done,failed,saved.position(),0);
        } catch(RuntimeException error){
            errors.accept(error);
            if(pending(player) && player.getGameMode()==GameMode.SPECTATOR && (previous==null || !previous.mode().equals("SPECTATOR")))emergencySafety(player,previous);
            failed.run();
        }
    }
    private void emergencySafety(Player player,CinematicJournal.Saved previous) {
        // A lost/corrupt backup cannot tell us the original flags. Leave a safe playable state,
        // retain the unresolved marker and report the lost exact restoration to the administrator.
        var failures=new ArrayList<RuntimeException>();
        attempt(failures,()->player.setSpectatorTarget(null));
        attempt(failures,()->{
            forcedModes.put(player.getUniqueId(),GameMode.SURVIVAL);
            try{player.setGameMode(GameMode.SURVIVAL);}finally{forcedModes.remove(player.getUniqueId());}
        });
        attempt(failures,()->player.setAllowFlight(previous!=null && previous.allowFlight()));
        attempt(failures,()->player.setFlying(previous!=null && previous.flying()));
        attempt(failures,()->player.setInvulnerable(previous!=null && previous.invulnerable()));
        if(player.getGameMode()==GameMode.SPECTATOR)failures.add(new IllegalStateException("Last-resort cinematic mode restoration vetoed"));
        failures.forEach(errors);
    }
    private void recoverPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,
            Runnable done,Runnable failed,Point point,int stage) {
        if(!connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
        if(point==null){nextPosition(player,saved,marker,connected,done,failed,stage);return;}
        try {
            World world=Objects.requireNonNull(Bukkit.getWorld(point.world()),"Missing cinematic return world");
            int x=((int)Math.floor(point.x()))>>4,z=((int)Math.floor(point.z()))>>4;
            if(world.isChunkLoaded(x,z)) {deliverPosition(player,saved,marker,connected,done,failed,point,stage);return;}
            world.getChunkAtAsync(x,z).thenApply(chunk->chunk).orTimeout(10,TimeUnit.SECONDS)
                    .whenComplete((chunk,error)->main.accept(()->{
                if(!connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
                boolean retained=false;
                try {
                    if(error!=null)throw new CompletionException(error);
                    retain.accept(chunk);retained=true;
                    deliverPosition(player,saved,marker,connected,done,failed,point,stage);
                } catch(RuntimeException failure) {
                    errors.accept(failure);nextPosition(player,saved,marker,connected,done,failed,stage);
                } finally {if(retained)release.accept(chunk);}
            }));
        } catch(RuntimeException error){errors.accept(error);nextPosition(player,saved,marker,connected,done,failed,stage);}
    }
    private void deliverPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,
            Runnable done,Runnable failed,Point point,int stage) {
        try {
            if(!teleport.test(player,point))throw new IllegalStateException("Cinematic return teleport cancelled");
            // A different plugin may have changed attributes while the chunk was loading.
            restoreAttributes(player,saved,true);confirm(player,saved);done.run();
        } catch(RuntimeException error){errors.accept(error);nextPosition(player,saved,marker,connected,done,failed,stage);}
    }
    private void nextPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,
            Runnable done,Runnable failed,int stage) {
        if(!connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
        if(stage>=2){failed.run();return;}
        Point fallback=null;
        try {
            if(stage==0)fallback=exit.apply(player);
            else if(!Bukkit.getWorlds().isEmpty()) {
                Location at=Bukkit.getWorlds().getFirst().getSpawnLocation();
                fallback=new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());
            }
        } catch(RuntimeException error){errors.accept(error);}
        recoverPosition(player,saved,marker,connected,done,failed,fallback,stage+1);
    }
    void close() {journal.close();}
}
