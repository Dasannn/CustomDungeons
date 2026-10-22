package dev.dasan.customdungeons.mob;

import java.util.OptionalDouble;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Spider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VanillaAttributesTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @AfterEach void reset() { VanillaAttributes.clearCache(); }

    private static AttributeInstance base(double value) {
        var instance = mock(AttributeInstance.class);
        when(instance.getBaseValue()).thenReturn(value);
        return instance;
    }

    @Test void readsTheTypeBaseValueInsteadOfAFixedOne() {
        // #0002: the editor used to propose 1 for every attribute; a spider walks at 0.3.
        var world = mock(World.class);
        var spider = mock(Spider.class);
        var speed = base(0.3);
        var health = base(16);
        when(spider.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);
        when(spider.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
        when(world.createEntity(any(Location.class), eq(Spider.class))).thenReturn(spider);
        assertEquals(OptionalDouble.of(0.3), VanillaAttributes.of("minecraft:spider", "speed", world));
        assertEquals(OptionalDouble.of(16), VanillaAttributes.of("SPIDER", "max-health", world));
        verify(world, times(1)).createEntity(any(Location.class), eq(Spider.class));
    }

    @Test void missingAttributesUnknownTypesAndFailedCreationAreEmpty() {
        var world = mock(World.class);
        var creeper = mock(Creeper.class);
        when(world.createEntity(any(Location.class), eq(Creeper.class))).thenReturn(creeper);
        assertTrue(VanillaAttributes.of("CREEPER", "damage", world).isEmpty());
        assertTrue(VanillaAttributes.of("NOT_A_MOB", "speed", world).isEmpty());
        assertTrue(VanillaAttributes.of("SPIDER", "speed", null).isEmpty());
        // Nothing is cached when the server returns no entity, so a later read can still succeed.
        assertTrue(VanillaAttributes.of("SPIDER", "speed", world).isEmpty());
        var spider = mock(Spider.class);
        var speed = base(0.3);
        when(spider.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed);
        when(world.createEntity(any(Location.class), eq(Spider.class))).thenReturn(spider);
        assertEquals(OptionalDouble.of(0.3), VanillaAttributes.of("SPIDER", "speed", world));
    }
}
