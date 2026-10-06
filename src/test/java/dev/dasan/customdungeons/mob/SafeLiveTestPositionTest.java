package dev.dasan.customdungeons.mob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SafeLiveTestPositionTest {
    @Test void requiresSupportedFootprintAndEnoughHeightForScaledWarden() {
        assertTrue(LiveTestService.safePosition(0,64,0,1.8,5.8,(x,y,z)->y==63,(x,y,z)->y>=64));
        assertFalse(LiveTestService.safePosition(0,64,0,1.8,5.8,(x,y,z)->false,(x,y,z)->true));
        assertFalse(LiveTestService.safePosition(0,64,0,1.8,5.8,(x,y,z)->y==63,(x,y,z)->y>=64&&y<69));
        assertFalse(LiveTestService.safePosition(0,64,0,1.8,5.8,(x,y,z)->y==63&&x==0,(x,y,z)->true));
    }
    @Test void exactFractionalPositionChecksItsWholeFootprintAndCeiling() {
        assertTrue(LiveTestService.safePosition(.9,64.25,.9,.6,1.95,(x,y,z)->y==63,(x,y,z)->y>=64&&y<=66));
        assertFalse(LiveTestService.safePosition(.9,64.25,.9,.6,1.95,(x,y,z)->y==63,(x,y,z)->y>=64&&y<66));
        assertFalse(LiveTestService.safePosition(.9,64.25,.9,.6,1.95,(x,y,z)->x==0&&z==0&&y==63,(x,y,z)->true));
    }
    @Test void publicApiDimensionsAreScaledAndProbeNeverSpawns() {
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        var warden=org.mockito.Mockito.mock(org.bukkit.entity.Warden.class);
        org.mockito.Mockito.when(world.createEntity(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(org.bukkit.entity.Warden.class))).thenReturn(warden);
        org.mockito.Mockito.when(warden.getWidth()).thenReturn(.9); org.mockito.Mockito.when(warden.getHeight()).thenReturn(2.9);
        org.mockito.Mockito.when(world.getMinHeight()).thenReturn(0); org.mockito.Mockito.when(world.getMaxHeight()).thenReturn(256);
        org.mockito.Mockito.when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        org.mockito.Mockito.when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
            int x=invocation.getArgument(0),y=invocation.getArgument(1),z=invocation.getArgument(2);
            var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
            org.mockito.Mockito.when(block.getType()).thenReturn(y==63 || y>=67 ? org.bukkit.Material.STONE : org.bukkit.Material.AIR);
            org.mockito.Mockito.when(block.isSolid()).thenReturn(y==63 || y>=67);
            org.mockito.Mockito.when(block.isEmpty()).thenReturn(y>=64 && y<67);
            org.mockito.Mockito.when(block.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1));
            return block;
        });
        var template=new dev.dasan.customdungeons.model.MobTemplate("warden","WARDEN","",100,10,.3,0,1,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false);
        var at=new org.bukkit.Location(world,.5,64,.5);
        assertEquals(at,LiveTestService.safeSpawnLocation(at,template).orElseThrow());
        var vanilla=new dev.dasan.customdungeons.model.MobTemplate("warden","WARDEN","",100,10,.3,0,0,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false);
        assertEquals(at,LiveTestService.safeSpawnLocation(at,vanilla).orElseThrow());
        var doubled=new dev.dasan.customdungeons.model.MobTemplate(template.id(),template.entityType(),"",100,10,.3,0,2,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false);
        // The ceiling prevents a six-block-tall Warden from standing on the available floor.
        assertTrue(LiveTestService.safeSpawnLocation(at,doubled).isEmpty());
        org.mockito.Mockito.verify(world,org.mockito.Mockito.never()).spawnEntity(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(org.bukkit.entity.EntityType.class));
    }

}
