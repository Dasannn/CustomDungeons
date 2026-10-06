package dev.dasan.customdungeons.ability;

public final class Abilities {
    private Abilities() {}

    public static void registerDefaults(AbilityRegistry r) {
        // Each ability task adds one XxxAbilities.register(r) line here.
        dev.dasan.customdungeons.ability.impl.CoreAbilities.register(r);
    }
}
