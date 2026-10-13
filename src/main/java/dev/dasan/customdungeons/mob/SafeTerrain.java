package dev.dasan.customdungeons.mob;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.util.BoundingBox;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

/** Shared main-thread terrain validation. Never requests or loads a chunk. */
public final class SafeTerrain {
    private SafeTerrain() {}
    public static boolean safeColumn(boolean solid,boolean fluid,boolean leaves,double clearance,double height,boolean border) {
        return solid&&!fluid&&!leaves&&border&&Double.isFinite(height)&&height>0&&clearance>=height;
    }
    public static boolean hazardous(Block b) {
        var type=b.getType();
        return b.isLiquid() || b.getBlockData() instanceof Waterlogged w&&w.isWaterlogged()
                || type==Material.WATER || type==Material.LAVA || type==Material.KELP || type==Material.KELP_PLANT
                || type==Material.SEAGRASS || type==Material.TALL_SEAGRASS || type==Material.BUBBLE_COLUMN
                || type.name().endsWith("_LEAVES");
    }
    public static boolean insideBorder(World world,double x,double z,double half) {
        var border=world.getWorldBorder();var center=border.getCenter();double radius=border.getSize()/2;
        return x-half>=center.getX()-radius && x+half<=center.getX()+radius
                && z-half>=center.getZ()-radius && z+half<=center.getZ()+radius;
    }
    public static Optional<Location> validate(World world,int x,int z,double width,double height) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0||!insideBorder(world,x+.5,z+.5,width/2))return Optional.empty();
        if(!world.isChunkLoaded(x>>4,z>>4))return Optional.empty();
        return validateAt(world,x,world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE)+1,z,width,height);
    }
    /** Nearest safe walking floor in this encounter, including rooms below a solid roof. */
    public static Optional<Location> interior(World world,int x,int z,int referenceY,double width,double height,MobArea area) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0
                ||!world.isChunkLoaded(x>>4,z>>4)||!insideBorder(world,x+.5,z+.5,width/2))return Optional.empty();
        if(area!=null&&!area.world().equals(world.getName()))return Optional.empty();
        int minimum=world.getMinHeight()+1,maximum=(int)Math.floor(world.getMaxHeight()-height);
        if(area!=null) {
            var bounds=area.bounds();
            minimum=Math.max(minimum,(int)Math.ceil(bounds.getMinY()));
            maximum=Math.min(maximum,(int)Math.floor(Math.ceil(bounds.getMaxY())-height));
        } else {minimum=Math.max(minimum,referenceY-16);maximum=Math.min(maximum,referenceY+16);}
        if(maximum<minimum)return Optional.empty();
        int start=Math.clamp(referenceY,minimum,maximum);
        // Air belongs to the room below its ceiling. Search downward, including
        // protruding support shapes; a solid column may be a raised interior floor.
        maximum=Math.min(maximum,world.getBlockAt(x,start,z).isPassable()?start:start+4);
        for(int offset=0;offset<=Math.max(start-minimum,maximum-start);offset++)for(int sign:offset==0?new int[]{1}:new int[]{-1,1}) {
            int y=start+sign*offset;if(y<minimum||y>maximum)continue;
            var at=validateAt(world,x,y,z,width,height);
            if(at.isPresent()&&fitsArea(at.get(),width,height,area))return at;
        }
        return Optional.empty();
    }
    /** Full body, not only its feet, stays within the host's block-coordinate bounds. */
    public static boolean fitsArea(Location at,double width,double height,MobArea area) {
        if(area==null)return true;
        return area.contains(at.clone().subtract(width/2,0,width/2))
                &&area.contains(new Location(at.getWorld(),Math.nextDown(at.getX()+width/2),Math.nextDown(at.getY()+height),Math.nextDown(at.getZ()+width/2)));
    }
    /** Highest real support beneath an exact moving footprint. The caller validates
     * swept body clearance; this lookup never loads chunks or crosses a ceiling. */
    public static java.util.OptionalDouble supportFloor(Location at,double referenceY,double width,MobArea area) {
        return supportFloor(at,referenceY,width,area,new HashMap<>());
    }
    /** Operation-local cache shared with the caller's swept collision checks. */
    public static java.util.OptionalDouble supportFloor(Location at,double referenceY,double width,MobArea area,Map<Block,List<BoundingBox>> shapes) {
        var world=at.getWorld();double half=width/2;
        if(world==null||!Double.isFinite(referenceY)||!Double.isFinite(width)||width<=0
                ||!insideBorder(world,at.getX(),at.getZ(),half)
                ||area!=null&&!area.world().equals(world.getName()))return java.util.OptionalDouble.empty();
        int minX=(int)Math.floor(at.getX()-half),maxX=(int)Math.ceil(at.getX()+half);
        int minZ=(int)Math.floor(at.getZ()-half),maxZ=(int)Math.ceil(at.getZ()+half);
        for(int x=minX;x<maxX;x++)for(int z=minZ;z<maxZ;z++)
            if(!world.isChunkLoaded(x>>4,z>>4))return java.util.OptionalDouble.empty();
        int top=Math.min(world.getMaxHeight()-1,(int)Math.floor(referenceY));
        double highest=Double.NEGATIVE_INFINITY;int supportLevel=Integer.MIN_VALUE;
        for(int y=top;y>=Math.max(world.getMinHeight(),top-4);y--) {
            for(int x=minX;x<maxX;x++)for(int z=minZ;z<maxZ;z++) {
                var block=world.getBlockAt(x,y,z);
                if(hazardous(block)) {
                    if(!Double.isFinite(highest)||y+1>highest+1e-7)return java.util.OptionalDouble.empty();
                    continue;
                }
                for(var box:collisionBoxes(block,shapes))if(y+box.getMaxY()<=referenceY+1+1e-7
                        &&x+box.getMaxX()>at.getX()-half&&x+box.getMinX()<at.getX()+half
                        &&z+box.getMaxZ()>at.getZ()-half&&z+box.getMinZ()<at.getZ()+half)
                    highest=Math.max(highest,y+box.getMaxY());
            }
            if(Double.isFinite(highest)&&supportLevel==Integer.MIN_VALUE)supportLevel=y;
            // Fences and walls protrude into the next cell; that lower support can
            // stand above carpet or snow already found in the higher one.
            if(supportLevel!=Integer.MIN_VALUE&&y<supportLevel)break;
        }
        if(!Double.isFinite(highest))return java.util.OptionalDouble.empty();
        var feet=new Location(world,at.getX(),highest,at.getZ());
        return fitsArea(feet,width,1,area)?java.util.OptionalDouble.of(highest):java.util.OptionalDouble.empty();
    }
    /** Read local Paper boxes once, without copying or mutating them. */
    public static List<BoundingBox> collisionBoxes(Block block,Map<Block,List<BoundingBox>> cache) {
        return cache.computeIfAbsent(block,key->{
            if(key.isEmpty()&&key.isPassable())return List.of();
            var shape=key.getCollisionShape();
            if(shape!=null)return List.copyOf(shape.getBoundingBoxes());
            var box=key.getBoundingBox();
            if(box!=null)return List.of(new BoundingBox(box.getMinX()-key.getX(),box.getMinY()-key.getY(),box.getMinZ()-key.getZ(),
                    box.getMaxX()-key.getX(),box.getMaxY()-key.getY(),box.getMaxZ()-key.getZ()));
            return key.isSolid()||!key.isPassable()?List.of(new BoundingBox(0,0,0,1,1,1)):List.of();
        });
    }
    /** Destinations used to relocate or create actors must have a traversable exit. */
    public static boolean hasWalkableExit(Location at,double width,double height,MobArea area,double referenceY) {
        if(!Double.isFinite(referenceY)||!fitsArea(at,width,height,area)||!clearBody(at,width,height))return false;
        // A neighbour alone is insufficient: a multi-cell hole can have a completely
        // closed floor. Below the encounter floor, prove a short route back up too.
        record Cell(int x,int y,int z) {}
        var queue=new java.util.ArrayDeque<Location>();
        var seen=new java.util.HashSet<Cell>();
        queue.add(at);seen.add(new Cell(at.getBlockX(),at.getBlockY(),at.getBlockZ()));
        while(!queue.isEmpty()&&seen.size()<=64) {
            var from=queue.removeFirst();
            for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                double x=from.getX()+direction[0],z=from.getZ()+direction[1];
                for(int y=from.getBlockY()+2;y>=from.getBlockY()-1;y--) {
                    var candidate=validateAt(at.getWorld(),(int)Math.floor(x),y,(int)Math.floor(z),width,height);
                    if(candidate.isEmpty())continue;
                    var next=candidate.get();next.setX(x);next.setZ(z);
                    if(Math.abs(next.getY()-from.getY())>1+1e-7||!fitsArea(next,width,height,area)
                            ||!clearBody(next,width,height)||!walkingStep(from,next,width,height,area))continue;
                    if(next.getY()>=Math.floor(referenceY)-1e-7)return true;
                    var cell=new Cell(next.getBlockX(),next.getBlockY(),next.getBlockZ());
                    if(seen.add(cell))queue.addLast(next);
                }
            }
        }
        return false;
    }
    private static boolean walkingStep(Location from,Location to,double width,double height,MobArea area) {
        double feet=Math.max(from.getY(),to.getY());
        for(double fraction:new double[]{0,.5,1}) {
            var point=new Location(from.getWorld(),from.getX()+(to.getX()-from.getX())*fraction,
                    feet,from.getZ()+(to.getZ()-from.getZ())*fraction);
            if(!fitsArea(point,width,height,area)||!clearBody(point,width,height))return false;
        }
        return true;
    }
    /** Validate a particular floor (including indoors), without consulting the surface height map. */
    public static Optional<Location> validateAt(World world,int x,int feetY,int z,double width,double height) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0||!insideBorder(world,x+.5,z+.5,width/2))return Optional.empty();
        if(!world.isChunkLoaded(x>>4,z>>4))return Optional.empty();
        int y=feetY-1;
        if(y<world.getMinHeight()||feetY>=world.getMaxHeight())return Optional.empty();
        var ground=world.getBlockAt(x,y,z);
        if(hazardous(ground))return Optional.empty();
        if(ground.getCollisionShape()==null&&ground.getBoundingBox()==null&&!ground.isSolid())return Optional.empty();
        var support=collisions(ground,x,y,z);
        double half=width/2,feet=Double.NEGATIVE_INFINITY;
        for(var box:support)if(box.getMaxX()>x+.5-half&&box.getMinX()<x+.5+half
                &&box.getMaxZ()>z+.5-half&&box.getMinZ()<z+.5+half)feet=Math.max(feet,box.getMaxY());
        if(!Double.isFinite(feet)||feet+height>world.getMaxHeight())return Optional.empty();
        var at=new Location(world,x+.5,feet,z+.5);
        return clearBody(at,width,height)?Optional.of(at):Optional.empty();
    }
    /** Collision shapes are local to the block; bounding-box adapters already use world coordinates. */
    private static List<BoundingBox> collisions(Block block,int x,int y,int z) {
        if(block.isEmpty()&&block.isPassable())return List.of();
        var shape=block.getCollisionShape();
        if(shape!=null)return shape.getBoundingBoxes().stream().map(box->box.clone().shift(x,y,z)).toList();
        // Supports lightweight public-API adapters without replacing real voxel shapes.
        var box=block.getBoundingBox();
        if(box!=null)return List.of(box);
        return block.isSolid()||!block.isPassable()?List.of(new BoundingBox(x,y,z,x+1,y+1,z+1)):List.of();
    }
    /** Includes supporting neighbours: fences and walls can extend half a block above their cell. */
    public static boolean clearBody(Location feet,double width,double height) {
        var world=feet.getWorld();double half=width/2;
        if(world==null||!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0
                ||!insideBorder(world,feet.getX(),feet.getZ(),half))return false;
        var body=new BoundingBox(feet.getX()-half,feet.getY()+1e-7,feet.getZ()-half,
                feet.getX()+half,feet.getY()+height,feet.getZ()+half);
        for(int x=(int)Math.floor(body.getMinX());x<Math.ceil(body.getMaxX());x++)
            for(int z=(int)Math.floor(body.getMinZ());z<Math.ceil(body.getMaxZ());z++) {
                if(!world.isChunkLoaded(x>>4,z>>4))return false;
                for(int y=Math.max(world.getMinHeight(),feet.getBlockY()-1);y<Math.ceil(body.getMaxY());y++) {
                    var block=world.getBlockAt(x,y,z);
                    if(hazardous(block)&&(y+1>feet.getY()||y==feet.getBlockY()-1))return false;
                    for(var collision:collisions(block,x,y,z))if(body.overlaps(collision))return false;
                }
            }
        return true;
    }
    /** Bottom of the first collision above the floor, including partial ceilings. */
    public static double ceiling(Location feet,double width,double distance) {
        int bottom=feet.getBlockY()-1;
        var world=feet.getWorld();double half=width/2,top=Math.min(feet.getY()+distance,world.getMaxHeight());
        for(int x=(int)Math.floor(feet.getX()-half);x<Math.ceil(feet.getX()+half);x++)
            for(int z=(int)Math.floor(feet.getZ()-half);z<Math.ceil(feet.getZ()+half);z++) {
                if(!world.isChunkLoaded(x>>4,z>>4))return feet.getY();
                for(int y=bottom;y<=Math.min(world.getMaxHeight()-1,Math.ceil(top));y++) {
                    var block=world.getBlockAt(x,y,z);
                    if(hazardous(block)&&y+1>feet.getY())top=Math.min(top,Math.max(feet.getY(),y));
                    for(var box:collisions(block,x,y,z))if(box.getMaxY()>feet.getY()+1e-7
                            &&box.getMaxX()>feet.getX()-half&&box.getMinX()<feet.getX()+half
                            &&box.getMaxZ()>feet.getZ()-half&&box.getMinZ()<feet.getZ()+half)
                        top=Math.min(top,Math.max(feet.getY(),box.getMinY()));
                }
            }
        return top;
    }
}
