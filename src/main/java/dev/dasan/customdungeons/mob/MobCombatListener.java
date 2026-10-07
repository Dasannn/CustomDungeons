package dev.dasan.customdungeons.mob;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** Preserve Paper's damage bookkeeping; virtual HP owns survival and the native HP mirror. */
public final class MobCombatListener implements Listener {
    private static final Map<Entity,Integer> ABILITY_DAMAGE = new IdentityHashMap<>();
    private final Set<LivingEntity> pendingMirrors=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Consumer<Runnable> afterEvents;
    private boolean mirrorQueued;

    public MobCombatListener(Plugin plugin) {
        this(task -> plugin.getServer().getScheduler().runTask(plugin,task));
    }
    MobCombatListener(Consumer<Runnable> afterEvents) { this.afterEvents=afterEvents; }

    public static void abilityDamage(LivingEntity target,double amount,Entity source) {
        ABILITY_DAMAGE.merge(source,1,Integer::sum);
        try { target.damage(amount,source); }
        finally { if(ABILITY_DAMAGE.get(source)==1) ABILITY_DAMAGE.remove(source); else ABILITY_DAMAGE.computeIfPresent(source,(s,n)->n-1); }
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void melee(EntityDamageByEntityEvent event) {
        if((event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                || !MobKeys.isDungeonMob(event.getDamager()) || ABILITY_DAMAGE.containsKey(event.getDamager()))return;
        Double damage=event.getDamager().getPersistentDataContainer().get(MobKeys.VIRTUAL_ATTACK_DAMAGE,PersistentDataType.DOUBLE);
        if(damage!=null) event.setDamage(damage);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damaged(EntityDamageEvent event) {
        if(event.isCancelled() || !(event.getEntity() instanceof LivingEntity entity) || !MobHealth.virtual(entity))return;
        double remaining=MobHealth.remainingAfterDamage(entity,event);
        MobHealth.remember(entity,remaining);
        float physical=(float)entity.getHealth();
        if(remaining>0 && physical-(float)event.getFinalDamage()<=0) {
            // Prevent die()/dropCustomDeathLoot entirely. Only this boundary hit
            // changes BASE/lastHurt; armor, absorption and i-frame modifiers stay
            // in their original units, and virtual HP already used the original final.
            float floor=Math.min(physical,MobHealth.physicalFloor(remaining));
            float allowed=Math.max(0,physical-floor);
            if(physical-allowed<floor)allowed=Math.nextDown(allowed);
            double modifiers=0;
            for(var modifier:EntityDamageEvent.DamageModifier.values())
                if(modifier!=EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier))
                    modifiers+=event.getDamage(modifier);
            event.setDamage(EntityDamageEvent.DamageModifier.BASE,allowed-modifiers);
            // At extreme magnitudes, cancellation may make the bounded result
            // unrepresentable. A negative final would create native absorption.
            // In that case apply zero physical damage, preserving vanilla's
            // original absorption consumption. Virtual HP already used all defenses.
            if(event.getFinalDamage()<0 || physical-(float)event.getFinalDamage()<floor) {
                double absorption=event.getDamage(EntityDamageEvent.DamageModifier.ABSORPTION);
                for(var modifier:EntityDamageEvent.DamageModifier.values())
                    if(modifier!=EntityDamageEvent.DamageModifier.BASE
                            && modifier!=EntityDamageEvent.DamageModifier.ABSORPTION && event.isApplicable(modifier))
                        event.setDamage(modifier,0);
                event.setDamage(EntityDamageEvent.DamageModifier.BASE,-absorption);
            }
        }
        // Arrange native death in this same hit, with its original damage source/credit.
        if(remaining==0 && event.getFinalDamage()>0)
            entity.setHealth(Math.min(entity.getHealth(),(double)(float)event.getFinalDamage()));
        reconcileAfterEvent(entity);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void healed(EntityRegainHealthEvent event) {
        if(event.isCancelled() || !(event.getEntity() instanceof LivingEntity entity) || !MobHealth.virtual(entity))return;
        MobHealth.remember(entity,MobHealth.current(entity)+Math.max(0,event.getAmount()));
        reconcileAfterEvent(entity);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void resurrect(EntityResurrectEvent event) {
        // Zero authoritative HP is terminal, including when a totem is equipped.
        if(MobHealth.virtual(event.getEntity()))event.setCancelled(true);
    }
    private void reconcileAfterEvent(LivingEntity entity) {
        pendingMirrors.add(entity);
        if(mirrorQueued)return;
        mirrorQueued=true;
        // Paper exposes no post-health-write event. One shared one-shot drains all dirty
        // mirrors on the next main-thread turn, after native damage/heal application.
        afterEvents.accept(() -> {
            var pending=List.copyOf(pendingMirrors);
            pendingMirrors.clear();mirrorQueued=false;
            for(var mob:pending) if(mob.isValid() && !mob.isDead())MobHealth.mirror(mob);
        });
    }
}
