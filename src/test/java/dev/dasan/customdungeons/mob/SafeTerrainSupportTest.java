package dev.dasan.customdungeons.mob;

import java.util.List;
import org.bukkit.*;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeTerrainSupportTest {
    @Test void theExactFootprintDistinguishesAHoleFromABodyWideEnoughToBridgeIt() {
        var f=new SafeTerrainInteriorTest();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=61;y<=63;y++) {
            var block=f.block(x,y,z);when(block.getType()).thenReturn(Material.AIR);
            when(block.isSolid()).thenReturn(false);when(block.isPassable()).thenReturn(true);
        }
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            var block=f.block(x,60,z);when(block.getType()).thenReturn(Material.STONE);
            when(block.isSolid()).thenReturn(true);when(block.isPassable()).thenReturn(false);
        }
        var at=new Location(f.world,.5,64,.5);
        assertEquals(61,SafeTerrain.supportFloor(at,64,.6,f.area).orElseThrow());
        assertEquals(64,SafeTerrain.supportFloor(at,64,4,f.area).orElseThrow());
    }
    @Test void protrudingNeighbourSupportUsesItsActualHighestCollision() {
        var f=new SafeTerrainInteriorTest();var shape=mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(.25,0,0,.75,1.5,1)));
        when(f.block(1,63,0).getCollisionShape()).thenReturn(shape);
        assertEquals(64.5,SafeTerrain.supportFloor(new Location(f.world,.5,64,.5),64,2,f.area).orElseThrow());
    }
    @Test void aFenceInTheLowerCellCanStandAboveCarpetInTheHigherCell() {
        var f=new SafeTerrainInteriorTest();var carpet=mock(VoxelShape.class);var fence=mock(VoxelShape.class);
        when(carpet.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,0,0,1,.0625,1)));
        when(fence.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(.25,0,0,.75,1.5,1)));
        when(f.block(0,64,0).getCollisionShape()).thenReturn(carpet);
        when(f.block(0,64,0).isPassable()).thenReturn(false);
        when(f.block(1,63,0).getCollisionShape()).thenReturn(fence);
        assertEquals(64.5,SafeTerrain.supportFloor(new Location(f.world,.5,64.5,.5),64.5,2,f.area).orElseThrow());
    }
    @Test void supportSearchNeverLoadsMissingChunksAndRejectsLiquidFootprints() {
        var f=new SafeTerrainInteriorTest();var at=new Location(f.world,.5,64,.5);
        when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertTrue(SafeTerrain.supportFloor(at,64,.6,f.area).isEmpty());
        verify(f.world,never()).getChunkAt(anyInt(),anyInt());
        when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(f.block(0,63,0).getType()).thenReturn(Material.WATER);
        assertTrue(SafeTerrain.supportFloor(at,64,.6,f.area).isEmpty());
    }
}
