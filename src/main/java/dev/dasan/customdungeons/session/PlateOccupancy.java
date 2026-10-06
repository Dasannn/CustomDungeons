package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;

/** Block occupancy from session players only; entities and redstone power are irrelevant. */
final class PlateOccupancy {
    private record Position(String world,int x,int y,int z) {
        static Position of(Point p) {return new Position(p.world(),(int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
    }
    static boolean allOccupied(List<Point> plates,List<Point> players) {
        if(plates.isEmpty())return false;
        var occupied=new HashSet<Position>();players.forEach(p->occupied.add(Position.of(p)));
        return plates.stream().allMatch(p->occupied.contains(Position.of(p)));
    }
}
