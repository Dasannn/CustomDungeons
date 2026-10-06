package dev.dasan.customdungeons.tool;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/** Restrict configurable tools to inert items, including safe visual alternatives. */
final class ToolMaterials {
    private static final Set<Material> INERT = Set.of(Material.BLAZE_ROD, Material.AMETHYST_SHARD,
            Material.BREEZE_ROD, Material.ECHO_SHARD, Material.STICK, Material.PAPER,
            Material.FEATHER, Material.FLINT, Material.QUARTZ, Material.PRISMARINE_SHARD,
            Material.PRISMARINE_CRYSTALS, Material.BONE);

    private ToolMaterials() {}

    static Material defaultFor(ToolType type) {
        return switch (type) {
            case REGION -> Material.BLAZE_ROD;
            case DOOR -> Material.AMETHYST_SHARD;
            case SPAWNER -> Material.BREEZE_ROD;
            case POINT -> Material.ECHO_SHARD;
            case PLATE -> Material.STONE_PRESSURE_PLATE;
        };
    }

    static Material resolve(ToolType type, @Nullable String configured) {
        if (configured != null) {
            String name = configured.trim().toUpperCase(Locale.ROOT);
            if (name.startsWith("MINECRAFT:")) name = name.substring("MINECRAFT:".length());
            try {
                Material material = Material.valueOf(name);
                if (INERT.contains(material) || type==ToolType.PLATE && material==Material.STONE_PRESSURE_PLATE) return material;
            } catch (IllegalArgumentException ignored) {
                // Invalid or interactive materials always use the inert default.
            }
        }
        return defaultFor(type);
    }
}
