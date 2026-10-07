package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CinematicRouteTest {
    Region box(int x,int y,int z) {return Region.of("world",new BlockPos(x,y,z),new BlockPos(x+10,y+10,z+10));}
    DungeonDef dungeon(Region area,List<Region> regions,Region entrance) {
        var b=new DungeonSessionFlowTest().definition(3);
        var rooms=regions.stream().map(r->new RoomDef("r",r,null,null,UnlockMode.AUTOMATIC,null,List.of())).toList();
        return new DungeonDef(b.id(),b.displayName(),true,b.lobby(),b.exit(),1,0,0,3,false,0,0,false,b.scaling(),b.hooks(),b.reward(),rooms,List.of(),area)
                .withStart(StartMode.AUTO,List.of(),3,entrance,false,true,true,5);
    }
    final CinematicRoute.Limits limits=new CinematicRoute.Limits(-64,320,-1000,1000,-1000,1000);
    @Test void oneRoomWithoutAreaHasOrbitRoomCenterAndRoomZeroFinal() {
        var d=dungeon(null,List.of(box(0,60,0)),null);var route=CinematicRoute.calculate(d,limits);
        assertEquals(101,route.size());assertEquals(5.5,route.getLast().x());assertEquals(65.5,route.getLast().y());
        assertTrue(route.subList(0,40).stream().anyMatch(p->p.x()<0));
        assertTrue(route.stream().anyMatch(p->p.x()==5.5 && p.y()==65.5 && p.z()==5.5));
    }
    @Test void areaSetsOrbitButVisitsLRoomsInOrderAtDifferentHeightsThenEntrance() {
        var rs=List.of(box(0,60,0),box(30,80,0),box(30,100,30));
        var door=box(-10,60,0);var route=CinematicRoute.calculate(dungeon(box(0,120,0),rs,door),limits);
        int previous=40;
        for(var r:rs) {
            var center=CinematicRoute.center(r);
            int found=-1;for(int i=previous;i<route.size();i++)if(route.get(i).x()==center.x() && route.get(i).y()==center.y() && route.get(i).z()==center.z()){found=i;break;}
            assertTrue(found>=previous);previous=found;
        }
        assertEquals(CinematicRoute.center(door).x(),route.getLast().x());
        assertTrue(route.getFirst().y()>130);
    }
    @Test void fallbackIncludesDoorsAndLobbyAndAllSamplesStayWithinWorldLimits() {
        var d=dungeon(null,List.of(box(0,310,0)),box(-200,60,0));
        var tight=new CinematicRoute.Limits(-64,320,-25,25,-25,25);
        var route=CinematicRoute.calculate(d,tight);
        for(var p:route) {
            assertTrue(p.x()>=-25 && p.x()<=25);assertTrue(p.z()>=-25 && p.z()<=25);
            assertTrue(p.y()>=-63 && p.y()<=318);assertTrue(Float.isFinite(p.yaw()) && Float.isFinite(p.pitch()));
        }
        assertEquals(-200,CinematicRoute.bounds(d).min().x());
        assertEquals(10,CinematicRoute.bounds(d).max().x());
    }
    @Test void interpolatedSegmentsEaseSmoothlyAndDurationIsExact() {
        var d=dungeon(box(0,60,0),List.of(box(30,80,0)),null);
        var route=CinematicRoute.calculate(d,limits);assertEquals(d.introSeconds()*20+1,route.size());
        for(int i=1;i<route.size();i++)assertTrue(Math.abs(route.get(i).y()-route.get(i-1).y())<10);
    }

    @Test void roomCornersAndOrbitSeamHaveBoundedCameraTurns() {
        var route=CinematicRoute.calculate(dungeon(null,List.of(box(0,60,0),box(30,100,0),box(30,100,30)),null),limits);
        for(int i=1;i<route.size();i++) {
            assertTrue(Math.abs(route.get(i).yaw()-route.get(i-1).yaw())<=15.001,"yaw at frame "+i);
            assertTrue(Math.abs(route.get(i).pitch()-route.get(i-1).pitch())<=10.001,"pitch at frame "+i);
        }
    }
}
