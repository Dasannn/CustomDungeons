package dev.dasan.customdungeons.mob;

import org.bukkit.plugin.Plugin;
import java.util.function.Predicate;
import java.util.function.BiPredicate;
import dev.dasan.customdungeons.ability.EffectAudience;
import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.config.EntityHeights;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.Trigger;
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

/** One isolated MobHost and one ticker per administrator. All Bukkit work is main-thread. */
public final class LiveTestService implements MobHost, AutoCloseable {
    private static final NamespacedKey LIVE = MobsPlatform.key("live_test");
    private static Manager manager;
    private final Manager services;
    private final UUID id = UUID.randomUUID();
    private final Player admin;
    private final Location origin;
    private final boolean originalInvulnerable;
    private final Map<UUID, ActiveMob> mobs = new LinkedHashMap<>();
    private final Set<Entity> entities = new HashSet<>();
    private record StolenItem(Player owner, ItemStack item) {}
    private final Map<ActiveMob, List<StolenItem>> stolen = new IdentityHashMap<>();
    private List<Player> nearbyPlayers;
    private final Clock clock = new Clock();
    private final Blocks blocks = new Blocks();
    private final AbilityEngine engine;
    private final BossController bosses;
    private final Map<String,MobTemplate> templates;
    private BukkitTask ticker;
    private boolean closed;
    private boolean invulnerable;
    Mob principal;

    LiveTestService(Manager services, Player admin) {
        this.services = services; this.admin = admin; origin = admin.getLocation().clone();
        originalInvulnerable = admin.isInvulnerable(); invulnerable=originalInvulnerable;
        nearbyPlayers=List.of(admin);
        engine = new AbilityEngine(services.platform.abilityRegistry(), services.platform);
        templates = new HashMap<>(services.platform.templates());
        bosses = new BossController(services.factory, templates);
    }
    public static void register(Plugin plugin, MobsPlatform platform,
            BiPredicate<Player, MobTemplate> validation, Predicate<Player> available) {
        manager = new Manager(plugin, platform, validation, available,
                plugin.getDataFolder().toPath().resolve("live-test-blocks.journal"));
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities())
            if (entity.getPersistentDataContainer().has(LIVE, PersistentDataType.BYTE)) MobHealth.terminate(entity, false);
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
    }
    public static boolean start(Player player, MobTemplate template) {
        Manager s = Objects.requireNonNull(manager, "Live-test services not registered");
        if (!player.hasPermission("customdungeons.admin.test")) { s.platform.messages().send(player,"livetest.no-permission"); return false; }
        if (s.tests.containsKey(player.getUniqueId())) { s.platform.messages().send(player,"livetest.already-running"); return false; }
        if (!s.validation.test(player, template)) return false;
        var test = new LiveTestService(s,player);
        s.tests.put(player.getUniqueId(),test);
        try {
            Location at = spawnLocation(player);
            boolean enoughSpace = safeSpawnLocation(at,template).isPresent();
            test.templates.put(template.id(),template);
            test.refreshPlayers(at);
            ActiveMob primary=test.spawn(template,at);
            if(primary==null || !primary.entity().isValid() || primary.entity().isDead()) {
                test.close(); return false;
            }
            test.principal=primary.entity();
            test.ticker = Bukkit.getScheduler().runTaskTimer(s.plugin,test::tick,1,1);
            if (!enoughSpace) s.platform.messages().send(player,"livetest.space-warning");
            s.platform.messages().send(player,"livetest.started"); return true;
        } catch (RuntimeException error) {
            test.close(); s.plugin.getLogger().warning("Live-test spawn failed: " + error.getClass().getSimpleName());
            s.platform.messages().send(player,"livetest.invalid"); return false;
        }
    }
    public static void stop(Player player) {
        if (manager == null) return;
        LiveTestService test = manager.tests.get(player.getUniqueId());
        if (test != null) test.close();
        else manager.platform.messages().send(player,"livetest.not-running");
    }
    public static boolean owns(Entity entity) { return manager != null && manager.owner(entity) != null; }
    public static void stopAll() {
        if(manager!=null)for(var test:List.copyOf(manager.tests.values()))test.close();
    }
    public static boolean active(Player player) { return manager!=null && manager.tests.containsKey(player.getUniqueId()); }
    public static boolean invulnerable(Player player) {
        return manager != null && manager.tests.containsKey(player.getUniqueId()) && manager.tests.get(player.getUniqueId()).invulnerable;
    }
    public static void toggleInvulnerable(Player player) {
        if (manager != null && manager.tests.containsKey(player.getUniqueId()) && player.hasPermission("customdungeons.admin.test")) {
            var test=manager.tests.get(player.getUniqueId());
            test.setInvulnerable(!test.invulnerable);
            String key=test.invulnerable?"livetest.invulnerable-on":"livetest.invulnerable-off";
            manager.platform.messages().send(player,key);
            player.sendActionBar(manager.platform.messages().get(key));
        }
    }
    void setInvulnerable(boolean value) { invulnerable=value; admin.setInvulnerable(value); }
    /** World-space collision boxes; null means unavailable (outside bounds or unloaded). */
    @FunctionalInterface interface CollisionBoxes {
        Collection<org.bukkit.util.BoundingBox> at(int x,int y,int z);
    }
    static boolean safePosition(double x,double y,double z,double width,double height,CollisionBoxes collisions) {
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(width) || !Double.isFinite(height) || width<=0 || height<=0 || width>128 || height>1024) return false;
        var body=new org.bukkit.util.BoundingBox(x-width/2,y,z-width/2,x+width/2,y+height,z+width/2);
        int minX=(int)Math.floor(body.getMinX()), maxX=(int)Math.ceil(body.getMaxX())-1;
        int minZ=(int)Math.floor(body.getMinZ()), maxZ=(int)Math.ceil(body.getMaxZ())-1;
        int bottom=(int)Math.floor(y)-1, top=(int)Math.ceil(body.getMaxY())-1;
        boolean supported=false;
        for(int bx=minX;bx<=maxX;bx++) for(int bz=minZ;bz<=maxZ;bz++) for(int by=bottom;by<=top;by++) {
            var boxes=collisions.at(bx,by,bz);
            if(boxes==null) return false;
            for(var box:boxes) {
                // Touching faces are allowed; an overlap means some of the body is obstructed.
                if(body.overlaps(box)) return false;
                if(Math.abs(box.getMaxY()-y)<1e-7 && box.getHeight()>0
                        && box.getMinX()<body.getMaxX() && box.getMaxX()>body.getMinX()
                        && box.getMinZ()<body.getMaxZ() && box.getMaxZ()>body.getMinZ()) supported=true;
            }
        }
        return supported;
    }
    static Optional<Location> safeSpawnLocation(Location from,MobTemplate template) {
        Location preferred=from; World world=Objects.requireNonNull(from.getWorld());
        String name=template.entityType().toUpperCase(Locale.ROOT).replace("MINECRAFT:","");
        // Public API creates an unspawned entity; no events, mobs or chunk tickets are introduced.
        Entity dimensions=world.createEntity(preferred,Objects.requireNonNull(EntityType.valueOf(name).getEntityClass()));
        Double attributeScale=template.attributes().values().get("scale");
        double scale=attributeScale==null ? dev.dasan.customdungeons.config.NumericRanges.effectiveScale(template.scale())
                : Math.max(dev.dasan.customdungeons.config.NumericRanges.SCALE_ATTRIBUTE_MIN,attributeScale);
        double width=dimensions.getWidth()*scale, height=dimensions.getHeight()*scale;
        boolean safe=safePosition(preferred.getX(),preferred.getY(),preferred.getZ(),width,height,(x,y,z)-> {
            if(y<world.getMinHeight() || y>=world.getMaxHeight() || !world.isChunkLoaded(x>>4,z>>4)) return null;
            // VoxelShape boxes are block-local; clone before shifting so API shapes stay unchanged.
            return world.getBlockAt(x,y,z).getCollisionShape().getBoundingBoxes().stream()
                    .map(box->box.clone().shift(x,y,z)).toList();
        });
        return safe ? Optional.of(preferred.clone()) : Optional.empty();
    }
    static Location spawnLocation(Player player) {
        Location at=player.getLocation();
        var hit=player.rayTraceBlocks(32);
        if(hit==null || hit.getHitBlock()==null) return spawnLocation(at);
        Block block=hit.getHitBlock();
        var box=block.getBoundingBox();
        double top=box.getHeight()>0 ? box.getMaxY() : block.getY()+1;
        return new Location(block.getWorld(),block.getX()+.5,top,block.getZ()+.5,at.getYaw(),at.getPitch());
    }
    public static Location spawnLocation(Location from) {
        return from.clone();
    }
    public static boolean shouldStop(boolean online, boolean sameWorld, double distanceSquared) {
        return !online || !sameWorld || !Double.isFinite(distanceSquared) || distanceSquared > 48 * 48;
    }
    private ActiveMob spawn(MobTemplate template, Location at) {
        if (closed || mobs.size() >= services.platform.limits().maxAliveMobsPerSession()) return null;
        ActiveMob mob = services.factory.spawn(template,at,this,1);
        if (!track(mob.entity())) return null;
        mobs.put(mob.entity().getUniqueId(),mob);
        if (template.boss()) { bosses.barFor(mob); bosses.startMusic(mob); }
        fire(Trigger.ON_SPAWN,mob,null);
        return mob;
    }
    private boolean track(Entity entity) {
        if (closed || (entity instanceof Mob && !mobs.containsKey(entity.getUniqueId())
                && mobs.size() >= services.platform.limits().maxAliveMobsPerSession())) {
            MobHealth.terminate(entity,false);
            return false;
        }
        entity.getPersistentDataContainer().set(LIVE,PersistentDataType.BYTE,(byte)1);
        entity.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,id.toString());
        entities.add(entity);
        if (entity instanceof Mob summoned && !mobs.containsKey(entity.getUniqueId())
                && !dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(entity)) {
            // Direct public-API summons (e.g. internal vexes) also belong to the context.
            var vanilla=new MobTemplate("__live_minion",summoned.getType().name(),"",0,0,0,0,0,
                    Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
            mobs.put(entity.getUniqueId(),new ActiveMob(summoned,vanilla,this));
        }
        if(entity instanceof Explosive explosive) { explosive.setIsIncendiary(false); explosive.setYield(0); }
        return true;
    }
    void tick() {
        if(principal==null || !principal.isValid() || principal.isDead()) { close(); return; }
        boolean same = admin.getWorld().equals(origin.getWorld());
        if (shouldStop(admin.isOnline() && !admin.isDead() && admin.hasPermission("customdungeons.admin.test"),same,
                same ? admin.getLocation().distanceSquared(origin) : 0)) { close(); return; }
        refreshPlayers(principal.getLocation());
        services.executing = this;
        try {
            clock.advance();
            for (ActiveMob mob : List.copyOf(mobs.values())) {
                if (!mob.entity().isValid() || mob.entity().isDead()) { removeMob(mob); continue; }
                if (mob.entity().getTarget() instanceof Player player && !combatParticipant(player)) mob.entity().setTarget(null);
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
        List<StolenItem> items=stolen.remove(thief);
        if(items!=null) returnItems(items);
        thief.stolenItems().clear();
    }
    private void returnStolenTo(Player player) {
        for(var it=stolen.entrySet().iterator();it.hasNext();) {
            var entry=it.next();
            var items=entry.getValue();
            returnItems(items.stream().filter(item->item.owner().equals(player)).toList());
            items.removeIf(item->item.owner().equals(player));
            if(items.isEmpty()) it.remove();
        }
    }
    private void returnItems(List<StolenItem> items) {
        LiveTestService previous=services.executing;
        services.executing=null; // Returned player items never belong to test cleanup.
        try {
            for(var item:items) {
                Player owner=item.owner();
                if(ThiefReturns.dropIfBuilding(owner,item.item()))continue;
                for(ItemStack leftover:owner.getInventory().addItem(item.item()).values())
                    owner.getWorld().dropItemNaturally(owner.getLocation(),leftover);
            }
        } finally { services.executing=previous; }
    }
    /** Cache only proximity, once per live tick before queued abilities execute. */
    private void refreshPlayers(Location at) {
        List<Player> next=new ArrayList<>();
        next.add(admin);
        if(at.getWorld()!=null) {
            double radius=services.platform.limits().effectViewRadius();
            for(Player player:at.getWorld().getPlayers()) {
                if(player.equals(admin)) continue;
                Location location=player.getLocation();
                if(at.getWorld().equals(location.getWorld()) && location.distanceSquared(at)<=radius*radius) next.add(player);
            }
        }
        nearbyPlayers=List.copyOf(next);
    }
    private static boolean combatMode(Player player) {
        return player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE;
    }
    /** State may change between ticks (especially dungeon join); never cache eligibility. */
    private boolean eligibleParticipant(Player player) {
        if(!combatMode(player) || !player.isOnline() || !player.isValid() || player.isDead()) return false;
        return services.available.test(player);
    }
    private boolean combatParticipant(Player player) {
        return !closed && nearbyPlayers.contains(player) && eligibleParticipant(player);
    }
    @Override public void close() {
        if (closed) return; closed = true;
        if (ticker != null) ticker.cancel();
        services.tests.remove(admin.getUniqueId(),this);
        services.splits.removeIf(s -> s.test==this);
        clock.clear();
        for (ActiveMob mob : List.copyOf(mobs.values())) removeMob(mob);
        for (Entity entity : List.copyOf(entities)) if (entity.isValid()) MobHealth.terminate(entity,false);
        entities.clear(); blocks.restoreAll(); admin.setInvulnerable(originalInvulnerable);
        if (admin.isOnline()) services.platform.messages().send(admin,"livetest.stopped");
    }
    @Override public UUID id() { return id; }
    @Override public Collection<Player> audience(Location at) {
        return at.getWorld() == null ? List.of()
                : EffectAudience.nearby(at.getWorld().getPlayers(), at, services.platform.limits().effectViewRadius());
    }
    @Override public Collection<Player> players() { return closed ? List.of() : nearbyPlayers.stream().filter(this::eligibleParticipant).toList(); }
    @Override public MobArea area() { return null; }
    @Override public Collection<ActiveMob> mobs() { return List.copyOf(mobs.values()); }
    @Override public ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner) {
        MobTemplate template = templates.get(templateId);
        return template == null || closed ? null : spawn(template,at);
    }
    @Override public TempBlocks tempBlocks() { return blocks; }
    @Override public TickScheduler scheduler() { return clock; }
    @Override public void onItemStolen(UUID owner, ItemStack item, ActiveMob thief) {
        Player player=players().stream().filter(p->p.getUniqueId().equals(owner) && combatParticipant(p)).findFirst()
                .orElseThrow(()->new IllegalArgumentException("Not a live-test participant"));
        stolen.computeIfAbsent(thief,k -> new ArrayList<>()).add(new StolenItem(player,item.clone()));
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

    private final class Blocks implements dev.dasan.customdungeons.runtime.RestorableTempBlocks {
        private record Lease(BlockData original,BlockData expected) {}
        private final Map<Block, Lease> originals = new HashMap<>();
        private final Set<Block> placed = new HashSet<>();
        @Override public boolean place(Block block, BlockData data, int ttlTicks) {
            if (closed || !block.isEmpty() || services.reserved.containsKey(block)) return false;
            services.reserved.put(block, LiveTestService.this);
            BlockData original = block.getBlockData().clone(),expected=data.clone();
            var lease=new Lease(original,expected);originals.put(block,lease);
            // Write-ahead journal: disk work is asynchronous, block placement waits for durability.
            CompletableFuture<Void> persisted = services.journal.add(block,original,expected);
            clock.runLater(1, new Runnable() {
                @Override public void run() {
                    if (closed || originals.get(block)!=lease) return;
                    if (!persisted.isDone()) { clock.runLater(1,this); return; }
                    if (persisted.isCompletedExceptionally() || !block.isEmpty()) { restore(block); return; }
                    block.setBlockData(expected,false); placed.add(block);
                    clock.runLater(Math.max(1,ttlTicks),() -> {if(originals.get(block)==lease)restore(block);});
                }
            });
            return true;
        }
        public void restore(Block block) {
            Lease lease = originals.remove(block);
            if (lease != null) {
                if (placed.remove(block)) dev.dasan.customdungeons.runtime.BlockRestoration.restore(block,lease.original(),lease.expected().getAsString());
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
                    restoreRecorded(world,key,value);
                }
                persist().join();
            } catch (Exception error) { throw new IllegalStateException("Cannot recover live-test blocks",error); }
        }
        void worldLoaded(World world) {
            String prefix=world.getUID()+";";
            for (String key:List.copyOf(entries.keySet())) {
                if(!key.startsWith(prefix)) continue;
                restoreRecorded(world,key,entries.remove(key));
            }
            persist();
        }
        private void restoreRecorded(World world,String key,String recorded) {
            String[] pos=key.split(";"),data=recorded.split("\t",2);
            Block block=world.getBlockAt(Integer.parseInt(pos[1]),Integer.parseInt(pos[2]),Integer.parseInt(pos[3]));
            if(data.length==2)dev.dasan.customdungeons.runtime.BlockRestoration.restore(block,data[0],data[1]);
            // Pre-T57b live-test records had no placed data and restored unconditionally.
            // Keep that legacy rule; newly written records always carry exact ownership.
            else block.setBlockData(Bukkit.createBlockData(data[0]),false);
        }
        CompletableFuture<Void> add(Block block,BlockData original,BlockData placed) { entries.put(key(block),original.getAsString()+"\t"+placed.getAsString()); return persist(); }
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
        final Plugin plugin;
        final MobsPlatform platform;
        final BiPredicate<Player, MobTemplate> validation;
        final Predicate<Player> available;
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
        Manager(Plugin plugin, MobsPlatform platform,
                BiPredicate<Player, MobTemplate> validation, Predicate<Player> available, Path journalFile) {
            this.plugin=plugin; this.platform=platform;
            this.validation=validation; this.available=available; factory=new MobFactory(platform);
            journal=new Journal(journalFile);
        }
        @EventHandler public void worldLoaded(org.bukkit.event.world.WorldLoadEvent event) { journal.worldLoaded(event.getWorld()); }
        LiveTestService owner(Entity entity) {
            String id=entity.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING);
            for (LiveTestService test : tests.values()) if(test.id.toString().equals(id) || test.entities.contains(entity)) return test;
            if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) return owner(shooter);
            if (entity instanceof Vex vex && vex.getOwner()!=null) return owner(vex.getOwner());
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
            CreatureSpawnEvent.SpawnReason reason=event instanceof CreatureSpawnEvent creature
                    ? creature.getSpawnReason() : event.getEntity().getEntitySpawnReason();
            if(test==null && isSummon(reason)) test=nearestSummoner(event.getEntity().getLocation());
            if(test!=null && !test.track(event.getEntity())) event.setCancelled(true);
        }
        private boolean isSummon(CreatureSpawnEvent.SpawnReason reason) {
            return reason==CreatureSpawnEvent.SpawnReason.SPELL
                    || reason==CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                    || reason==CreatureSpawnEvent.SpawnReason.DUPLICATION
                    || reason==CreatureSpawnEvent.SpawnReason.POTION_EFFECT;
        }
        private LiveTestService nearestSummoner(Location at) {
            LiveTestService nearest=null;
            double distance=16*16;
            for(LiveTestService test:tests.values()) {
                if(test.closed) continue;
                for(ActiveMob mob:test.mobs.values()) {
                    if(!mob.entity().isValid() || mob.entity().isDead()) continue;
                    Location source=mob.entity().getLocation();
                    if(!Objects.equals(source.getWorld(),at.getWorld())) continue;
                    double candidate=source.distanceSquared(at);
                    if(candidate<=distance) { distance=candidate; nearest=test; }
                }
            }
            return nearest;
        }
        @EventHandler public void loaded(EntitiesLoadEvent event) {
            for(Entity e:event.getEntities()) if(e.getPersistentDataContainer().has(LIVE,PersistentDataType.BYTE)) {
                LiveTestService test=owner(e);
                if(test==null) MobHealth.terminate(e,false); else test.track(e);
            }
        }
        @EventHandler(priority=EventPriority.HIGHEST) public void target(EntityTargetLivingEntityEvent event) {
            LiveTestService test=owner(event.getEntity());
            if(test!=null && event.getTarget()!=null && (!(event.getTarget() instanceof Player player) || !test.combatParticipant(player))) {
                event.setCancelled(true); return;
            }
        }
        @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
            if(event.getEntity() instanceof Player player) {
                var live=tests.get(player.getUniqueId());
                if(live!=null && live.invulnerable) {
                    // Keep an uncancelled, zero-damage cause for ON_HIT and delayed combo effects.
                    if(event instanceof EntityDamageByEntityEvent) event.setDamage(0);
                    else { event.setCancelled(true); return; }
                }
            }
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
                    if(!(event.getEntity() instanceof Player player) || !attacker.combatParticipant(player)) { event.setCancelled(true); return; }
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
            List<StolenItem> stolen=test.stolen.get(mob);
            if(stolen!=null) event.getDrops().removeIf(drop -> stolen.stream().anyMatch(item->drop.isSimilar(item.item())));
            test.fire(Trigger.ON_DEATH,mob,event);
            if(event.getEntity().equals(test.principal)) test.close(); else test.removeMob(mob);
        }
        @EventHandler public void removed(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
            LiveTestService test=owner(event.getEntity());
            if(test==null || test.closed) return;
            if(event.getEntity().equals(test.principal)) test.close();
            else { var mob=test.mobs.get(event.getEntity().getUniqueId()); if(mob!=null) test.removeMob(mob); }
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
        @EventHandler public void quit(PlayerQuitEvent event) {
            for(var live:List.copyOf(tests.values())) live.returnStolenTo(event.getPlayer());
            var test=tests.get(event.getPlayer().getUniqueId()); if(test!=null) test.close();
        }
        @EventHandler public void playerDeath(PlayerDeathEvent event) { LiveTestService test=tests.get(event.getEntity().getUniqueId()); if(test!=null) test.clock.runLater(1,test::close); }
        @EventHandler(priority=EventPriority.HIGHEST) public void explode(EntityExplodeEvent event) { if(owner(event.getEntity())!=null) event.blockList().clear(); }
        @EventHandler(priority=EventPriority.HIGHEST) public void prime(ExplosionPrimeEvent event) { if(owner(event.getEntity())!=null) event.setFire(false); }
        @EventHandler(priority=EventPriority.HIGHEST) public void splash(PotionSplashEvent event) {
            LiveTestService test=owner(event.getPotion()); if(test==null) return;
            for (LivingEntity target:List.copyOf(event.getAffectedEntities())) if(!(target instanceof Player player) || !test.combatParticipant(player)) event.setIntensity(target,0);
        }
        @EventHandler(priority=EventPriority.HIGHEST) public void cloud(AreaEffectCloudApplyEvent event) {
            LiveTestService test=owner(event.getEntity()); if(test!=null) event.getAffectedEntities().removeIf(e -> !(e instanceof Player player) || !test.combatParticipant(player));
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
