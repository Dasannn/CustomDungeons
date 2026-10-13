package dev.dasan.customdungeons.mob;

import org.bukkit.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeTerrainExitTest {
    private static void solid(SafeTerrainInteriorTest f,int x,int y,int z,boolean solid) {
        var block=f.block(x,y,z);
        when(block.getType()).thenReturn(solid?Material.STONE:Material.AIR);
        when(block.isSolid()).thenReturn(solid);when(block.isPassable()).thenReturn(!solid);
    }
    private static SafeTerrainInteriorTest hole() {
        var f=new SafeTerrainInteriorTest();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            for(int y=61;y<=63;y++)solid(f,x,y,z,false);
            solid(f,x,60,z,true);
        }
        return f;
    }
    @Test void aThreeBlockHoleWithoutStepsIsRejectedEvenWithNeighboursOnItsFloor() {
        var f=hole();var at=SafeTerrain.interior(f.world,0,0,64,.6,1.8,f.area).orElseThrow();
        assertEquals(61,at.getY());
        assertFalse(SafeTerrain.hasWalkableExit(at,.6,1.8,f.area,64));
    }
    @Test void oneBlockStepsMakeTheSameHoleAnEscapableDestination() {
        var f=hole();solid(f,1,61,0,true);solid(f,2,63,0,false);solid(f,2,62,0,true);
        var at=SafeTerrain.interior(f.world,0,0,64,.6,1.8,f.area).orElseThrow();
        assertTrue(SafeTerrain.hasWalkableExit(at,.6,1.8,f.area,64));
    }
    @Test void aLowCeilingPreventsClimbingAnOtherwiseValidStep() {
        var f=hole();solid(f,1,61,0,true);solid(f,2,63,0,false);solid(f,2,62,0,true);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)solid(f,x,63,z,true);
        var at=new Location(f.world,.5,61,.5);
        assertTrue(SafeTerrain.clearBody(at,.6,1.8));
        assertFalse(SafeTerrain.hasWalkableExit(at,.6,1.8,f.area,64));
    }
    @Test void anExitMustReachTheEncounterFloorRatherThanAnotherEnclosedStep() {
        var f=hole();solid(f,1,61,0,true);solid(f,2,63,0,false);solid(f,2,62,0,true);
        for(int x=1;x<=4;x++)for(int z=-2;z<=2;z++)solid(f,x,65,z,true);
        assertFalse(SafeTerrain.hasWalkableExit(new Location(f.world,.5,61,.5),.6,1.8,f.area,64));
    }
    @Test void aFlatDestinationNeedsAnActualSafeNeighbourInsideTheLoadedArea() {
        var f=new SafeTerrainInteriorTest();var at=new Location(f.world,.5,64,.5);
        assertTrue(SafeTerrain.hasWalkableExit(at,.6,1.8,f.area,64));
        for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int y=64;y<=66;y++)solid(f,d[0],y,d[1],true);
        assertFalse(SafeTerrain.hasWalkableExit(at,.6,1.8,f.area,64));
        verify(f.world,never()).getChunkAt(anyInt(),anyInt());
    }
}
