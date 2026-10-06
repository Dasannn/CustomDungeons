package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.potion.*;
public final class ShulkerBulletAbility implements Ability {
    public String id() { return "shulker_bullet"; }
    public Material icon() { return Material.SHULKER_SHELL; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("levitationSeconds", ParamType.DOUBLE, 5.0, 0.05, 3600)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesB.targets(ctx)) {
            var bullet = BorrowedAbilitiesB.launch(ctx, org.bukkit.entity.ShulkerBullet.class,
                    BorrowedAbilitiesB.aim(ctx, target), (context, at, hit) -> {
                        if (hit instanceof org.bukkit.entity.LivingEntity living) {
                            Effects.damage(living, 4, context.caster());
                            living.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                                    (int) Math.round(context.params().getDouble("levitationSeconds") * 20), 0));
                        }
                    });
            bullet.setTarget(target);
        }
    }
}
