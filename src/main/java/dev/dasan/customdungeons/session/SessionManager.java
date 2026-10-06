package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.storage.Storage;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;

public final class SessionManager {
    private final CustomDungeonsPlugin plugin;
    private final DefinitionStore definitions;
    private final PluginConfig config;
    private final Storage storage;
    private final SessionTempBlocks.Journal blockJournal=new SessionTempBlocks.Journal();
    private record ChunkKey(UUID world,int x,int z) {
        static ChunkKey of(Chunk chunk) { return new ChunkKey(chunk.getWorld().getUID(),chunk.getX(),chunk.getZ()); }
    }
    private final Map<ChunkKey,Integer> chunkReferences=new HashMap<>();
    void retainChunk(Chunk chunk) {
        ChunkKey key=ChunkKey.of(chunk);
        if (!chunkReferences.containsKey(key)) chunk.addPluginChunkTicket(plugin);
        chunkReferences.merge(key,1,Integer::sum);
    }
    void releaseChunk(Chunk chunk) {
        ChunkKey key=ChunkKey.of(chunk);
        Integer references=chunkReferences.get(key);
        if (references==null) return;
        if (references==1) { chunkReferences.remove(key); chunk.removePluginChunkTicket(plugin); }
        else chunkReferences.put(key,references-1);
    }
    private final Map<String,DungeonSession> sessions=new HashMap<>();
    private final Map<UUID,DungeonSession> players=new HashMap<>();
    private final Map<UUID,Map<String,Instant>> cooldowns=new HashMap<>();
    private final Map<UUID,Long> connections=new HashMap<>();
    private final Map<UUID,DungeonSessionRuntime> runtimes=new HashMap<>();
    private final Set<UUID> authorizedTeleports=new HashSet<>();
    private final List<SessionLifecycleListener> listeners=new ArrayList<>();
    private boolean closed;
    private final List<SessionTempBlocks> retiredTemps=new ArrayList<>();
    private long connectionSerial;
    public SessionManager(CustomDungeonsPlugin plugin, DefinitionStore definitions, PluginConfig config, Storage storage) {
        this.plugin=plugin; this.definitions=definitions; this.config=config; this.storage=storage;
        if (Bukkit.getWorld(config.dungeonWorld()) == null && config.autoCreateWorld())
            new WorldCreator(config.dungeonWorld()).generator(new VoidGenerator()).createWorld();
        plugin.getServer().getPluginManager().registerEvents(new SessionListener(this),plugin);
        for (Player player : Bukkit.getOnlinePlayers()) connected(player);
    }
    private static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
    static boolean worldsReady(dev.dasan.customdungeons.model.DungeonDef def,java.util.function.Predicate<String> loaded) {
        if (def.lobby()==null || def.exit()==null || def.rooms().isEmpty()) return false;
        if (!loaded.test(def.lobby().world()) || !loaded.test(def.exit().world())) return false;
        for (var room : def.rooms()) {
            if (room.region()==null || room.checkpoint()==null || !loaded.test(room.region().world()) || !loaded.test(room.checkpoint().world())) return false;
            if (room.door()!=null && !loaded.test(room.door().world())) return false;
            for (var spawner : room.spawners()) if (spawner.location()==null || !loaded.test(spawner.location().world())) return false;
        }
        return true;
    }
    public JoinResult join(Player player, String dungeonId) {
        if (closed) return JoinResult.RESETTING;
        if (players.containsKey(player.getUniqueId())) return JoinResult.ALREADY_IN;
        var def=definitions.dungeons().get(dungeonId);
        if (def == null) return JoinResult.DISABLED;
        DungeonSession existing=sessions.get(dungeonId);
        SessionState state=existing == null ? SessionState.FREE : existing.state().state();
        JoinResult result=JoinRules.check(state,def.enabled() && worldsReady(def,name -> name!=null && Bukkit.getWorld(name)!=null),false,existing == null ? 0 : existing.survivors().size(),def.maxPlayers(),
            player.hasPermission("customdungeons.bypass.limit"),cooldowns.getOrDefault(player.getUniqueId(),Map.of()).get(dungeonId),Instant.now(),
            player.hasPermission("customdungeons.bypass.cooldown"),player.hasPermission("customdungeons.player.join") && (!def.requirePermission() || player.hasPermission("customdungeons.join."+dungeonId)));
        if (result != JoinResult.OK) return result;
        if (existing == null || state == SessionState.FREE) existing=create(def,false);
        players.put(player.getUniqueId(),existing);
        JoinResult joined=existing.join(player);
        runtime(existing).ticker.start(); return joined;
    }
    private DungeonSession create(dev.dasan.customdungeons.model.DungeonDef def, boolean test) {
        retiredTemps.removeIf(SessionTempBlocks::drained);
        var runtime=new DungeonSessionRuntime(plugin,this,definitions,config,storage);
        var session=new DungeonSession(def,test,runtime); session.maxAlive(config.limits().maxAliveMobsPerSession());
        session.addListener(new SessionLifecycleListener() {
            public void onStateChange(DungeonSession s,SessionState from,SessionState to) {
                if (to==SessionState.FREE) { retiredTemps.add(runtime.temp); runtimes.remove(s.id()); }
            }
        });
        listeners.forEach(session::addListener); runtime.attach(session);
        sessions.put(def.id(),session); runtimes.put(session.id(),runtime); return session;
    }
    public void leave(Player player) { sessionOf(player.getUniqueId()).ifPresent(s -> s.leave(player.getUniqueId())); }
    public Optional<DungeonSession> sessionOf(UUID player) { return Optional.ofNullable(players.get(player)); }
    public Optional<DungeonSession> session(String dungeonId) { return Optional.ofNullable(sessions.get(dungeonId)); }
    public void startTest(Player admin,String dungeonId) {
        if (closed || players.containsKey(admin.getUniqueId())) return;
        var def=definitions.dungeons().get(dungeonId);
        if (def == null || !worldsReady(def,name -> name!=null && Bukkit.getWorld(name)!=null) || session(dungeonId).filter(s -> s.state().state()!=SessionState.FREE).isPresent()) return;
        var session=create(def,true); players.put(admin.getUniqueId(),session); session.join(admin); session.forceStart(); runtime(session).ticker.start();
    }
    public void forceStart(String dungeonId) { session(dungeonId).ifPresent(DungeonSession::forceStart); }
    public void stop(String dungeonId) { session(dungeonId).ifPresent(s -> s.finish(false)); }
    public void reset(String dungeonId) { stop(dungeonId); }
    public void shutdown() { closed=true; for (DungeonSession session : List.copyOf(sessions.values())) session.finish(false); for (SessionTempBlocks temp : retiredTemps) temp.flushOnDisable(); retiredTemps.clear(); sessions.clear(); runtimes.clear(); players.clear(); cooldowns.clear(); }
    public void addListener(SessionLifecycleListener listener) { listeners.add(listener); sessions.values().forEach(s -> s.addListener(listener)); }
    public void cacheCooldown(UUID player,String dungeon,Instant until) { cooldowns.computeIfAbsent(player,k -> new HashMap<>()).put(dungeon,until); }
    Collection<DungeonSession> activeSessions() { return sessions.values().stream().filter(s -> s.state().state()!=SessionState.FREE).toList(); }
    Optional<DungeonSession> byId(String id) { return sessions.values().stream().filter(s -> s.id().toString().equals(id)).findFirst(); }
    SessionTempBlocks.Journal blockJournal() { return blockJournal; }
    DungeonSessionRuntime runtime(DungeonSession session) { return runtimes.get(session.id()); }
    void detach(UUID player,DungeonSession session) { players.remove(player,session); }
    void teleport(Player player,Location location) {
        authorizedTeleports.add(player.getUniqueId());
        try { player.teleport(location); } finally { authorizedTeleports.remove(player.getUniqueId()); }
    }
    boolean authorized(Player player) { return authorizedTeleports.contains(player.getUniqueId()); }
    void observe(CompletableFuture<?> operation) {
        operation.exceptionally(error -> { plugin.getLogger().warning("Session storage operation failed: "+error.getClass().getSimpleName()); return null; });
    }
    void main(Runnable task) { if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin,() -> { if (!closed) task.run(); }); }
    void connected(Player player) {
        UUID uuid=player.getUniqueId(); long generation=++connectionSerial; connections.put(uuid,generation);
        for (String dungeon : definitions.dungeons().keySet()) observe(storage.cooldownUntil(uuid,dungeon).thenAccept(until -> main(() -> {
            if (Objects.equals(connections.get(uuid),generation)) until.ifPresent(value -> cooldowns.computeIfAbsent(uuid,k -> new HashMap<>()).putIfAbsent(dungeon,value));
        })));
        observe(storage.takePendingExit(uuid).thenAccept(exit -> main(() -> {
            if (!Objects.equals(connections.get(uuid),generation) || !player.isOnline()) {
                exit.ifPresent(point -> observe(storage.addPendingExit(uuid,point))); return;
            }
            // Avoid a delayed exit teleport after a player has already entered another session.
            if (!players.containsKey(uuid)) exit.ifPresent(point -> teleport(player,DungeonSessionRuntime.location(point)));
        })));
    }
    void disconnected(Player player) { leave(player); connections.remove(player.getUniqueId()); cooldowns.remove(player.getUniqueId()); }
}
