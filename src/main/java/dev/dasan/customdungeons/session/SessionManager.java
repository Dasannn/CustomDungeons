package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.storage.*;
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
    final ScoreboardTemplates scoreboardTemplates;
    private final SessionTempBlocks.Journal blockJournal=new SessionTempBlocks.Journal();
    private record ReturnLoad(Optional<Point> exit,Optional<ReturnTarget> original,boolean queryFailed) {}
    private record RecoveryRead<T>(T value,boolean failed) {}
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
    private final Set<UUID> returning=new HashSet<>();
    private final Set<UUID> pendingDisconnects=new HashSet<>();
    private final Map<String,Set<UUID>> recoveredOccupants=new HashMap<>();
    private boolean closed;
    private final DisconnectService disconnects;
    private final CinematicRecovery cinematics;
    private final RespawnDestinations respawns;
    Point outsideSpawn() {return respawns.spawn();}
    CompletableFuture<Point> outsideSpawnAsync() {return respawns.spawnAsync(this::main,this::retainChunk,this::releaseChunk);}
    private record LoadingTeleport(Point target) {}
    private final Map<UUID,LoadingTeleport> loadingTeleports=new HashMap<>();
    void teleportPrepared(Player player,Point target,boolean spawn) {
        UUID uuid=player.getUniqueId();
        if(closed || !player.isOnline()){observe(storage.addPendingExit(uuid,target));return;}
        var pending=loadingTeleports.get(uuid);
        if(pending!=null && Objects.equals(pending.target(),target))return;
        var operation=new LoadingTeleport(target);loadingTeleports.put(uuid,operation);Long generation=connections.get(uuid);
        CompletableFuture<Point> destination=spawn?outsideSpawnAsync():CompletableFuture.completedFuture(target);
        observe(destination.thenCompose(point->{
            World world=Bukkit.getWorld(point.world());
            if(world==null)return CompletableFuture.failedFuture(new IllegalStateException("Missing teleport world"));
            return boundedRecovery(world.getChunkAtAsync(((int)Math.floor(point.x()))>>4,((int)Math.floor(point.z()))>>4))
                    .thenApply(chunk->new AbstractMap.SimpleImmutableEntry<>(point,chunk));
        }).whenComplete((loaded,error)->main(()->{
            if(loadingTeleports.get(uuid)!=operation || !Objects.equals(connections.get(uuid),generation))return;
            loadingTeleports.remove(uuid);
            if(!player.isOnline()){observe(storage.addPendingExit(uuid,target));return;}
            if(error!=null){recoveryFailed("session teleport",error);observe(storage.addPendingExit(uuid,target));return;}
            boolean retained=false;
            try {
                retainChunk(loaded.getValue());retained=true;
                if(!recoveryTeleport(player,DungeonSessionRuntime.location(loaded.getKey())))
                    observe(storage.addPendingExit(uuid,loaded.getKey()));
            } finally {if(retained)releaseChunk(loaded.getValue());}
        })));
    }
    CinematicRecovery cinematics() {return cinematics;}
    public void tickCinematicRecovery() {
        if(!closed && cinematics.hasPending())cinematics.tick(Bukkit.getCurrentTick());
    }
    public void recoveryTicker(dev.dasan.customdungeons.tool.PreviewRenderer renderer) {
        renderer.recoveryWork(cinematics::hasPending,this::tickCinematicRecovery);
        cinematics.onPendingChanged(()->{if(!closed)renderer.refreshRecoveries();});
    }
    private final List<SessionTempBlocks> retiredTemps=new ArrayList<>();
    private long connectionSerial;
    public SessionManager(CustomDungeonsPlugin plugin, DefinitionStore definitions, PluginConfig config, Storage storage) {
        this.plugin=plugin; this.definitions=definitions; this.config=config; this.storage=storage;
        java.util.function.Consumer<String> warning=key->plugin.getLogger().warning(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(key)));
        var loader=new ConfigLoader(path->plugin.getLogger().warning(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(
                        "config.invalid-value",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("path",path)))));
        respawns=new RespawnDestinations(loader.loadRespawnWorld(plugin.getConfig()),config.dungeonWorld(),this::insideDungeon,warning);
        disconnects=new DisconnectService(storage,this);
        cinematics=new CinematicRecovery(new CinematicJournal(plugin.getDataFolder().toPath(),java.util.concurrent.ForkJoinPool.commonPool()),
                (player,point)->recoveryTeleport(player,DungeonSessionRuntime.location(point)),this::main,
                error->plugin.getLogger().log(java.util.logging.Level.WARNING,"Cinematic recovery failed",error),this::retainChunk,this::releaseChunk,
                player->knownExit(player).orElse(null),this::outsideSpawn,this::outsideSpawnAsync);
        scoreboardTemplates=ScoreboardTemplates.load(plugin.getConfig(),path->plugin.getLogger().warning(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(
                        path.endsWith(".overflow")?"scoreboard.truncated":"scoreboard.invalid-config",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("path",path)))));
        if (Bukkit.getWorld(config.dungeonWorld()) == null && config.autoCreateWorld())
            new WorldCreator(config.dungeonWorld()).generator(new VoidGenerator()).createWorld();
        plugin.getServer().getPluginManager().registerEvents(new SessionListener(this),plugin);
        definitions.onReload(this::reloadCooldowns);
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
        if(def.entranceDoor()!=null && !loaded.test(def.entranceDoor().world()))return false;
        for(var plate:java.util.stream.Stream.concat(def.plates().stream(),def.exitPlates().stream()).toList())if(!loaded.test(plate.world()))return false;
        for (var room : def.rooms()) {
            if (room.region()==null || room.checkpoint()==null || !loaded.test(room.region().world()) || !loaded.test(room.checkpoint().world())) return false;
            if (room.door()!=null && !loaded.test(room.door().world())) return false;
            for (var spawner : room.spawners()) if (spawner.location()==null || !loaded.test(spawner.location().world())) return false;
        }
        return true;
    }
    public JoinResult join(Player player, String dungeonId) {
        if (closed) return JoinResult.RESETTING;
        if (definitions.isReloading()) return JoinResult.RELOADING;
        if (players.containsKey(player.getUniqueId())) return JoinResult.ALREADY_IN;
        if(recoveryPending(player.getUniqueId()) || sessions.values().stream().anyMatch(s->s.evacuating() && s.survivors().contains(player.getUniqueId())))return JoinResult.RESETTING;
        var def=definitions.dungeons().get(dungeonId);
        if (def == null) return JoinResult.DISABLED;
        if(vacating(dungeonId))return JoinResult.RESETTING;
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
        return create(def,test,definitions.spawnerPresets());
    }
    private DungeonSession create(dev.dasan.customdungeons.model.DungeonDef def, boolean test, Map<String,SpawnerPreset> presets) {
        def = SpawnerPresets.resolve(def,presets);
        retiredTemps.removeIf(SessionTempBlocks::drained);
        var runtime=new DungeonSessionRuntime(plugin,this,definitions,config,storage,scoreboardTemplates);
        var session=new DungeonSession(def,test,runtime); session.maxAlive(config.limits().maxAliveMobsPerSession());
        session.addListener(new SessionLifecycleListener() {
            public void onStateChange(DungeonSession s,SessionState from,SessionState to) {
                if (to==SessionState.FREE) { retiredTemps.add(runtime.temp); runtimes.remove(s.id()); }
            }
        });
        listeners.forEach(session::addListener); runtime.attach(session);
        sessions.put(def.id(),session); runtimes.put(session.id(),runtime); return session;
    }
    void recoverOccupants(String dungeon,Set<UUID> occupants) {recoveredOccupants.computeIfAbsent(dungeon,k->new HashSet<>()).addAll(occupants);}
    public boolean vacating(String dungeon) {
        if(session(dungeon).filter(DungeonSession::evacuating).isPresent())return true;
        var occupants=recoveredOccupants.get(dungeon);var def=definitions.dungeons().get(dungeon);
        if(occupants==null || def==null)return false;
        occupants.removeIf(uuid->{var p=Bukkit.getPlayer(uuid);if(p==null || !p.isOnline())return true;var at=p.getLocation();
            return !DungeonSessionRuntime.containsDungeon(def,at);});
        if(occupants.isEmpty())recoveredOccupants.remove(dungeon);
        return !occupants.isEmpty();
    }
    void exitPlate(Player player) {
        for(var s:activeSessions())if(s.exitPlate(player.getUniqueId()))break;
    }
    public void leave(Player player) { for(var runtime:runtimes.values())runtime.sidebar.remove(player.getUniqueId()); sessionOf(player.getUniqueId()).ifPresent(s -> s.leave(player.getUniqueId())); }
    /** Includes pending vanilla acknowledgement, not only an active return teleport. */
    public boolean recoveryPending(UUID player) {
        if(returning.contains(player) || pendingDisconnects.contains(player) || loadingTeleports.containsKey(player))return true;
        Player online=Bukkit.getPlayer(player);
        return online!=null && CinematicRecovery.pending(online);
    }
    public Optional<DungeonSession> sessionOf(UUID player) { return Optional.ofNullable(players.get(player)); }
    public Optional<DungeonSession> session(String dungeonId) { return Optional.ofNullable(sessions.get(dungeonId)); }
    public void startTest(Player admin,String dungeonId) {
        if (closed || definitions.isReloading() || players.containsKey(admin.getUniqueId()) || recoveryPending(admin.getUniqueId()) || vacating(dungeonId)) return;
        var def=definitions.dungeons().get(dungeonId);
        if (def == null || !worldsReady(def,name -> name!=null && Bukkit.getWorld(name)!=null) || session(dungeonId).filter(s -> s.state().state()!=SessionState.FREE).isPresent()) return;
        var presets=definitions.spawnerPresets();
        if (!new Validator().validate(def,definitions.mobs(),presets).isEmpty()) return;
        var session=create(def,true,presets); players.put(admin.getUniqueId(),session); session.join(admin); session.forceStart(); runtime(session).ticker.start();
    }
    public void forceStart(String dungeonId) { if(!vacating(dungeonId))session(dungeonId).ifPresent(DungeonSession::forceStart); }
    public void stop(String dungeonId) { session(dungeonId).ifPresent(s -> s.finish(false,true)); }
    public void reset(String dungeonId) { stop(dungeonId); }
    public void shutdown() { closed=true; for (DungeonSession session : List.copyOf(sessions.values())) session.finish(false,true); for(var runtime:List.copyOf(runtimes.values())){runtime.ticker.stop();runtime.sidebar.clear();runtime.temp.flushOnDisable();} for (SessionTempBlocks temp : retiredTemps) temp.flushOnDisable(); retiredTemps.clear(); sessions.clear(); runtimes.clear(); players.clear(); cooldowns.clear(); loadingTeleports.clear(); disconnects.close(); cinematics.close(); }
    public void addListener(SessionLifecycleListener listener) { listeners.add(listener); sessions.values().forEach(s -> s.addListener(listener)); }
    public void cacheCooldown(UUID player,String dungeon,Instant until) { cooldowns.computeIfAbsent(player,k -> new HashMap<>()).put(dungeon,until); }
    Collection<DungeonSession> activeSessions() { return sessions.values().stream().filter(s -> s.state().state()!=SessionState.FREE).toList(); }
    Optional<DungeonSession> byId(String id) { return sessions.values().stream().filter(s -> s.id().toString().equals(id)).findFirst(); }
    SessionTempBlocks.Journal blockJournal() { return blockJournal; }
    DungeonSessionRuntime runtime(DungeonSession session) { return runtimes.get(session.id()); }
    CompletableFuture<Void> persistJoin(DungeonSession s,UUID player,ReturnTarget target) {
        for(var listener:listeners)if(listener instanceof RunRecorder recorder)return recorder.persistJoin(s,player,target);
        var active=new ActiveSessionRecord(s.id(),s.def().id(),s.survivors(),s.def().exit());
        return ((ExitPersistence)storage).saveReturnTarget(player,target).thenCompose(unused->storage.markActive(active));
    }
    CompletableFuture<Void> persistDeparture(DungeonSession s) {
        return CompletableFuture.allOf(listeners.stream().filter(RunRecorder.class::isInstance)
                .map(l->((RunRecorder)l).playerDeparted(s)).toArray(CompletableFuture[]::new));
    }
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
    private void reloadCooldowns() {
        if (closed) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Long generation = connections.get(uuid);
            if (generation != null) loadCooldowns(uuid,generation);
        }
    }
    private void loadCooldowns(UUID uuid,long generation) {
        for (String dungeon : definitions.dungeons().keySet()) observe(storage.cooldownUntil(uuid,dungeon).thenAccept(until -> main(() -> {
            if (Objects.equals(connections.get(uuid),generation)) until.ifPresent(value -> cooldowns.computeIfAbsent(uuid,k -> new HashMap<>()).merge(dungeon,value,
                    (cached,loaded) -> cached.isAfter(loaded) ? cached : loaded));
        })));
    }
    /** Bound each database/chunk operation without completing the underlying operation's future. */
    long recoveryTimeoutMillis() { return 10_000; }
    private <T> CompletableFuture<T> boundedRecovery(CompletableFuture<T> operation) {
        return operation.thenApply(value->value).orTimeout(recoveryTimeoutMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);
    }
    private void recoveryFailed(String operation,Throwable error) {
        while(error.getCause()!=null && (error instanceof java.util.concurrent.CompletionException || error instanceof java.util.concurrent.ExecutionException))error=error.getCause();
        plugin.getLogger().warning("Return recovery "+operation+" failed; using fallback: "+error.getClass().getSimpleName());
    }
    private <T> CompletableFuture<RecoveryRead<T>> recoveryRead(java.util.function.Supplier<CompletableFuture<T>> read,T fallback,String operation) {
        try {
            return boundedRecovery(read.get()).handle((value,error)->{
                if(error!=null)recoveryFailed(operation,error);
                return new RecoveryRead<>(error==null?value:fallback,error!=null);
            });
        } catch(RuntimeException error) {
            recoveryFailed(operation,error);
            return CompletableFuture.completedFuture(new RecoveryRead<>(fallback,true));
        }
    }
    /** Re-enable recovery cannot acknowledge an in-memory vanilla marker. */
    void connected(Player player) {connected(player,false);}
    void joined(Player player) {connected(player,true);}
    private void connected(Player player,boolean realJoin) {
        UUID uuid=player.getUniqueId(); long generation=++connectionSerial; connections.put(uuid,generation);
        returning.add(uuid);pendingDisconnects.add(uuid);
        String confirmedGeneration=realJoin?disconnects.appliedGeneration(player):null;
        AmbienceEffects.recover(player);
        Runnable recover=()->disconnects.reconnect(player,confirmedGeneration,()->!closed && !Bukkit.isStopping() && player.isOnline()
                && Objects.equals(connections.get(uuid),generation),
                ()->{pendingDisconnects.remove(uuid);connectedReturn(player,generation);},()->{
                    if(Objects.equals(connections.get(uuid),generation)) {
                        pendingDisconnects.remove(uuid);returning.remove(uuid);
                    }
                });
        // Cinematic state is independent of definitions: recover it first, even when loading fails.
        // This also captures its vanilla acknowledgement before any asynchronous reads.
        Runnable afterCinematic=()->{
            try{loadCooldowns(uuid,generation);}catch(RuntimeException error){recoveryFailed("cooldown query",error);}
            if(!definitions.isReloading()){recover.run();return;}
            observe(boundedRecovery(definitions.reloadCompletion()).whenComplete((unused,error)->main(()->{
                if(!Objects.equals(connections.get(uuid),generation) || !player.isOnline())return;
                if(error==null)recover.run();
                else {disconnectFailed("definition load",error);connectedReturn(player,generation);}
            })));
        };
        cinematics.recover(player,realJoin,()->!closed && player.isOnline() && Objects.equals(connections.get(uuid),generation),
                afterCinematic,()->{pendingDisconnects.remove(uuid);returning.remove(uuid);});
    }

    private void connectedReturn(Player player,long generation) {
        UUID uuid=player.getUniqueId();
        var original=recoveryRead(()->storage instanceof ExitPersistence journal?journal.returnTarget(uuid)
                :CompletableFuture.completedFuture(Optional.<ReturnTarget>empty()),Optional.<ReturnTarget>empty(),"return-position query");
        var legacy=recoveryRead(()->storage.takePendingExit(uuid),Optional.<Point>empty(),"exit query");
        observe(legacy.thenCombine(original,(exit,target)->new ReturnLoad(exit.value(),target.value(),exit.failed() || target.failed()))
                .thenAccept(values->main(()->recoverReturn(player,generation,values))));
    }
    private Optional<Point> knownExit(Player player) {
        UUID uuid=player.getUniqueId();
        for(var entry:recoveredOccupants.entrySet())if(entry.getValue().contains(uuid)) {
            var def=definitions.dungeons().get(entry.getKey());if(def!=null)return Optional.ofNullable(def.exit());
        }
        for(var session:sessions.values())if(session.recoveryPlayers().contains(uuid))return Optional.ofNullable(session.def().exit());
        return definitions.dungeons().values().stream().filter(d->DungeonSessionRuntime.containsDungeon(d,player.getLocation()))
                .map(dev.dasan.customdungeons.model.DungeonDef::exit).filter(Objects::nonNull).findFirst();
    }
    private boolean connectedForReturn(Player player,long generation,Optional<Point> exit) {
        if(Objects.equals(connections.get(player.getUniqueId()),generation) && player.isOnline())return true;
        exit.ifPresent(point->observe(storage.addPendingExit(player.getUniqueId(),point)));
        return false;
    }
    private void recoverReturn(Player player,long generation,ReturnLoad values) {
        var exit=values.original().map(ReturnTarget::exit).or(values::exit);
        if(!connectedForReturn(player,generation,exit))return;
        UUID uuid=player.getUniqueId();
        if(players.containsKey(uuid)){returning.remove(uuid);return;}
        if(values.original().isEmpty() && exit.isEmpty() && !values.queryFailed()){returning.remove(uuid);return;}
        exit=exit.or(()->knownExit(player));
        boolean previous=!values.queryFailed() && values.original().filter(r->r.destination()==FinishDestination.PREVIOUS).isPresent();
        var point=previous?values.original().orElseThrow().previous():exit.orElse(null);
        prepareReturn(player,generation,values.original(),exit,point,previous?0:1);
    }
    /** Stages: previous position -> EXIT -> primary-world spawn. Every failure advances a stage. */
    private void prepareReturn(Player player,long generation,Optional<ReturnTarget> original,
            Optional<Point> exit,Point point,int stage) {
        if(!connectedForReturn(player,generation,exit))return;
        try {
            if(point==null || !Double.isFinite(point.x()) || !Double.isFinite(point.y()) || !Double.isFinite(point.z()))throw new IllegalStateException("Missing return point");
            var world=Bukkit.getWorld(point.world());if(world==null)throw new IllegalStateException("Missing return world");
            if(insideDungeon(DungeonSessionRuntime.location(point)))
                throw new IllegalStateException("Return point inside dungeon");
            int x=((int)Math.floor(point.x()))>>4,z=((int)Math.floor(point.z()))>>4;
            if(world.isChunkLoaded(x,z)){deliverReturn(player,generation,original,exit,point,stage);return;}
            observe(boundedRecovery(world.getChunkAtAsync(x,z)).handle((chunk,error)->{main(()->{
                if(!connectedForReturn(player,generation,exit))return;
                if(error!=null){retryReturn(player,generation,original,exit,stage,error);return;}
                boolean retained=false;
                try {
                    retainChunk(chunk);retained=true;
                    deliverReturn(player,generation,original,exit,point,stage);
                } catch(RuntimeException failure) {retryReturn(player,generation,original,exit,stage,failure);}
                finally {if(retained)releaseChunk(chunk);}
            });return null;}));
        } catch(RuntimeException failure) {retryReturn(player,generation,original,exit,stage,failure);}
    }
    private void retryReturn(Player player,long generation,Optional<ReturnTarget> original,
            Optional<Point> exit,int stage,Throwable failure) {
        recoveryFailed(stage==0?"previous-position load":stage==1?"exit load":"spawn return",failure);
        if(!connectedForReturn(player,generation,exit))return;
        if(stage==0) {prepareReturn(player,generation,original,exit,exit.orElse(null),1);return;}
        if(stage==1) {
            try {
                observe(outsideSpawnAsync().whenComplete((point,error)->main(()->{
                    if(error!=null)retryReturn(player,generation,original,exit,2,error);
                    else prepareReturn(player,generation,original,exit,point,2);
                })));
            } catch(RuntimeException error) {retryReturn(player,generation,original,exit,2,error);}
            return;
        }
        // Even an unavailable spawn or a third-party cancellation cannot leave the join guard stuck.
        returning.remove(player.getUniqueId());
        exit.ifPresent(point->observe(storage.addPendingExit(player.getUniqueId(),point)));
    }
    private void deliverReturn(Player player,long generation,Optional<ReturnTarget> original,
            Optional<Point> exit,Point point,int stage) {
        if(!connectedForReturn(player,generation,exit))return;
        UUID uuid=player.getUniqueId();
        if(players.containsKey(uuid)){returning.remove(uuid);return;}
        if(stage==0 && !DungeonSessionRuntime.safePrevious(point)) {
            prepareReturn(player,generation,original,exit,exit.orElse(null),1);return;
        }
        authorizedTeleports.add(uuid);boolean delivered;
        try{delivered=player.teleport(DungeonSessionRuntime.location(point));}finally{authorizedTeleports.remove(uuid);}
        if(!delivered){retryReturn(player,generation,original,exit,stage,new IllegalStateException("Teleport cancelled"));return;}
        returning.remove(uuid);
        if(storage instanceof ExitPersistence journal)original.ifPresent(r->{
            try{observe(journal.clearReturnTarget(uuid,r.sessionId()));}
            catch(RuntimeException error){recoveryFailed("return-position cleanup",error);}
        });
    }
    void worldChanged(Player player) {
        UUID uuid=player.getUniqueId();
        for(var runtime:runtimes.values()) {
            DungeonSession session=runtime.session();
            runtime.ambience.remove(player);
            runtime.sidebar.worldChanged(player,()->players.get(uuid)==session
                    || session.evacuating() && session.survivors().contains(uuid) && runtime.inside(session,player));
        }
    }
    boolean insideDungeon(Location at) {
        return definitions.dungeons().values().stream().anyMatch(d->DungeonSessionRuntime.containsDungeon(d,at))
                || sessions.values().stream().anyMatch(s->DungeonSessionRuntime.containsDungeon(s.def(),at));
    }
    Point outsideExit() {
        return java.util.stream.Stream.concat(definitions.dungeons().values().stream(),sessions.values().stream().map(DungeonSession::def))
                .map(DungeonDef::exit).filter(Objects::nonNull).filter(p->Bukkit.getWorld(p.world())!=null
                        && Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
                        && !insideDungeon(DungeonSessionRuntime.location(p))).findFirst().orElse(null);
    }
    boolean recoveryTeleport(Player player,Location at) {
        authorizedTeleports.add(player.getUniqueId());
        try{return player.teleport(at);}finally{authorizedTeleports.remove(player.getUniqueId());}
    }
    void disconnectFailed(String operation,Throwable error) {
        plugin.getLogger().warning("Disconnect recovery "+operation+" failed: "+error.getClass().getSimpleName());
    }
    boolean disconnectDeath(org.bukkit.event.entity.PlayerDeathEvent event) {return disconnects.death(event);}
    boolean disconnectRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {return disconnects.respawn(event);}
    void disconnectDrop(org.bukkit.event.entity.ItemSpawnEvent event) {disconnects.drop(event);}
    void disconnected(Player player) {disconnected(player,org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED);}
    void disconnected(Player player,org.bukkit.event.player.PlayerQuitEvent.QuitReason reason) {
        UUID uuid=player.getUniqueId();
        // Paper sets stopping before firing quit events; onDisable alone would be too late.
        if(!closed && !Bukkit.isStopping()) {
            var owner=sessionOf(uuid);
            owner.filter(s->s.state().state()==SessionState.LOBBY || s.state().state()==SessionState.RUNNING)
                    .ifPresent(s->{
                        if(s.introActive()) {
                            // No T44 penalty, including a player who already skipped while the group is still in intro.
                            observe(storage.addPendingExit(uuid,s.def().exit()));
                        } else disconnects.record(s,player,reason);
                    });
            owner.ifPresent(s->s.disconnect(uuid));
            for(var session:List.copyOf(sessions.values()))if(session.recoveryPlayers().contains(uuid)
                    || session.survivors().contains(uuid))session.disconnect(uuid);
            players.remove(uuid);
            for(var runtime:runtimes.values())runtime.sidebar.remove(uuid);
        }
        connections.remove(uuid);loadingTeleports.remove(uuid);disconnects.disconnected(uuid);cooldowns.remove(uuid);returning.remove(uuid);pendingDisconnects.remove(uuid);
    }
}
