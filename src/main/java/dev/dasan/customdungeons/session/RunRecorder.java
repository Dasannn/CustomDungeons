package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.storage.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread snapshots, ordered asynchronous storage operations per session. */
public final class RunRecorder implements SessionLifecycleListener, Listener {
    private static final class Run {
        final Instant opened = Instant.now();
        final DungeonSession session;
        Run(DungeonSession session) { this.session=session; }
        final Set<UUID> players = new LinkedHashSet<>();
        final Map<UUID,Integer> kills = new HashMap<>(), deaths = new HashMap<>();
        CompletableFuture<Long> id;
        CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    }
    private final Storage storage;
    private final SessionManager manager;
    private final Logger logger;
    private final org.bukkit.plugin.Plugin plugin;
    private final List<CompletableFuture<?>> pending = new ArrayList<>();
    private final Map<UUID,Run> runs = new HashMap<>();
    public RunRecorder(Storage storage, SessionManager manager, Logger logger) {
        this(storage,manager,logger,null);
    }
    public RunRecorder(Storage storage, SessionManager manager, Logger logger, org.bukkit.plugin.Plugin plugin) {
        this.storage=storage; this.manager=manager; this.logger=logger; this.plugin=plugin;
    }
    @Override public void onStateChange(DungeonSession s, SessionState from, SessionState to) {
        if (to == SessionState.LOBBY) {
            runs.put(s.id(),new Run(s)); snapshotActive(s);
        } else if (to == SessionState.RUNNING) {
            Run run=runs.get(s.id());
            run.players.addAll(s.survivors());
            if (!s.testMode()) {
                var participants=Set.copyOf(run.players);
                Instant start=Instant.now();
                run.id=run.tail.thenCompose(unused -> storage.startRun(s.def().id(),start,participants));
                run.tail=run.id.thenApply(unused -> null);
            }
            snapshotActive(s);
        } else if (to == SessionState.FREE) {
            Run run=runs.remove(s.id());
            if (run != null) observe(run.tail.thenCompose(unused -> storage.clearActive(s.id())));
        }
    }
    private void snapshotActive(DungeonSession s) { snapshotActive(s,s.survivors()); }
    private void snapshotActive(DungeonSession s, Set<UUID> activePlayers) {
        Run run=runs.get(s.id()); if (run == null) return;
        run.players.addAll(s.survivors());
        // T09 separately persists exits for eliminated/left players. Never redirect them again after a later crash.
        var active=new ActiveSessionRecord(s.id(),s.def().id(),activePlayers,s.def().exit());
        run.tail=run.tail.thenCompose(unused -> storage.markActive(active)); observe(run.tail);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) {
        UUID player=event.getPlayer().getUniqueId();
        for (Run run : List.copyOf(runs.values()))
            if (run.players.contains(player) || run.session.survivors().contains(player)) snapshotActive(run.session);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) {
        UUID player=event.getEntity().getUniqueId();
        manager.sessionOf(player).ifPresent(s -> {
            Run run=runs.get(s.id()); if (run != null) {
                run.deaths.merge(player,1,Integer::sum);
                if (s.livesLeft(player) == 1) without(s,player);
            }
        });
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        manager.sessionOf(event.getPlayer().getUniqueId()).ifPresent(s -> without(s,event.getPlayer().getUniqueId()));
    }
    private void without(DungeonSession s, UUID player) {
        var remaining=new HashSet<>(s.survivors()); remaining.remove(player); snapshotActive(s,Set.copyOf(remaining));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void kill(EntityDeathEvent event) {
        var killer=event.getEntity().getKiller(); if (killer == null) return;
        String session=event.getEntity().getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
        manager.sessionOf(killer.getUniqueId()).filter(s -> s.id().toString().equals(session)).ifPresent(s -> {
            Run run=runs.get(s.id()); if (run != null) run.kills.merge(killer.getUniqueId(),1,Integer::sum);
        });
    }
    @Override public void onFinished(DungeonSession s, RunResult result, Set<UUID> survivors) {
        if (s.testMode()) return;
        Run run=runs.get(s.id()); if (run == null) return;
        Instant end=Instant.now();
        // Cancelled lobbies have no RUNNING transition; create their complete participant record now.
        if (run.id == null) {
            var participants=Set.copyOf(run.players);
            run.id=run.tail.thenCompose(unused -> storage.startRun(s.def().id(),run.opened,participants));
            run.tail=run.id.thenApply(unused -> null);
        }
        List<RunPlayerRecord> records=run.players.stream().map(p -> new RunPlayerRecord(p,
                run.kills.getOrDefault(p,0),run.deaths.getOrDefault(p,0),survivors.contains(p),
                result == RunResult.COMPLETED && survivors.contains(p))).toList();
        var id=run.id;
        run.tail=run.tail.thenCompose(unused -> id.thenCompose(value -> storage.finishRun(value,result,end,records)));
        if (result == RunResult.COMPLETED && s.def().cooldownSeconds() > 0) {
            Instant until=end.plusSeconds(s.def().cooldownSeconds());
            for (UUID player : survivors) {
                manager.cacheCooldown(player,s.def().id(),until);
                run.tail=run.tail.thenCompose(unused -> storage.setCooldown(player,s.def().id(),until));
            }
        }
        observe(run.tail);
    }
    /** PluginDisableEvent precedes onDisable: finish sessions and drain chains before Storage.close. */
    @EventHandler(priority=EventPriority.HIGHEST)
    public void disable(org.bukkit.event.server.PluginDisableEvent event) {
        if (plugin == null || event.getPlugin() != plugin) return;
        manager.shutdown();
        for (var operation : List.copyOf(pending)) operation.handle((unused,error) -> null).join();
        pending.clear();
    }
    private void observe(CompletableFuture<?> operation) {
        pending.removeIf(CompletableFuture::isDone);
        pending.add(operation);
        operation.exceptionally(error -> { logger.log(java.util.logging.Level.WARNING,"Run persistence failed",error); return null; });
    }
}
