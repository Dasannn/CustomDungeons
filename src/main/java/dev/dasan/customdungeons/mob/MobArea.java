package dev.dasan.customdungeons.mob;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

/** Room bounds in their owning world, using block coordinates like dungeon regions. */
public record MobArea(String world, BoundingBox bounds) {
    public MobArea {
        Objects.requireNonNull(world);
        bounds = Objects.requireNonNull(bounds).clone();
    }

    @Override public BoundingBox bounds() { return bounds.clone(); }

    public boolean contains(Location at) {
        return at.getWorld() != null && contains(at.getWorld().getName(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ());
    }

    public boolean contains(String world, int x, int y, int z) {
        return this.world.equals(world) && bounds.contains(x, y, z);
    }
}
