package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** One isolated SessionContext and one ticker per administrator. All Bukkit work is main-thread. */
public final class LiveTestService implements SessionContext, AutoCloseable {
    private static final NamespacedKey LIVE = new NamespacedKey("customdungeons", "live_test");
    private static Manager manager;
    private final Manager services;
    private final UUID id = UUID.randomUUID();
    private final Player admin;
    private final Location origin;
    private final boolean originalInvulnerable;
    private final Map<UUID, ActiveMob> mobs = new LinkedHashMap<>();
    private final Set<Entity> entities = new HashSet<>();
    private final Map<ActiveMob, List<ItemStack>> stolen = new IdentityHashMap<>();
    private final Clock clock = new Clock();
    private final Blocks blocks = new Blocks();
    private final AbilityEngine engine;
    private final BossController bosses;
    private final Map<String,MobTemplate> templates;
    private BukkitTask ticker;
    private boolean closed;

    LiveTestService(Manager services, Player admin) {
        this.services = services; this.admin = admin; origin = admin.getLocation().clone();
        originalInvulnerable = admin.isInvulnerable();
        engine = new AbilityEngine(services.plugin.abilityRegistry(), services.config);
        templates = new HashMap<>(services.store.mobs());
        bosses = new BossController(services.factory, templates);
    }
    public static void register(CustomDungeonsPlugin plugin) {
        manager = new Manager(plugin);
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
    }
    public static boolean start(Player player, MobTemplate template) {
        Manager s = Objects.requireNonNull(manager, "Live-test services not registered");
        if (!player.hasPermission("customdungeons.admin.test")) { s.plugin.messages().send(player,"livetest.no-permission"); return false; }
        if (s.tests.containsKey(player.getUniqueId())) { s.plugin.messages().send(player,"livetest.already-running"); return false; }
        Set<String> ids = s.plugin.abilityRegistry().all().stream().map(a -> a.id()).collect(java.util.stream.Collectors.toSet());
        if (!new Validator().validate(template,s.config,ids).isEmpty()) { s.plugin.messages().send(player,"livetest.invalid"); return false; }
        var test = new LiveTestService(s,player);
        s.tests.put(player.getUniqueId(),test);
        try {
            Location at = spawnLocation(player.getLocation());
            if (!at.getBlock().isPassable() || !at.clone().add(0,1,0).getBlock().isPassable()) {
                s.plugin.messages().send(player,"livetest.blocked"); test.close(); return false;
            }
            test.templates.put(template.id(),template);
            test.spawn(template,at);
            test.ticker = Bukkit.getScheduler().runTaskTimer(s.plugin,test::tick,1,1);
            s.plugin.messages().send(player,"livetest.started"); return true;
        } catch (RuntimeException error) {
            test.close(); s.plugin.getLogger().warning("Live-test spawn failed: " + error.getClass().getSimpleName());
            s.plugin.messages().send(player,"livetest.invalid"); return false;
        }
    }
    public static void stop(Player player) {
        if (manager == null) return;
        LiveTestService test = manager.tests.get(player.getUniqueId());
        if (test != null) test.close();
    }
    public static boolean invulnerable(Player player) {
        return manager != null && manager.tests.containsKey(player.getUniqueId()) && player.isInvulnerable();
    }
    public static void toggleInvulnerable(Player player) {
        if (manager != null && manager.tests.containsKey(player.getUniqueId()) && player.hasPermission("customdungeons.admin.test")) {
            player.setInvulnerable(!player.isInvulnerable());
            manager.plugin.messages().send(player,player.isInvulnerable()?"livetest.invulnerable-on":"livetest.invulnerable-off");
        }
    }
    public static Location spawnLocation(Location from) {
        double yaw=Math.toRadians(from.getYaw());
        return from.clone().add(-Math.sin(yaw)*4,0,Math.cos(yaw)*4);
    }
    public static boolean shouldStop(boolean online, boolean sameWorld, double distanceSquared, long tick, int maxSeconds) {
        return !online || !sameWorld || !Double.isFinite(distanceSquared) || distanceSquared > 48 * 48 || tick >= (long)maxSeconds * 20;
    }
    private ActiveMob spawn(MobTemplate template, Location at) {
        if (closed || mobs.size() >= services.config.limits().maxAliveMobsPerSession()) return null;
        ActiveMob mob = services.factory.spawn(template,at,this,1);
        track(mob.entity()); mobs.put(mob.entity().getUniqueId(),mob);
        mob.entity().setTarget(admin);
        if (template.boss()) { bosses.barFor(mob); bosses.startMusic(mob); }
        fire(Trigger.ON_SPAWN,mob,null);
        return mob;
    }
    private void track(Entity entity) {
        entity.getPersistentDataContainer().set(LIVE,PersistentDataType.BYTE,(byte)1);
        entity.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,id.toString());
        entities.add(entity);
        if (entity instanceof Mob summoned && !mobs.containsKey(entity.getUniqueId())) {
            // Direct public-API summons (e.g. internal vexes) also belong to the context.
            var vanilla=new MobTemplate("__live_minion",summoned.getType().name(),"",0,0,0,0,0,
                    Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
            mobs.put(entity.getUniqueId(),new ActiveMob(summoned,vanilla,this));
        }
        if(entity instanceof Explosive explosive) { explosive.setIsIncendiary(false); explosive.setYield(0); }
    }
    void tick() {
        boolean same = admin.getWorld().equals(origin.getWorld());
        if (shouldStop(admin.isOnline() && !admin.isDead() && admin.hasPermission("customdungeons.admin.test"),same,
                same ? admin.getLocation().distanceSquared(origin) : 0,clock.currentTick(),services.config.liveTestMaxSeconds())) { close(); return; }
        services.executing = this;
        try {
            clock.advance();
            for (ActiveMob mob : List.copyOf(mobs.values())) {
                if (!mob.entity().isValid() || mob.entity().isDead()) { removeMob(mob); continue; }
                bosses.onDamaged(mob,clock.currentTick()); // Current/post-damage health, never speculative event health.
            }
            engine.tick(mobs.values(),clock.currentTick());
            bosses.tickMusic(clock.currentTick());
            entities.removeIf(e -> !e.isValid() && !(e instanceof Mob));
        } catch (RuntimeException error) {
            services.plugin.getLogger().warning("Live-test tick failed: " + error.getClass().getSimpleName());
            close();
        } finally { services.executing = null; }
    }
    private void fire(Trigger trigger, ActiveMob mob, Event event) {
        LiveTestService previous=services.executing;
        services.executing=this;
        try { engine.fire(trigger,mob,event,clock.currentTick()); }
        finally { services.executing=previous; }
    }
    private void removeMob(ActiveMob mob) {
        bosses.cleanup(mob); returnStolen(mob); mobs.remove(mob.entity().getUniqueId());
    }
    private void returnStolen(ActiveMob thief) {
        List<ItemStack> items = stolen.remove(thief);
        LiveTestService previous = services.executing;
        services.executing = null; // Returned player items are never owned by the test cleanup.
        try {
            if (items != null) for (ItemStack item : items) {
                for (ItemStack leftover : admin.getInventory().addItem(item).values()) admin.getWorld().dropItemNaturally(admin.getLocation(),leftover);
            }
        } finally { services.executing = previous; }
        thief.stolenItems().clear();
    }
    @Override public void close() {
        if (closed) return; closed = true;
        if (ticker != null) ticker.cancel();
        services.tests.remove(admin.getUniqueId(),this);
        services.splits.removeIf(s -> s.test==this);
        clock.clear();
        for (ActiveMob mob : List.copyOf(mobs.values())) removeMob(mob);
        for (Entity entity : List.copyOf(entities)) if (entity.isValid()) entity.remove();
        entities.clear(); blocks.restoreAll(); admin.setInvulnerable(originalInvulnerable);
        if (admin.isOnline()) services.plugin.messages().send(admin,"livetest.stopped");
    }
    @Override public UUID id() { return id; }
    @Override public boolean isLiveTest() { return true; }
    @Override public Collection<Player> players() { return closed ? List.of() : List.of(admin); }
    @Override public Region currentRoomRegion() { return null; }
    @Override public Collection<ActiveMob> mobs() { return List.copyOf(mobs.values()); }
    @Override public ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner) {
        MobTemplate template = templates.get(templateId);
        return template == null || closed ? null : spawn(template,at);
    }
    @Override public TempBlocks tempBlocks() { return blocks; }
    @Override public TickScheduler scheduler() { return clock; }
    @Override public void onItemStolen(UUID owner, ItemStack item, ActiveMob thief) {
        if (!owner.equals(admin.getUniqueId()) || closed) throw new IllegalArgumentException("Not a live-test participant");
        stolen.computeIfAbsent(thief,k -> new ArrayList<>()).add(item.clone());
    }

    /** Pure queue, used by the sole ticker; work queued by work is deferred until the next tick. */
    public static final class Clock implements TickScheduler {
        private record Work(long due, long order, Runnable task) {}
        private final PriorityQueue<Work> pending = new PriorityQueue<>(Comparator.comparingLong(Work::due).thenComparingLong(Work::order));
        private long tick, order;
        @Override public void runLater(int delay, Runnable task) { pending.add(new Work(tick+Math.max(1,delay),order++,task)); }
        @Override public long currentTick() { return tick; }
        public void advance() {
            tick++;
            while (!pending.isEmpty() && pending.peek().due() <= tick) pending.remove().task().run();
        }
        public void clear() { pending.clear(); }
    }

    private final class Blocks implements TempBlocks {
        private final Map<Block, BlockData> originals = new HashMap<>();
        private final Set<Block> placed = new HashSet<>();
        @Override public boolean place(Block block, BlockData data, int ttlTicks) {
            if (closed || !block.isEmpty() || services.reserved.containsKey(block)) return false;
            services.reserved.put(block, LiveTestService.this);
            BlockData original = block.getBlockData().clone(); originals.put(block,original);
            // Write-ahead journal: disk work is asynchronous, block placement waits for durability.
            CompletableFuture<Void> persisted = services.journal.add(block,original);
            clock.runLater(1, new Runnable() {
                @Override public void run() {
                    if (closed || !originals.containsKey(block)) return;
                    if (!persisted.isDone()) { clock.runLater(1,this); return; }
                    if (persisted.isCompletedExceptionally() || !block.isEmpty()) { restore(block); return; }
                    block.setBlockData(data,false); placed.add(block);
                    clock.runLater(Math.max(1,ttlTicks),() -> restore(block));
                }
            });
            return true;
        }
        private void restore(Block block) {
            BlockData original = originals.remove(block);
            if (original != null) {
                if (placed.remove(block)) block.setBlockData(original,false);
                services.reserved.remove(block,LiveTestService.this); services.journal.remove(block);
            }
        }
        @Override public void restoreAll() { for (Block block : List.copyOf(originals.keySet())) restore(block); }
    }

    /** Serialized asynchronous write-ahead snapshots; recovery is allowed to do I/O in onEnable. */
    static final class Journal {
        private final Path file;
        private final Map<String,String> entries = new LinkedHashMap<>();
        private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);
        Journal(Path file) { this.file=file; recover(); }
        private String key(Block block) { return block.getWorld().getUID()+";"+block.getX()+";"+block.getY()+";"+block.getZ(); }
        private void recover() {
            if (!Files.exists(file)) return;
            try {
                for (String line : Files.readAllLines(file)) {
                    int tab=line.indexOf('\t'); if(tab<0) continue;
                    String key=line.substring(0,tab), value=line.substring(tab+1); String[] pos=key.split(";");
                    World world=Bukkit.getWorld(UUID.fromString(pos[0]));
                    if(world==null) { entries.put(key,value); continue; }
                    world.getBlockAt(Integer.parseInt(pos[1]),Integer.parseInt(pos[2]),Integer.parseInt(pos[3])).setBlockData(Bukkit.createBlockData(value),false);
                }
                persist().join();
            } catch (Exception error) { throw new IllegalStateException("Cannot recover live-test blocks",error); }
        }
        void worldLoaded(World world) {
            String prefix=world.getUID()+";";
            for (String key:List.copyOf(entries.keySet())) {
                if(!key.startsWith(prefix)) continue;
                String[] pos=key.split(";");
                world.getBlockAt(Integer.parseInt(pos[1]),Integer.parseInt(pos[2]),Integer.parseInt(pos[3]))
                        .setBlockData(Bukkit.createBlockData(entries.remove(key)),false);
            }
            persist();
        }
        CompletableFuture<Void> add(Block block,BlockData original) { entries.put(key(block),original.getAsString()); return persist(); }
        void remove(Block block) { entries.remove(key(block)); persist(); }
        private CompletableFuture<Void> persist() {
            String content=entries.entrySet().stream().map(e -> e.getKey()+"\t"+e.getValue()+"\n").collect(java.util.stream.Collectors.joining());
            writes=writes.handle((v,e) -> null).thenRunAsync(() -> {
                try {
                    Files.createDirectories(file.getParent()); Path tmp=file.resolveSibling(file.getFileName()+".tmp");
                    try (var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                        java.nio.ByteBuffer bytes=StandardCharsets.UTF_8.encode(content);
                        while(bytes.hasRemaining()) channel.write(bytes);
                        channel.force(true);
                    }
                    Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception error) { throw new CompletionException(error); }
            });
            return writes;
        }
        void close() { writes.join(); }
    }

    static final class Manager implements Listener {
        final CustomDungeonsPlugin plugin;
        final PluginConfig config;
        final DefinitionStore store;
        final MobFactory factory;
        final Journal journal;
        final Map<UUID,LiveTestService> tests = new HashMap<>();
        final Map<Block,LiveTestService> reserved = new HashMap<>();
        LiveTestService executing;
        final List<Split> splits=new ArrayList<>();
        private static final class Split {
            final LiveTestService test; final Location at; int remaining;
            Split(LiveTestService test,Location at,int remaining) { this.test=test; this.at=at; this.remaining=remaining; }
        }
        Manager(CustomDungeonsPlugin plugin) {
            this.plugin=plugin; config=Objects.requireNonNull(Bukkit.getServicesManager().load(PluginConfig.class));
            store=Objects.requireNonNull(Bukkit.getServicesManager().load(DefinitionStore.class)); factory=new MobFactory(config);
            journal=new Journal(plugin.getDataFolder().toPath().resolve("live-test-blocks.journal"));
            for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) if(entity.getPersistentDataContainer().has(LIVE,PersistentDataType.BYTE)) entity.remove();
        }
        Manager(CustomDungeonsPlugin plugin, PluginConfig config, DefinitionStore store, MobFactory factory, Path journalFile) {
            this.plugin=plugin; this.config=config; this.store=store; this.factory=factory; journal=new Journal(journalFile);
        }
        @EventHandler public void worldLoaded(org.bukkit.event.world.WorldLoadEvent event) { journal.worldLoaded(event.getWorld()); }
        LiveTestService owner(Entity entity) {
            String id=entity.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
            for (LiveTestService test : tests.values()) if(test.id.toString().equals(id) || test.entities.contains(entity)) return test;
            if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) return owner(shooter);
            if (entity instanceof EvokerFangs fangs && fangs.getOwner()!=null) return owner(fangs.getOwner());
            if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity source) return owner(source);
            return null;
        }
        @EventHandler public void spawned(EntitySpawnEvent event) {
            LiveTestService test=executing != null ? executing : owner(event.getEntity());
            if(test==null && event instanceof CreatureSpawnEvent creature && creature.getSpawnReason()==CreatureSpawnEvent.SpawnReason.SLIME_SPLIT) {
                Location at=event.getEntity().getLocation();
                Split split=splits.stream().filter(s -> !s.test.closed && s.remaining>0 && s.at.getWorld().equals(at.getWorld()) && s.at.distanceSquared(at)<=256)
                        .min(Comparator.comparingDouble(s -> s.at.distanceSquared(at))).orElse(null);
                if(split!=null) { test=split.test; if(--split.remaining==0) splits.remove(split); }
            }
            if(test!=null) test.track(event.getEntity());
        }
        @EventHandler public void loaded(EntitiesLoadEvent event) {
            for(Entity e:event.getEntities()) if(e.getPersistentDataContainer().has(LIVE,PersistentDataType.BYTE)) {
                LiveTestService test=owner(e);
                if(test==null) e.remove(); else test.track(e);
            }
        }
        @EventHandler(priority=EventPriority.HIGHEST) public void target(EntityTargetLivingEntityEvent event) {
            LiveTestService test=owner(event.getEntity());
            if(test!=null && event.getTarget()!=null && !event.getTarget().equals(test.admin)) event.setCancelled(true);
        }
        @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
            LiveTestService test=owner(event.getEntity());
            if(test!=null) {
                ActiveMob mob=test.mobs.get(event.getEntity().getUniqueId());
                if(mob!=null) {
                    if(test.clock.currentTick()<mob.invulnerableUntil()) { event.setCancelled(true); return; }
                    test.fire(Trigger.ON_DAMAGED,mob,event);
                }
            }
            if(event instanceof EntityDamageByEntityEvent byEntity) {
                LiveTestService attacker=owner(byEntity.getDamager());
                if(attacker!=null) {
                    if(!event.getEntity().equals(attacker.admin)) { event.setCancelled(true); return; }
                    Entity source=byEntity.getDamager();
                    if(source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source=shooter;
                    ActiveMob mob=attacker.mobs.get(source.getUniqueId());
                    if(mob!=null) attacker.fire(Trigger.ON_HIT,mob,event);
                }
            }
        }
        @EventHandler public void death(EntityDeathEvent event) {
            LiveTestService test=owner(event.getEntity()); if(test==null) return;
            ActiveMob mob=test.mobs.get(event.getEntity().getUniqueId()); if(mob==null) return;
            if(!mob.template().vanillaDrops()) { event.getDrops().clear(); event.setDroppedExp(0); }
            List<ItemStack> stolen=test.stolen.get(mob);
            if(stolen!=null) event.getDrops().removeIf(drop -> stolen.stream().anyMatch(drop::isSimilar));
            test.fire(Trigger.ON_DEATH,mob,event); test.removeMob(mob);
        }
        @EventHandler(ignoreCancelled=true) public void transform(EntityTransformEvent event) {
            LiveTestService test=owner(event.getEntity());
            if(test!=null) for(Entity entity:event.getTransformedEntities()) test.track(entity);
        }
        @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void split(SlimeSplitEvent event) {
            LiveTestService test=owner(event.getEntity());
            if(test==null) return;
            Split split=new Split(test,event.getEntity().getLocation(),event.getCount()); splits.add(split);
            test.clock.runLater(1,() -> splits.remove(split));
        }
        @EventHandler public void quit(PlayerQuitEvent event) { stop(event.getPlayer()); }
        @EventHandler public void playerDeath(PlayerDeathEvent event) { LiveTestService test=tests.get(event.getEntity().getUniqueId()); if(test!=null) test.clock.runLater(1,test::close); }
        @EventHandler(priority=EventPriority.HIGHEST) public void explode(EntityExplodeEvent event) { if(owner(event.getEntity())!=null) event.blockList().clear(); }
        @EventHandler(priority=EventPriority.HIGHEST) public void prime(ExplosionPrimeEvent event) { if(owner(event.getEntity())!=null) event.setFire(false); }
        @EventHandler(priority=EventPriority.HIGHEST) public void splash(PotionSplashEvent event) {
            LiveTestService test=owner(event.getPotion()); if(test==null) return;
            for (LivingEntity target:List.copyOf(event.getAffectedEntities())) if(!target.equals(test.admin)) event.setIntensity(target,0);
        }
        @EventHandler(priority=EventPriority.HIGHEST) public void cloud(AreaEffectCloudApplyEvent event) {
            LiveTestService test=owner(event.getEntity()); if(test!=null) event.getAffectedEntities().removeIf(e -> !e.equals(test.admin));
        }
        @EventHandler(priority=EventPriority.HIGHEST) public void ignite(org.bukkit.event.block.BlockIgniteEvent event) { if(event.getIgnitingEntity()!=null && owner(event.getIgnitingEntity())!=null) event.setCancelled(true); }
        @EventHandler(priority=EventPriority.HIGHEST) public void changeBlock(EntityChangeBlockEvent event) { if(owner(event.getEntity())!=null) event.setCancelled(true); }
        @EventHandler public void disable(PluginDisableEvent event) {
            if(event.getPlugin()!=plugin) return;
            for(LiveTestService test:List.copyOf(tests.values())) test.close();
            journal.close(); manager=null;
        }
    }
}
