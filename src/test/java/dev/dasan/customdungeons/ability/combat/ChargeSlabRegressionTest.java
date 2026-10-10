package dev.dasan.customdungeons.ability.combat;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Paper 26.3 isSolid() uses BLOCKS_MOTION, which includes #minecraft:slabs.
 * A bottom slab blocks movement only below its top face, not the whole voxel. */
class ChargeSlabRegressionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
    @Test void supportedChargeAboveBottomSlabsMustNotTreatTheFloorAsAWall() {
        try (var f = new CombatServiceTest.Fixture()) {
            var shape = mock(VoxelShape.class);
            when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0, 0, 0, 1, .5, 1)));
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 16; z++) {
                var slab = mock(Block.class);
                when(slab.getType()).thenReturn(Material.STONE_SLAB);
                when(slab.isSolid()).thenReturn(true);
                when(slab.isPassable()).thenReturn(false);
                when(slab.getCollisionShape()).thenReturn(shape);
                when(slab.getWorld()).thenReturn(f.world);
                f.terrain.put(x + ":64:" + z, slab);
            }
            f.mobPosition = new Location(f.world, 0, 64.5, 0);
            var body = new BoundingBox(-.3, 64.5, -.3, .3, 66.3, .3);
            assertFalse(body.overlaps(new BoundingBox(0, 64, 0, 1, 64.5, 1)),
                    "standing on the slab is physically clear");
            f.cast("charge");
            f.step(21);
            assertTrue(f.mobPosition.getZ() > .5,
                    "clearVolume floors Y and falsely reports the supporting slab as a wall");
        }
    }
}
