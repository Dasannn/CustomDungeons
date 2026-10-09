package dev.dasan.customdungeons.ability.control;
import dev.dasan.customdungeons.ability.AbilityRegistry;
/** RF-HAB-10: declarative parameters generate the editor without menu-specific branches. */
public final class ControlAbilities {
    private ControlAbilities() {}
    public static void register(AbilityRegistry r) {
        r.register(new GrabThrowAbility());
        r.register(new LevitationCageAbility());
        r.register(new DrainGrabAbility());
        r.register(new BombMarkAbility());
        r.register(new TacticalSummonAbility());
        r.register(new RootsAbility());
        r.register(new AnchorSpearAbility());
        r.register(new SoulChainAbility());
    }
}
