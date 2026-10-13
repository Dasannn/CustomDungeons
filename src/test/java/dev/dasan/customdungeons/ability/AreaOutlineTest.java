package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobArea;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AreaOutlineTest {
    final World world=mock(World.class);
    Location at(double x,double z){return new Location(world,x,64.1,z);}
    @Test void enclosingZoneClipsToTheRoomInsteadOfCancellingOrMovingItsCenter() {
        when(world.getName()).thenReturn("room");
        var area=new MobArea("room",new BoundingBox(-3,60,-3,3,70,3));
        var points=AreaOutline.clip(area,List.of(at(-8,-8),at(8,-8),at(8,8),at(-8,8)));
        assertEquals(4,points.size());assertTrue(points.stream().allMatch(area::contains));
        assertEquals(-3,points.stream().mapToDouble(Location::getX).min().orElseThrow());
        assertEquals(3,points.stream().mapToDouble(Location::getX).max().orElseThrow(),1e-8);
    }
    @Test void disjointZoneHasNoWarningAndPartialZoneKeepsOnlyTheIntersection() {
        when(world.getName()).thenReturn("room");
        var area=new MobArea("room",new BoundingBox(0,60,0,4,70,4));
        assertTrue(AreaOutline.clip(area,List.of(at(5,5),at(8,5),at(8,8),at(5,8))).isEmpty());
        var points=AreaOutline.clip(area,List.of(at(-2,1),at(2,1),at(2,3),at(-2,3)));
        assertEquals(4,points.size());assertTrue(points.stream().allMatch(area::contains));
        assertEquals(0,points.stream().mapToDouble(Location::getX).min().orElseThrow());
        assertEquals(2,points.stream().mapToDouble(Location::getX).max().orElseThrow());
    }
    @Test void differentWorldOrHeightNeverProjectsAnUnrelatedWarningIntoTheRoom() {
        when(world.getName()).thenReturn("room");
        var polygon=List.of(at(0,0),at(1,0),at(1,1),at(0,1));
        assertTrue(AreaOutline.clip(new MobArea("other",new BoundingBox(-3,60,-3,3,70,3)),polygon).isEmpty());
        assertTrue(AreaOutline.clip(new MobArea("room",new BoundingBox(-3,65,-3,3,70,3)),polygon).isEmpty());
        assertEquals(polygon,AreaOutline.clip(null,polygon));
    }
}
