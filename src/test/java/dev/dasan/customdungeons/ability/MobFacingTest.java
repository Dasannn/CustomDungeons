package dev.dasan.customdungeons.ability;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobFacingTest {
    @Test void movementTurnsTheBodyTowardItsActualHorizontalAdvance() {
        var from=new Location(null,0,64,0,180,0);
        var to=new Location(null,0,64.4,1,180,0);
        var facing=to.clone();facing.setYaw(MobMovement.movementYaw(from,to));facing.setPitch(0);
        assertTrue(facing.getDirection().dot(to.toVector().subtract(from.toVector()).setY(0))>.99);
        to.setX(1);to.setZ(0);facing.setYaw(MobMovement.movementYaw(from,to));
        assertTrue(facing.getDirection().getX()>.99);
    }
    @Test void verticalMotionKeepsTheExistingDirection() {
        var from=new Location(null,0,64,0,45,0);
        var to=from.clone().add(0,1,0);to.setYaw(125);
        assertEquals(125,MobMovement.movementYaw(from,to));
    }
}
