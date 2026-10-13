package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.mob.MobArea;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

/** Clips an ordered warning boundary, including the new edges along the room walls. */
public final class AreaOutline {
    private AreaOutline() {}
    /** Circular boundary with room-edge intersections and the nearest corner represented explicitly. */
    public static List<Location> circle(MobArea area,Location center,double radius) {
        var angles=new java.util.TreeSet<Double>();
        for(int i=0;i<12;i++)angles.add(2*Math.PI*i/12);
        if(area!=null) {
            var b=area.bounds();
            double minX=Math.ceil(b.getMinX()),maxX=Math.nextDown(Math.ceil(b.getMaxX()));
            double minZ=Math.ceil(b.getMinZ()),maxZ=Math.nextDown(Math.ceil(b.getMaxZ()));
            double nearestX=Math.clamp(center.getX(),minX,maxX),nearestZ=Math.clamp(center.getZ(),minZ,maxZ);
            if(nearestX!=center.getX()||nearestZ!=center.getZ())addAngle(angles,Math.atan2(nearestZ-center.getZ(),nearestX-center.getX()));
            for(double x:new double[]{minX,maxX}) {
                double d=(x-center.getX())/radius;
                if(Math.abs(d)<1){double a=Math.acos(d);addAngle(angles,a);addAngle(angles,-a);}
            }
            for(double z:new double[]{minZ,maxZ}) {
                double d=(z-center.getZ())/radius;
                if(Math.abs(d)<1){double a=Math.asin(d);addAngle(angles,a);addAngle(angles,Math.PI-a);}
            }
        }
        return clip(area,angles.stream().map(a->center.clone().add(radius*Math.cos(a),.1,radius*Math.sin(a))).toList());
    }
    private static void addAngle(java.util.Set<Double> angles,double angle) {angles.add((angle+2*Math.PI)%(2*Math.PI));}
    /** The boundary itself is included. The same ordered vertices are drawn and queried. */
    public static boolean contains(List<Location> polygon,double x,double z) {
        if(polygon.size()<3)return false;
        boolean inside=false;
        for(int i=0,j=polygon.size()-1;i<polygon.size();j=i++) {
            var a=polygon.get(j);var b=polygon.get(i);
            double dx=b.getX()-a.getX(),dz=b.getZ()-a.getZ();
            double cross=(x-a.getX())*dz-(z-a.getZ())*dx;
            if(Math.abs(cross)<=1e-9&&x>=Math.min(a.getX(),b.getX())-1e-9&&x<=Math.max(a.getX(),b.getX())+1e-9
                    &&z>=Math.min(a.getZ(),b.getZ())-1e-9&&z<=Math.max(a.getZ(),b.getZ())+1e-9)return true;
            if((a.getZ()>z)!=(b.getZ()>z)&&x<dx*(z-a.getZ())/dz+a.getX())inside=!inside;
        }
        return inside;
    }
    public static List<Location> clip(MobArea area,List<Location> polygon) {
        if(area==null||polygon.isEmpty())return polygon;
        var first=polygon.getFirst();var box=area.bounds();
        if(first.getWorld()==null||!area.world().equals(first.getWorld().getName())
                ||first.getBlockY()<box.getMinY()||first.getBlockY()>=box.getMaxY())return List.of();
        // MobArea tests block coordinates. Keep the exclusive far boundary in the last valid block.
        double[] edges={Math.ceil(box.getMinX()),Math.nextDown(Math.ceil(box.getMaxX())),
                Math.ceil(box.getMinZ()),Math.nextDown(Math.ceil(box.getMaxZ()))};
        List<Location> result=polygon;
        for(int edge=0;edge<4&&!result.isEmpty();edge++) {
            var next=new ArrayList<Location>();Location previous=result.getLast();
            boolean previousInside=inside(previous,edge,edges[edge]);
            for(var at:result) {
                boolean nowInside=inside(at,edge,edges[edge]);
                if(nowInside!=previousInside) {
                    double fraction=(edges[edge]-coordinate(previous,edge))/(coordinate(at,edge)-coordinate(previous,edge));
                    var intersection=previous.clone().add(at.toVector().subtract(previous.toVector()).multiply(fraction));
                    if(edge<2)intersection.setX(edges[edge]);else intersection.setZ(edges[edge]);
                    next.add(intersection);
                }
                if(nowInside)next.add(at);
                previous=at;previousInside=nowInside;
            }
            result=next;
        }
        return List.copyOf(result);
    }
    private static double coordinate(Location at,int edge) {return edge<2?at.getX():at.getZ();}
    private static boolean inside(Location at,int edge,double boundary) {
        return edge%2==0?coordinate(at,edge)>=boundary:coordinate(at,edge)<=boundary;
    }
}
