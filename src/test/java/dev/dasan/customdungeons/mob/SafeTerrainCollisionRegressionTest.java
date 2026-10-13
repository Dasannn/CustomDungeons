package dev.dasan.customdungeons.mob;
import org.bukkit.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SafeTerrainCollisionRegressionTest {
 @Test void stairsUseBothCollisionBoxesAndSpaceUnderAnUpperSlabRemainsUsable() {
  var f=new SafeTerrainInteriorTest();var ground=f.block(0,63,0);var head=f.block(0,65,0);
  var stair=mock(org.bukkit.util.VoxelShape.class);var slab=mock(org.bukkit.util.VoxelShape.class);
  when(stair.getBoundingBoxes()).thenReturn(java.util.List.of(new BoundingBox(0,0,0,1,.5,1),new BoundingBox(.5,.5,0,1,1,1)));
  when(ground.getCollisionShape()).thenReturn(stair);when(ground.getType()).thenReturn(Material.STONE_STAIRS);
  when(slab.getBoundingBoxes()).thenReturn(java.util.List.of(new BoundingBox(0,.5,0,1,1,1)));
  when(head.getCollisionShape()).thenReturn(slab);when(head.isPassable()).thenReturn(false);
  assertEquals(64,SafeTerrain.validateAt(f.world,0,64,0,.6,1.4).orElseThrow().getY());
  assertEquals(64,SafeTerrain.interior(f.world,0,0,64,.6,1.4,f.area).orElseThrow().getY());
  assertTrue(SafeTerrain.validateAt(f.world,0,64,0,.6,1.6).isEmpty());
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.CsvSource({"OAK_FENCE,1.5", "COBBLESTONE_WALL,1.5", "STONE_SLAB,1.0", "STONE_STAIRS,1.0", "WHITE_CARPET,0.0625", "SNOW,0.375"})
 void feetStandAboveTheActualSupportCollision(Material type,double top) {
  var f=new SafeTerrainInteriorTest();var support=f.block(0,63,0);
  when(support.getType()).thenReturn(type);when(support.isSolid()).thenReturn(false);
  var shape=mock(org.bukkit.util.VoxelShape.class);
  when(shape.getBoundingBoxes()).thenReturn(java.util.List.of(new BoundingBox(.25,0,.25,.75,top,.75)));
  when(support.getCollisionShape()).thenReturn(shape);
  var feet=SafeTerrain.interior(f.world,0,0,64,.6,1.8,f.area).orElseThrow();
  assertEquals(63+top,feet.getY());
  when(f.world.getHighestBlockYAt(anyInt(),anyInt(),any())).thenReturn(63);
  assertEquals(63+top,SafeTerrain.validate(f.world,0,0,.6,1.8).orElseThrow().getY());
 }
 @Test void bodyCannotOverlapAFenceProtrudingFromTheNeighbouringSupport() {
  var f=new SafeTerrainInteriorTest();var neighbour=f.block(1,63,0);
  var shape=mock(org.bukkit.util.VoxelShape.class);
  when(shape.getBoundingBoxes()).thenReturn(java.util.List.of(new BoundingBox(.25,0,0,.75,1.5,1)));
  when(neighbour.getCollisionShape()).thenReturn(shape);
  assertTrue(SafeTerrain.interior(f.world,0,0,64,2,1.8,f.area).isEmpty());
 }

 @Test void interiorDoesNotPutFeetInsideTheProtrudingSupportBlock(){
  var f=new SafeTerrainInteriorTest();
  var ground=f.block(0,63,0);
  when(ground.getType()).thenReturn(Material.OAK_FENCE);
  var collision=new BoundingBox(0,63,0,1,64.5,1);
  when(ground.getBoundingBox()).thenReturn(collision);
  var point=SafeTerrain.interior(f.world,0,0,64,.6,1.8,f.area);
  assertTrue(point.isEmpty()||point.get().getY()>=collision.getMaxY(),"Interior floor candidate at "+point+" is inside a 1.5 block support collision");
 }
}
