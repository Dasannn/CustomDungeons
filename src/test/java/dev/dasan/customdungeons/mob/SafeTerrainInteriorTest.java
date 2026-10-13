package dev.dasan.customdungeons.mob;

import java.util.HashMap;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SafeTerrainInteriorTest {
    final World world=mock(World.class);
    final java.util.Map<String,Block> blocks=new HashMap<>();
    final MobArea area=new MobArea("room",new BoundingBox(-8,60,-8,8,70,8));
    SafeTerrainInteriorTest() {
        when(world.getName()).thenReturn("room");when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
        var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);
        when(border.getCenter()).thenReturn(new Location(world,0,64,0));when(border.getSize()).thenReturn(1000.0);
        when(world.getHighestBlockYAt(anyInt(),anyInt(),any(HeightMap.class))).thenReturn(70);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->block(i.getArgument(0),i.getArgument(1),i.getArgument(2)));
    }
    Block block(int x,int y,int z) {
        return blocks.computeIfAbsent(x+":"+y+":"+z,k->{var b=mock(Block.class);
            boolean solid=y==63||y==70;when(b.getType()).thenReturn(solid?Material.STONE:Material.AIR);
            when(b.isSolid()).thenReturn(solid);when(b.isPassable()).thenReturn(!solid);return b;});
    }
    @Test void indoorSearchFindsFloorAndSurfaceModeStillFindsRoof() {
        assertEquals(64,SafeTerrain.interior(world,0,0,64,.6,1.8,area).orElseThrow().getY());
        verify(world,never()).getHighestBlockYAt(anyInt(),anyInt(),any());
        assertEquals(71,SafeTerrain.validate(world,0,0,.6,1.8).orElseThrow().getY());
    }
    @Test void aReferenceNearTheCeilingCannotCrossItToChooseTheCloserRoof() {
        var tallArea=new MobArea("room",new BoundingBox(-8,60,-8,8,80,8));
        assertEquals(64,SafeTerrain.interior(world,0,0,68,.6,1.8,tallArea).orElseThrow().getY());
        assertEquals(64,SafeTerrain.interior(world,0,0,68,.6,1.8,null).orElseThrow().getY());
    }
    @Test void aRaisedFloorInAnAdjacentColumnStillHasUsableHeadroom() {
        when(block(0,64,0).isPassable()).thenReturn(false);
        when(block(0,64,0).isSolid()).thenReturn(true);
        when(block(0,64,0).getType()).thenReturn(Material.STONE);
        assertEquals(65,SafeTerrain.interior(world,0,0,64,.6,1.8,area).orElseThrow().getY());
    }
    @Test void aWallColumnCannotLeadTheInteriorSearchOntoTheRoof() {
        for(int y=64;y<70;y++) {
            when(block(0,y,0).isPassable()).thenReturn(false);
            when(block(0,y,0).isSolid()).thenReturn(true);
            when(block(0,y,0).getType()).thenReturn(Material.STONE);
        }
        var tallArea=new MobArea("room",new BoundingBox(-8,60,-8,8,80,8));
        assertTrue(SafeTerrain.interior(world,0,0,64,.6,1.8,tallArea).isEmpty());
    }
    @Test void aFourBlockRiseIsAnInteriorFloorRatherThanTheWorldRoof() {
        for(int y=64;y<68;y++) {
            when(block(0,y,0).isPassable()).thenReturn(false);
            when(block(0,y,0).isSolid()).thenReturn(true);
            when(block(0,y,0).getType()).thenReturn(Material.STONE);
        }
        assertEquals(68,SafeTerrain.interior(world,0,0,64,.6,1.8,area).orElseThrow().getY());
    }
    @Test void aBodyCanTouchTheExclusiveAreaBoundaryWithoutCrossingIt() {
        assertEquals(7.5,SafeTerrain.interior(world,7,0,64,1,1.8,area).orElseThrow().getX());
    }
    @Test void rejectsFluidsWaterloggingVoidInsufficientSpaceAndBoundsWithoutLoadingChunks() {
        when(block(0,63,0).getType()).thenReturn(Material.WATER);
        assertTrue(SafeTerrain.interior(world,0,0,64,.6,1.8,area).isEmpty());
        when(block(0,63,0).getType()).thenReturn(Material.STONE);
        var logged=mock(Waterlogged.class);when(logged.isWaterlogged()).thenReturn(true);
        when(block(0,63,0).getBlockData()).thenReturn(logged);
        assertTrue(SafeTerrain.interior(world,0,0,64,.6,1.8,area).isEmpty());
        when(block(0,63,0).getBlockData()).thenReturn(null);when(block(0,63,0).isSolid()).thenReturn(false);
        assertTrue(SafeTerrain.interior(world,0,0,64,.6,1.8,area).isEmpty());
        when(block(0,63,0).isSolid()).thenReturn(true);when(block(0,65,0).isPassable()).thenReturn(false);
        assertTrue(SafeTerrain.interior(world,0,0,64,.6,1.8,area).isEmpty());
        assertTrue(SafeTerrain.interior(world,7,0,64,2,1.8,area).isEmpty());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        assertTrue(SafeTerrain.interior(world,0,0,64,.6,1.8,area).isEmpty());
        verify(world,never()).getChunkAt(anyInt(),anyInt());
    }
}
