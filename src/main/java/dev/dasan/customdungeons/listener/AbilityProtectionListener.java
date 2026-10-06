package dev.dasan.customdungeons.listener;

import dev.dasan.customdungeons.ability.Effects;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;

/** Terrain and participant protection for projectiles launched with Effects. */
public final class AbilityProtectionListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent event) {
        if (Effects.marked(event.getEntity())) event.blockList().clear();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prime(ExplosionPrimeEvent event) {
        if (Effects.marked(event.getEntity())) event.setFire(false);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hit(ProjectileHitEvent event) {
        if (!Effects.marked(event.getEntity())) return;
        if (event.getHitBlock() != null || (event.getHitEntity() != null
                && !Effects.projectileTargetAllowed(event.getEntity(), event.getHitEntity()))) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Projectile projectile && Effects.marked(projectile)
                && !Effects.projectileTargetAllowed(projectile, event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void splash(PotionSplashEvent event) {
        if (!Effects.marked(event.getPotion())) return;
        for (var target : java.util.List.copyOf(event.getAffectedEntities())) {
            if (!Effects.projectileTargetAllowed(event.getPotion(), target)) event.setIntensity(target, 0);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void ignite(BlockIgniteEvent event) {
        if (Effects.marked(event.getIgnitingEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void combust(EntityCombustByEntityEvent event) {
        if (Effects.marked(event.getCombuster())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void changeBlock(EntityChangeBlockEvent event) {
        if (Effects.marked(event.getEntity())) event.setCancelled(true);
    }
}
