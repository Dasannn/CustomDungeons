package dev.dasan.customdungeons.ability.zone;

import dev.dasan.customdungeons.ability.AbilityRegistry;

public final class ZoneAbilities {
    private ZoneAbilities() {}
    public static void register(AbilityRegistry registry) {
        registry.register(new VortexAbility());
        registry.register(new InvertedGravityAbility());
        registry.register(new CrackedFloorAbility());
        registry.register(new SweepAbility());
        registry.register(new FallingPillarsAbility());
        registry.register(new PoisonPoolsAbility());
        registry.register(new ChargedBeamAbility());
        registry.register(new ArrowRainAbility());
        registry.register(new RiftAbility());
    }
}
