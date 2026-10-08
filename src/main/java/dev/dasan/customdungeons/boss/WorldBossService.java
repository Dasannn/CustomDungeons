package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.config.EntityHeights;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.Trigger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Runtime owner: one shared task while at least one encounter lives, none while idle/searching. */
public final class WorldBossService implements Listener,AutoCloseable {
    public static final NamespacedKey MARKER=MobsPlatform.key("world_boss");
    final Plugin plugin;
    final MobsPlatform platform;
    final MobFactory factory;
    final Predicate<Player> available;
    final BossBlockJournal journal;
    final Map<UUID,WorldEncounter> owners=new HashMap<>();
    final Map<UUID,WorldEncounter> encounters=new LinkedHashMap<>();
    final BossRegistry registry;
    final BossSpawner spawner;
    private final BossSlots slots=new BossSlots();
    private final Set<Search> searches=new HashSet<>();
    private record Search(String id,CompletableFuture<Boolean> result,java.util.concurrent.atomic.AtomicBoolean active) {}
    private final java.util.function.BooleanSupplier reloading;
    private BukkitTask ticker;
    private long generation;
    private boolean closed;
    public WorldBossService(Plugin plugin,MobsPlatform platform,BossRegistry registry,EntityHeights heights,int attempts,
                            Predicate<Player> available,java.util.function.BooleanSupplier reloading) {
        this.plugin=plugin;this.platform=platform;this.registry=registry;this.available=available;this.reloading=reloading;
        factory=new MobFactory(platform);spawner=new BossSpawner(plugin,heights,attempts);journal=new BossBlockJournal(plugin);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(World world:Bukkit.getWorlds()) {
            for(Entity e:world.getEntities())if(marked(e))MobHealth.terminate(e,false);
            journal.recover(world);
        }
    }
    public void reloadSettings(int attempts) {spawner.attempts(attempts);}
    public BossRegistry registry() {return registry;}
    public Map<String,MobTemplate> templates() {return registry.all(platform.templates());}
    /** Includes live encounters whose saved template was removed. */
    public Map<String,MobTemplate> listed() {
        var listed=new TreeMap<>(templates());
        for(var encounter:encounters.values())if(!encounter.closed)listed.putIfAbsent(encounter.template.id(),encounter.template);
        return Collections.unmodifiableMap(listed);
    }
    public boolean orphaned(String id) {return alive(id)>0&&!templates().containsKey(id);}
    public int alive(String id) {return (int)encounters.values().stream().filter(e->e.template.id().equals(id)&&!e.closed).count();}
    public List<Location> positions(String id) {return encounters.values().stream().filter(e->e.template.id().equals(id)&&!e.closed&&e.principal!=null).map(e->e.principal.getLocation()).toList();}
    public CompletableFuture<Boolean> spawn(CommandSender sender,String id) {
        var template=templates().get(id);
        if(template==null){tell(sender,"not-found",Placeholder.unparsed("boss",id));return CompletableFuture.completedFuture(false);}
        var b=template.worldBoss();
        if(closed||reloading.getAsBoolean()){tell(sender,"busy");return CompletableFuture.completedFuture(false);}
        if(Bukkit.getWorld(b.world())==null||b.xMin()>=b.xMax()||b.zMin()>=b.zMax()) {
            tell(sender,"validation-error");return CompletableFuture.completedFuture(false);
        }
        if(!slots.reserve(id,b.maxAlive())) {
            tell(sender,"spawn-limit",Placeholder.unparsed("alive",Integer.toString(slots.count(id))),Placeholder.unparsed("max",Integer.toString(b.maxAlive())));
            return CompletableFuture.completedFuture(false);
        }
        var search=new Search(id,new CompletableFuture<>(),new java.util.concurrent.atomic.AtomicBoolean(true));searches.add(search);long version=generation;
        tell(sender,"spawn-searching");
        try {
            spawner.find(template,search.active()::get).whenComplete((point,error)->{
                // Only current searches may publish on the main thread.
                if(!plugin.isEnabled()||!search.active().get()||closed||version!=generation)return;
                if(!searches.remove(search))return;
                search.active().set(false);
                if(error!=null||point.isEmpty()||closed||version!=generation||reloading.getAsBoolean()) {
                    slots.release(id);tell(sender,"spawn-no-location",Placeholder.unparsed("attempts",Integer.toString(spawner.attempts())));search.result().complete(false);return;
                }
                WorldEncounter encounter=new WorldEncounter(this,template,point.get());encounters.put(encounter.id,encounter);
                try {
                    encounter.start();
                    if(encounter.closed||encounter.principal==null||!encounter.principal.isValid()||encounter.principal.isDead()) {
                        encounter.close();search.result().complete(false);return;
                    }
                    if(ticker==null)ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
                    tell(sender,"spawned",Placeholder.unparsed("boss",id),Placeholder.unparsed("world",b.world()),
                            Placeholder.unparsed("x",Integer.toString(point.get().getBlockX())),Placeholder.unparsed("y",Integer.toString(point.get().getBlockY())),Placeholder.unparsed("z",Integer.toString(point.get().getBlockZ())));
                    search.result().complete(true);
                } catch(RuntimeException failure) {encounter.close();search.result().complete(false);tell(sender,"spawn-failed");warnFailure("spawn-failed",failure);}
            });
        } catch(RuntimeException failure) {search.active().set(false);searches.remove(search);slots.release(id);search.result().complete(false);tell(sender,"spawn-failed");}
        return search.result();
    }
    public int despawn(CommandSender sender,String id) {
        int count=0;
        // Cancel in-flight searches too: an administrative retirement cannot spawn a late boss.
        for(Search s:List.copyOf(searches))if(s.id().equals(id)){s.active().set(false);searches.remove(s);slots.release(id);s.result().complete(false);}
        for(WorldEncounter e:List.copyOf(encounters.values()))if(e.template.id().equals(id)){e.close();count++;}
        tell(sender,"despawned",Placeholder.unparsed("boss",id),Placeholder.unparsed("count",Integer.toString(count)));return count;
    }
    public void list(CommandSender sender) {
        tell(sender,"command-list-heading",Placeholder.unparsed("count",Integer.toString(listed().size())));
        listed().forEach((id,m)->{
            var positions=positions(id);
            var position=positions.isEmpty()?platform.messages().get("gui.world-boss.no-position"):
                    net.kyori.adventure.text.Component.join(net.kyori.adventure.text.JoinConfiguration.separator(net.kyori.adventure.text.Component.newline()),
                            positions.stream().map(p->platform.messages().get("gui.world-boss.position",Placeholder.unparsed("world",p.getWorld().getName()),
                                    Placeholder.unparsed("x",Integer.toString(p.getBlockX())),Placeholder.unparsed("y",Integer.toString(p.getBlockY())),Placeholder.unparsed("z",Integer.toString(p.getBlockZ())))).toList());
            tell(sender,"command-list-line",Placeholder.component("boss",net.kyori.adventure.text.Component.text(id).append(orphaned(id)?net.kyori.adventure.text.Component.space().append(platform.messages().get("gui.world-boss.orphan-marker")):net.kyori.adventure.text.Component.empty())),Placeholder.unparsed("alive",Integer.toString(alive(id))),Placeholder.unparsed("max",Integer.toString(m.worldBoss().maxAlive())),Placeholder.component("position",position));
        });
    }
    private void tick() {
        for(WorldEncounter e:List.copyOf(encounters.values()))try {e.tick();}catch(RuntimeException failure){warnFailure("tick-failed",failure);e.close();}
    }
    void retired(WorldEncounter e) {
        if(encounters.remove(e.id,e))slots.release(e.template.id());
        if(encounters.isEmpty()&&ticker!=null){ticker.cancel();ticker=null;}
    }
    public void cancelSearches() {
        generation++;
        for(Search s:List.copyOf(searches)){s.active().set(false);searches.remove(s);slots.release(s.id());s.result().complete(false);}
    }
    public void retireAll() {
        cancelSearches();
        for(WorldEncounter e:List.copyOf(encounters.values()))e.close();
    }
    public boolean ticking() {return ticker!=null;}
    void tell(CommandSender sender,String key,TagResolver... args) {platform.messages().send(sender,"gui.world-boss."+key,args);}
    private void warnFailure(String key,RuntimeException failure) {
        String message=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(platform.messages().get("gui.world-boss."+key));
        plugin.getLogger().warning(message+": "+failure.getClass().getSimpleName());
    }
    static boolean marked(Entity e) {return e.getPersistentDataContainer().has(MARKER);}
    WorldEncounter owner(Entity entity) {
        String id=entity.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING);
        if(id==null)return null;
        var known=owners.get(entity.getUniqueId());
        if(known!=null && known.id.toString().equals(id) && !known.closed)return known;
        for(var encounter:encounters.values())if(encounter.id.toString().equals(id)&&!encounter.closed)return encounter;
        return null;
    }
    @EventHandler public void spawned(EntitySpawnEvent event) {
        WorldEncounter e=owner(event.getEntity());
        if(e!=null&&!e.track(event.getEntity()))event.setCancelled(true);
    }
    /** Called by the common launch listener after the shooter's explicit PDC has been copied. */
    public void trackProjectile(Projectile projectile) {
        WorldEncounter encounter=owner(projectile);
        if(encounter!=null)encounter.track(projectile);
        else if(marked(projectile))MobHealth.terminate(projectile,false); // A retired encounter cannot launch new remnants.
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageEvent event) {
        WorldEncounter e=owner(event.getEntity());
        if(e!=null) {
            var mob=e.active.get(event.getEntity().getUniqueId());
            if(mob!=null) {
                if(e.clock.currentTick()<mob.invulnerableUntil()){event.setCancelled(true);return;}
                e.fire(Trigger.ON_DAMAGED,mob,event);
            }
        }
        if(event instanceof EntityDamageByEntityEvent by) {
            WorldEncounter attacker=owner(by.getDamager());
            if(attacker!=null) {
                if(!(event.getEntity() instanceof Player p)||!attacker.participant(p)){event.setCancelled(true);return;}
                Entity source=by.getDamager();if(source instanceof Projectile projectile&&projectile.getShooter() instanceof Entity shooter)source=shooter;
                var mob=attacker.active.get(source.getUniqueId());if(mob!=null)attacker.fire(Trigger.ON_HIT,mob,event);
            }
        }
    }
    @EventHandler public void countDamage(MobDamageAppliedEvent applied) {
        var event=applied.damage();
        WorldEncounter e=owner(applied.entity());if(e==null||e.damage==null||!applied.entity().equals(e.principal))return;
        if(event.getCause()==EntityDamageEvent.DamageCause.KILL||event.isCancelled())return;
        if(applied.amount()>0&&applied.remaining()==0)e.lethalDamage=event;
        if(event instanceof EntityDamageByEntityEvent by) {
            Player player=damagePlayer(by.getDamager());
            if(player!=null&&e.eligible(player,e.principal.getLocation())) {
                e.contributors.put(player.getUniqueId(),player);
                e.damage.add(player.getUniqueId(),applied.amount(),applied.amount()+applied.remaining());
            }
        }
    }
    private static Player damagePlayer(Entity attacker) {
        if(attacker instanceof Player p)return p;
        if(attacker instanceof Projectile p&&p.getShooter() instanceof Entity shooter)return damagePlayer(shooter);
        if(attacker instanceof Tameable pet&&pet.getOwner()!=null) {
            var owner=pet.getOwner();return owner instanceof Player p?p:Bukkit.getPlayer(owner.getUniqueId());
        }
        return null;
    }
    static boolean rewardDeath(EntityDamageEvent last,EntityDamageEvent lethal) {
        return last!=null&&last==lethal&&!last.isCancelled()&&last.getCause()!=EntityDamageEvent.DamageCause.KILL;
    }
    @EventHandler public void death(EntityDeathEvent event) {
        var e=owner(event.getEntity());if(e==null||e.closed)return;
        var mob=e.active.get(event.getEntity().getUniqueId());
        if(mob!=null) {
            if(!mob.template().vanillaDrops()){event.getDrops().clear();event.setDroppedExp(0);}
            // Stolen items are returned through the platform once, never duplicated in death drops.
            event.getDrops().removeIf(item->mob.stolenItems().stream().anyMatch(item::isSimilar));
            e.fire(Trigger.ON_DEATH,mob,event);
        }
        if(event.getEntity().equals(e.principal)) {
            boolean pays=rewardDeath(event.getEntity().getLastDamageCause(),e.lethalDamage)
                    &&!event.getDamageSource().getDamageType().getKey().getKey().equals("generic_kill");
            try {e.reward(pays);}finally{e.close();}
        } else if(mob!=null)e.removeMob(mob);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void target(EntityTargetLivingEntityEvent event) {
        var e=owner(event.getEntity());if(e!=null&&event.getTarget()!=null&&(!(event.getTarget() instanceof Player p)||!e.participant(p)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void chunkUnloaded(ChunkUnloadEvent event) {
        for(var e:List.copyOf(encounters.values()))if(e.entities.stream().anyMatch(entity->{
            var at=entity.getLocation();return at.getWorld().equals(event.getWorld())&&(at.getBlockX()>>4)==event.getChunk().getX()&&(at.getBlockZ()>>4)==event.getChunk().getZ();
        }))e.close();
    }
    @EventHandler public void unloaded(EntitiesUnloadEvent event) {
        for(Entity entity:event.getEntities()){var e=owner(entity);if(e!=null)e.close();}
    }
    @EventHandler public void loaded(EntitiesLoadEvent event) {
        if(event.getChunk()!=null)journal.recoverLoadedChunk(event.getWorld(),event.getChunk().getX(),event.getChunk().getZ());
        for(Entity entity:event.getEntities())if(marked(entity)){var e=owner(entity);if(e==null)MobHealth.terminate(entity,false);}
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event){journal.recover(event.getWorld());}
    @EventHandler(ignoreCancelled=true) public void worldUnloaded(WorldUnloadEvent event){for(var e:List.copyOf(encounters.values()))if(e.origin.getWorld().equals(event.getWorld()))e.close();}
    @EventHandler public void removed(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent event) {
        var e=owner(event.getEntity());if(e==null||e.closed)return;
        if(event.getEntity().equals(e.principal))e.close();else {var mob=e.active.get(event.getEntity().getUniqueId());if(mob!=null)e.removeMob(mob);}
    }
    @EventHandler(ignoreCancelled=true) public void transform(EntityTransformEvent event){var e=owner(event.getEntity());if(e!=null)event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void split(SlimeSplitEvent event){if(owner(event.getEntity())!=null)event.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent event){for(var e:List.copyOf(encounters.values()))e.returnStolenTo(event.getPlayer());}
    // Projectile terrain/fire protection lives in the shared AbilityProtectionListener.
    @EventHandler(priority=EventPriority.HIGHEST) public void explode(EntityExplodeEvent event){if(!(event.getEntity() instanceof Projectile)&&owner(event.getEntity())!=null)event.blockList().clear();}
    @EventHandler(priority=EventPriority.HIGHEST) public void prime(ExplosionPrimeEvent event){if(!(event.getEntity() instanceof Projectile)&&owner(event.getEntity())!=null)event.setFire(false);}
    @EventHandler(priority=EventPriority.HIGHEST) public void splash(PotionSplashEvent event){var e=owner(event.getPotion());if(e!=null)for(var target:List.copyOf(event.getAffectedEntities()))if(!(target instanceof Player p)||!e.participant(p))event.setIntensity(target,0);}
    @EventHandler(priority=EventPriority.HIGHEST) public void cloud(AreaEffectCloudApplyEvent event){var e=owner(event.getEntity());if(e!=null)event.getAffectedEntities().removeIf(p->!(p instanceof Player player)||!e.participant(player));}
    @EventHandler(priority=EventPriority.HIGHEST) public void ignite(org.bukkit.event.block.BlockIgniteEvent event){if(event.getIgnitingEntity()!=null&&!(event.getIgnitingEntity() instanceof Projectile)&&owner(event.getIgnitingEntity())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void blockChange(EntityChangeBlockEvent event){if(!(event.getEntity() instanceof Projectile)&&owner(event.getEntity())!=null)event.setCancelled(true);}
    @Override public void close(){if(closed)return;closed=true;retireAll();journal.close();}
}
