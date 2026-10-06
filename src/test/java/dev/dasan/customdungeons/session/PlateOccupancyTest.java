package dev.dasan.customdungeons.session;
import dev.dasan.customdungeons.model.Point;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlateOccupancyTest {
    Point point(String world,double x,double y){return new Point(world,x,y,0.5,0,0);}
    @Test void requiresEveryDistinctBlockAndCorrectWorldAndHeight() {
        var a=point("world",-.5,64);var b=point("world",.5,64);
        var plates=List.of(a,b);
        assertFalse(PlateOccupancy.allOccupied(List.of(),List.of(a)));
        assertFalse(PlateOccupancy.allOccupied(plates,List.of(a,a)));
        assertFalse(PlateOccupancy.allOccupied(plates,List.of(a,point("other",.5,64))));
        assertFalse(PlateOccupancy.allOccupied(plates,List.of(a,point("world",.5,65))));
        assertTrue(PlateOccupancy.allOccupied(plates,List.of(point("world",-.2,64.0625),point("world",.7,64.0625))));
    }
}
