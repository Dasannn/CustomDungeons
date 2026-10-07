package dev.dasan.customdungeons.ability.impl;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PaperApiTestBootstrapTest {
    @Test void repeatedBootstrapKeepsRegistryAndCachedConstants() {
        PaperApiTestBootstrap.initialize();
        var registry = Registry.EFFECT;
        var blindness = PotionEffectType.BLINDNESS;
        PaperApiTestBootstrap.initialize();
        assertSame(registry, Registry.EFFECT);
        assertSame(blindness, registry.get(NamespacedKey.minecraft("blindness")));
        assertTrue(PotionEffectType.INSTANT_DAMAGE.isInstant());
        assertFalse(blindness.isInstant());
    }

    @Test void effectOverrideRestoresPreviousEntryEvenWhenTestFails() {
        PaperApiTestBootstrap.initialize();
        var key = NamespacedKey.minecraft("test_scoped_effect");
        var previous = Registry.EFFECT.get(key);
        var temporary = mock(PotionEffectType.class);
        assertThrows(AssertionError.class, () -> {
            try (var ignored = PaperApiTestBootstrap.withEffect(key, temporary)) {
                assertSame(temporary, Registry.EFFECT.get(key));
                throw new AssertionError("simulated failed assertion");
            }
        });
        assertSame(previous, Registry.EFFECT.get(key));
    }
    @Test void missingEntryScopeRestoresLookupEvenAfterAssertionFailure() {
        PaperApiTestBootstrap.initialize();var key=NamespacedKey.minecraft("scoped_missing_sound");
        var previous=Registry.SOUNDS.get(key);
        assertThrows(AssertionError.class,()->{
            try(var ignored=PaperApiTestBootstrap.withoutEntry(org.bukkit.Sound.class,key)) {
                assertNull(Registry.SOUNDS.get(key));throw new AssertionError("simulated failed assertion");
            }
        });
        assertSame(previous,Registry.SOUNDS.get(key));
    }

}
