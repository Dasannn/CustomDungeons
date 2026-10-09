package dev.dasan.customdungeons.ability.zone;

/** Geometry and hard ceilings, independent of Paper and host state. */
public final class ZoneRules {
    private ZoneRules() {}
    public static boolean cone(double x,double z,double dx,double dz,double radius,double angle) {
        double length=Math.hypot(x,z),direction=Math.hypot(dx,dz);
        return length<=radius && (length==0 || direction>0 && (x*dx+z*dz)/(length*direction)>=Math.cos(Math.toRadians(angle/2)));
    }
    public static boolean beam(double x,double z,double dx,double dz,double length,double width) {
        double along=x*dx+z*dz;
        return along>=0 && along<=length && Math.abs(x*dz-z*dx)<=width/2;
    }
    public static boolean cracked(double x,double z,int pattern,int wave) {
        int cx=(int)Math.floor(x/2),cz=(int)Math.floor(z/2);
        // Paired half-rings leave half the tiles open, also across negative coordinates.
        int cell=switch(pattern) {case 1 -> (int)Math.floor(Math.hypot(cx+.5,cz+.5))+(cx<0?1:0);case 2 -> cx;default -> cx+cz;};
        return Math.floorMod(cell+wave,2)==0;
    }
    public record Tile(int x,int z) {}
    /** Include partial edge tiles; drawing must use the same 2x2 grid as damage. */
    public static java.util.List<Tile> tiles(double radius,int pattern,int wave) {
        if(!Double.isFinite(radius)||radius<0||radius>16)return java.util.List.of();
        var result=new java.util.ArrayList<Tile>();
        int minimum=(int)Math.floor(-radius/2),maximum=(int)Math.floor(radius/2);
        for(int cx=minimum;cx<=maximum;cx++)for(int cz=minimum;cz<=maximum;cz++) {
            int x=cx*2,z=cz*2;
            double nearestX=Math.clamp(0,x,x+2),nearestZ=Math.clamp(0,z,z+2);
            if(nearestX*nearestX+nearestZ*nearestZ<=radius*radius&&cracked(x,z,pattern,wave))result.add(new Tile(x,z));
        }
        return java.util.List.copyOf(result);
    }
    public static int zoneLimit(int mobs) {return Math.clamp(mobs,0,16);}
    public static int arrowLimit(int mobs) {return (int)Math.clamp((long)mobs*8,0,64);}
    public static int particleBudget(double density) {return Double.isFinite(density)?(int)Math.clamp(Math.round(128*density),0,128):32;}
    public static double damage(double amount,double health,double maximum) {
        if(!Double.isFinite(amount)||amount<=0)return 0;
        return health>=maximum ? Math.min(amount,Math.max(0,health-1)) : amount;
    }
    public static boolean riftDistance(double nearest,double minimum,double maximum) {
        return Double.isFinite(nearest)&&nearest>=minimum&&nearest<=maximum;
    }
}
