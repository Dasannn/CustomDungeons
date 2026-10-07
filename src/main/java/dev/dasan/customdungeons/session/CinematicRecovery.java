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
    private final Supplier<Point> spawn;
    private final Map<UUID,GameMode> forcedModes=new HashMap<>();
    private final Map<UUID,Pending> retries=new LinkedHashMap<>();
    private final Map<UUID,String> warnings=new HashMap<>();
    private Runnable changed=()->{};
    private boolean closed;
    private long tick;
    private static final class Pending {
        final Player player;
        final CinematicJournal.Saved saved;
        final Point target;
        final int stage;
        final BooleanSupplier valid;
        final Runnable done;
        boolean busy;
        long due;
        Pending(Player player,CinematicJournal.Saved saved,Point target,int stage,BooleanSupplier valid,Runnable done) {
            this.player=player;this.saved=saved;this.target=target;this.stage=stage;this.valid=valid;this.done=done;
        }
    }
    boolean hasPending() {return !retries.isEmpty();}
    void onPendingChanged(Runnable changed) {this.changed=changed;changed.run();}
    void warn(CinematicJournal.Saved saved,Throwable error) {warn(saved.player(),saved.token().toString(),error);}
    private void warn(UUID player,String token,Throwable error) {
        if(!Objects.equals(warnings.put(player,token),token))errors.accept(error);
    }
    void cancel(CinematicJournal.Saved saved) {
        var pending=retries.get(saved.player());
        if(pending!=null && pending.saved.token().equals(saved.token())) {retries.remove(saved.player());changed.run();}
    }
    void defer(Player player,CinematicJournal.Saved saved,Point target,int stage,BooleanSupplier valid,Runnable done) {
        if(closed)return;
        observe(journal.pending(saved));
        var pending=new Pending(player,saved,target,stage,valid,done);pending.due=tick+20;
        retries.put(saved.player(),pending);changed.run();
    }
    private boolean current(Pending pending) {
        return !closed && retries.get(pending.saved.player())==pending && pending.player.isOnline() && pending.valid.getAsBoolean()
                && active(pending.player,pending.saved);
    }
    /** Plugin-wide recovery work, driven by existing tickers; one attempt per 20 server ticks. */
    void tick(long now) {
        tick=now;
        if(retries.isEmpty())return;
        for(var pending:List.copyOf(retries.values())) {
            if(!current(pending)) {
                if(retries.remove(pending.saved.player(),pending))changed.run();
                continue; // Preserve the journal and marker for the next real connection.
            }
            if(pending.busy || now<pending.due)continue;
            pending.busy=true;pending.due=now+20;
            try {
                restoreAttributes(pending.player,pending.saved,true);
                Runnable done=()->{if(retries.remove(pending.saved.player(),pending)){changed.run();pending.done.run();}};
                String marker="active:"+pending.saved.token();
                recoverPosition(pending.player,pending.saved,marker,()->current(pending),done,
                        ()->{pending.busy=false;pending.due=tick+20;},pending.target,pending.stage);
            } catch(RuntimeException error) {
                warn(pending.saved,error);pending.busy=false;
            }
        }
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,Consumer<Throwable> errors) {
        this(journal,teleport,main,errors,chunk->{},chunk->{});
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,
            Consumer<Throwable> errors,Consumer<Chunk> retain,Consumer<Chunk> release) {
        this(journal,teleport,main,errors,retain,release,player->null);
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,
            Consumer<Throwable> errors,Consumer<Chunk> retain,Consumer<Chunk> release,Function<Player,Point> exit) {
        this(journal,teleport,main,errors,retain,release,exit,RespawnDestinations::defaultSpawn);
    }
    CinematicRecovery(CinematicJournal journal,BiPredicate<Player,Point> teleport,Consumer<Runnable> main,
            Consumer<Throwable> errors,Consumer<Chunk> retain,Consumer<Chunk> release,Function<Player,Point> exit,Supplier<Point> spawn) {
        this.journal=journal;this.teleport=teleport;this.main=main;this.errors=errors;this.retain=retain;this.release=release;this.exit=exit;this.spawn=spawn;
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
        confirm(player,saved,saved.position());
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
    private void confirm(Player player,CinematicJournal.Saved saved,Point expected) {
        String marker=player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING);
        if(marker!=null && !marker.equals("active:"+saved.token()) && !marker.equals("restored:"+saved.token()))
            throw new IllegalStateException("Cinematic generation changed during confirmation");
        if(!attributesMatch(player,saved) || !positionMatches(player,expected))
            throw new IllegalStateException("Cinematic final state differs from restoration target");
        if(active(player,saved)) {
            player.getPersistentDataContainer().set(MARKER,PersistentDataType.STRING,"restored:"+saved.token());
        }
        observe(journal.restored(saved));
    }
    private boolean positionMatches(Player player,Point expected) {
        Location at=player.getLocation();
        if(at.getWorld()==null || !at.getWorld().getName().equals(expected.world()))return false;
        double x=at.getX()-expected.x(),y=at.getY()-expected.y(),z=at.getZ()-expected.z();
        return x*x+y*y+z*z<.25 && Math.abs(Math.IEEEremainder((double)at.getYaw()-expected.yaw(),360))<=1
                && Math.abs((double)at.getPitch()-expected.pitch())<=1;
    }
    private static void throwFailures(List<RuntimeException> failures) {
        if(failures.isEmpty())return;
        var failure=failures.removeFirst();failures.forEach(failure::addSuppressed);throw failure;
    }
    private static void attempt(List<RuntimeException> failures,Runnable action) {try{action.run();}catch(RuntimeException error){failures.add(error);}}
    private void observe(CompletableFuture<?> operation) {operation.exceptionally(error->{errors.accept(error);return null;});}
    /** Capture the vanilla marker before any asynchronous work. Only a real login acknowledges it. */
    void recover(Player player,boolean realJoin,BooleanSupplier connected,Runnable done,Runnable failed) {
        if(closed || !connected.getAsBoolean())return;
        if(retries.remove(player.getUniqueId())!=null)changed.run();
        var pdc=player.getPersistentDataContainer();
        String marker=pdc==null?null:pdc.get(MARKER,PersistentDataType.STRING);
        if(marker==null){done.run();return;}
        CinematicJournal.Saved previous=null;
        try {
            String[] parts=marker.split(":",2);UUID token=UUID.fromString(parts[1]);
            if(parts[0].equals("restored")) {
                if(!realJoin){done.run();return;}
                var saved=journal.get(player.getUniqueId(),token);
                // A previously verified restoration permits later ordinary gameplay changes.
                // An unverified journal still needs live proof before it can be acknowledged.
                if(saved.isPresent() && !saved.get().restored()
                        && (!attributesMatch(player,saved.get()) || !positionMatches(player,saved.get().position()))) {
                    player.getPersistentDataContainer().set(MARKER,PersistentDataType.STRING,"active:"+token);
                    recover(player,false,connected,done,failed);return;
                }
                var updated=saved.filter(s->!s.restored()).map(journal::restored).orElseGet(()->CompletableFuture.completedFuture(null));
                updated.thenCompose(v->journal.acknowledge(player.getUniqueId(),token)).whenComplete((v,error)->main.accept(()->{
                    if(closed || !connected.getAsBoolean())return;
                    if(error!=null){errors.accept(error);failed.run();return;}
                    if(marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))
                        player.getPersistentDataContainer().remove(MARKER);
                    warnings.remove(player.getUniqueId());
                    done.run();
                }));return;
            }
            if(!parts[0].equals("active"))throw new IllegalStateException("Invalid cinematic marker");
            var saved=journal.get(player.getUniqueId(),token).orElseThrow(()->new IllegalStateException("Missing cinematic backup"));
            previous=saved;observe(journal.pending(saved));
            try{restoreAttributes(player,saved,false);}catch(RuntimeException cancelled) {
                warn(saved,cancelled);restoreAttributes(player,saved,true);
            }
            Runnable pending=()->{defer(player,saved,saved.position(),0,connected,done);failed.run();};
            recoverPosition(player,saved,marker,connected,done,pending,saved.position(),0);
        } catch(RuntimeException error){
            if(previous==null)warn(player.getUniqueId(),marker,error);else warn(previous,error);
            if(pending(player) && player.getGameMode()==GameMode.SPECTATOR && (previous==null || !previous.mode().equals("SPECTATOR")))emergencySafety(player,previous);
            if(previous!=null)defer(player,previous,previous.position(),0,connected,done);
            failed.run();
        }
    }
    void emergencySafety(Player player,CinematicJournal.Saved previous) {
        if(previous!=null && (player.getGameMode()!=GameMode.SPECTATOR || previous.mode().equals("SPECTATOR")))return;
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
        for(var failure:failures) {
            if(previous!=null)warn(previous,failure);else warn(player.getUniqueId(),player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING),failure);
        }
    }
    private void recoverPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,
            Runnable done,Runnable failed,Point point,int stage) {
        if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
        if(point==null){nextPosition(player,saved,marker,connected,done,failed,stage);return;}
        try {
            World world=Objects.requireNonNull(Bukkit.getWorld(point.world()),"Missing cinematic return world");
            int x=((int)Math.floor(point.x()))>>4,z=((int)Math.floor(point.z()))>>4;
            world.getChunkAtAsync(x,z).thenApply(chunk->chunk).orTimeout(10,TimeUnit.SECONDS)
                    .whenComplete((chunk,error)->main.accept(()->{
                if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
                boolean retained=false;
                try {
                    if(error!=null)throw new CompletionException(error);
                    retain.accept(chunk);retained=true;
                    deliverPosition(player,saved,marker,connected,done,failed,point,stage);
                } catch(RuntimeException failure) {
                    warn(saved,failure);nextPosition(player,saved,marker,connected,done,failed,stage);
                } finally {if(retained)release.accept(chunk);}
            }));
        } catch(RuntimeException error){warn(saved,error);nextPosition(player,saved,marker,connected,done,failed,stage);}
    }
    private void deliverPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,
            Runnable done,Runnable failed,Point point,int stage) {
        if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
        try {
            if(!tryPosition(player,saved,marker,connected,point))throw new IllegalStateException("Cinematic return teleport cancelled or redirected");
            done.run();
        } catch(RuntimeException error){warn(saved,error);nextPosition(player,saved,marker,connected,done,failed,stage);}
    }
    private boolean tryPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,Point point) {
        if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return false;
        if(!teleport.test(player,point))return false;
        if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return false;
        // A different plugin may have changed attributes while the chunk was loading.
        if(!attributesMatch(player,saved))restoreAttributes(player,saved,true);
        if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return false;
        confirm(player,saved,point);return true;
    }
    private void nextPosition(Player player,CinematicJournal.Saved saved,String marker,BooleanSupplier connected,
            Runnable done,Runnable failed,int stage) {
        if(closed || !connected.getAsBoolean() || !marker.equals(player.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING)))return;
        if(stage>=2){failed.run();return;}
        Point fallback=null;
        try {
            if(stage==0)fallback=exit.apply(player);
            else fallback=spawn.get();
        } catch(RuntimeException error){warn(saved,error);}
        recoverPosition(player,saved,marker,connected,done,failed,fallback,stage+1);
    }
    void close() {closed=true;retries.clear();warnings.clear();changed.run();journal.close();}
}
