package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.storage.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

/** Bukkit effects owned by one session. No database waits or repeating tasks. */
final class DungeonSessionRuntime implements SessionServices {
    private final CustomDungeonsPlugin plugin;
    private final SessionManager manager;
    private final DefinitionStore definitions;
    private final PluginConfig config;
    private final Storage storage;
    private final MobFactory factory;
    final AbilityEngine abilities;
    final BossController bosses;
    final SessionTempBlocks temp;
    private final SessionChunks chunks;
    private final SessionBossBar bar;
    final SessionSidebar sidebar;
    final SessionAmbience ambience;
    final SessionCinematic cinematic;
    private List<Point> cinematicRoute;
    private final ScoreboardTemplates sidebarTemplates;
    private final Map<UUID,List<Stolen>> stolenByMob = new HashMap<>();
    private final Map<UUID,Point> spawnTargets=new HashMap<>();
    private final Map<UUID,Stolen> stolenDrops = new HashMap<>();
    private final Map<UUID,Stolen> containerTransfers = new HashMap<>();
    private record Stolen(UUID owner, ItemStack item) {}
    private DungeonSession session;
    DoorService doors;
    KeyService keys;
    SessionTicker ticker;
    DungeonSessionRuntime(CustomDungeonsPlugin plugin, SessionManager manager, DefinitionStore definitions, PluginConfig config, Storage storage) {
        this(plugin,manager,definitions,config,storage,ScoreboardTemplates.load(plugin.getConfig(),path->{}));
    }
    DungeonSessionRuntime(CustomDungeonsPlugin plugin, SessionManager manager, DefinitionStore definitions, PluginConfig config, Storage storage, ScoreboardTemplates sidebarTemplates) {
        this.plugin=plugin; this.manager=manager; this.definitions=definitions; this.config=config; this.storage=storage;
        this.sidebarTemplates=sidebarTemplates;
        chunks=new SessionChunks(manager);
        cinematic=new SessionCinematic(manager.cinematics(),(player,point)->manager.recoveryTeleport(player,location(point)),
                player->{
                    var title=plugin.messages().get("cinematic.title",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("dungeon",dev.dasan.customdungeons.text.Text.parse(session.def().displayName())));
                    var subtitle=plugin.messages().get("cinematic.subtitle");
                    if(!net.kyori.adventure.text.Component.empty().equals(title))player.showTitle(net.kyori.adventure.title.Title.title(title,subtitle));
                    player.sendActionBar(plugin.messages().get("cinematic.skip-hint"));
                },error->plugin.getLogger().log(java.util.logging.Level.WARNING,"Cinematic restoration failed",error));
        factory = new MobFactory(config); abilities = new AbilityEngine(plugin.abilityRegistry(),config);
        ambience=new SessionAmbience(plugin.messages(),AmbienceSettings.load(plugin.getConfig(),path->plugin.getLogger().warning(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(
                        "config.invalid-value",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("path",path))))),config,definitions.mobs());
        bosses = new BossController(factory,definitions.mobs(),ambience::pauseMusic);
        temp = new SessionTempBlocks(storage,error -> plugin.getLogger().warning("Session block persistence failed: "+error.getClass().getSimpleName()),manager.blockJournal());
        bar = new SessionBossBar(plugin.messages());
        sidebar = new SessionSidebar(()->Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard(),
                sidebarTemplates.refreshTicks(),player->plugin.getLogger().warning(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get(
                        "scoreboard.conflict",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player",player.getName())))),
                error->plugin.getLogger().log(java.util.logging.Level.WARNING,
                        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plugin.messages().get("scoreboard.failed")),error));
    }
    DungeonSession session() { return session; }
    void attach(DungeonSession session) {
        this.session=session; doors=new DoorService(session,temp,config); keys=new KeyService(session,doors);
        doors.ambience((region,room)->ambience.door(session,region,room));
        ticker = new SessionTicker(plugin,session,bosses);
    }
    @Override public void invulnerable(Player player,boolean value) {
        player.setInvulnerable(value);
        String key=value ? "livetest.invulnerable-on" : "livetest.invulnerable-off";
        plugin.messages().send(player,key);
        player.sendActionBar(plugin.messages().get(key));
    }
    static Messages messages() { return org.bukkit.plugin.java.JavaPlugin.getPlugin(CustomDungeonsPlugin.class).messages(); }
    static Location location(Point p) {
        return new Location(Objects.requireNonNull(Bukkit.getWorld(p.world()),"Missing world: "+p.world()),p.x(),p.y(),p.z(),p.yaw(),p.pitch());
    }
    static boolean contains(Region region, Location at) {
        return region != null && at.getWorld() != null && region.contains(at.getWorld().getName(),at.getBlockX(),at.getBlockY(),at.getBlockZ());
    }
    static boolean containsDungeon(DungeonDef def,Location at) {
        return at!=null && (contains(def.area(),at) || contains(def.entranceDoor(),at)
                || def.rooms().stream().anyMatch(r->contains(r.region(),at) || contains(r.door(),at)));
    }
    public void teleport(Player player, Point point) {
        if(!player.isOnline()) {manager.observe(storage.addPendingExit(player.getUniqueId(),point));return;}
        boolean spawn=Objects.equals(spawnTargets.remove(player.getUniqueId()),point);
        manager.teleportPrepared(player,point,spawn);
    }
    private SidebarData.Inputs sidebarInputs(Player player) {
        return SidebarData.inputs(session,player,plugin.messages(),occupiedPlates(),keys.heldInCurrentRoom());
    }
    private ScoreboardTemplates.Frame sidebarFrame(SidebarData.Inputs inputs) {
        var data=SidebarData.format(inputs,plugin.messages());
        return sidebarTemplates.render(data.state(),data.values(),data.conditions());
    }
    private int occupiedPlates() {
        if(session.def().startMode()!=StartMode.PLATES || session.state().state()!=SessionState.LOBBY)return 0;
        var positions=new ArrayList<Point>();
        for(Player player:session.players()) {
            if(!player.isOnline() || player.isDead() || player.getGameMode()==GameMode.SPECTATOR || !player.isOnGround())continue;
            var at=player.getLocation();
            positions.add(new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),0,0));
        }
        var valid=session.def().plates().stream().filter(plate->{
            var world=Bukkit.getWorld(plate.world());int x=(int)Math.floor(plate.x()),y=(int)Math.floor(plate.y()),z=(int)Math.floor(plate.z());
            return world!=null && world.isChunkLoaded(x>>4,z>>4) && world.getBlockAt(x,y,z).getType()==Material.STONE_PRESSURE_PLATE;
        }).toList();
        return PlateOccupancy.occupiedCount(valid,positions);
    }
    public void joined(DungeonSession s,Player player,Runnable ready) {
        if(sidebarTemplates.enabled())sidebar.join(player,s.scheduler().currentTick(),()->sidebarInputs(player),this::sidebarFrame);
        if(!(storage instanceof ExitPersistence)){ready.run();return;}
        var target=new ReturnTarget(s.id(),s.previous(player.getUniqueId()),s.def().exit(),s.def().finishDestination());
        // Persist both journals before lobby teleport; no main-thread database wait.
        manager.observe(manager.persistJoin(s,player.getUniqueId(),target)
                .whenComplete((unused,error)->manager.main(()->{
                    if(error==null){if(s.def().finishDestination()==FinishDestination.PREVIOUS)chunks.remember(target.previous());ready.run();}
                    else {plugin.messages().send(player,"session.join-save-failed");s.finish(false,true);}
                })));
    }
    public Point destination(DungeonSession s,Player player) {
        Point target=new ReturnTarget(s.id(),s.previous(player.getUniqueId()),s.def().exit(),s.def().finishDestination())
                .resolve(point->safePrevious(point) && !containsDungeon(s.def(),location(point)) && !manager.insideDungeon(location(point)));
        if(RespawnDestinations.valid(target) && Bukkit.getWorld(target.world())!=null
                && !containsDungeon(s.def(),location(target)) && !manager.insideDungeon(location(target))
                )return target;
        Point spawn=manager.outsideSpawn();spawnTargets.put(player.getUniqueId(),spawn);return spawn;
    }
    static boolean safePrevious(Point p) {
        if(p==null || !Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()))return false;
        World world=Bukkit.getWorld(p.world());
        if(world==null || !world.isChunkLoaded(((int)Math.floor(p.x()))>>4,((int)Math.floor(p.z()))>>4) || p.y()<=world.getMinHeight() || p.y()+1>=world.getMaxHeight())return false;
        Location at=new Location(world,p.x(),p.y(),p.z());
        if(!world.getWorldBorder().isInside(at))return false;
        var feet=at.getBlock();var head=feet.getRelative(org.bukkit.block.BlockFace.UP);var support=feet.getRelative(org.bukkit.block.BlockFace.DOWN);
        return feet.isPassable() && head.isPassable() && support.getType().isSolid()
                && !unsafe(feet.getType()) && !unsafe(head.getType()) && !unsafe(support.getType());
    }
    private static boolean unsafe(Material type) {
        return switch(type){case LAVA,WATER,FIRE,SOUL_FIRE,MAGMA_BLOCK,CACTUS,CAMPFIRE,SOUL_CAMPFIRE,POWDER_SNOW,SWEET_BERRY_BUSH->true;default->false;};
    }
    public boolean inside(DungeonSession s,Player p) {return p.isOnline() && SessionServices.super.inside(s,p);}
    public boolean onExitPlate(DungeonSession s,Player p) {
        if(!p.isOnline() || p.isDead() || p.getGameMode()==GameMode.SPECTATOR || !p.isOnGround())return false;
        var at=p.getLocation();var point=new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),0,0);
        for(var plate:s.def().exitPlates()) {
            World world=Bukkit.getWorld(plate.world());int x=(int)Math.floor(plate.x()),y=(int)Math.floor(plate.y()),z=(int)Math.floor(plate.z());
            if(world!=null && world.isChunkLoaded(x>>4,z>>4) && world.getBlockAt(x,y,z).getType()==Material.POLISHED_BLACKSTONE_PRESSURE_PLATE
                    && PlateOccupancy.allOccupied(List.of(plate),List.of(point)))return true;
        }
        return false;
    }
    public void reentered(DungeonSession s,Player p) {manager.observe(manager.persistDeparture(s));}
    public void departed(DungeonSession s,Player p) {
        if(p.isOnline() && storage instanceof ExitPersistence journal)manager.observe(manager.persistDeparture(s).thenCompose(unused->journal.clearReturnTarget(p.getUniqueId(),s.id())));
    }
    public void exiting(DungeonSession s,int seconds) {bar.exiting(s,seconds);}
    public void released(DungeonSession s) {cinematic.clear();ambience.clear();ticker.stop();bar.clear();sidebar.clear();chunks.close();}
    public void scoreboardRemoved(Player player) {sidebar.remove(player.getUniqueId());}

    public boolean prepareStart(DungeonSession session) {
        if(session.def().introCinematic() && cinematicRoute==null) {
            World world=Objects.requireNonNull(Bukkit.getWorld(session.def().lobby().world()));
            var border=world.getWorldBorder();var center=border.getCenter();double radius=border.getSize()/2-.5;
            var limits=new CinematicRoute.Limits(world.getMinHeight(),world.getMaxHeight(),
                    Math.max(-29_999_983,center.getX()-radius),Math.min(29_999_983,center.getX()+radius),
                    Math.max(-29_999_983,center.getZ()-radius),Math.min(29_999_983,center.getZ()+radius));
            cinematicRoute=CinematicRoute.calculate(session.def(),limits);
            // Request only the bounded set of chunks actually crossed by the camera.
            for(Point frame:cinematicRoute)chunks.remember(frame);
        }
        return chunks.prepare(session.def());
    }
    public void recoveryTick(long tick) {manager.tickCinematicRecovery();}
    public boolean introTick(DungeonSession session) {
        if(cinematic.preparing() && !chunks.prepare(session.def()))return true;
        return cinematic.tick(session);
    }
    public void introRestore(DungeonSession session,Player player) {cinematic.restore(player);}
    public boolean canSpawnAt(Location at) { return chunks.ready(at); }
    public boolean platesReady(DungeonSession session) {
        var positions=new ArrayList<Point>();
        for(Player player:session.players()) {
            if(!player.isOnline() || player.isDead() || player.getGameMode()==GameMode.SPECTATOR || !player.isOnGround())continue;
            var at=player.getLocation();
            positions.add(new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),0,0));
        }
        for(Point plate:session.def().plates()) {
            var world=Bukkit.getWorld(plate.world());int x=(int)Math.floor(plate.x()),y=(int)Math.floor(plate.y()),z=(int)Math.floor(plate.z());
            if(world==null || !world.isChunkLoaded(x>>4,z>>4) || world.getBlockAt(x,y,z).getType()!=Material.STONE_PRESSURE_PLATE)return false;
        }
        return PlateOccupancy.allOccupied(session.def().plates(),positions);
    }
    public void lobbyCountdown(DungeonSession session,int seconds,boolean cancelled) {
        if(seconds<0 && !cancelled)return;
        String key=cancelled?(session.def().startMode()==StartMode.PLATES?"session.start-countdown-cancelled":"session.start-countdown-minimum"):"session.start-countdown";
        var text=plugin.messages().get(key,net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("seconds",Integer.toString(Math.max(0,seconds))));
        for(Player player:session.players()) {
            player.sendActionBar(text);
            player.showTitle(net.kyori.adventure.title.Title.title(text,net.kyori.adventure.text.Component.empty(),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,java.time.Duration.ofSeconds(1),java.time.Duration.ZERO)));
        }
    }
    public void start(DungeonSession session) {
        if(session.introActive()) {
            // Also retain the exact pre-camera positions, which need not lie along the route.
            for(Player player:session.players()) {
                var at=player.getLocation();chunks.remember(new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch()));
            }
            cinematic.start(session,Objects.requireNonNull(cinematicRoute));
        }
        doors.closeAll();openEntrance();
    }
    private void openEntrance() {
        doors.openEntrance().thenAccept(opened->{
            if(!opened) {
                if(session.state().state()==SessionState.RUNNING)session.scheduler().runLater(20,this::openEntrance);
                return;
            }
            for(Player player:session.players()) {
                if(!session.introActive())player.showTitle(net.kyori.adventure.title.Title.title(plugin.messages().get("session.started-title"),plugin.messages().get("session.started-subtitle")));
                player.playSound(player.getLocation(),"minecraft:block.iron_door.open",1,1);
            }
        });
    }
    public ActiveMob spawn(DungeonSession session, String template, Location at) {
        MobTemplate mob = definitions.mobs().get(template);
        if (mob == null) { session.scheduler().runLater(1,() -> session.finish(false)); return null; }
        ActiveMob active = factory.spawn(mob,at,session,Scaling.healthMultiplier(session.initialPlayers(),session.def().minPlayers(),session.def().scaling().extraHealthPerPlayer()));
        // Fire after tracking, allowing spawn abilities to create correctly attributed minions.
        session.scheduler().runLater(1,() -> {
            if (session.mob(active.entity().getUniqueId()) != active) return;
            abilities.fire(Trigger.ON_SPAWN,active,null,session.scheduler().currentTick());
            if (mob.boss()) { bosses.barFor(active); bosses.startMusic(active); }
        });
        return active;
    }
    public Location spawnLocation(DungeonSession session, String spawner) {
        SpawnerDef def = session.def().rooms().get(session.roomIndex()).spawners().stream().filter(s -> s.id().equals(spawner)).findFirst().orElseThrow();
        Location at=location(def.location());
        double angle=Math.random()*Math.PI*2, radius=Math.sqrt(Math.random())*def.radius();
        Location candidate=at.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
        return contains(session.currentRoomRegion(),candidate) ? candidate : at;
    }
    public void tick(DungeonSession session) {
        long tick=session.scheduler().currentTick();
        sidebar.refresh(tick,this::sidebarInputs,this::sidebarFrame);
        if(session.evacuating()) {temp.tick(tick);return;}
        if(session.introActive()) {chunks.tick();temp.tick(tick);bar.update(session);return;}
        ambience.tick(session,bosses.musicPlaying());
        abilities.tick(session.mobs(),tick);
        chunks.tick(); temp.tick(tick); keys.tick();
        if (tick%20 == 0) for (ActiveMob mob : session.mobs()) if (!contains(session.currentRoomRegion(),mob.entity().getLocation())) {
            Location at=spawnLocation(session,session.origin(mob.entity().getUniqueId()));
            if (chunks.ready(at)) mob.entity().teleport(at);
        }
        bar.update(session);
    }
    public void roomCleared(DungeonSession session) {
        ambience.cleared(session);
        if(session.roomIndex()==session.def().rooms().size()-1)return;
        keys.roomCleared();
        switch (session.def().rooms().get(session.roomIndex()).openingMode()) {
            case KEY -> keys.create();
            case AUTOMATIC -> openAutomaticDoor(session.roomIndex());
            case EXTERNAL_KEY -> { /* Puzzle controls delivery; clearing never creates a key. */ }
        }
    }
    private void openAutomaticDoor(int room) {
        doors.open(room).thenAccept(success -> {
            if (!success && session.state().state()==SessionState.RUNNING && session.roomIndex()==room)
                session.scheduler().runLater(20,() -> openAutomaticDoor(room));
        });
    }
    public void removed(DungeonSession session, ActiveMob mob, org.bukkit.event.Event event) {
        keys.carrierDied(mob);
        abilities.fire(Trigger.ON_DEATH,mob,event,session.scheduler().currentTick());
        bosses.cleanup(mob);
        List<Stolen> items = stolenByMob.remove(mob.entity().getUniqueId());
        if (items != null) for (Stolen stolen : items) {
            Item item=mob.entity().getWorld().dropItem(mob.entity().getLocation(),stolen.item());
            stolenDrops.put(item.getUniqueId(),stolen);
        }
        mob.stolenItems().clear();
    }
    boolean tracksDrop(UUID item) {
        return stolenDrops.containsKey(item) || containerTransfers.containsKey(item);
    }
    void pickedUp(Item item, int remaining) {
        Stolen stolen=stolenDrops.remove(item.getUniqueId());
        if (stolen == null) stolen=containerTransfers.remove(item.getUniqueId());
        if (stolen != null && remaining>0) { ItemStack rest=stolen.item().clone(); rest.setAmount(remaining); stolenDrops.put(item.getUniqueId(),new Stolen(stolen.owner(),rest)); }
    }
    void containerPickup(Item item) {
        UUID uuid=item.getUniqueId(); Stolen stolen=stolenDrops.remove(uuid);
        if (stolen == null) return;
        containerTransfers.put(uuid,stolen);
        session.scheduler().runLater(1,() -> settleContainer(item));
    }
    private void settleContainer(Item item) {
        Stolen stolen=containerTransfers.remove(item.getUniqueId());
        if (stolen != null && item.isValid()) stolenDrops.put(item.getUniqueId(),new Stolen(stolen.owner(),item.getItemStack().clone()));
    }
    void removedDrop(Item item) {
        Stolen stolen=stolenDrops.remove(item.getUniqueId());
        if (stolen != null) returnItem(stolen);
    }
    public void stolen(UUID owner, ItemStack item, ActiveMob thief) {
        ItemStack copy=item.clone(); thief.stolenItems().add(copy);
        stolenByMob.computeIfAbsent(thief.entity().getUniqueId(),k -> new ArrayList<>()).add(new Stolen(owner,copy));
    }
    private void returnItem(Stolen stolen) {
        Player player=Bukkit.getPlayer(stolen.owner());
        List<ItemStack> remaining=player != null && player.isOnline()
            ? List.copyOf(player.getInventory().addItem(stolen.item()).values()) : List.of(stolen.item());
        if (!remaining.isEmpty()) manager.observe(storage.addClaims(stolen.owner(),remaining));
    }
    public void disconnected(DungeonSession session, Player player) {
        ambience.remove(player);
        sidebar.remove(player.getUniqueId());keys.leave(player);
        manager.detach(player.getUniqueId(),session);
        manager.observe(manager.persistDeparture(session));
    }
    public void leave(DungeonSession session, Player player) {
        ambience.remove(player);
        sidebar.remove(player.getUniqueId());
        keys.leave(player); manager.observe(storage.addPendingExit(player.getUniqueId(),destination(session,player)));
        manager.detach(player.getUniqueId(),session);
        manager.observe(manager.persistDeparture(session));
    }
    public void observerFailed(RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.WARNING,"Session observer failed",error); }
    public TempBlocks tempBlocks() { return temp; }
    public void finish(DungeonSession session) {
        cinematic.clear();cinematicRoute=null;
        ambience.clear();
        for (var items : stolenByMob.values()) for (Stolen item : items) returnItem(item);
        stolenByMob.clear();
        for (var entry : List.copyOf(containerTransfers.entrySet())) {
            Entity entity=Bukkit.getEntity(entry.getKey());
            if (entity instanceof Item item && item.isValid()) settleContainer(item);
        }
        containerTransfers.clear();
        for (var entry : List.copyOf(stolenDrops.entrySet())) {
            stolenDrops.remove(entry.getKey());
            Entity entity=Bukkit.getEntity(entry.getKey());
            if (entity != null) entity.remove();
            returnItem(entry.getValue());
        }
        stolenDrops.clear();
        keys.clear();
        for (ActiveMob mob : session.mobs()) { bosses.cleanup(mob); mob.entity().remove(); }
        // Includes session-owned projectiles or other entities created by abilities.
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities())
            if (session.id().toString().equals(entity.getPersistentDataContainer().get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING))) entity.remove();
        temp.restoreAll(); bar.clear();
        for (Player player : session.players()) manager.detach(player.getUniqueId(),session);
    }
}
