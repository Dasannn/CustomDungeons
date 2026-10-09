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
    private final Consumer<MobDamageAppliedEvent> applied;

    public MobCombatListener(Plugin plugin) {
        this(task -> plugin.getServer().getScheduler().runTask(plugin,task), event -> plugin.getServer().getPluginManager().callEvent(event));
    }
    MobCombatListener(Consumer<Runnable> afterEvents) { this(afterEvents, event -> {}); }
    MobCombatListener(Consumer<Runnable> afterEvents, Consumer<MobDamageAppliedEvent> applied) { this.afterEvents=afterEvents; this.applied=applied; }

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
        // Old PDC values and external mutations cannot overflow Paper's float defenses.
        if(damage!=null && !Double.isNaN(damage))
            event.setDamage(Math.clamp(damage,0,dev.dasan.customdungeons.config.NumericRanges.UNBOUNDED_MAX));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damaged(EntityDamageEvent event) {
        if(event.isCancelled() || !(event.getEntity() instanceof LivingEntity entity)
                || dev.dasan.customdungeons.ability.combat.CombatService.isDecoy(entity))return;
        if(!MobHealth.virtual(entity)&&!entity.getPersistentDataContainer().has(MobKeys.SESSION,PersistentDataType.STRING))return;
        double before=MobHealth.current(entity);
        double remaining=MobHealth.remainingAfterDamage(entity,event);
        if(!MobHealth.virtual(entity)) {
            applied.accept(new MobDamageAppliedEvent(entity,event,Math.max(0,before-remaining),remaining));
            return;
        }
        if(!MobHealth.floatSafeDamage(event)) {
            if(remaining>0) { event.setCancelled(true);return; }
            // Invalid defenses cannot be handed to native damage/absorption code.
            // Keep this event's source and arrange a finite, physically lethal hit.
            for(var modifier:EntityDamageEvent.DamageModifier.values())
                if(modifier!=EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier))event.setDamage(modifier,0);
            double physicalHealth=entity.getHealth();
            event.setDamage(EntityDamageEvent.DamageModifier.BASE,Double.isFinite(physicalHealth)
                    ? Math.clamp(physicalHealth,1,MobHealth.PHYSICAL_LIMIT) : 1);
        }
        MobHealth.remember(entity,remaining);
        applied.accept(new MobDamageAppliedEvent(entity,event,Math.max(0,before-remaining),remaining));
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
            double base=allowed-modifiers;
            if(MobHealth.floatSafe(base))event.setDamage(EntityDamageEvent.DamageModifier.BASE,base);
            // At extreme magnitudes, cancellation may make the bounded result
            // unrepresentable. A negative final would create native absorption.
            // In that case apply zero physical damage, preserving vanilla's
            // original absorption consumption. Virtual HP already used all defenses.
            if(!MobHealth.floatSafe(base) || !MobHealth.floatSafeDamage(event)
                    || event.getFinalDamage()<0 || physical-(float)event.getFinalDamage()<floor) {
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
