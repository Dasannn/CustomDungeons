package dev.dasan.customdungeons.ability.impl.generic;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesB;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.*;
public final class ArrowEffectAbility implements Ability {
    public String id() { return "arrow_effect"; }
    public Material icon() { return Material.TIPPED_ARROW; }
    public List<ParamSpec> params() { return BorrowedAbilitiesB.potionParams(); }
    public void execute(AbilityContext ctx) {
        var effect = BorrowedAbilitiesB.potion(ctx);
        if (effect == null) return;
        for (var target : BorrowedAbilitiesB.targets(ctx)) {
            var aim = BorrowedAbilitiesB.aim(ctx, target);
            if (aim.lengthSquared() == 0) continue;
            // Native Arrow applies its damage and the declared potion only after the participant guard.
            var arrow = BorrowedAbilitiesB.launch(ctx, Arrow.class, aim.multiply(2), (context, at, hit) -> {});
            arrow.addCustomEffect(effect, true);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        }
    }
}
