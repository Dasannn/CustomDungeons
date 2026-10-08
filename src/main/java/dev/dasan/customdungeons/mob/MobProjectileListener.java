package dev.dasan.customdungeons.mob;

import java.util.function.Consumer;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

/** Launch ownership shared by dungeon mobs and world encounters. */
public final class MobProjectileListener implements Listener {
    private final Consumer<Projectile> onMarked;
    public MobProjectileListener(Consumer<Projectile> onMarked) { this.onMarked=onMarked; }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void launch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Entity shooter) inherit(event.getEntity(),shooter);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void shoot(EntityShootBowEvent event) {
        // The bow event supplies the shooter even before the projectile's own shooter is set.
        if (event.getProjectile() instanceof Projectile projectile) inherit(projectile,event.getEntity());
    }
    private void inherit(Projectile projectile,Entity shooter) {
        if (!OwnedEntities.markProjectile(projectile,shooter)) return;
        if (projectile instanceof Explosive explosive) explosive.setIsIncendiary(false);
        onMarked.accept(projectile);
    }
}
