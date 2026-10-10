package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.AbilityContext;
import dev.dasan.customdungeons.ability.AbilityCasts;
import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.ability.control.LevitationCageAbility;
import dev.dasan.customdungeons.model.AbilityInstance;
import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.model.Trigger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

/** Ensures a charge cannot starve a subsequent control with the same cadence. */
class AbilityCadenceRegressionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @Test void chargeMustNotStarveALaterCageWithTheSameGuiDefaultCadence() {
        try (var f = new CombatServiceTest.Fixture()) {
            var cage = spy(new LevitationCageAbility());
            // Observe dispatch only; cage internals and player movement are a separate reproduction.
            doAnswer(i->{AbilityCasts.started(i.getArgument(0));return null;}).when(cage).execute(any(AbilityContext.class), anyInt());
            f.registry.register(cage);
            f.caster.abilities().addAll(List.of(instance("charge"), instance("levitation_cage")));
            var engine = new AbilityEngine(f.registry, f.platform);
            for (int i = 0; i < 400; i++) {
                engine.tick(List.of(f.caster), f.tick.get());
                f.step(1);
            }
            verify(f.entity, atLeastOnce()).setAI(false);
            verify(cage, atLeastOnce()).execute(any(AbilityContext.class), anyInt());
        }
    }
    private static AbilityInstance instance(String id) {
        return new AbilityInstance(id, Trigger.EVERY_X_SECONDS, 5,
                TargetMode.NEAREST, 16, 100, 1, 20, Map.of());
    }
}
