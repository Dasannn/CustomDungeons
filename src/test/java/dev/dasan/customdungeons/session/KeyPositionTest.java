package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.bukkit.*;
import org.bukkit.block.Block;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyPositionTest {
    final Region room=Region.of("arena",new BlockPos(20,64,0),new BlockPos(39,72,10));
    final Point checkpoint=new Point("arena",23,65,5,-90,0);
    Region door(int x, int z) { return Region.of("arena",new BlockPos(x,65,z),new BlockPos(x,68,z)); }

    @Test void wallAtNegativeZCannotPutReplacementOutsideSecondRoom() {
        var gate=Region.of("arena",new BlockPos(39,65,0),new BlockPos(39,68,10));
        Point at=DoorService.keyPosition(room,gate,checkpoint,p -> p.y()==65 && p.x()<39);
        assertEquals(new Point("arena",38.5,65.5,4.5,0,0),at);
        assertTrue(room.contains(at.world(),(int)Math.floor(at.x()),(int)Math.floor(at.y()),(int)Math.floor(at.z())));
    }
    @Test void nearestSafeBlockWorksOnEverySideAndRejectsObstructedOrUnsupportedBlocks() {
        Set<BlockPos> safe=Set.of(new BlockPos(21,65,5),new BlockPos(37,65,5),new BlockPos(25,65,1),new BlockPos(25,65,9));
        assertEquals(21.5,DoorService.keyPosition(room,door(20,5),checkpoint,safe::contains).x());
        assertEquals(37.5,DoorService.keyPosition(room,door(39,5),checkpoint,safe::contains).x());
        assertEquals(1.5,DoorService.keyPosition(room,door(25,0),checkpoint,safe::contains).z());
        assertEquals(9.5,DoorService.keyPosition(room,door(25,10),checkpoint,safe::contains).z());
    }
    @Test void noSafeBlockOrNoDoorUsesExactRoomCheckpoint() {
        assertEquals(checkpoint,DoorService.keyPosition(room,door(39,5),checkpoint,p -> false));
        assertEquals(checkpoint,DoorService.keyPosition(room,null,checkpoint,p -> true));
    }
    @Test void bothBlocksOfHeadroomMustBeInsideRoom() {
        assertEquals(checkpoint,DoorService.keyPosition(room,door(39,5),checkpoint,p -> p.y()==72));
    }
    @Test void runtimeRequiresAirAtFeetAndHeadAndSolidFloor() {
        World world=mock(World.class);
        Material air=mock(Material.class), stone=mock(Material.class), glass=mock(Material.class);
        when(air.isAir()).thenReturn(true); when(stone.isSolid()).thenReturn(true); when(glass.isSolid()).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(call -> {
            int x=call.getArgument(0), y=call.getArgument(1), z=call.getArgument(2);
            Block block=mock(Block.class);
            // Closest cells: no floor at z=0, glass at feet at z=1, glass overhead at z=2.
            Material type=y==64 ? (z==0 ? air : stone) : air;
            if (x==39 || x==38 && (z==1 && y==65 || z==2 && y==66)) type=glass;
            when(block.getType()).thenReturn(type); return block;
        });
        Point middle=new Point("arena",23,65,0,0,0);
        var gate=Region.of("arena",new BlockPos(39,65,0),new BlockPos(39,68,10));
        var lowRoom=Region.of("arena",new BlockPos(20,64,0),new BlockPos(39,66,10));
        var def=new RoomDef("second",lowRoom,middle,gate,UnlockMode.KEY,null,List.of());
        try (var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("arena")).thenReturn(world);
            Location at=DoorService.keyRespawn(def);
            assertEquals(38.5,at.getX()); assertEquals(65.5,at.getY()); assertEquals(3.5,at.getZ());
        }
    }

}
