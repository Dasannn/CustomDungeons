package dev.dasan.customdungeons.tool;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolMaterialsTest {
    @Test void defaultsAreInertForAllTools() {
        assertEquals(Material.BLAZE_ROD, ToolMaterials.resolve(ToolType.REGION, null));
        assertEquals(Material.AMETHYST_SHARD, ToolMaterials.resolve(ToolType.DOOR, null));
        assertEquals(Material.BREEZE_ROD, ToolMaterials.resolve(ToolType.SPAWNER, null));
        assertEquals(Material.ECHO_SHARD, ToolMaterials.resolve(ToolType.POINT, null));
    }

    @Test void safeConfiguredMaterialsAreAcceptedCaseInsensitively() {
        for (ToolType type : ToolType.values()) {
            assertEquals(Material.FEATHER, ToolMaterials.resolve(type, " feather "));
            assertEquals(Material.ECHO_SHARD, ToolMaterials.resolve(type, "minecraft:echo_shard"));
        }
    }

    @Test void unsafeOrInvalidMaterialsFallBackToTheTypeDefault() {
        for (ToolType type : ToolType.values()) {
            for (String configured : new String[] {"", "missing", "AIR", "STONE", "COMPASS", "CLOCK",
                    "FISHING_ROD", "ENDER_PEARL", "IRON_HOE", "SPAWNER", "BOW", "APPLE", "BUCKET"}) {
                assertEquals(ToolMaterials.defaultFor(type), ToolMaterials.resolve(type, configured));
            }
        }
    }
}
