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
        boolean restoring,exhausted,returning;
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
    private long preparedAt,startedAt=-1;
    SessionCinematic(CinematicRecovery recovery,BiPredicate<Player,Point> teleport,Consumer<Player> title,Consumer<Throwable> errors) {
        this.recovery=recovery;this.teleport=teleport;this.title=title;this.errors=errors;
    }
    void start(DungeonSession session,List<Point> route) {
        this.route=route;preparedAt=session.scheduler().currentTick();startedAt=-1;
        for(Player player:session.players()) {
            var saved=CinematicRecovery.capture(player);
            viewers.put(player.getUniqueId(),new Viewer(player,saved,recovery.backup(saved)));
        }
    }
    boolean preparing() {return startedAt<0 && !viewers.isEmpty();}
    boolean contains(UUID player) {return viewers.containsKey(player);}
    boolean tick(DungeonSession session) {
        if(viewers.isEmpty())return false;
        long now=session.scheduler().currentTick();
        if(startedAt<0) {
            for(Viewer viewer:viewers.values()) {
                if(viewer.persisted.isCompletedExceptionally())viewer.persisted.join();
                if(!viewer.persisted.isDone()) {
                    if(now-preparedAt>=200)throw new IllegalStateException("Cinematic backup timeout");
                    return true;
                }
            }
            startedAt=now;
            for(Viewer viewer:viewers.values()) {recovery.activate(viewer.player,viewer.saved);title.accept(viewer.player);}
        }
        int index=(int)Math.min(now-startedAt,route.size()-1);
        Point frame=route.isEmpty()?null:route.get(index);
        for(Viewer viewer:List.copyOf(viewers.values())) {
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
        if(viewers.isEmpty()){route=List.of();return false;}
        return true;
    }
    void restore(Player player) {
        Viewer viewer=viewers.get(player.getUniqueId());if(viewer==null)return;
        viewer.restoring=true;
        try{recovery.restore(player,viewer.saved);remove(viewer);}
        catch(RuntimeException failure) {
            // Terminal paths have no next session tick: enforce safety immediately.
            errors.accept(failure);
            try{recovery.forceRestore(player,viewer.saved);remove(viewer);}
            catch(RuntimeException forcedFailure) {
                if(!player.isOnline() && recovery.attributesMatch(player,viewer.saved))remove(viewer);
                else fallback(viewer,()->remove(viewer));
            }
        }
    }
    private void remove(Viewer viewer) {
        viewers.remove(viewer.player.getUniqueId(),viewer);skips.remove(viewer.player.getUniqueId());
    }
    private void completed(DungeonSession session,Viewer viewer) {
        remove(viewer);
        if(session.def().teleportOnStart() && !teleport.test(viewer.player,session.checkpoint()))
            throw new IllegalStateException("Cinematic start teleport cancelled");
    }
    private void restoreTick(DungeonSession session,Viewer viewer) {
        if(viewer.returning)return;
        if(viewer.exhausted) {
            // Retain ownership if even the last-resort API mutation was vetoed.
            try {
                recovery.restoreAttributes(viewer.player,viewer.saved,true);
                // A prior attempt may have returned the position before a mode veto.
                // Confirm it without restarting the bounded teleport/chunk retry sequence.
                if(recovery.confirmCurrentPosition(viewer.player,viewer.saved))completed(session,viewer);
            } catch(RuntimeException ignored){}
            return;
        }
        try{recovery.restore(viewer.player,viewer.saved);}
        catch(RuntimeException failure) {
            if(++viewer.attempts<RESTORE_ATTEMPTS)return;
            errors.accept(new IllegalStateException("Cinematic restoration exhausted tick retries; forcing saved mode",failure));
            try{recovery.forceRestore(viewer.player,viewer.saved);}
            catch(RuntimeException forcedFailure){fallback(viewer,()->completed(session,viewer));return;}
        }
        completed(session,viewer);
    }
    private void fallback(Viewer viewer,Runnable done) {
        if(viewer.returning || viewer.exhausted)return;
        viewer.returning=true;
        recovery.recover(viewer.player,false,()->viewer.player.isOnline() && viewers.get(viewer.player.getUniqueId())==viewer,
                done,()->{viewer.returning=false;viewer.exhausted=true;});
    }
    void clear() {
        // A failed player must not prevent the remaining participants from recovering.
        for(Viewer viewer:List.copyOf(viewers.values()))try{restore(viewer.player);}catch(RuntimeException error){errors.accept(error);}
        route=List.of();
    }
}
