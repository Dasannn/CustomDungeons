package dev.dasan.customdungeons.mob;

import java.util.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;

/** Event-only virtual stats. Ability damage is explicitly scoped so it never becomes a melee override. */
public final class MobCombatListener implements Listener {
    private static final Set<EntityDamageEvent> SCALED = Collections.newSetFromMap(new WeakHashMap<>());
    // HIGH ability dispatch precedes the HIGHEST incoming conversion.
    public static double projectedPhysicalDamage(EntityDamageEvent event) {
        return event.getEntity() instanceof LivingEntity entity && MobHealth.virtual(entity) && !SCALED.contains(event)
                ? event.getFinalDamage()/MobHealth.factor(entity) : event.getFinalDamage();
    }
    private static final Map<Entity,Integer> ABILITY_DAMAGE = new IdentityHashMap<>();
    private final Map<EntityDamageEvent,Double> beforeDamage = new WeakHashMap<>();
    private final Map<EntityRegainHealthEvent,Double> beforeHealing = new WeakHashMap<>();
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
    @SuppressWarnings("deprecation")
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent event) {
        if(!(event.getEntity() instanceof LivingEntity entity) || !MobHealth.virtual(entity))return;
        double health=MobHealth.current(entity);
        beforeDamage.put(event,health);
        if(event.getCause()==EntityDamageEvent.DamageCause.KILL || event.getCause()==EntityDamageEvent.DamageCause.VOID) {
            event.setDamage(Math.max(event.getDamage(),entity.getHealth()));return;
        }
        double factor=MobHealth.factor(entity);
        double original=event.getFinalDamage();
        if(original>=health-Math.ulp(health)*4 && original>0) {
            // Lethal hits must survive Paper's float conversion, including maxima below one.
            for(var modifier:EntityDamageEvent.DamageModifier.values()) if(event.isApplicable(modifier))
                event.setDamage(modifier,modifier==EntityDamageEvent.DamageModifier.BASE ? entity.getHealth() : 0);
            SCALED.add(event);return;
        }
        var scaled=new EnumMap<EntityDamageEvent.DamageModifier,Double>(EntityDamageEvent.DamageModifier.class);
        for(var modifier:EntityDamageEvent.DamageModifier.values()) if(event.isApplicable(modifier))
            scaled.put(modifier,event.getDamage(modifier)/factor);
        // Preserve armor/resistance/absorption already calculated for the original virtual hit.
        scaled.forEach(event::setDamage);
        SCALED.add(event);

    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void damaged(EntityDamageEvent event) {
        Double before=beforeDamage.remove(event);
        if(before==null || event.isCancelled() || !(event.getEntity() instanceof LivingEntity entity))return;
        double damage=event.getFinalDamage();
        double remaining=before-damage*MobHealth.factor(entity);
        if(event.getCause()==EntityDamageEvent.DamageCause.KILL || event.getCause()==EntityDamageEvent.DamageCause.VOID)remaining=0;
        if(damage>=entity.getHealth() || (damage>0 && remaining<=Math.ulp(before)*4))remaining=0;
        MobHealth.remember(entity,Math.max(0,remaining),(double)Math.max(0,(float)entity.getHealth()-(float)damage));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void heal(EntityRegainHealthEvent event) {
        if(!(event.getEntity() instanceof LivingEntity entity) || !MobHealth.virtual(entity))return;
        beforeHealing.put(event,MobHealth.current(entity));
        event.setAmount(event.getAmount()/MobHealth.factor(entity));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void healed(EntityRegainHealthEvent event) {
        Double before=beforeHealing.remove(event);
        if(before==null || event.isCancelled() || !(event.getEntity() instanceof LivingEntity entity))return;
        var maximum=entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double physical=Math.min(maximum.getValue(),entity.getHealth()+event.getAmount());
        MobHealth.remember(entity,Math.min(MobHealth.maximum(entity),before+event.getAmount()*MobHealth.factor(entity)),(double)(float)physical);
    }
}
