package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.mob.Scaling;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.storage.RunResult;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Main-thread session ownership. All effects use SessionServices; no independent tasks. */
public final class DungeonSession implements MobHost {
    private final UUID id = UUID.randomUUID();
    private final DungeonDef def;
    private final boolean testMode;
    private final SessionServices services;
    private final SessionStateMachine state = new SessionStateMachine();
    private final Map<UUID,Player> participants = new LinkedHashMap<>();
    private List<Player> participantSnapshot;
    private final Map<UUID,Point> previous = new HashMap<>();
    private final Map<UUID,Player> former = new LinkedHashMap<>();
    private final Map<UUID,Player> occupants = new LinkedHashMap<>();
    private final Set<UUID> joining = new HashSet<>();
    private final Map<UUID,Integer> sidebarKills = new HashMap<>();
    private long finishedAt;
    private boolean forcedExit;
    private final Map<UUID,Integer> lives = new HashMap<>();
    private final Map<UUID,ActiveMob> mobs = new LinkedHashMap<>();
    private final Map<UUID,SpawnOrigin> origins = new HashMap<>();
    private final Map<SpawnOrigin,Deque<SpawnOrder>> pending = new LinkedHashMap<>();
    private final Map<String,RoomProgress> zones = new LinkedHashMap<>();
    private final Map<SpawnOrigin,MobHost> hosts = new HashMap<>();
    private final List<SessionLifecycleListener> listeners = new ArrayList<>();
    private final NavigableMap<Long,List<Runnable>> tasks = new TreeMap<>();
    private final Map<UUID,Boolean> invulnerability = new HashMap<>();
    private final Set<UUID> protectedPlayers = new HashSet<>();
    private final Random random = new Random();
    private final TickScheduler scheduler = new TickScheduler() {
        public long currentTick() { return tick; }
        public void runLater(int ticks, Runnable task) {
            tasks.computeIfAbsent(tick + Math.max(1,ticks), k -> new ArrayList<>()).add(task);
        }
    };
    private long tick, startedAt;
    private final LobbyCountdown lobbyCountdown;
    private int lastCountdown=-1;
    private int roomIndex, initialPlayers, maxAlive = Integer.MAX_VALUE;
    private final dev.dasan.customdungeons.mob.MobCapacity capacity=new dev.dasan.customdungeons.mob.MobCapacity();
    private RoomProgress progress;
    private boolean roomStarted, awaitingEntry, ending, startRequested, intro;
    private final Map<String,Integer> waveIndices = new HashMap<>(), emitted = new HashMap<>();

    DungeonSession(DungeonDef def, boolean testMode, SessionServices services) {
        this.def = def; this.testMode = testMode; this.services = services;
        lobbyCountdown=new LobbyCountdown(def.startMode()==StartMode.PLATES?def.plateCountdownSeconds():def.lobbyCountdownSeconds());
    }
    Point returnPoint(Player player) { return services.destination(this,player); }
    public Point previous(UUID player) { return previous.getOrDefault(player,def.exit()); }
    Set<UUID> recoveryPlayers() { return Set.copyOf(occupants.keySet()); }
    public boolean evacuating() { return ending && state.state()!=SessionState.FREE; }
    public DungeonDef def() { return def; }
    public SessionStateMachine state() { return state; }
    public boolean testMode() { return testMode; }
    public UUID id() { return id; }
    public int livesLeft(UUID player) { return lives.getOrDefault(player,0); }
    public Set<UUID> survivors() { return Set.copyOf(participants.keySet()); }
    public int roomIndex() { return roomIndex; }
    int initialPlayers() { return initialPlayers; }
    int sidebarKills(UUID player) { return sidebarKills.getOrDefault(player,0); }
    int sidebarKillsTotal() { return sidebarKills.values().stream().mapToInt(Integer::intValue).sum(); }
    int countdownSeconds() { return lobbyCountdown.secondsLeft(tick); }
    boolean awaitingRoomEntry() { return awaitingEntry; }
    long elapsedTicks() { return initialPlayers==0 || intro?0:Math.max(0,(ending?finishedAt:tick)-startedAt); }
    int finishCountdownSeconds() {
        return ending && !forcedExit && def.finishMode()==FinishMode.DELAYED
                ? (int)Math.max(0,def.exitGraceSeconds()-(tick-finishedAt)/20) : -1;
    }
    SpawnOrigin origin(UUID mob) { return origins.get(mob); }
    public int waveNumber() {
        if(waveIndices.isEmpty())return 1;
        int maximum=Integer.MIN_VALUE;
        for(int index:waveIndices.values())maximum=Math.max(maximum,index+1);
        return maximum;
    }
    boolean introActive() { return intro; }
    public boolean roomStarted() { return roomStarted; }
    public Collection<Player> players() {
        if(participantSnapshot==null)participantSnapshot=List.copyOf(participants.values());
        return participantSnapshot;
    }
    private Player removeParticipant(UUID uuid) {
        var removed=participants.remove(uuid);if(removed!=null)participantSnapshot=null;return removed;
    }
    @Override public Collection<Player> audience(Location at) { return players(); }
    @Override public dev.dasan.customdungeons.mob.MobArea area() {
        Region region = currentRoomRegion();
        return region == null ? null : new dev.dasan.customdungeons.mob.MobArea(region.world(), new org.bukkit.util.BoundingBox(
                region.min().x(), region.min().y(), region.min().z(),
                (double) region.max().x() + 1, (double) region.max().y() + 1, (double) region.max().z() + 1));
    }
    public Collection<ActiveMob> mobs() { return List.copyOf(mobs.values()); }
    Collection<ActiveMob> roomMobs() {
        return originMobs(roomOrigin(""));
    }
    SpawnOrigin roomOrigin(String spawner) { return new SpawnOrigin(SpawnOrigin.Kind.ROOM,def.rooms().get(roomIndex).id(),spawner); }
    Region region(SpawnOrigin origin) {
        return origin.kind()==SpawnOrigin.Kind.ROOM
                ? def.rooms().stream().filter(r->r.id().equals(origin.id())).findFirst().orElseThrow().region()
                : def.optionalZones().stream().filter(z->z.id().equals(origin.id())).findFirst().orElseThrow().region();
    }
    List<SpawnerDef> spawners(SpawnOrigin origin) {
        return origin.kind()==SpawnOrigin.Kind.ROOM
                ? def.rooms().stream().filter(r->r.id().equals(origin.id())).findFirst().orElseThrow().spawners()
                : def.optionalZones().stream().filter(z->z.id().equals(origin.id())).findFirst().orElseThrow().spawners();
    }
    MobHost host(SpawnOrigin origin) { return hosts.computeIfAbsent(origin,key->new OriginMobHost(this,key)); }
    Collection<ActiveMob> originMobs(SpawnOrigin origin) {
        return mobs.values().stream().filter(m->origin.sameRegion(origins.get(m.entity().getUniqueId()))).toList();
    }
    public Region currentRoomRegion() { return def.rooms().isEmpty() ? null : def.rooms().get(roomIndex).region(); }
    public TempBlocks tempBlocks() { return services.tempBlocks(); }
    public TickScheduler scheduler() { return scheduler; }
    public Point checkpoint() { return def.rooms().get(roomIndex).checkpoint(); }
    void maxAlive(int value) { maxAlive = Math.max(1,value); }
    void addListener(SessionLifecycleListener listener) { listeners.add(listener); }

    JoinResult join(Player player) {
        UUID uuid = player.getUniqueId();
        if (participants.containsKey(uuid)) return JoinResult.ALREADY_IN;
        if (state.state() != SessionState.FREE && state.state() != SessionState.LOBBY) return evacuating()?JoinResult.RESETTING:JoinResult.RUNNING;
        var at=player.getLocation();
        previous.put(uuid,at==null || at.getWorld()==null?def.exit():new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch()));
        former.put(uuid,player);occupants.put(uuid,player);joining.add(uuid);
        participants.put(uuid,player);participantSnapshot=null; lives.put(uuid,def.lives());
        if (state.state() == SessionState.FREE) {
            change(state::openLobby);
        }
        services.joined(this,player,()->{
            joining.remove(uuid);
            if(!ending && participants.get(uuid)==player)services.teleport(player,def.lobby());
        });
        if (def.maxPlayers() > 0 && participants.size() == def.maxPlayers())
            for (var listener : List.copyOf(listeners)) notifyListener(() -> listener.onLobbyFull(this));
        return JoinResult.OK;
    }
    void forceStart() {
        if (state.state() != SessionState.LOBBY || participants.isEmpty()) return;
        startRequested = true;
        tryStart();
    }
    private void tryStart() {
        if (!joining.isEmpty() || !services.prepareStart(this)) return;
        startRequested = false;
        initialPlayers = participants.size(); startedAt = tick; roomIndex = 0;
        intro=def.introCinematic();
        change(state::start);
        services.start(this);
        if(!intro && def.teleportOnStart()) for (Player player : players()) services.teleport(player,checkpoint());
        awaitingEntry=true;
    }
    void enterRoom(int index) {
        if (state.state() == SessionState.RUNNING && index == roomIndex && !intro && awaitingEntry && !roomStarted) startRoom();
    }
    private void startRoom() {
        progress = startProgress(def.rooms().get(roomIndex).spawners()); roomStarted = true; awaitingEntry = false; waveIndices.clear(); emitted.clear();
        services.roomEntered(this);
    }
    private RoomProgress startProgress(List<SpawnerDef> spawners) {
        var result=new RoomProgress(spawners,n->Scaling.count(n,initialPlayers,def.minPlayers(),def.scaling().extraMobsPerPlayer()),random);
        result.start(tick);return result;
    }
    private void activateZones() {
        for(var zone:def.optionalZones()) if(!zones.containsKey(zone.id()) && players().stream().anyMatch(p->eligible(p)
                && DungeonSessionRuntime.contains(zone.region(),p.getLocation()))) zones.put(zone.id(),startProgress(zone.spawners()));
    }
    private boolean eligible(Player player) {
        return player.isOnline() && !player.isDead() && livesLeft(player.getUniqueId())>0 && player.getGameMode()!=org.bukkit.GameMode.SPECTATOR;
    }
    void tick() {
        tick++;
        services.recoveryTick(tick);
        if(evacuating()) { tickExit(); return; }
        if (state.state() == SessionState.LOBBY) {
            boolean ready=def.startMode()==StartMode.PLATES?services.platesReady(this):participants.size()>=def.minPlayers() || testMode;
            boolean elapsed=lobbyCountdown.tick(tick,ready);
            int seconds=lobbyCountdown.secondsLeft(tick);
            if(seconds!=lastCountdown) {
                services.lobbyCountdown(this,seconds,seconds<0 && lastCountdown>=0);lastCountdown=seconds;
            }
            if(startRequested || elapsed)tryStart();
            if(state.state()==SessionState.LOBBY)services.tick(this);
            return;
        }
        if (state.state() != SessionState.RUNNING) return;
        if(intro) {
            if(services.introTick(this)) {services.tick(this);runTasks();return;}
            intro=false;startedAt=tick;
        }
        if (def.timeLimitSeconds()>0 && tick-startedAt >= (long)def.timeLimitSeconds()*20) { finish(false,true); return; }
        if(tick%10==0)activateZones();
        if(awaitingEntry && tick%10==0) {
            for(Player player:players()) {
                Location at=player.getLocation();
                if(eligible(player) && at!=null && DungeonSessionRuntime.contains(currentRoomRegion(),at)) {enterRoom(roomIndex);break;}
            }
        }
        // Removal callbacks are normally delivered by Paper. This also catches silent invalidation.
        for (ActiveMob mob : mobs()) if (!mob.entity().isValid() || mob.entity().isDead()) mobRemoved(mob.entity().getUniqueId(),null);
        Map<SpawnOrigin,Integer> counts = new HashMap<>();
        origins.values().forEach(o -> counts.merge(o,1,Integer::sum));
        pending.forEach((o,q) -> counts.merge(o,q.size(),Integer::sum));
        if(roomStarted) {
            var origin=roomOrigin("");
            progress.tick(tick,counts(origin,counts)).forEach((spawner,orders)-> {
                var definition=spawners(origin).stream().filter(sp->sp.id().equals(spawner)).findFirst().orElseThrow();
                int index=waveIndices.getOrDefault(spawner,0);
                int total=definition.waves().get(index).entries().stream().mapToInt(e -> Scaling.count(e.count(),initialPlayers,def.minPlayers(),def.scaling().extraMobsPerPlayer())).sum();
                if(emitted.getOrDefault(spawner,0)>=total && index+1<definition.waves().size()) {index++;emitted.put(spawner,0);}
                waveIndices.put(spawner,index);emitted.merge(spawner,orders.size(),Integer::sum);
                queue(roomOrigin(spawner),orders);
            });
        }
        for(var zone:zones.entrySet()) {
            var origin=new SpawnOrigin(SpawnOrigin.Kind.ZONE,zone.getKey(),"");
            zone.getValue().tick(tick,counts(origin,counts)).forEach((spawner,orders)->
                    queue(new SpawnOrigin(SpawnOrigin.Kind.ZONE,zone.getKey(),spawner),orders));
        }
        for(var entry:pending.entrySet()) {
            while(!entry.getValue().isEmpty()) {
                Location at=services.spawnLocation(this,entry.getKey());
                if(!services.canSpawnAt(at))break;
                try(var slot=reserveMobSlot()) {
                    if(slot==null)break;
                    var order=entry.getValue().removeFirst();
                    ActiveMob mob=services.spawn(this,entry.getKey(),order.templateId(),at);
                    if(mob!=null)track(mob,entry.getKey());
                }
            }
        }
        if(roomStarted && progress.cleared() && roomMobs().isEmpty() && pending.entrySet().stream()
                .filter(e->roomOrigin("").sameRegion(e.getKey())).allMatch(e->e.getValue().isEmpty())) {
            roomStarted=false;
            boolean finalRoom=roomIndex==def.rooms().size()-1;
            services.roomCleared(this);
            if(finalRoom) {finish(true);return;}
        }
        services.tick(this);
        runTasks();
    }
    private Map<String,Integer> counts(SpawnOrigin origin,Map<SpawnOrigin,Integer> all) {
        var result=new HashMap<String,Integer>();
        all.forEach((key,value)->{if(origin.sameRegion(key))result.put(key.spawner(),value);});
        return result;
    }
    private void queue(SpawnOrigin origin,List<SpawnOrder> orders) { pending.computeIfAbsent(origin,k->new ArrayDeque<>()).addAll(orders); }
    private void runTasks() {
        while (!tasks.isEmpty() && tasks.firstKey() <= tick && state.state() == SessionState.RUNNING) {
            for (Runnable task : tasks.pollFirstEntry().getValue()) {
                if (state.state() == SessionState.RUNNING) task.run();
            }
        }

    }
    void track(ActiveMob mob, String spawner) { track(mob,roomOrigin(spawner)); }
    void track(ActiveMob mob, SpawnOrigin origin) {
        if(dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(mob.entity()))return;
        mobs.put(mob.entity().getUniqueId(),mob); origins.put(mob.entity().getUniqueId(),origin);
    }
    @Override public dev.dasan.customdungeons.mob.MobCapacity.Reservation reserveMobSlot() {
        return ending || state.state()!=SessionState.RUNNING?null:capacity.reserve(mobs.size(),maxAlive);
    }
    public ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner) {
        return spawnMinion(at,owner,point->services.spawn(this,origins.get(owner.entity().getUniqueId()),templateId,point));
    }
    @Override public ActiveMob spawnMinionTemplate(MobTemplate template,Location at,ActiveMob owner) {
        return spawnMinion(at,owner,point->services.spawn(this,origins.get(owner.entity().getUniqueId()),template,point));
    }
    private ActiveMob spawnMinion(Location at,ActiveMob owner,java.util.function.Function<Location,ActiveMob> create) {
        if (ending || state.state() != SessionState.RUNNING) return null;
        if (!origins.containsKey(owner.entity().getUniqueId())) return null;
        var origin=origins.get(owner.entity().getUniqueId());
        if(!DungeonSessionRuntime.contains(region(origin),at))at=services.spawnLocation(this,origin);
        if(!services.canSpawnAt(at))return null;
        try(var slot=reserveMobSlot()) {
            if(slot==null)return null;
            var mob=create.apply(at);
            if(mob!=null)track(mob,origin);
            return mob;
        }
    }
    void mobRemoved(UUID uuid, org.bukkit.event.Event event) {
        ActiveMob mob = mobs.remove(uuid);
        if (mob == null || ending) return;
        if(event instanceof org.bukkit.event.entity.EntityDeathEvent death) {
            Player killer=death.getEntity().getKiller();
            if(killer!=null && participants.containsKey(killer.getUniqueId()))sidebarKills.merge(killer.getUniqueId(),1,Integer::sum);
        }
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
            Player player = removeParticipant(uuid);
            services.leave(this,player);
            restoreInvulnerable(player);
            if (participants.isEmpty()) finish(false);
        }
    }
    /** Quit removes every occupancy reference immediately and never teleports an offline player. */
    void disconnect(UUID uuid) {
        Player player=removeParticipant(uuid);
        if(player==null) player=former.get(uuid);
        former.remove(uuid);occupants.remove(uuid);joining.remove(uuid);previous.remove(uuid);
        if(player!=null) {restoreIntro(player);services.disconnected(this,player);restoreInvulnerable(player);}
        if(ending) tickExit();
        else if(participants.isEmpty()) finish(false);
    }
    void leave(UUID uuid) {
        Player player = removeParticipant(uuid);
        if (player == null) return;
        restoreIntro(player);services.leave(this,player); restoreInvulnerable(player);
        services.teleport(player,services.destination(this,player));
        if (participants.isEmpty()) finish(false);
    }
    public void skipWave() {
        if (!controlsAllowed() || state.state() != SessionState.RUNNING || !roomStarted) return;
        for (ActiveMob mob : roomMobs()) dev.dasan.customdungeons.mob.MobHealth.terminate(mob.entity(),true);
    }
    private boolean controlsAllowed() { return testMode || players().stream().anyMatch(p -> p.hasPermission("customdungeons.admin.debug")); }
    public void setInvulnerable(UUID uuid, boolean value) {
        if(intro)return; // The cinematic owns this temporary state until restoration.
        Player player = participants.get(uuid);
        if (player == null || !(testMode || player.hasPermission("customdungeons.admin.debug"))) return;
        invulnerability.putIfAbsent(uuid,player.isInvulnerable());
        if(value) protectedPlayers.add(uuid); else protectedPlayers.remove(uuid);
        services.invulnerable(player,value);
    }
    public boolean isTestInvulnerable(UUID player) { return protectedPlayers.contains(player); }
    private void restoreInvulnerable(Player player) {
        protectedPlayers.remove(player.getUniqueId());
        Boolean old = invulnerability.remove(player.getUniqueId());
        if (old != null) services.invulnerable(player,old);
    }
    private void restoreIntro(Player player) {
        try {services.introRestore(this,player);} catch(RuntimeException error){services.observerFailed(error);}
    }
    void finish(boolean completed) { finish(completed,false); }
    void finish(boolean completed,boolean force) {
        if (state.state()==SessionState.FREE) return;
        if(ending) { if(force) { forcedExit=true; tickExit(); } return; }
        for(Player player:players())restoreIntro(player);
        if(intro)startedAt=tick;
        intro=false;ending=true; forcedExit=force; finishedAt=tick;
        change(completed ? state::complete : state::fail);
        Set<UUID> alive=survivors();
        for(var listener:List.copyOf(listeners)) notifyListener(()->listener.onFinished(this,completed?RunResult.COMPLETED:RunResult.FAILED,alive));
        services.finish(this);
        for(Player player:players())restoreInvulnerable(player);
        mobs.clear();origins.clear();pending.clear();zones.clear();hosts.clear();tasks.clear();joining.clear();
        roomStarted=false;awaitingEntry=false;progress=null;
        tickExit();
    }
    /** Disable persists player returns first, then enters reset even while the server still sees occupants. */
    void beginShutdownReset() {
        if(!ending && state.state()!=SessionState.FREE)finish(false,true);
        if(state.state()==SessionState.COMPLETED || state.state()==SessionState.FAILED)change(state::beginReset);
    }
    private void tickExit() {
        services.tick(this);
        boolean due=ExitPolicy.due(def.finishMode().name(),def.exitGraceSeconds(),tick-finishedAt,forcedExit);
        if(due) for(Player player:List.copyOf(participants.values())) exit(player);
        if(tick%10==0 || tick==finishedAt) {
            for(Player player:former.values())if(services.inside(this,player) && !occupants.containsKey(player.getUniqueId())) {
                occupants.put(player.getUniqueId(),player);services.reentered(this,player);
            }
            for(Player player:List.copyOf(occupants.values())) {
                UUID uuid=player.getUniqueId();
                if(state.state()==SessionState.COMPLETED && participants.containsKey(uuid) && services.onExitPlate(this,player)) exit(player);
                if(!services.inside(this,player)) {
                    occupants.remove(uuid);services.departed(this,player);
                    removeParticipant(uuid);services.scoreboardRemoved(player);
                } else if(due && !participants.containsKey(uuid)) {
                    // Eliminated players and failed/cancelled teleports must not release the area lock.
                    services.teleport(player,services.destination(this,player));
                    if(!services.inside(this,player)){occupants.remove(uuid);services.departed(this,player);}
                }
            }
        }
        if(def.finishMode()==FinishMode.DELAYED && tick%20==0 && !due)
            services.exiting(this,Math.max(0,def.exitGraceSeconds()-(int)((tick-finishedAt)/20)));
        if(participants.isEmpty() && occupants.isEmpty()) {
            if(state.state()!=SessionState.RESETTING)change(state::beginReset);
            if(services.reset(this)) {
                services.released(this);previous.clear();former.clear();
                change(state::finishReset);
            }
        }
    }
    boolean exitPlate(UUID uuid) {
        Player player=participants.get(uuid);
        if(state.state()!=SessionState.COMPLETED || player==null || !services.onExitPlate(this,player))return false;
        exit(player);tickExit();return true;
    }
    private void exit(Player player) {
        services.teleport(player,services.destination(this,player));
        removeParticipant(player.getUniqueId());
        services.scoreboardRemoved(player);
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
