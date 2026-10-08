package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.model.BlockPos;
import dev.dasan.customdungeons.model.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobAreaTest {
    @Test void matchesLegacyRegionAcrossWorldsHeightsAndFractionalCoordinates() {
        var legacy = Region.of("dungeon", new BlockPos(-3, 60, -1), new BlockPos(0, 70, 1));
        var area = new MobArea("dungeon", new BoundingBox(-3, 60, -1, 1, 71, 2));
        for (String name : new String[] {"dungeon", "other"}) {
            World world = mock(World.class);
            when(world.getName()).thenReturn(name);
            for (double x : new double[] {-3.01, -3, -.01, 0, .99, 1})
                for (double y : new double[] {59.99, 60, 70, 70.99, 71})
                    for (double z : new double[] {-1.01, -1, 0, 1.99, 2}) {
                        var at = new Location(world, x, y, z);
                        assertEquals(legacy.contains(name, at.getBlockX(), at.getBlockY(), at.getBlockZ()),
                                area.contains(at), at.toString());
                    }
        }
        assertFalse(area.contains(new Location(null, 0, 64, 0)));
    }

    @Test void boundsAreDefensivelyCopiedOnInputAndOutput() {
        var box = new BoundingBox(0, 0, 0, 2, 2, 2);
        var area = new MobArea("world", box);
        box.shift(100, 100, 100);
        area.bounds().shift(100, 100, 100);
        assertTrue(area.contains("world", 1, 1, 1));
    }
}
