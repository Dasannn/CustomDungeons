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
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,Consumer<Throwable> errors) {
        this(journal,teleport,main,errors,chunk->{},chunk->{});
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,
            Consumer<Throwable> errors,Consumer<Chunk> retain,Consumer<Chunk> release) {
        this.journal=journal;this.teleport=teleport;this.main=main;this.errors=errors;this.retain=retain;this.release=release;
    }
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
        String marker=player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING);
        if(("active:"+saved.token()).equals(marker)) {
            var failures=new ArrayList<RuntimeException>();
            attempt(failures,()->{if(player.getGameMode()==GameMode.SPECTATOR)player.setSpectatorTarget(null);});
            attempt(failures,()->{if(!teleport.test(player,saved.position()))throw new IllegalStateException("Cinematic restore teleport cancelled");});
            // A teleport failure must never leave spectator, god mode or flight behind.
            attempt(failures,()->player.setGameMode(GameMode.valueOf(saved.mode())));
            attempt(failures,()->player.setAllowFlight(saved.allowFlight()));
            attempt(failures,()->player.setFlying(saved.flying()));
            attempt(failures,()->player.setInvulnerable(saved.invulnerable()));
            attempt(failures,()->{
                if(player.getGameMode()!=GameMode.valueOf(saved.mode()) || player.getAllowFlight()!=saved.allowFlight()
                        || player.isFlying()!=saved.flying() || player.isInvulnerable()!=saved.invulnerable())
                    throw new IllegalStateException("Cinematic state restoration cancelled");
            });
            if(!failures.isEmpty()) {
                var failure=failures.removeFirst();failures.forEach(failure::addSuppressed);throw failure;
            }
            player.getPersistentDataContainer().set(MARKER,PersistentDataType.STRING,"restored:"+saved.token());
        }
        observe(journal.restored(saved));
    }
    private static void attempt(List<RuntimeException> failures,Runnable action) {try{action.run();}catch(RuntimeException error){failures.add(error);}}
    private void observe(CompletableFuture<?> operation) {operation.exceptionally(error->{errors.accept(error);return null;});}
    /** Capture the vanilla marker before any asynchronous work. Only a real login acknowledges it. */
    void recover(Player player,boolean realJoin,BooleanSupplier connected,Runnable done,Runnable failed) {
        var pdc=player.getPersistentDataContainer();
        String marker=pdc==null?null:pdc.get(MARKER,PersistentDataType.STRING);
        if(marker==null){done.run();return;}
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
            World world=Objects.requireNonNull(Bukkit.getWorld(saved.position().world()),"Missing cinematic return world");
            Point p=saved.position();
            world.getChunkAtAsync(((int)Math.floor(p.x()))>>4,((int)Math.floor(p.z()))>>4)
                    .thenApply(chunk->chunk).orTimeout(10,TimeUnit.SECONDS).whenComplete((chunk,error)->main.accept(()->{
                if(!connected.getAsBoolean())return;
                boolean retained=false;
                try {
                    if(error!=null)throw new CompletionException(error);
                    if(!marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))
                        throw new IllegalStateException("Cinematic generation changed during recovery");
                    retain.accept(chunk);retained=true;
                    restore(player,saved);done.run();
                } catch(RuntimeException failure){errors.accept(failure);failed.run();}
                finally {if(retained)release.accept(chunk);}
            }));
        } catch(RuntimeException error){errors.accept(error);failed.run();}
    }
    void close() {journal.close();}
}
