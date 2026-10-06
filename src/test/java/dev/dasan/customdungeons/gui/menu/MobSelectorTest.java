package dev.dasan.customdungeons.gui.menu;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobSelectorTest {
    @Test void numericLabelsDropWholeNumberDecimalsWithoutRoundingFractions() {
        assertEquals("280", MobMenuBase.formatValue(280.0));
        assertEquals("20", MobMenuBase.formatValue(20));
        assertEquals("1.5", MobMenuBase.formatValue(1.5));
        assertEquals("66", MobMenuBase.formatValue(.66 * 100));
        assertEquals("NaN", MobMenuBase.formatValue(Double.NaN));
    }

    @Test void sectionReadinessKeepsNestedPhaseErrorsInCombat() {
        var stats = new dev.dasan.customdungeons.config.ValidationError("max-health","validation.stat-range",java.util.Map.of());
        var phase = new dev.dasan.customdungeons.config.ValidationError("phases[0].equipment.HEAD","validation.armor",java.util.Map.of());
        var equipment = new dev.dasan.customdungeons.config.ValidationError("equipment.HEAD","validation.armor",java.util.Map.of());
        var identity = new dev.dasan.customdungeons.config.ValidationError("entity-type","validation.entity-type",java.util.Map.of());
        for (String section : List.of("identity","stats","equipment","combat")) assertTrue(MobMenuBase.sectionReady(section,List.of()));
        assertFalse(MobMenuBase.sectionReady("stats",List.of(stats)));
        assertTrue(MobMenuBase.sectionReady("identity",List.of(stats)));
        assertFalse(MobMenuBase.sectionReady("combat",List.of(phase)));
        assertTrue(MobMenuBase.sectionReady("equipment",List.of(phase)));
        assertFalse(MobMenuBase.sectionReady("equipment",List.of(equipment)));
        assertFalse(MobMenuBase.sectionReady("identity",List.of(identity)));
    }

    @Test void filtersAreCaseInsensitiveTrimmedAndKeepRegistryKeys() {
        var keys = List.of("minecraft:entity.zombie.hurt", "minecraft:block.anvil.use", "pack:Boss.Roar");
        assertEquals(List.of(keys.get(2)), MobMenuBase.filterChoices(keys, "  BOSS  "));
        assertEquals(keys, MobMenuBase.filterChoices(keys, ""));
        assertTrue(MobMenuBase.filterChoices(keys, "missing").isEmpty());
    }
    @Test void soundCategoriesPreserveCustomNamespaces() {
        assertEquals("entity", MobMenuBase.soundCategory("minecraft:entity.zombie.hurt"));
        assertEquals("block", MobMenuBase.soundCategory("minecraft:block.anvil.use"));
        assertEquals("pack", MobMenuBase.soundCategory("pack:boss.roar"));
        assertEquals("other", MobMenuBase.soundCategory("custom"));
    }
    @Test void configurableValuesParseLegacyHexAndMiniMessage() {
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertEquals("Boss", plain.serialize(MobMenuBase.displayValue("name", "&6Boss")));
        assertEquals("Boss", plain.serialize(MobMenuBase.displayValue("title", "&#ff8800<bold>Boss</bold>")));
    }
}
