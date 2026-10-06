package dev.dasan.customdungeons.ability.impl.borrowed;
import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.PotionContents;
public final class WitchPotionsAbility implements Ability {
    public String id() { return "witch_potions"; }
    public Material icon() { return Material.SPLASH_POTION; }
    public List<ParamSpec> params() { return BorrowedAbilitiesB.potionParams(); }
    public void execute(AbilityContext ctx) {
        var effect = BorrowedAbilitiesB.potion(ctx);
        if (effect == null) return;
        for (var target : BorrowedAbilitiesB.targets(ctx)) {
            var aim = BorrowedAbilitiesB.aim(ctx, target);
            if (aim.lengthSquared() == 0) continue;
            var potion = BorrowedAbilitiesB.launch(ctx, ThrownPotion.class, aim.multiply(0.8).add(new org.bukkit.util.Vector(0, 0.2, 0)), (at, hit) -> {
                Effects.particles(ctx.session(), at, Particle.WITCH, 16, 0.5);
                Effects.sound(ctx.session(), at, Sound.ENTITY_SPLASH_POTION_BREAK, 1, 1);
                for (var player : ctx.session().players())
                    if (BorrowedAbilitiesB.allowed(ctx, player) && player.getWorld().equals(at.getWorld())
                            && player.getLocation().distanceSquared(at) <= 16) player.addPotionEffect(effect);
            });
            var item = new ItemStack(Material.SPLASH_POTION);
            item.setData(DataComponentTypes.POTION_CONTENTS, PotionContents.potionContents().addCustomEffect(effect));
            potion.setItem(item);
        }
    }
}
