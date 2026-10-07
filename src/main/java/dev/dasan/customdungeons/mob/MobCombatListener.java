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
        // Arrange native death in this same hit, with its original damage source/credit.
        // No event modifier is changed: lastHurt and absorption remain in vanilla units.
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
    @EventHandler(priority=EventPriority.LOWEST)
    public void death(EntityDeathEvent event) {
        LivingEntity entity=event.getEntity();
        if(!MobHealth.virtual(entity) || MobHealth.current(entity)<=0)return;
        // Paper die() restores reviveHealth without resetting lastHurt or damageCooldownTime.
        // Its cancelled path skips drops, XP, death sounds and post-death tasks.
        event.setCancelled(true);
        event.setReviveHealth(MobHealth.mirroredHealth(entity));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void resurrect(EntityResurrectEvent event) {
        // A premature physical death must not consume a totem or replace absorption/effects.
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
