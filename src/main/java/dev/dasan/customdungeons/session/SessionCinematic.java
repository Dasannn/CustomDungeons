package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.entity.Player;

/** One session's cinematic, stepped exclusively from SessionTicker. */
final class SessionCinematic {
    private static final int RESTORE_ATTEMPTS=20;
    private static final class Viewer {
        final Player player;
        final CinematicJournal.Saved saved;
        final CompletableFuture<Void> persisted;
        boolean restoring,returning,terminal;
        int attempts;
        Viewer(Player player,CinematicJournal.Saved saved,CompletableFuture<Void> persisted) {
            this.player=player;this.saved=saved;this.persisted=persisted;
        }
    }
    private final CinematicRecovery recovery;
    private final BiPredicate<Player,Point> teleport;
    private final Consumer<Player> title;
    private final Consumer<Throwable> errors;
    private final Map<UUID,Viewer> viewers=new LinkedHashMap<>();
    private final Set<UUID> skips=new HashSet<>();
    void skip(UUID player) {if(viewers.containsKey(player))skips.add(player);}
    private List<Point> route=List.of();
    private long preparedAt,startedAt=-1,generation;
    private DungeonSession owner;
    SessionCinematic(CinematicRecovery recovery,BiPredicate<Player,Point> teleport,Consumer<Player> title,Consumer<Throwable> errors) {
        this.recovery=recovery;this.teleport=teleport;this.title=title;this.errors=errors;
    }
    void start(DungeonSession session,List<Point> route) {
        owner=session;generation++;this.route=route;preparedAt=session.scheduler().currentTick();startedAt=-1;
        for(Player player:session.players()) {
            var saved=CinematicRecovery.capture(player);
            viewers.put(player.getUniqueId(),new Viewer(player,saved,recovery.backup(saved)));
        }
    }
    private boolean activeViewers() {return viewers.values().stream().anyMatch(viewer->!viewer.terminal);}
    boolean preparing() {return startedAt<0 && activeViewers();}
    boolean contains(UUID player) {return viewers.containsKey(player);}
    boolean tick(DungeonSession session) {
        if(!activeViewers())return false;
        long now=session.scheduler().currentTick();
        if(startedAt<0) {
            for(Viewer viewer:viewers.values()) {
                if(viewer.terminal)continue;
                if(viewer.persisted.isCompletedExceptionally())viewer.persisted.join();
                if(!viewer.persisted.isDone()) {
                    if(now-preparedAt>=200)throw new IllegalStateException("Cinematic backup timeout");
                    return true;
                }
            }
            startedAt=now;
            for(Viewer viewer:viewers.values()) {if(viewer.terminal)continue;recovery.activate(viewer.player,viewer.saved);title.accept(viewer.player);}
        }
        int index=(int)Math.min(now-startedAt,route.size()-1);
        Point frame=route.isEmpty()?null:route.get(index);
        for(Viewer viewer:List.copyOf(viewers.values())) {
            if(viewer.terminal)continue;
            Player player=viewer.player;
            if(!player.isOnline()) {
                try {restore(player);} catch(RuntimeException error){errors.accept(error);}
                continue; // The quit event owns membership; an offline TP must not stop the other cameras.
            }
            if(viewer.restoring || frame==null){viewer.restoring=true;restoreTick(session,viewer);continue;}
            if(skips.contains(player.getUniqueId()) || player.isSneaking() || index==route.size()-1) {
                // Deliver the final frame before returning; individual skips do not affect other cameras.
                if(!skips.contains(player.getUniqueId()) && !player.isSneaking() && !teleport.test(player,frame))throw new IllegalStateException("Cinematic camera teleport cancelled");
                viewer.restoring=true;restoreTick(session,viewer);
            } else if(!teleport.test(player,frame))throw new IllegalStateException("Cinematic camera teleport cancelled");
        }
        if(!activeViewers()){route=List.of();return false;}
        return true;
    }
    void restore(Player player) {
        Viewer viewer=viewers.get(player.getUniqueId());if(viewer==null)return;
        recovery.cancel(viewer.saved);viewer.restoring=true;viewer.returning=false;
        try{recovery.restore(player,viewer.saved);remove(viewer);}
        catch(RuntimeException failure) {
            recovery.warn(viewer.saved,failure);
            try{recovery.forceRestore(player,viewer.saved);remove(viewer);}
            catch(RuntimeException forcedFailure) {
                if(!player.isOnline()) {remove(viewer);return;} // Durable backup remains pending for login.
                recovery.emergencySafety(player,viewer.saved);
                defer(viewer,active(owner,viewer,generation));
            }
        }
    }
    private void remove(Viewer viewer) {
        recovery.cancel(viewer.saved);
        viewers.remove(viewer.player.getUniqueId(),viewer);skips.remove(viewer.player.getUniqueId());
    }
    private boolean tracked(Viewer viewer,long expectedGeneration) {
        return generation==expectedGeneration && viewer.player.isOnline() && viewers.get(viewer.player.getUniqueId())==viewer;
    }
    private boolean active(DungeonSession session,Viewer viewer,long expectedGeneration) {
        return owner==session && tracked(viewer,expectedGeneration) && session.introActive()
                && session.state().state()==SessionState.RUNNING && session.survivors().contains(viewer.player.getUniqueId());
    }
    private void completed(DungeonSession session,Viewer viewer,long expectedGeneration) {
        if(!active(session,viewer,expectedGeneration))return;
        remove(viewer);
        if(session.def().teleportOnStart() && !teleport.test(viewer.player,session.checkpoint()))
            throw new IllegalStateException("Cinematic start teleport cancelled");
    }
    private void restoreTick(DungeonSession session,Viewer viewer) {
        if(viewer.returning)return;
        try{recovery.restore(viewer.player,viewer.saved);}
        catch(RuntimeException failure) {
            if(++viewer.attempts<RESTORE_ATTEMPTS)return;
            recovery.warn(viewer.saved,new IllegalStateException("Cinematic restoration exhausted tick retries; forcing saved mode",failure));
            try{recovery.forceRestore(viewer.player,viewer.saved);}
            catch(RuntimeException forcedFailure){
                recovery.emergencySafety(viewer.player,viewer.saved);defer(viewer,true);return;
            }
        }
        completed(session,viewer,generation);
    }
    private void defer(Viewer viewer,boolean inIntro) {
        viewer.returning=true;viewer.terminal=!inIntro;
        long expectedGeneration=generation;
        // Terminal retries target the final destination, never a stale pre-camera lobby.
        Point target=inIntro?viewer.saved.position():owner.returnPoint(viewer.player);
        recovery.defer(viewer.player,viewer.saved,target,inIntro?0:1,
                ()->inIntro?active(owner,viewer,expectedGeneration):viewer.player.isOnline(),
                ()->{if(inIntro)completed(owner,viewer,expectedGeneration);else remove(viewer);});
    }
    void clear() {
        // Invalidate ALL outstanding chunk callbacks before the session's exit teleport.
        generation++;
        for(Viewer viewer:List.copyOf(viewers.values()))try{restore(viewer.player);}catch(RuntimeException error){errors.accept(error);}
        route=List.of();
    }
}
