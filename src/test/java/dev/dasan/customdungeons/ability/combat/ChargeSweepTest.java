package dev.dasan.customdungeons.ability.combat;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeSweepTest {
    @Test void detectsThinWallsBetweenSamplePoints() {
        var body=new BoundingBox(0,64,0,.1,64.4,.1);
        var wall=new BoundingBox(.155,64,0,.16,65,1);
        assertEquals(.055,ChargeSweep.entry(body,1,0,wall,1),1e-9);
    }
    @Test void diagonalSweepDoesNotCollideWithEmptyBoundingRectangleCorners() {
        var body=new BoundingBox(0,64,0,.1,65,.1);
        var corner=new BoundingBox(.7,64,0,.8,65,.1);
        assertEquals(Double.POSITIVE_INFINITY,ChargeSweep.entry(body,1,1,corner,1));
    }
    @Test void respectsSmallBodiesAndTheLowerVoxelsProtrudingShape() {
        var body=new BoundingBox(.44,64.000001,0,.56,64.36,.12);
        var slab=new BoundingBox(0,64.5,1,1,65,2);
        var fence=new BoundingBox(.375,63,1.375,.625,64.5,1.625);
        assertEquals(Double.POSITIVE_INFINITY,ChargeSweep.entry(body,0,1,slab,3));
        assertEquals(1.255,ChargeSweep.entry(body,0,1,fence,3),1e-9);
    }
    @Test void floorContactAndEndPointContactDoNotOverlap() {
        var body=new BoundingBox(0,64,0,1,66,1);
        assertEquals(Double.POSITIVE_INFINITY,ChargeSweep.entry(body,0,1,new BoundingBox(0,63,0,1,64,10),5));
        assertEquals(Double.POSITIVE_INFINITY,ChargeSweep.entry(body,0,1,new BoundingBox(0,64,2,1,66,3),1));
        assertEquals(0,ChargeSweep.entry(body,0,1,new BoundingBox(0,64,.5,1,66,2),1));
    }
}
