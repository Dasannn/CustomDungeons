package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;

/** Pure geometry. One immutable frame per tick, precomputed before the spectator mutation. */
final class CinematicRoute {
    record Limits(double minY,double maxY,double minX,double maxX,double minZ,double maxZ) {
        Limits {
            if(!Double.isFinite(minY+maxY+minX+maxX+minZ+maxZ) || maxY-minY<3 || minX>maxX || minZ>maxZ)
                throw new IllegalArgumentException("Invalid camera bounds");
        }
        Point clamp(Point p) {
            return new Point(p.world(),Math.clamp(p.x(),minX,maxX),Math.clamp(p.y(),minY+1,maxY-2),
                    Math.clamp(p.z(),minZ,maxZ),0,0);
        }
    }
    static Point center(Region r) {
        return new Point(r.world(),((double)r.min().x()+r.max().x()+1)/2,
                ((double)r.min().y()+r.max().y()+1)/2,((double)r.min().z()+r.max().z()+1)/2,0,0);
    }
    static Region bounds(DungeonDef def) {
        if(def.area()!=null)return def.area();
        var regions=new ArrayList<Region>();
        for(var room:def.rooms()) {regions.add(room.region());if(room.door()!=null)regions.add(room.door());}
        if(def.entranceDoor()!=null)regions.add(def.entranceDoor());
        var lobby=def.lobby();regions.add(Region.of(lobby.world(),new BlockPos((int)Math.floor(lobby.x()),(int)Math.floor(lobby.y()),(int)Math.floor(lobby.z())),
                new BlockPos((int)Math.floor(lobby.x()),(int)Math.floor(lobby.y()),(int)Math.floor(lobby.z()))));
        String world=regions.getFirst().world();
        if(regions.stream().anyMatch(r->!world.equals(r.world())))throw new IllegalArgumentException("Camera route spans worlds");
        return Region.of(world,new BlockPos(regions.stream().mapToInt(r->r.min().x()).min().orElseThrow(),
                regions.stream().mapToInt(r->r.min().y()).min().orElseThrow(),regions.stream().mapToInt(r->r.min().z()).min().orElseThrow()),
                new BlockPos(regions.stream().mapToInt(r->r.max().x()).max().orElseThrow(),regions.stream().mapToInt(r->r.max().y()).max().orElseThrow(),
                        regions.stream().mapToInt(r->r.max().z()).max().orElseThrow()));
    }
    static List<Point> calculate(DungeonDef def,Limits limits) {
        if(def.introSeconds()<5 || def.introSeconds()>20 || def.rooms().isEmpty())throw new IllegalArgumentException("Invalid cinematic duration/rooms");
        Region box=bounds(def);Point target=limits.clamp(center(box));String world=box.world();
        int duration=def.introSeconds()*20,orbit=duration*2/5;
        double radius=Math.hypot(((double)box.max().x()-box.min().x()+1)/2,
                ((double)box.max().z()-box.min().z()+1)/2)+8;
        double height=box.max().y()+12;
        var frames=new ArrayList<Point>(duration+1);
        for(int tick=0;tick<=orbit;tick++) {
            double angle=2*Math.PI*ease((double)tick/orbit);
            Point at=limits.clamp(new Point(world,target.x()+Math.cos(angle)*radius,height,target.z()+Math.sin(angle)*radius,0,0));
            frames.add(look(at,target));
        }
        var stops=new ArrayList<Point>();stops.add(frames.getLast());
        if(def.rooms().stream().anyMatch(room->!world.equals(room.region().world())))throw new IllegalArgumentException("Camera route spans worlds");
        // Reserve ten ticks for the final entrance leg, then at least ten per room visit.
        int remaining=duration-orbit,visits=Math.min(def.rooms().size(),(remaining-10)/10);
        for(int visit=0;visit<visits;visit++) {
            int index=visits==1?0:(int)Math.round((double)visit*(def.rooms().size()-1)/(visits-1));
            var room=def.rooms().get(index);
            stops.add(limits.clamp(center(room.region())));
        }
        stops.add(limits.clamp(center(def.entranceDoor()!=null?def.entranceDoor():def.rooms().getFirst().region())));
        int legs=stops.size()-1;
        for(int leg=0;leg<legs;leg++) {
            int from=remaining*leg/legs,to=remaining*(leg+1)/legs;
            for(int t=1;t<=to-from;t++) {
                Point a=stops.get(leg),b=stops.get(leg+1);double u=ease((double)t/(to-from));
                Point at=new Point(world,a.x()+(b.x()-a.x())*u,a.y()+(b.y()-a.y())*u,a.z()+(b.z()-a.z())*u,0,0);
                // At the target itself keep the last orientation instead of snapping to yaw/pitch zero.
                Point faced=look(at,b);
                if(at.x()==b.x() && at.y()==b.y() && at.z()==b.z()) {
                    var previous=frames.getLast();faced=new Point(world,at.x(),at.y(),at.z(),previous.yaw(),previous.pitch());
                }
                frames.add(faced);
            }
        }
        // Shortest-arc turns stay continuous at the yaw seam and at room corners.
        for(int i=1;i<frames.size();i++) {
            Point p=frames.get(i);float previous=frames.get(i-1).yaw();
            float delta=(p.yaw()-previous)%360;if(delta>180)delta-=360;if(delta<-180)delta+=360;
            float pitch=frames.get(i-1).pitch();
            frames.set(i,new Point(p.world(),p.x(),p.y(),p.z(),previous+Math.clamp(delta,-15,15),
                    pitch+Math.clamp(p.pitch()-pitch,-10,10)));
        }
        return List.copyOf(frames);
    }
    private static double ease(double t) {return t*t*(3-2*t);}
    private static Point look(Point at,Point target) {
        double dx=target.x()-at.x(),dy=target.y()-at.y(),dz=target.z()-at.z();
        return new Point(at.world(),at.x(),at.y(),at.z(),(float)Math.toDegrees(Math.atan2(-dx,dz)),
                (float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
    }
}
