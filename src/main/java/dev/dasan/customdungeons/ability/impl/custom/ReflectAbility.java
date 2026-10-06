package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class ReflectAbility implements Ability {
    public String id() { return "reflect"; }
    public Material icon() { return Material.SHIELD; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("speed", ParamType.DOUBLE, 1.5, 0.1, 4)); }
    public void execute(AbilityContext ctx) {
        if (!(ctx.cause() instanceof EntityDamageByEntityEvent event) || event.isCancelled()
                || !event.getEntity().equals(ctx.caster().entity()) || !(event.getDamager() instanceof Projectile incoming)
                || !(incoming.getShooter() instanceof LivingEntity shooter)) return;
        event.setCancelled(true);
        if (incoming instanceof Trident trident) {
            // Keep the player's original weapon outside ability-projectile cleanup.
            trident.setLoyaltyLevel(0);
            trident.setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
            trident.teleport(ctx.caster().entity().getLocation());
            trident.setVelocity(new org.bukkit.util.Vector());
            trident.setGravity(true);
            return;
        }
        if (!CustomAbilitiesB.participant(ctx, shooter)) return;
        var direction = shooter.getEyeLocation().toVector().subtract(ctx.caster().entity().getEyeLocation().toVector());
        if (direction.lengthSquared() == 0) return;
        var type = incoming.getType().getEntityClass();
        if (type == null || !Projectile.class.isAssignableFrom(type)) return;
        Projectile reflected = Effects.launch(ctx.caster(), type.asSubclass(Projectile.class),
                direction.normalize().multiply(ctx.params().getDouble("speed")));
        if (incoming instanceof AbstractArrow original && reflected instanceof AbstractArrow copy) {
            copy.setDamage(original.getDamage()); copy.setCritical(original.isCritical());
            copy.setPierceLevel(original.getPierceLevel());
            copy.setPickupStatus(original.getPickupStatus());
            copy.setItemStack(original.getItemStack().clone());
            if (original.getWeapon() != null) copy.setWeapon(original.getWeapon().clone());
        }
        if (incoming instanceof Arrow original && reflected instanceof Arrow copy) {
            copy.setBasePotionType(original.getBasePotionType());
            for (var effect : original.getCustomEffects()) copy.addCustomEffect(effect, true);
            if (original.getColor() != null) copy.setColor(original.getColor());
        }
        if (incoming instanceof ThrowableProjectile original && reflected instanceof ThrowableProjectile copy)
            copy.setItem(original.getItem().clone());
        if (incoming instanceof SpectralArrow original && reflected instanceof SpectralArrow copy)
            copy.setGlowingTicks(original.getGlowingTicks());
        if (incoming instanceof ShulkerBullet && reflected instanceof ShulkerBullet bullet) bullet.setTarget(shooter);
        if (incoming instanceof WitherSkull original && reflected instanceof WitherSkull copy) copy.setCharged(original.isCharged());
        if (incoming instanceof ThrownPotion original && reflected instanceof ThrownPotion copy) copy.setItem(original.getItem().clone());
        incoming.remove();
    }
}
