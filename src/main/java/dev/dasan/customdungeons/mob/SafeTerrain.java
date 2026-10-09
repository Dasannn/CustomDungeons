package dev.dasan.customdungeons.mob;

import java.util.Optional;
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
    /** Validate a particular floor (including indoors), without consulting the surface height map. */
    public static Optional<Location> validateAt(World world,int x,int feetY,int z,double width,double height) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0||!insideBorder(world,x+.5,z+.5,width/2))return Optional.empty();
        if(!world.isChunkLoaded(x>>4,z>>4))return Optional.empty();
        int y=feetY-1;
        if(y<world.getMinHeight()||feetY>=world.getMaxHeight())return Optional.empty();
        var ground=world.getBlockAt(x,y,z);
        int top=(int)Math.ceil(y+1+height)-1;
        if(!safeColumn(ground.isSolid(),hazardous(ground),ground.getType().name().endsWith("_LEAVES"),world.getMaxHeight()-(y+1),height,true))return Optional.empty();
        for(int bx=(int)Math.floor(x+.5-width/2);bx<Math.ceil(x+.5+width/2);bx++)
            for(int bz=(int)Math.floor(z+.5-width/2);bz<Math.ceil(z+.5+width/2);bz++) {
                if(!world.isChunkLoaded(bx>>4,bz>>4))return Optional.empty();
                for(int by=y+1;by<=top;by++) {
                    Block block=world.getBlockAt(bx,by,bz);
                    if(hazardous(block)||!block.isPassable())return Optional.empty();
                }
            }
        return Optional.of(new Location(world,x+.5,y+1,z+.5));
    }
}
