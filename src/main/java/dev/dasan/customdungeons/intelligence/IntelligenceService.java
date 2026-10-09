package dev.dasan.customdungeons.intelligence;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.Trigger;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

/** One listener, no tasks. Hosts drive tick through AbilityEngine. All ownership is explicit. */
public final class IntelligenceService implements Listener,AutoCloseable {
    private static IntelligenceService current;
    private final MobsPlatform platform;
    private final IntelligenceRules rules;
    private final Map<UUID,State> mobs=new HashMap<>();
    private final Map<UUID,Set<State>> players=new HashMap<>();
    private final Cooldowns cooldowns=new Cooldowns();
    /** Compatibility entry points; landing state lives in ability, shared with T57a. */
    public void fallTicker(Runnable changed) {FallProtection.shared().ticker(changed);}
    public static boolean hasProtectedFalls() {return FallProtection.shared().hasPending();}
    public static void tickProtectedFalls() {FallProtection.shared().tick();}
    private void protectFall(Player player) {FallProtection.shared().protect(player);}
    private void finishFall(Player player) {FallProtection.shared().finish(player);}
    private static final class State {
        final ActiveMob mob;final IntelligenceBrain brain;
        final Set<UUID> indexed=new HashSet<>();
        final Set<IntelligenceBrain.Adaptation> applied=new HashSet<>();
        long triggerTick=Long.MIN_VALUE;
        State(ActiveMob mob,IntelligenceDef def,IntelligenceRules rules) {this.mob=mob;brain=new IntelligenceBrain(def,new EncounterMemory(),rules);}
    }
    public IntelligenceService(MobsPlatform platform,IntelligenceRules rules) {this.platform=platform;this.rules=rules;current=this;}
    public static IntelligenceBrain brain(ActiveMob mob) {var s=state(mob);return s==null?null:s.brain;}
    private static State state(ActiveMob mob) {return current==null?null:current.mobs.get(mob.entity().getUniqueId());}
    public static void track(ActiveMob mob) {
        if(current!=null&&!CombatService.isDecoy(mob.entity())&&mob.template().intelligence().level()>0)current.mobs.put(mob.entity().getUniqueId(),new State(mob,mob.template().intelligence(),current.rules));
    }
    public static void phase(ActiveMob mob,IntelligenceDef phase,long tick) {
        if(current==null||CombatService.isDecoy(mob.entity()))return;
        var s=state(mob);var old=s==null?effective(mob):s.brain.definition();var next=old.phase(phase);
        if(s==null&&next.level()>0) {s=new State(mob,next,current.rules);current.mobs.put(mob.entity().getUniqueId(),s);}
        if(s!=null){s.brain.changeLevel(next,tick);current.retract(s,true);if(next.level()==0)for(var id:List.copyOf(s.indexed))current.unindex(s,id);}
    }
    private static IntelligenceDef effective(ActiveMob mob) {
        var d=mob.template().intelligence();for(int i=0;i<mob.phaseIndex();i++)d=d.phase(mob.template().phases().get(i).intelligence());return d;
    }
    public static void tick(ActiveMob mob,AbilityEngine engine,long tick) {
        var s=state(mob);if(s==null)return;
        if(!mob.entity().isValid()||mob.entity().isDead()){current.remove(s);return;}
        if(s.brain.definition().level()==0)return;
        s.brain.clock(tick);
        current.retract(s,false);
        for(var a:s.brain.active())if(tick>=a.begins()&&tick<a.expires()&&s.applied.add(a))current.apply(s,a);
        if(Math.floorMod(tick,10)!=Math.floorMod(mob.entity().getUniqueId().hashCode(),10))return;
        current.index(s);
        for(var a:s.brain.evaluate(tick,s.indexed))current.announce(s,"intelligence.notice."+a.rule().id());
        current.retract(s,false);
        for(var a:s.brain.active())if(tick>=a.begins()&&s.applied.add(a))current.apply(s,a);
        current.triggers(s,engine,tick);
    }
    private void index(State s) {
        Set<UUID> eligible=new HashSet<>();
        if(s.brain.definition().level()>0) for(var p:s.mob.session().players())if(eligible(s,p)&&eligible.size()<16)eligible.add(p.getUniqueId());
        for(var id:List.copyOf(s.indexed))if(!eligible.contains(id)) {
            unindex(s,id);s.brain.withdraw(id);retract(s,false);
        }
        s.brain.memory().reserve(eligible);
        for(var id:eligible)if(s.indexed.add(id))players.computeIfAbsent(id,k->new HashSet<>()).add(s);
    }
    private boolean eligible(State s,Player p) {
        return p.isOnline()&&!p.isDead()&&p.isValid()&&p.getGameMode()!=GameMode.CREATIVE&&p.getGameMode()!=GameMode.SPECTATOR
            &&s.mob.session().players().contains(p)&&p.getWorld().equals(s.mob.entity().getWorld())&&p.getLocation().distanceSquared(s.mob.entity().getLocation())<=32*32;
    }
    private void unindex(State s,UUID id) {s.indexed.remove(id);var set=players.get(id);if(set!=null){set.remove(s);if(set.isEmpty())players.remove(id);}}
    private void announce(State s,String key) {
        var at=s.mob.entity().getLocation();var limits=platform.limits();
        for(var p:EffectAudience.viewers(s.mob.session(),at,limits.effectViewRadius())) {
            p.sendActionBar(platform.messages().get(key));p.playSound(at,"minecraft:block.amethyst_block.chime",SoundCategory.HOSTILE,.6f,.7f);
            int count=Math.clamp((int)(8*limits.particleDensity()),0,16);if(count>0)p.spawnParticle(Particle.ENCHANT,at.clone().add(0,1,0),count,.4,.5,.4,0);
        }
    }
    private void apply(State s,IntelligenceBrain.Adaptation a) {
        var p=Bukkit.getPlayer(a.player());if(p==null||!eligible(s,p))return;
        int ticks=switch(a.rule().response()) {case SHIELD->80;default->160;};
        switch(a.rule().response()) {
            case ITEM_COOLDOWN -> cooldowns.add(p,a,cooldownItem(a),ticks);
            case SHIELD -> cooldowns.add(p,a,Material.SHIELD,ticks);
            case GROUND -> { protectFall(p);p.setGliding(false);p.setFallDistance(0);p.setVelocity(p.getVelocity().setY(-.2));
                // The guard survives withdrawal of this adaptation and host.
                cooldowns.add(p,a,Material.FIREWORK_ROCKET,160);
            }
            case PUSH -> {protectFall(p);var away=p.getLocation().toVector().subtract(s.mob.entity().getLocation().toVector());if(away.lengthSquared()>0)p.setVelocity(away.normalize().multiply(.6).setY(.25));}
            case ENRAGE -> {var speed=s.mob.entity().getAttribute(Attribute.MOVEMENT_SPEED);if(speed!=null)speed.addTransientModifier(new AttributeModifier(speedKey(s,a),.15,AttributeModifier.Operation.ADD_SCALAR));}
            default -> { }
        }
    }
    private static Material cooldownItem(IntelligenceBrain.Adaptation a) {var item=Material.matchMaterial(a.damageType());return item==null?(a.rule().id().equals("pearl")?Material.ENDER_PEARL:Material.GOLDEN_APPLE):item;}
    private static NamespacedKey speedKey(State s,IntelligenceBrain.Adaptation a) {return MobsPlatform.key("intelligence_"+s.mob.entity().getUniqueId()+"_"+a.rule().id());}
    private void retract(State s,boolean notice) {
        for(var a:s.brain.drainRemoved()) {
            cooldowns.remove(a);
            if(s.applied.remove(a)&&a.rule().response()==IntelligenceRules.Response.ENRAGE) {var speed=s.mob.entity().getAttribute(Attribute.MOVEMENT_SPEED);if(speed!=null)speed.getModifiers().stream().filter(m->m.getKey().equals(speedKey(s,a))).toList().forEach(speed::removeModifier);}
            if(notice)announce(s,"intelligence.notice.release");
        }
    }
    /** Sole withdrawal path for mob, host, reload, death and disconnect. */
    private void cleanupEffects(State s,UUID player) {
        if(player==null)s.brain.clear();else s.brain.forget(player);
        retract(s,false);
        for(var id:List.copyOf(s.indexed))if(player==null||player.equals(id))unindex(s,id);
    }
    private void remove(State s) {cleanupEffects(s,null);mobs.remove(s.mob.entity().getUniqueId());}
    /** Reload also owns live-test effects, even if their inventories are closed. */
    public static void reset() {if(current!=null)current.clearEffects();}
    private void clearEffects() {
        for(var s:List.copyOf(mobs.values()))remove(s);
        players.clear();cooldowns.clear();
        // Fall safety deliberately outlives the removed mobs.
    }
    public static void cleanup(ActiveMob mob) {var s=state(mob);if(s!=null)current.remove(s);}
    @Override public void close() {
        clearEffects();
        FallProtection.shared().close();
        if(current==this)current=null;
    }
    /** Every memory write enters here, including shared player actions and indirect shield hits. */
    private EncounterMemory memoryFor(State s,Player p,Entity source,Entity target) {
        return CombatService.isDecoy(s.mob.entity())||CombatService.isDecoy(p)
                ||CombatService.isDecoySource(source)||CombatService.isDecoy(target)||!eligible(s,p)?null:s.brain.memory();
    }
    private void observe(Player p,String pattern,String type,double amount) {observe(p,p,pattern,type,amount);}
    private void observe(Player p,Entity source,String pattern,String type,double amount) {
        if(players.isEmpty())return;var set=players.get(p.getUniqueId());if(set==null)return;
        for(var s:List.copyOf(set)) {
            var memory=memoryFor(s,p,source,p);if(memory==null)continue;
            long tick=s.mob.session().scheduler().currentTick();memory.record(p.getUniqueId(),pattern,type,amount,tick);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void consume(PlayerItemConsumeEvent e) {
        if(players.isEmpty())return;Material item=e.getItem().getType();if(item!=Material.GOLDEN_APPLE&&item!=Material.ENCHANTED_GOLDEN_APPLE&&item!=Material.POTION)return;
        observe(e.getPlayer(),"consumables",item.name(),0);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void heal(EntityRegainHealthEvent e) {if(!players.isEmpty()&&e.getEntity() instanceof Player p)observe(p,"heal","",0);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void resurrect(EntityResurrectEvent e) {if(!players.isEmpty()&&e.getEntity() instanceof Player p)observe(p,"totem","",0);}
    // Air clicks may deny the block interaction while allowing the rocket item.
    @EventHandler(priority=EventPriority.MONITOR) public void interact(PlayerInteractEvent e) {
        if(players.isEmpty()||e.getItem()==null||!e.getAction().isRightClick()||e.useItemInHand()==Event.Result.DENY)return;
        Material item=e.getItem().getType();if(item==Material.FIREWORK_ROCKET&&e.getPlayer().isGliding())observe(e.getPlayer(),"flight",item.name(),0);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void launch(ProjectileLaunchEvent e) {
        if(players.isEmpty())return;if(e.getEntity() instanceof EnderPearl pearl&&pearl.getShooter() instanceof Player p)observe(p,"pearl",Material.ENDER_PEARL.name(),0);
        if(e.getEntity() instanceof ThrownPotion potion&&potion.getShooter() instanceof Player p){observe(p,"consumables",potion.getItem().getType().name(),0);}
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void glide(EntityToggleGlideEvent e) {if(!players.isEmpty()&&e.isGliding()&&e.getEntity() instanceof Player p)observe(p,"flight","ELYTRA",0);}
    @EventHandler public void quit(PlayerQuitEvent e) {forget(e.getPlayer());finishFall(e.getPlayer());}
    @EventHandler public void playerDeath(PlayerDeathEvent e) {forget(e.getEntity());}
    private void forget(Player p) {for(var s:List.copyOf(mobs.values()))cleanupEffects(s,p.getUniqueId());cooldowns.clear(p.getUniqueId());}
    @EventHandler public void death(EntityDeathEvent e) {var s=mobs.get(e.getEntity().getUniqueId());if(s!=null)remove(s);}
    @EventHandler public void removed(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent e) {var s=mobs.get(e.getEntity().getUniqueId());if(s!=null)remove(s);}
    @EventHandler public void worldChanged(PlayerChangedWorldEvent e) {forget(e.getPlayer());}
    // Kept for integrations/tests; the plugin registers FallProtection as the listener.
    public void fall(EntityDamageEvent e) {FallProtection.shared().fall(e);}
    public void move(PlayerMoveEvent e) {FallProtection.shared().move(e);}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e) {
        if(mobs.isEmpty()||CombatService.isDecoy(e.getEntity())||CombatService.isDecoySource(e.getDamager()))return;var s=mobs.get(e.getEntity().getUniqueId());
        Player p=e.getDamager() instanceof Player player?player:e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player player?player:null;
        if(s!=null&&s.brain.definition().level()>0&&p!=null&&eligible(s,p)) {
            boolean weak=weak(s,e.getDamager(),p);boolean critical=e.isCritical();
            s.brain.clock(s.mob.session().scheduler().currentTick());
            e.setDamage(e.getDamage()*s.brain.damageMultiplier(weak,e.getCause().name(),critical));
        }
        if(e.getEntity() instanceof Player target) {
            State source=mobs.get(e.getDamager().getUniqueId());
            if(source==null&&e.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Entity owner) {
                var shooter=mobs.get(owner.getUniqueId());
                if(shooter!=null&&shooter.mob.session().id().toString().equals(shot.getPersistentDataContainer().get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)))source=shooter;
            }
            if(source!=null&&source.brain.definition().level()>0) {
                if(!eligible(source,target)){e.setCancelled(true);return;}
                State attacker=source;
                if(attacker.brain.active().stream().anyMatch(a->a.rule().response()==IntelligenceRules.Response.ENRAGE&&a.begins()<=attacker.mob.session().scheduler().currentTick()))e.setDamage(e.getDamage()*1.15);
                double max=target.getAttribute(Attribute.MAX_HEALTH)==null?20:target.getAttribute(Attribute.MAX_HEALTH).getValue();
                e.setDamage(FairCombat.nonLethalBase(e.getDamage(),e.getFinalDamage(),max));
            }
        }
    }
    static boolean shieldBlocked(EntityDamageByEntityEvent event) {
        return event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)
            && event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)<0;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void shield(EntityDamageByEntityEvent event) {
        if(players.isEmpty()||!(event.getEntity() instanceof Player player)||!shieldBlocked(event))return;
        observe(player,event.getDamager(),"shield","",0);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void applied(MobDamageAppliedEvent e) {
        var s=mobs.get(e.entity().getUniqueId());if(s==null||CombatService.isDecoy(e.entity())||s.brain.definition().level()==0||e.amount()<=0)return;
        s.brain.damageDuringWarning(e.amount(),MobHealth.maximum(s.mob.entity()));
        if(e.damage() instanceof EntityDamageByEntityEvent hit) {
            Player p=hit.getDamager() instanceof Player player?player:hit.getDamager() instanceof Projectile shot&&shot.getShooter() instanceof Player player?player:null;
            if(p==null)return;var memory=memoryFor(s,p,hit.getDamager(),e.entity());if(memory==null)return;
            boolean ranged=hit.getDamager() instanceof Projectile;boolean critical=hit.isCritical();
            Material weapon=hit.getDamager() instanceof AbstractArrow arrow&&arrow.getWeapon()!=null?arrow.getWeapon().getType():p.getInventory().getItemInMainHand().getType();
            String pattern=!ranged&&weapon==Material.MACE?"mace":critical?"critical":"damage";
            boolean back=backHit(s,hit.getDamager(),p);boolean weak=weak(s,hit.getDamager(),p);
            memory.record(p.getUniqueId(),pattern,hit.getCause().name(),e.amount(),s.mob.session().scheduler().currentTick(),back,ranged,weak,weapon.name());
        }
    }
    public static boolean behind(Mob mob,Player p) {var direction=mob.getLocation().getDirection().setY(0);var to=p.getLocation().toVector().subtract(mob.getLocation().toVector()).setY(0);return to.lengthSquared()>0&&direction.dot(to.normalize())<-.5;}
    private static boolean backHit(State s,Entity source,Player p) {
        if(source instanceof Projectile shot&&shot.getVelocity().lengthSquared()>0)return s.mob.entity().getLocation().getDirection().dot(shot.getVelocity().normalize())>.5;
        return behind(s.mob.entity(),p);
    }
    private boolean weak(State s,Entity source,Player p) {
        return switch(s.brain.definition().weakPoint()) {
            case NONE->false;case BACK->backHit(s,source,p);
            case HEAD->source instanceof Projectile?source.getLocation().getY()>=s.mob.entity().getEyeLocation().getY()-.25:p.getEyeLocation().getDirection().dot(s.mob.entity().getEyeLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize())>.97;
        };
    }
    private void triggers(State s,AbilityEngine engine,long tick) {
        int level=s.brain.definition().level();if(level<1)return;
        var target=TargetSelector.select(s.mob,dev.dasan.customdungeons.model.TargetMode.MOST_THREAT,32);
        if(!target.isEmpty()&&s.brain.memory().threat(target.getFirst().getUniqueId(),tick)>0)s.mob.entity().setTarget(target.getFirst());
        if(s.indexed.size()>=2)engine.fire(Trigger.SURROUNDED,s.mob,null,tick);
        if(level<2)return;
        int window=s.brain.definition().value("window")*20;
        var found=EnumSet.noneOf(Trigger.class);
        for(UUID id:s.indexed) {
            var p=Bukkit.getPlayer(id);if(p==null)continue;
            found.add(Trigger.PLAYER_NEAR_DEATH);
            var events=s.brain.memory().observations(id,tick);
            for(var e:events)if(e.tick()>s.triggerTick&&tick-e.tick()<=10) {if(e.behind())found.add(Trigger.ATTACKED_FROM_BEHIND);if(e.ranged())found.add(Trigger.RANGED_ATTACK);if(e.pattern().equals("heal")||e.pattern().equals("consumables"))found.add(Trigger.PLAYER_HEALED);}
            double burst=events.stream().filter(e->tick-e.tick()<=window).mapToDouble(EncounterMemory.Observation::damage).sum();if(burst>0)found.add(Trigger.DAMAGE_BURST);
            for(var rule:rules.all())if(s.brain.memory().repetitions(id,rule.pattern(),tick,s.brain.definition().value("window")*20)>=Math.max(2,s.brain.definition().value("repetitions")))found.add(Trigger.STRATEGY_DETECTED);
            if(level>=3&&events.stream().filter(EncounterMemory.Observation::weak).count()>=s.brain.definition().value("repetitions")) {s.mob.entity().setTarget(p);s.mob.entity().lookAt(p);found.add(Trigger.ATTACKED_FROM_BEHIND);}
        }
        for(var trigger:found)engine.fire(trigger,s.mob,null,tick);s.triggerTick=tick;
    }
    /** Layered item cooldown ownership: restore prior remaining time, preserve outside changes. */
    static final class Cooldowns {
        private record Key(UUID player,Material item) {}
        private static final class Entry {Player player;long priorUntil,installedUntil;final Map<Object,Long> owners=new IdentityHashMap<>();}
        private final Map<Key,Entry> entries=new HashMap<>();
        void add(Player p,Object owner,Material item,int ticks) {
            long now=Bukkit.getCurrentTick();var key=new Key(p.getUniqueId(),item);var entry=entries.computeIfAbsent(key,k->{var e=new Entry();e.player=p;e.priorUntil=now+p.getCooldown(item);return e;});
            if(entry.installedUntil>0&&Math.abs(now+p.getCooldown(item)-Math.max(now,entry.installedUntil))>1)entry.priorUntil=Math.max(entry.priorUntil,now+p.getCooldown(item));
            entry.owners.put(owner,now+ticks);entry.installedUntil=Math.max(entry.priorUntil,entry.owners.values().stream().mapToLong(Long::longValue).max().orElse(now));p.setCooldown(item,(int)Math.max(0,entry.installedUntil-now));
        }
        void clear() {clear(null);}
        void clear(UUID player) {
            if(entries.isEmpty())return;
            long now=Bukkit.getCurrentTick();
            for(var it=entries.entrySet().iterator();it.hasNext();) {
                var pair=it.next();if(player!=null&&!pair.getKey().player().equals(player))continue;
                var e=pair.getValue();
                if(Math.abs(now+e.player.getCooldown(pair.getKey().item())-Math.max(now,e.installedUntil))<=1)
                    e.player.setCooldown(pair.getKey().item(),(int)Math.max(0,e.priorUntil-now));
                it.remove();
            }
        }
        void remove(Object owner) {
            if(entries.isEmpty())return;
            long now=Bukkit.getCurrentTick();
            for(var it=entries.entrySet().iterator();it.hasNext();) {var pair=it.next();var e=pair.getValue();if(e.owners.remove(owner)==null)continue;
                long until=Math.max(e.priorUntil,e.owners.values().stream().mapToLong(Long::longValue).max().orElse(now));
                if(Math.abs((now+e.player.getCooldown(pair.getKey().item()))-Math.max(now,e.installedUntil))<=1)e.player.setCooldown(pair.getKey().item(),(int)Math.max(0,until-now));
                e.installedUntil=until;if(e.owners.isEmpty())it.remove();
            }
        }
    }
}
