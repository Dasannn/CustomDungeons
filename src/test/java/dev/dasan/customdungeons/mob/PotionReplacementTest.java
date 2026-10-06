package dev.dasan.customdungeons.mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PotionReplacementTest {
    @Test void weakerPotionReplacesStrongerEffectOfSameType() {
        Map<String, Integer> effects = new HashMap<>(Map.of("speed", 4, "strength", 2));
        List<String> calls = new ArrayList<>();
        MobFactory.replacePotion("speed", type -> {
            calls.add("remove:" + type);
            effects.remove(type);
        }, () -> {
            calls.add("add:speed");
            // Model Bukkit's refusal to downgrade an existing, stronger effect.
            effects.merge("speed", 0, Math::max);
        });
        assertEquals(0, effects.get("speed"));
        assertEquals(2, effects.get("strength"));
        assertEquals(List.of("remove:speed", "add:speed"), calls);
    }

    @Test void potionIsAppliedWhenNoPreviousEffectExists() {
        Map<String, Integer> effects = new HashMap<>();
        MobFactory.replacePotion("speed", effects::remove, () -> effects.put("speed", 1));
        assertEquals(Map.of("speed", 1), effects);
    }
}
