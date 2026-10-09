package dev.dasan.customdungeons.ability;

public final class Abilities {
    private Abilities() {}

    public static void registerDefaults(AbilityRegistry r) {
        // Each ability task adds one XxxAbilities.register(r) line here.
        dev.dasan.customdungeons.ability.impl.CoreAbilities.register(r);
        dev.dasan.customdungeons.ability.impl.custom.CustomAbilitiesB.register(r);
        dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesA.register(r);
        dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC.register(r);
        dev.dasan.customdungeons.ability.impl.custom.CustomAbilitiesA.register(r);
        dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesB.register(r);
        dev.dasan.customdungeons.ability.impl.generic.GenericAbilities.register(r);
        dev.dasan.customdungeons.ability.control.ControlAbilities.register(r);
        dev.dasan.customdungeons.ability.zone.ZoneAbilities.register(r);
    }
}
