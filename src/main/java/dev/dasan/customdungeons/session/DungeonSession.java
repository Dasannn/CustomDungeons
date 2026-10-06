package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.mob.Scaling;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.storage.RunResult;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Main-thread session ownership. All effects use SessionServices; no independent tasks. */
public final class DungeonSession implements SessionContext {
    private final UUID id = UUID.randomUUID();
    private final DungeonDef def;
    private final boolean testMode;
    private final SessionServices services;
    private final SessionStateMachine state = new SessionStateMachine();
    private final Map<UUID,Player> participants = new LinkedHashMap<>();
    private final Map<UUID,Integer> lives = new HashMap<>();
    private final Map<UUID,ActiveMob> mobs = new LinkedHashMap<>();
    private final Map<UUID,String> origins = new HashMap<>();
    private final Map<String,Deque<SpawnOrder>> pending = new LinkedHashMap<>();
    private final List<SessionLifecycleListener> listeners = new ArrayList<>();
    private final NavigableMap<Long,List<Runnable>> tasks = new TreeMap<>();
    private final Map<UUID,Boolean> invulnerability = new HashMap<>();
    private final Random random = new Random();
    private final TickScheduler scheduler = new TickScheduler() {
        public long currentTick() { return tick; }
        public void runLater(int ticks, Runnable task) {
            tasks.computeIfAbsent(tick + Math.max(1,ticks), k -> new ArrayList<>()).add(task);
        }
    };
    private long tick, lobbyEnd, startedAt;
    private int roomIndex, initialPlayers, maxAlive = Integer.MAX_VALUE;
    private RoomProgress progress;
    private boolean roomStarted, awaitingEntry, ending, startRequested;
    private final Map<String,Integer> waveIndices = new HashMap<>(), emitted = new HashMap<>();

    DungeonSession(DungeonDef def, boolean testMode, SessionServices services) {
        this.def = def; this.testMode = testMode; this.services = services;
    }
    public DungeonDef def() { return def; }
    public SessionStateMachine state() { return state; }
    public boolean testMode() { return testMode; }
    public UUID id() { return id; }
    public boolean isLiveTest() { return false; }
    public int livesLeft(UUID player) { return lives.getOrDefault(player,0); }
    public Set<UUID> survivors() { return Set.copyOf(participants.keySet()); }
    public int roomIndex() { return roomIndex; }
    int initialPlayers() { return initialPlayers; }
    String origin(UUID mob) { return origins.get(mob); }
    public int waveNumber() { return waveIndices.values().stream().mapToInt(i -> i+1).max().orElse(1); }
    public boolean roomStarted() { return roomStarted; }
    public Collection<Player> players() { return List.copyOf(participants.values()); }
    public Collection<ActiveMob> mobs() { return List.copyOf(mobs.values()); }
    public Region currentRoomRegion() { return def.rooms().isEmpty() ? null : def.rooms().get(roomIndex).region(); }
    public TempBlocks tempBlocks() { return services.tempBlocks(); }
    public TickScheduler scheduler() { return scheduler; }
    public Point checkpoint() { return def.rooms().get(roomIndex).checkpoint(); }
    void maxAlive(int value) { maxAlive = Math.max(1,value); }
    void addListener(SessionLifecycleListener listener) { listeners.add(listener); }

    JoinResult join(Player player) {
        UUID uuid = player.getUniqueId();
        if (participants.containsKey(uuid)) return JoinResult.ALREADY_IN;
        if (state.state() != SessionState.FREE && state.state() != SessionState.LOBBY) return JoinResult.RUNNING;
        participants.put(uuid,player); lives.put(uuid,def.lives());
        if (state.state() == SessionState.FREE) {
            lobbyEnd = tick + (long)def.lobbyCountdownSeconds()*20;
            change(state::openLobby);
        }
        services.teleport(player,def.lobby());
        if (def.maxPlayers() > 0 && participants.size() == def.maxPlayers())
            for (var listener : List.copyOf(listeners)) notifyListener(() -> listener.onLobbyFull(this));
        return JoinResult.OK;
    }
    void forceStart() {
        if (state.state() != SessionState.LOBBY || participants.isEmpty()) return;
        startRequested = true;
        if (!services.prepareStart(this)) return;
        startRequested = false;
        initialPlayers = participants.size(); startedAt = tick; roomIndex = 0;
        services.start(this);
        change(state::start);
        for (Player player : players()) services.teleport(player,checkpoint());
        startRoom();
    }
    void enterRoom(int index) {
        if (state.state() == SessionState.RUNNING && index == roomIndex && awaitingEntry && !roomStarted) startRoom();
    }
    private void startRoom() {
        progress = new RoomProgress(def.rooms().get(roomIndex),
            n -> Scaling.count(n,initialPlayers,def.minPlayers(),def.scaling().extraMobsPerPlayer()), random);
        progress.start(tick); roomStarted = true; awaitingEntry = false; waveIndices.clear(); emitted.clear();
    }
    void tick() {
        tick++;
        if (state.state() == SessionState.LOBBY && (startRequested || tick >= lobbyEnd)) {
            if (startRequested || participants.size() >= def.minPlayers() || testMode) forceStart(); else finish(false);
            return;
        }
        if (state.state() == SessionState.LOBBY) { services.tick(this); return; }
        if (state.state() != SessionState.RUNNING) return;
        // Removal callbacks are normally delivered by Paper. This also catches silent invalidation.
        for (ActiveMob mob : mobs()) if (!mob.entity().isValid() || mob.entity().isDead()) mobRemoved(mob.entity().getUniqueId(),null);
        if (roomStarted) {
            Map<String,Integer> counts = new HashMap<>();
            origins.values().forEach(s -> counts.merge(s,1,Integer::sum));
            pending.forEach((s,q) -> counts.merge(s,q.size(),Integer::sum));
            progress.tick(tick,counts).forEach((s,orders) -> {
                var spawner = def.rooms().get(roomIndex).spawners().stream().filter(sp -> sp.id().equals(s)).findFirst().orElseThrow();
                int index=waveIndices.getOrDefault(s,0);
                int total=spawner.waves().get(index).entries().stream().mapToInt(e -> Scaling.count(e.count(),initialPlayers,def.minPlayers(),def.scaling().extraMobsPerPlayer())).sum();
                if (emitted.getOrDefault(s,0)>=total && index+1<spawner.waves().size()) { index++; emitted.put(s,0); }
                waveIndices.put(s,index); emitted.merge(s,orders.size(),Integer::sum);
                pending.computeIfAbsent(s,k -> new ArrayDeque<>()).addAll(orders);
            });
            for (var entry : pending.entrySet()) {
                while (!entry.getValue().isEmpty() && mobs.size() < maxAlive) {
                    Location at=services.spawnLocation(this,entry.getKey());
                    if (!services.canSpawnAt(at)) break; // Keep the order until its chunk is ready.
                    var order = entry.getValue().removeFirst();
                    ActiveMob mob = services.spawn(this,order.templateId(),at);
                    if (mob != null) track(mob,entry.getKey());
                }
            }
            if (progress.cleared() && mobs.isEmpty() && pending.values().stream().allMatch(Deque::isEmpty)) {
                roomStarted = false;
                if (roomIndex == def.rooms().size()-1) { finish(true); return; }
                services.roomCleared(this);
            }
        }
        services.tick(this);
        while (!tasks.isEmpty() && tasks.firstKey() <= tick && state.state() == SessionState.RUNNING) {
            for (Runnable task : tasks.pollFirstEntry().getValue()) {
                if (state.state() == SessionState.RUNNING) task.run();
            }
        }
        if (def.timeLimitSeconds() > 0 && tick-startedAt >= (long)def.timeLimitSeconds()*20) finish(false);
    }
    void track(ActiveMob mob, String spawner) {
        mobs.put(mob.entity().getUniqueId(),mob); origins.put(mob.entity().getUniqueId(),spawner);
    }
    public ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner) {
        if (ending || state.state() != SessionState.RUNNING || mobs.size() >= maxAlive) return null;
        if (!origins.containsKey(owner.entity().getUniqueId())) return null;
        var mob = services.spawn(this,templateId,at);
        if (mob != null) track(mob,origins.get(owner.entity().getUniqueId()));
        return mob;
    }
    void mobRemoved(UUID uuid, org.bukkit.event.Event event) {
        ActiveMob mob = mobs.remove(uuid);
        if (mob == null || ending) return;
        // Keep origin through ON_DEATH so its summons remain part of the same spawner.
        services.removed(this,mob,event);
        origins.remove(uuid);
    }
    ActiveMob mob(UUID uuid) { return mobs.get(uuid); }
    void openDoor() {
        if (state.state() != SessionState.RUNNING || roomStarted || awaitingEntry || roomIndex >= def.rooms().size()-1) return;
        roomIndex++; awaitingEntry = true; waveIndices.clear(); emitted.clear();
    }
    void playerDied(UUID uuid) {
        if (!participants.containsKey(uuid)) return;
        int remaining = Math.max(0,livesLeft(uuid)-1); lives.put(uuid,remaining);
        if (remaining == 0) {
            Player player = participants.remove(uuid);
            services.leave(this,player);
            restoreInvulnerable(player);
            if (participants.isEmpty()) finish(false);
        }
    }
    void leave(UUID uuid) {
        Player player = participants.remove(uuid);
        if (player == null) return;
        services.leave(this,player); restoreInvulnerable(player);
        services.teleport(player,def.exit());
        if (participants.isEmpty()) finish(false);
    }
    public void skipWave() {
        if (!controlsAllowed() || state.state() != SessionState.RUNNING || !roomStarted) return;
        for (ActiveMob mob : mobs()) mob.entity().setHealth(0);
    }
    private boolean controlsAllowed() { return testMode || players().stream().anyMatch(p -> p.hasPermission("customdungeons.admin.debug")); }
    public void setInvulnerable(UUID uuid, boolean value) {
        Player player = participants.get(uuid);
        if (player == null || !(testMode || player.hasPermission("customdungeons.admin.debug"))) return;
        invulnerability.putIfAbsent(uuid,player.isInvulnerable());
        services.invulnerable(player,value);
    }
    private void restoreInvulnerable(Player player) {
        Boolean old = invulnerability.remove(player.getUniqueId());
        if (old != null) services.invulnerable(player,old);
    }
    void finish(boolean completed) {
        if (ending || state.state() == SessionState.FREE) return;
        ending = true;
        change(completed ? state::complete : state::fail);
        Set<UUID> alive = survivors();
        for (var listener : List.copyOf(listeners)) notifyListener(() -> listener.onFinished(this,completed ? RunResult.COMPLETED : RunResult.FAILED,alive));
        change(state::beginReset);
        services.finish(this);
        for (Player player : players()) { restoreInvulnerable(player); services.teleport(player,def.exit()); }
        participants.clear(); mobs.clear(); origins.clear(); pending.clear(); tasks.clear();
        roomStarted = false; awaitingEntry = false; progress = null;
        change(state::finishReset);
    }
    private void change(Runnable transition) {
        SessionState from = state.state(); transition.run();
        for (var listener : List.copyOf(listeners)) notifyListener(() -> listener.onStateChange(this,from,state.state()));
    }
    private void notifyListener(Runnable callback) {
        try { callback.run(); } catch (RuntimeException error) { services.observerFailed(error); }
    }
    public void onItemStolen(UUID owner, ItemStack item, ActiveMob thief) { services.stolen(owner,item,thief); }
}
