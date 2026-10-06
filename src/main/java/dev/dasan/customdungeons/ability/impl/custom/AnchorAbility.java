package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
import org.bukkit.potion.*;
public final class AnchorAbility implements Ability {
    public String id() { return "anchor"; }
    public Material icon() { return Material.IRON_CHAIN; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("ticks", ParamType.TICKS, 60, 1, 1200)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            int ticks = ctx.params().getInt("ticks");
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 255));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, -128));
            CustomAbilitiesA.chains(ctx, target.getLocation());
        }
    }
}
