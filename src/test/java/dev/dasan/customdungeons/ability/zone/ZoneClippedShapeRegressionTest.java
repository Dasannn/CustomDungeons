package dev.dasan.customdungeons.ability.zone;
import dev.dasan.customdungeons.mob.MobArea;
import java.util.*;
import org.bukkit.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ZoneClippedShapeRegressionTest {
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.CsvSource({"-3,-3,0,0", "-3,-3,1,1", "-3,-3,1.8,1.2", "1,-3,1.05,1.65", "1,-3,1.05,1.7", "1,1,1.01,1.01", "1,1,1.5,1.5", "1,1,1.2,1.2"})
 void damageAndTheReceivedPolygonAgreeAtCentreEdgeAndCorner(double minX,double minZ,double x,double z) {
  try(var f=new ZoneServiceTest.Fixture()) {
   var area=new MobArea("test",new BoundingBox(minX,60,minZ,3,80,3));when(f.host.area()).thenReturn(area);
   f.positions.put(f.player,new Location(f.world,0,64,0));f.positions.put(f.ally,new Location(f.world,x,64,z));
   f.cast("poison_pools",Map.of("count",1,"radius",2));
   var polygon=f.warningMarks(f.ally);f.step(20);
   if(polygonContains(polygon,x,z))verify(f.ally).damage(1,f.entity);
   else verify(f.ally,never()).damage(anyDouble(),any(org.bukkit.entity.Entity.class));
  }
 }
 static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
 static boolean polygonContains(List<Location> marks,double x,double z){
  boolean inside=false;
  for(int i=0,j=marks.size()-1;i<marks.size();j=i++){
   var a=marks.get(i);var b=marks.get(j);
   if((a.getZ()>z)!=(b.getZ()>z)&&x<(b.getX()-a.getX())*(z-a.getZ())/(b.getZ()-a.getZ())+a.getX())inside=!inside;
  }return inside;
 }
 @Test void clippedPoolMustNotDamageBeyondItsDrawnBoundary(){
  try(var f=new ZoneServiceTest.Fixture()){
   var area=new MobArea("test",new BoundingBox(1,60,-3,3,80,3));when(f.host.area()).thenReturn(area);
   f.positions.put(f.player,new Location(f.world,0,64,0));
   f.positions.put(f.ally,new Location(f.world,1.05,64,1.65));
   f.cast("poison_pools",Map.of("count",1,"radius",2));
   var initial=f.warningMarks(f.ally);
   assertFalse(initial.isEmpty());assertTrue(initial.stream().allMatch(area::contains));
   f.step(20);verify(f.ally).damage(1,f.entity);
   assertTrue(polygonContains(initial,1.05,1.65),"Damage at (1.05,1.65) outside clipped eight-point warning "+initial);
  }
 }
 @Test void clippedPoolTouchingRoomCornerMustNotCancelItsTrueIntersection(){
  try(var f=new ZoneServiceTest.Fixture()){
   var area=new MobArea("test",new BoundingBox(1,60,2,3,80,4));when(f.host.area()).thenReturn(area);
   f.positions.put(f.player,new Location(f.world,0,64,.4));
   f.positions.put(f.ally,new Location(f.world,1.01,64,2.01));
   assertTrue(Math.hypot(1.01,2.01-.4)<2);
   f.cast("poison_pools",Map.of("count",1,"radius",2));f.step(20);
   verify(f.ally).damage(1,f.entity);
  }
 }
}
