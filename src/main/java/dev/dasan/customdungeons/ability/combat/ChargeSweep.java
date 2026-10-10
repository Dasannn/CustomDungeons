package dev.dasan.customdungeons.ability.combat;

import org.bukkit.util.BoundingBox;

/** Exact continuous overlap of the mob's body and a public-API block collision box. */
final class ChargeSweep {
    private ChargeSweep() {}
    static double entry(BoundingBox body,double x,double z,BoundingBox obstacle,double distance) {
        return entry(body,x,z,obstacle,0,0,0,distance);
    }
    static double entry(BoundingBox body,double x,double z,BoundingBox obstacle,double offsetX,double offsetY,double offsetZ,double distance) {
        if(distance<=0 || body.getMaxY()<=obstacle.getMinY()+offsetY || body.getMinY()>=obstacle.getMaxY()+offsetY)return Double.POSITIVE_INFINITY;
        double minX=obstacle.getMinX()+offsetX,maxX=obstacle.getMaxX()+offsetX,minZ=obstacle.getMinZ()+offsetZ,maxZ=obstacle.getMaxZ()+offsetZ;
        double enterX=Double.NEGATIVE_INFINITY,exitX=Double.POSITIVE_INFINITY;
        double enterZ=Double.NEGATIVE_INFINITY,exitZ=Double.POSITIVE_INFINITY;
        if(x==0) {if(body.getMaxX()<=minX || body.getMinX()>=maxX)return Double.POSITIVE_INFINITY;}
        else {
            double a=(minX-body.getMaxX())/x,b=(maxX-body.getMinX())/x;
            enterX=Math.min(a,b);exitX=Math.max(a,b);
        }
        if(z==0) {if(body.getMaxZ()<=minZ || body.getMinZ()>=maxZ)return Double.POSITIVE_INFINITY;}
        else {
            double a=(minZ-body.getMaxZ())/z,b=(maxZ-body.getMinZ())/z;
            enterZ=Math.min(a,b);exitZ=Math.max(a,b);
        }
        double enter=Math.max(0,Math.max(enterX,enterZ)),exit=Math.min(distance,Math.min(exitX,exitZ));
        return enter<exit?enter:Double.POSITIVE_INFINITY;
    }
}
