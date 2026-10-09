package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.RewardDef;
import dev.dasan.customdungeons.model.Trigger;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/** A world boss and its owned summons; driven by the manager's one shared ticker. */
public final class WorldEncounter implements MobHost, AutoCloseable {
    final WorldBossService service;
    final UUID id=UUID.randomUUID();
    final MobTemplate template;
    final Location origin;
    final Map<UUID,ActiveMob> active=new LinkedHashMap<>();
    final Set<Entity> entities=new HashSet<>();
    final Map<UUID,Player> contributors=new HashMap<>();
    final LiveTestService.Clock clock=new LiveTestService.Clock();
    final AbilityEngine engine;
    final BossController bosses;
    final Blocks blocks=new Blocks();
    private record Stolen(Player owner,ItemStack item) {}
    private final Map<ActiveMob,List<Stolen>> stolen=new IdentityHashMap<>();
    Mob principal;
    DamageLedger damage;
    org.bukkit.event.entity.EntityDamageEvent lethalDamage;
    boolean closed;
    private long nearbyTick=Long.MIN_VALUE;
    private List<Player> nearby=List.of();
    WorldEncounter(WorldBossService service,MobTemplate template,Location at) {
        this.service=service;this.template=template;origin=at.clone();
        engine=new AbilityEngine(service.platform.abilityRegistry(),service.platform);
        bosses=new BossController(service.factory,service.platform.templates());
    }
    void start() {
        ActiveMob mob=spawn(template,origin);principal=mob.entity();
        damage=new DamageLedger(MobHealth.maximum(principal),template.worldBoss().minimumDamage());
        bosses.barFor(mob);bosses.startMusic(mob);fire(Trigger.ON_SPAWN,mob,null);
    }
    private ActiveMob spawn(MobTemplate m,Location at) {
        if(at.getWorld()==null||!at.getWorld().isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))return null;
        ActiveMob mob=service.factory.spawn(m,at,this,1,entity->entity.getPersistentDataContainer().set(WorldBossService.MARKER,PersistentDataType.STRING,id.toString()));
        track(mob.entity());active.put(mob.entity().getUniqueId(),mob);return mob;
    }
    boolean track(Entity entity) {
        if(!id.toString().equals(entity.getPersistentDataContainer().get(WorldBossService.MARKER,PersistentDataType.STRING)))return false;
        if(closed || entity instanceof Mob&&!active.containsKey(entity.getUniqueId())&&active.size()>=service.platform.limits().maxAliveMobsPerSession()) {
            MobHealth.terminate(entity,false);return false;
        }
        entity.getPersistentDataContainer().set(WorldBossService.MARKER,PersistentDataType.STRING,id.toString());
        entity.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,id.toString());
        entity.setPersistent(false);entities.add(entity);service.owners.put(entity.getUniqueId(),this);
        if(entity instanceof Mob mob&&!active.containsKey(entity.getUniqueId())) {
            var vanilla=new MobTemplate("__world_minion",mob.getType().name(),"",0,0,0,0,0,
                    Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
            active.put(entity.getUniqueId(),new ActiveMob(mob,vanilla,this));
        }
        if(entity instanceof Explosive explosive) {
            explosive.setIsIncendiary(false);
            if(!(entity instanceof Projectile))explosive.setYield(0);
        }
        return true;
    }
    void tick() {
        if(closed)return;
        if(principal==null||!principal.isValid()||principal.isDead()) {close();return;}
        clock.advance();
            for(ActiveMob mob:List.copyOf(active.values())) {
                if(!mob.entity().isValid()||mob.entity().isDead()) {removeMob(mob);continue;}
                if(mob.entity().getTarget()!=null&&(!(mob.entity().getTarget() instanceof Player p)||!participant(p)))mob.entity().setTarget(null);
                if(clock.currentTick()%20==0&&!inZone(mob.entity().getLocation())) {
                    if(origin.getWorld().isChunkLoaded(origin.getBlockX()>>4,origin.getBlockZ()>>4))mob.entity().teleport(origin);
                    else {close();return;} // Never synchronously load the leash destination.
                }
                bosses.onDamaged(mob,clock.currentTick());
            }
            engine.tick(active.values(),clock.currentTick());bosses.tickMusic(clock.currentTick());
            entities.removeIf(e->{if(e.isValid())return false;service.owners.remove(e.getUniqueId(),this);return true;});
    }
    void fire(Trigger trigger,ActiveMob mob,Event event) {
        engine.fire(trigger,mob,event,clock.currentTick());
    }
    boolean inZone(Location at) {
        var b=template.worldBoss();return at.getWorld()!=null&&inZone(at.getWorld().getName(),at.getX(),at.getZ(),b.world(),b.xMin(),b.xMax(),b.zMin(),b.zMax());
    }
    static boolean inZone(String world,double x,double z,String expected,int xmin,int xmax,int zmin,int zmax) {
        return expected.equals(world)&&x>=xmin&&x<xmax&&z>=zmin&&z<zmax;
    }
    boolean eligible(Player p,Location at) {
        return !closed&&p.isOnline()&&p.isValid()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE)
                &&p.getWorld().equals(at.getWorld())&&p.getLocation().distanceSquared(at)<=(double)template.worldBoss().radius()*template.worldBoss().radius()
                &&service.available.test(p);
    }
    boolean participant(Player p) {return eligible(p,principal==null?origin:principal.getLocation());}
    void reward(boolean byDamage) {
        Set<UUID> eligible=damage.eligible(byDamage);
        for(var entry:contributors.entrySet()) {
            if(eligible.contains(entry.getKey())) {
                service.platform.deliverReward(entry.getValue(),template.worldBoss().reward());
                service.tell(entry.getValue(),"reward-received",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("boss",template.id()));
            } else if(byDamage)service.tell(entry.getValue(),"reward-not-eligible",
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("damage",Double.toString(damage.percent(entry.getKey()))),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("minimum",Double.toString(template.worldBoss().minimumDamage())));
        }
    }
    void removeMob(ActiveMob mob) {
        bosses.cleanup(mob);returnStolen(mob);active.remove(mob.entity().getUniqueId());
    }
    private void returnStolen(ActiveMob mob) {
        var items=stolen.remove(mob);if(items!=null)for(var item:items)returnStolenItem(item);
        mob.stolenItems().clear();
    }
    private void returnStolenItem(Stolen item) {
        if(ThiefReturns.dropIfBuilding(item.owner(),item.item()))return;
        service.platform.deliverReward(item.owner(),new RewardDef(List.of(item.item()),0,0,List.of()));
    }
    void returnStolenTo(Player player) {
        for(var entry:stolen.entrySet())entry.getValue().removeIf(item->{
            if(!item.owner().equals(player))return false;
            returnStolenItem(item);
            entry.getKey().stolenItems().remove(item.item());return true;
        });
    }
    @Override public void close() {
        if(closed)return;closed=true;clock.clear();
        for(ActiveMob mob:List.copyOf(active.values()))removeMob(mob);
        for(Entity entity:List.copyOf(entities)) {service.owners.remove(entity.getUniqueId(),this);if(entity.isValid())MobHealth.terminate(entity,false);}
        entities.clear();nearby=List.of();blocks.restoreAll();service.retired(this);
    }
    @Override public UUID id() {return id;}
    private List<Player> nearbyPlayers() {
        if(closed||principal==null)return List.of();
        if(nearbyTick!=clock.currentTick()) {
            nearbyTick=clock.currentTick();
            Location center=principal.getLocation();
            nearby=center.getWorld().getNearbyPlayers(center,template.worldBoss().radius()).stream().filter(p->eligible(p,center)).toList();
        }
        return nearby;
    }
    @Override public Collection<Player> players() {return nearbyPlayers();}
    @Override public Collection<Player> audience(Location at) {return at.getWorld()==null?List.of():nearbyPlayers().stream().filter(p->eligible(p,at)).toList();}
    @Override public MobArea area() {
        var b=template.worldBoss();return new MobArea(origin.getWorld().getName(),new BoundingBox(b.xMin(),origin.getWorld().getMinHeight(),b.zMin(),b.xMax(),origin.getWorld().getMaxHeight(),b.zMax()));
    }
    @Override public Collection<ActiveMob> mobs() {return List.copyOf(active.values());}
    @Override public ActiveMob spawnMinion(String id,Location at,ActiveMob owner) {
        var m=service.platform.templates().get(id);
        if(closed||m==null||active.size()>=service.platform.limits().maxAliveMobsPerSession())return null;
        var mob=spawn(m,at);if(mob!=null)fire(Trigger.ON_SPAWN,mob,null);return mob;
    }
    @Override public TempBlocks tempBlocks() {return blocks;}
    @Override public TickScheduler scheduler() {return clock;}
    @Override public void onItemStolen(UUID owner,ItemStack item,ActiveMob thief) {
        Player p=Bukkit.getPlayer(owner);if(p==null)throw new IllegalArgumentException("Unknown item owner");
        stolen.computeIfAbsent(thief,k->new ArrayList<>()).add(new Stolen(p,item.clone()));
    }
    private final class Blocks implements dev.dasan.customdungeons.runtime.RestorableTempBlocks {
        private record Placed(BlockData original,BlockData expected) {}
        private final Map<Block,Placed> originals=new HashMap<>();
        @Override public boolean place(Block b,BlockData data,int ttlTicks) {
            if(closed||!b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4)||!b.isEmpty()||service.journal.reserved(b))return false;
            var original=b.getBlockData().clone();var expected=data.clone();var lease=new Placed(original,expected);originals.put(b,lease);
            var durable=service.journal.add(b,original,expected);
            clock.runLater(1,new Runnable(){public void run(){
                if(closed||originals.get(b)!=lease)return;
                if(!durable.isDone()){clock.runLater(1,this);return;}
                if(durable.isCompletedExceptionally()||!b.isEmpty()){restore(b);return;}
                b.setBlockData(expected,false);clock.runLater(Math.max(1,ttlTicks),()->{if(originals.get(b)==lease)restore(b);});
            }});return true;
        }
        public void restore(Block b) {
            var saved=originals.remove(b);if(saved==null)return;
            if(!b.getWorld().isChunkLoaded(b.getX()>>4,b.getZ()>>4))return;
            dev.dasan.customdungeons.runtime.BlockRestoration.restore(b,saved.original(),saved.expected().getAsString());
            service.journal.remove(b);
        }
        @Override public void restoreAll(){for(Block b:List.copyOf(originals.keySet()))restore(b);}
    }
}
