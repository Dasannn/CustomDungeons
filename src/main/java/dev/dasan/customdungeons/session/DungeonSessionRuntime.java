package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.storage.Storage;
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
    private final Map<UUID,List<Stolen>> stolenByMob = new HashMap<>();
    private final Map<UUID,Stolen> stolenDrops = new HashMap<>();
    private final Map<UUID,Stolen> containerTransfers = new HashMap<>();
    private record Stolen(UUID owner, ItemStack item) {}
    private DungeonSession session;
    DoorService doors;
    KeyService keys;
    SessionTicker ticker;
    DungeonSessionRuntime(CustomDungeonsPlugin plugin, SessionManager manager, DefinitionStore definitions, PluginConfig config, Storage storage) {
        this.plugin=plugin; this.manager=manager; this.definitions=definitions; this.config=config; this.storage=storage;
        chunks=new SessionChunks(manager);
        factory = new MobFactory(config); abilities = new AbilityEngine(plugin.abilityRegistry(),config);
        bosses = new BossController(factory,definitions.mobs());
        temp = new SessionTempBlocks(storage,error -> plugin.getLogger().warning("Session block persistence failed: "+error.getClass().getSimpleName()),manager.blockJournal());
        bar = new SessionBossBar(plugin.messages());
    }
    void attach(DungeonSession session) {
        this.session=session; doors=new DoorService(session,temp,config); keys=new KeyService(session,doors);
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
    public void teleport(Player player, Point point) { manager.teleport(player,location(point)); }
    public boolean prepareStart(DungeonSession session) { return chunks.prepare(session.def()); }
    public boolean canSpawnAt(Location at) { return chunks.ready(at); }
    public void start(DungeonSession session) { doors.closeAll(); }
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
        abilities.tick(session.mobs(),tick);
        chunks.tick(); temp.tick(tick); keys.tick();
        if (tick%20 == 0) for (ActiveMob mob : session.mobs()) if (!contains(session.currentRoomRegion(),mob.entity().getLocation())) {
            Location at=spawnLocation(session,session.origin(mob.entity().getUniqueId()));
            if (chunks.ready(at)) mob.entity().teleport(at);
        }
        bar.update(session);
    }
    public void roomCleared(DungeonSession session) {
        if (session.def().rooms().get(session.roomIndex()).unlock() == UnlockMode.KEY) keys.create();
        else doors.open(session.roomIndex());
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
    public void leave(DungeonSession session, Player player) {
        keys.leave(player); manager.observe(storage.addPendingExit(player.getUniqueId(),session.def().exit()));
        manager.detach(player.getUniqueId(),session);
    }
    public void observerFailed(RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.WARNING,"Session observer failed",error); }
    public TempBlocks tempBlocks() { return temp; }
    public void finish(DungeonSession session) {
        ticker.stop();
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
        chunks.close();
        for (Player player : session.players()) manager.detach(player.getUniqueId(),session);
    }
}
