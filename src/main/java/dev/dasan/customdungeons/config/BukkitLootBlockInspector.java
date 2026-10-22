package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.Point;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;

/** Main-thread, read-only inspection. Never loads chunks or changes blocks/inventories. */
public final class BukkitLootBlockInspector implements Validator.LootBlockInspector {
    private static final int[][] HORIZONTAL={{0,0},{1,0},{-1,0},{0,1},{0,-1}};

    @Override public Inspection inspect(Point location) {
        if(!Bukkit.isPrimaryThread())throw new IllegalStateException("Loot block inspection requires the main thread");
        if(location==null || location.world()==null || location.world().isBlank()
                || !Double.isFinite(location.x()) || !Double.isFinite(location.y()) || !Double.isFinite(location.z())
                || location.x()<=Integer.MIN_VALUE || location.x()>=Integer.MAX_VALUE
                || location.z()<=Integer.MIN_VALUE || location.z()>=Integer.MAX_VALUE)
            return new Inspection(false,false);
        var world=Bukkit.getWorld(location.world());
        if(world==null)return new Inspection(false,false,false);
        if(location.y()<world.getMinHeight() || location.y()>=world.getMaxHeight())return new Inspection(false,false);
        int x=(int)Math.floor(location.x()),y=(int)Math.floor(location.y()),z=(int)Math.floor(location.z());
        // Check every required chunk before any getBlockAt: neighbours can cross chunk boundaries.
        for(var offset:HORIZONTAL)
            if(!world.isChunkLoaded((x+offset[0])>>4,(z+offset[1])>>4))return new Inspection(false,false,false);
        var state=world.getBlockAt(x,y,z).getState();
        boolean occupied=state instanceof Container container && !container.getSnapshotInventory().isEmpty();
        // The durable journal currently stores type + BlockData only. TileState data (even for an
        // empty container) cannot be restored by that format; T63 must extend the backup before
        // admitting such states. Air and ordinary blocks are represented completely by BlockData.
        boolean restorable=state!=null && !occupied && !(state instanceof TileState);
        boolean doubleChest=false;
        for(int i=1;i<HORIZONTAL.length;i++) {
            var offset=HORIZONTAL[i];var type=world.getBlockAt(x+offset[0],y,z+offset[1]).getType();
            if(type==Material.CHEST || type==Material.TRAPPED_CHEST)doubleChest=true;
        }
        return new Inspection(restorable,doubleChest);
    }
}
