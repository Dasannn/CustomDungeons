package dev.dasan.customdungeons.ability.combat;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ChargeShapeRegressionTest {
  static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
  @Test void localCollisionBoxesNeedNoCopiesOrMutationToApplyTheirWorldOffset() {
    try(var f=new CombatServiceTest.Fixture()) {
      var local=spy(new BoundingBox(0,0,0,1,1,1));var shape=mock(VoxelShape.class);
      when(shape.getBoundingBoxes()).thenReturn(List.of(local));
      var wall=f.world.getBlockAt(0,64,3);when(wall.isEmpty()).thenReturn(false);when(wall.getCollisionShape()).thenReturn(shape);
      f.cast("charge");f.step(45);
      assertTrue(f.mobPosition.getZ()<3,"The unchanged local shape must still block at its actual world position");
      verify(local,never()).clone();verify(local,never()).shift(anyDouble(),anyDouble(),anyDouble());
      assertEquals(new BoundingBox(0,0,0,1,1,1),local);
    }
  }
  @Test void eachSweptAdvanceReadsSolidShapesOnceAndRechecksNextTick() {
    try(var f=new CombatServiceTest.Fixture()) {
      when(f.entity.getWidth()).thenReturn(3.9);when(f.entity.getHeight()).thenReturn(4.4);
      var shape=mock(VoxelShape.class);when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,0,0,1,1,1)));
      var floor=f.world.getBlockAt(0,63,1);when(floor.getCollisionShape()).thenReturn(shape);
      f.cast("charge");f.step(20);clearInvocations(floor);f.step(1);
      verify(floor,times(1)).getCollisionShape();
      var newWall=f.world.getBlockAt(0,64,2);when(newWall.isEmpty()).thenReturn(false);
      when(newWall.getCollisionShape()).thenReturn(shape);f.step(1);
      assertTrue(CombatService.paused(f.caster),"A new solid wall must stop the very next swept advance");
      assertTrue(f.mobPosition.getZ()<1.3,"Cached shapes must not persist into another tick");
    }
  }
  @Test void emptyAirVoxelsDoNotResolveCollisionShapes() {
    try(var f=new CombatServiceTest.Fixture()) {
      f.cast("charge");f.step(25);
      for(var block:f.terrain.values())if(block.isEmpty())verify(block,never()).getCollisionShape();
    }
  }
  @Test void smallScaledMobCanChargeUnderTheActualTopSlabClearance() {
    try(var f=new CombatServiceTest.Fixture()) {
      f.mobPosition.setX(.5);
      f.positions.put(f.player,new org.bukkit.Location(f.world,.5,64,2));
      f.positions.put(f.ally,new org.bukkit.Location(f.world,1.5,64,2));
      when(f.entity.getWidth()).thenReturn(.12);when(f.entity.getHeight()).thenReturn(.36);
      var slab=f.world.getBlockAt(0,64,3);when(slab.getType()).thenReturn(Material.STONE_SLAB);when(slab.isEmpty()).thenReturn(false);
      var shape=mock(VoxelShape.class);
      when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,.5,0,1,1,1)));
      when(slab.getCollisionShape()).thenReturn(shape);
      f.cast("charge");f.step(45);
      assertTrue(f.mobPosition.getZ()>5,"A 0.36-high mob was falsely stopped under 0.5 blocks of clearance: z="+f.mobPosition.getZ());
    }
  }
  @Test void fenceShapeExtendingAboveLowerVoxelBlocksTheCharge() {
    try(var f=new CombatServiceTest.Fixture()) {
      var fence=f.world.getBlockAt(0,63,3);
      when(fence.getType()).thenReturn(Material.OAK_FENCE);
      var shape=mock(VoxelShape.class);
      when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(.375,0,.375,.625,1.5,.625)));
      when(fence.getCollisionShape()).thenReturn(shape);
      f.mobPosition.setX(.5);
      f.positions.put(f.player,new org.bukkit.Location(f.world,.5,64,2));
      f.positions.put(f.ally,new org.bukkit.Location(f.world,1.5,64,2));
      f.cast("charge");f.step(45);
      assertTrue(f.mobPosition.getZ()<3.375,"The mob has charged through a 1.5-block fence: z="+f.mobPosition.getZ());
    }
  }
}
