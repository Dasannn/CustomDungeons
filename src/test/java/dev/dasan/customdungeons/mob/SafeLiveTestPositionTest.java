package dev.dasan.customdungeons.mob;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SafeLiveTestPositionTest {
    @Test void scaledWardenRequiresSupportAndEnoughHeight() {
        LiveTestService.CollisionBoxes floor=(x,y,z)-> y==63 ? java.util.List.of(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1)) : java.util.List.of();
        assertTrue(LiveTestService.safePosition(.5,64,.5,1.8,5.8,floor));
        assertFalse(LiveTestService.safePosition(.5,64,.5,1.8,5.8,(x,y,z)->java.util.List.of()));
        assertFalse(LiveTestService.safePosition(.5,64,.5,1.8,5.8,(x,y,z)->y==69 ? java.util.List.of(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1)):floor.at(x,y,z)));
    }
    @Test void exactFractionalBodyChecksCeilingAndPartialSupport() {
        LiveTestService.CollisionBoxes floor=(x,y,z)-> y==63 ? java.util.List.of(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1)) : java.util.List.of();
        assertTrue(LiveTestService.safePosition(.9,64,.9,.6,1.95,floor));
        assertFalse(LiveTestService.safePosition(.9,64.25,.9,.6,1.95,(x,y,z)->y==66 ? java.util.List.of(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+1,z+1)):floor.at(x,y,z)));
        // As in Minecraft, part of a collision surface under the feet can support the body.
        assertTrue(LiveTestService.safePosition(.9,64,.9,.6,1.95,(x,y,z)->x==0&&z==0 ? floor.at(x,y,z):java.util.List.of()));
    }
    @Test void collisionFacesMayTouchFeetAndHeadButMustNotOverlapBody() {
        LiveTestService.CollisionBoxes boxes=(x,y,z)-> {
            if(y==64) return java.util.List.of(new org.bukkit.util.BoundingBox(x,y,z,x+1,y+.5,z+1));
            if(y==66) return java.util.List.of(new org.bukkit.util.BoundingBox(x,y+.5,z,x+1,y+1,z+1));
            return java.util.List.of();
        };
        assertTrue(LiveTestService.safePosition(.5,64.5,.5,.6,2,boxes));
        assertFalse(LiveTestService.safePosition(.5,64.49,.5,.6,2,boxes));
        assertFalse(LiveTestService.safePosition(.5,64.5,.5,.6,2.01,boxes));
        assertFalse(LiveTestService.safePosition(.5,64.5,.5,.6,4,boxes));
        assertFalse(LiveTestService.safePosition(.5,64.5,.5,.6,2,(x,y,z)->java.util.List.of()));
        assertFalse(LiveTestService.safePosition(.5,64.5,.5,.6,2,(x,y,z)->null),"Unloaded space must not load chunks");
    }
    @Test void compoundStairShapeAllowsBodyInItsEmptyHalfAndRejectsItsRaisedHalf() {
        LiveTestService.CollisionBoxes boxes=(x,y,z)-> y==64 ? java.util.List.of(
                new org.bukkit.util.BoundingBox(x,y,z,x+1,y+.5,z+1),
                new org.bukkit.util.BoundingBox(x+.5,y+.5,z,x+1,y+1,z+1)) : java.util.List.of();
        assertTrue(LiveTestService.safePosition(.25,64.5,.5,.4,1,boxes));
        assertFalse(LiveTestService.safePosition(.75,64.5,.5,.4,1,boxes));
        assertTrue(LiveTestService.safePosition(.75,65,.5,.4,1,boxes));
    }

    @Test void lowerSlabSupportsMobAtItsActualTopWithoutFalseWarning() {
        var world=org.mockito.Mockito.mock(org.bukkit.World.class);
        var warden=org.mockito.Mockito.mock(org.bukkit.entity.Warden.class);
        org.mockito.Mockito.when(world.createEntity(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(org.bukkit.entity.Warden.class))).thenReturn(warden);
        org.mockito.Mockito.when(warden.getWidth()).thenReturn(.9); org.mockito.Mockito.when(warden.getHeight()).thenReturn(2.9);
        org.mockito.Mockito.when(world.getMinHeight()).thenReturn(0); org.mockito.Mockito.when(world.getMaxHeight()).thenReturn(256);
        org.mockito.Mockito.when(world.isChunkLoaded(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        org.mockito.Mockito.when(world.getBlockAt(org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
            int x=invocation.getArgument(0),y=invocation.getArgument(1),z=invocation.getArgument(2);
            boolean slab=x==10&&y==64&&z==-3;
            var block=org.mockito.Mockito.mock(org.bukkit.block.Block.class);
            org.mockito.Mockito.when(block.getType()).thenReturn(slab?org.bukkit.Material.STONE_SLAB:org.bukkit.Material.AIR);
            org.mockito.Mockito.when(block.isSolid()).thenReturn(slab);
            org.mockito.Mockito.when(block.isEmpty()).thenReturn(!slab);
            org.mockito.Mockito.when(block.getBoundingBox()).thenReturn(slab?new org.bukkit.util.BoundingBox(x,y,z,x+1,y+.5,z+1):new org.bukkit.util.BoundingBox());
            var shape=org.mockito.Mockito.mock(org.bukkit.util.VoxelShape.class);
            // Block collision shapes use coordinates relative to the block.
            org.mockito.Mockito.when(shape.getBoundingBoxes()).thenReturn(slab?java.util.List.of(new org.bukkit.util.BoundingBox(0,0,0,1,.5,1)):java.util.List.of());
            org.mockito.Mockito.when(block.getCollisionShape()).thenReturn(shape);
            return block;
        });
        var template=new dev.dasan.customdungeons.model.MobTemplate("warden","WARDEN","",100,10,.3,0,1,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false);
        var at=new org.bukkit.Location(world,10.5,64.5,-2.5);
        assertEquals(at,LiveTestService.safeSpawnLocation(at,template).orElseThrow());
        assertTrue(LiveTestService.safeSpawnLocation(at.clone().subtract(0,.01,0),template).isEmpty(),"Sinking into the slab must still warn");
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
            var shape=org.mockito.Mockito.mock(org.bukkit.util.VoxelShape.class);
            org.mockito.Mockito.when(shape.getBoundingBoxes()).thenReturn(y==63||y>=67 ? java.util.List.of(new org.bukkit.util.BoundingBox(0,0,0,1,1,1)):java.util.List.of());
            org.mockito.Mockito.when(block.getCollisionShape()).thenReturn(shape);
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
        var attributeScaled=new dev.dasan.customdungeons.model.MobTemplate(template.id(),template.entityType(),"",100,10,.3,0,0,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false,
            new dev.dasan.customdungeons.model.MobAttributes(java.util.Map.of("scale",16d)));
        assertTrue(LiveTestService.safeSpawnLocation(at,attributeScaled).isEmpty(),"Attribute scale must determine the body height");
        var attributeSmall=new dev.dasan.customdungeons.model.MobTemplate(template.id(),template.entityType(),"",100,10,.3,0,16,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false,
            new dev.dasan.customdungeons.model.MobAttributes(java.util.Map.of("scale",1d)));
        assertEquals(at,LiveTestService.safeSpawnLocation(at,attributeSmall).orElseThrow());
        // The ceiling prevents a six-block-tall Warden from standing on the available floor.
        assertTrue(LiveTestService.safeSpawnLocation(at,doubled).isEmpty());
        var attributeZero=new dev.dasan.customdungeons.model.MobTemplate(template.id(),template.entityType(),"",100,10,.3,0,16,
            java.util.Map.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),false,"RED",null,java.util.List.of(),false,
            new dev.dasan.customdungeons.model.MobAttributes(java.util.Map.of("scale",0d)));
        org.mockito.Mockito.when(warden.getHeight()).thenReturn(20d);
        assertTrue(LiveTestService.safeSpawnLocation(at,vanilla).isEmpty(),"Legacy zero still means vanilla dimensions");
        assertEquals(at,LiveTestService.safeSpawnLocation(at,attributeZero).orElseThrow(),"Explicit attribute zero uses Minecraft's minimum scale");
        org.mockito.Mockito.verify(world,org.mockito.Mockito.never()).spawnEntity(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(org.bukkit.entity.EntityType.class));
    }

}
