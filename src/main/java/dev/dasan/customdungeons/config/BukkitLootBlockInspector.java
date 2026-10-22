package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.Point;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;

/** Main-thread, read-only inspection. Never loads chunks or changes blocks/inventories. */
public final class BukkitLootBlockInspector implements Validator.LootBlockInspector {
    public static final org.bukkit.NamespacedKey LOOT_CHEST=new org.bukkit.NamespacedKey("customdungeons","loot_chest");
    private static final int[][] HORIZONTAL={{0,0},{1,0},{-1,0},{0,1},{0,-1}};

    /** A pristine empty single chest has no state beyond BlockData; other TileStates stay rejected. */
    public static boolean plainChest(org.bukkit.block.BlockState state) {
        if(!(state instanceof org.bukkit.block.Chest chest))return false;
        return chest.getBlockData() instanceof org.bukkit.block.data.type.Chest data
                && data.getType()==org.bukkit.block.data.type.Chest.Type.SINGLE
                && chest.getSnapshotInventory().isEmpty() && !chest.isLocked() && chest.customName()==null
                && chest.getLootTable()==null && chest.getSeed()==0
                && (chest.getPersistentDataContainer().isEmpty() || chest.getPersistentDataContainer().getKeys().equals(java.util.Set.of(LOOT_CHEST)) && ownedChest(chest));
    }
    /** Ownership survives unrelated plugin tags; snapshot admission above still rejects unknown state. */
    public static boolean ownedChest(org.bukkit.block.Chest chest) {
        var pdc=chest.getPersistentDataContainer();
        if(pdc.has(LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE)
                && Byte.valueOf((byte)1).equals(pdc.get(LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE)))return true;
        String token=pdc.has(LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)
                ?pdc.get(LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING):null;
        if(token==null)return false;
        try{return java.util.UUID.fromString(token).toString().equals(token);}catch(IllegalArgumentException invalid){return false;}
    }
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
        boolean restorable=state!=null && !occupied && (!(state instanceof TileState) || plainChest(state));
        boolean doubleChest=false;
        for(int i=1;i<HORIZONTAL.length;i++) {
            var offset=HORIZONTAL[i];var type=world.getBlockAt(x+offset[0],y,z+offset[1]).getType();
            if(type==Material.CHEST || type==Material.TRAPPED_CHEST)doubleChest=true;
        }
        return new Inspection(restorable,doubleChest);
    }
}
