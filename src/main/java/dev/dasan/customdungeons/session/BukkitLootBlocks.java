package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.BukkitLootBlockInspector;
import dev.dasan.customdungeons.model.Point;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.Inventory;

/** No synchronous chunk loads, no physics and no drops on any replacement path. */
final class BukkitLootBlocks implements LootChestService.Blocks {
    private static final String OWNED="loot-chest-v1:",OWNED_TOKEN="loot-chest-v2:";
    private record Snapshot(String data,String token,boolean legacy) {
        static Snapshot parse(String encoded) {
            if(encoded.startsWith(OWNED_TOKEN)) {
                int end=OWNED_TOKEN.length()+36;
                String token=encoded.substring(OWNED_TOKEN.length(),end);
                UUID.fromString(token);
                if(encoded.charAt(end)!=':')throw new IllegalArgumentException("Invalid loot snapshot");
                return new Snapshot(encoded.substring(end+1),token,false);
            }
            return new Snapshot(encoded.startsWith(OWNED)?encoded.substring(OWNED.length()):encoded,null,encoded.startsWith(OWNED));
        }
        String encoded() {return token!=null?OWNED_TOKEN+token+":"+data:(legacy?OWNED:"")+data;}
    }
    private record Key(String world,int x,int z) {}
    private final SessionManager manager;
    private final Set<Key> loading=new HashSet<>();
    private final Map<Key,Chunk> retained=new HashMap<>();
    private final Map<Key,Set<Point>> needed=new HashMap<>();
    private boolean closed;
    BukkitLootBlocks(SessionManager manager) {this.manager=manager;}
    public boolean ready(Point p) {
        if(closed)return false;
        var world=Bukkit.getWorld(p.world());if(world==null)return false;
        int x=(int)p.x(),z=(int)p.z();boolean ready=true;
        for(var offset:new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
            int cx=(x+offset[0])>>4,cz=(z+offset[1])>>4;
            var key=new Key(p.world(),cx,cz);
            if(loading.contains(key) || retained.containsKey(key))needed.computeIfAbsent(key,k->new HashSet<>()).add(p);
            if(world.isChunkLoaded(cx,cz))continue;
            needed.computeIfAbsent(key,k->new HashSet<>()).add(p);ready=false;
            if(loading.add(key))world.getChunkAtAsync(cx,cz).whenComplete((chunk,error)->manager.main(()->{
                loading.remove(key);
                if(closed || error!=null || !needed.containsKey(key) || chunk.getWorld()!=Bukkit.getWorld(key.world()))return;
                if(!retained.containsKey(key)){manager.retainChunk(chunk);retained.put(key,chunk);}
                manager.loot().chunksAvailable(key.world());
            }));
        }
        return ready;
    }
    private Block block(Point p) {
        if(!ready(p))throw new IllegalStateException("Loot chunks not ready");
        return Objects.requireNonNull(Bukkit.getWorld(p.world())).getBlockAt((int)p.x(),(int)p.y(),(int)p.z());
    }
    public String snapshot(Point p) {
        var inspection=new BukkitLootBlockInspector().inspect(p);
        if(!inspection.prepared() || !inspection.restorable() || inspection.doubleChest())throw new IllegalStateException("Loot block is not restorable");
        var block=block(p);
        return snapshot(block).encoded();
    }
    private Snapshot snapshot(Block block) {
        var state=block.getState();String token=null;boolean legacy=false;
        if(state instanceof Chest chest) {
            var pdc=chest.getPersistentDataContainer();
            if(pdc.has(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING))
                token=pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING);
            legacy=token==null && BukkitLootBlockInspector.ownedChest(chest);
        }
        return new Snapshot(block.getBlockData().getAsString(),token,legacy);
    }
    public String expectedChest() {
        return new Snapshot(Bukkit.createBlockData("minecraft:chest[type=single]").getAsString(),UUID.randomUUID().toString(),false).encoded();
    }
    public void chest(Point p,String expected) {
        var snapshot=Snapshot.parse(expected);
        if(snapshot.token()==null)throw new IllegalArgumentException("Loot mutation requires its own token");
        var block=block(p);
        clear(block);
        // Remove any previous TileState, including names, locks, loot tables and PDC.
        block.setType(Material.AIR,false);
        block.setBlockData(Bukkit.createBlockData(snapshot.data()),false);
        mark(block,snapshot.token());inventory(p).clear();
    }
    private void mark(Block block,String token) {
        if(!(block.getState() instanceof Chest chest))throw new IllegalStateException("Loot chest missing");
        if(token==null)chest.getPersistentDataContainer().set(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);
        else chest.getPersistentDataContainer().set(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING,token);
        if(!chest.update(true,false))throw new IllegalStateException("Loot ownership not applied");
    }
    @Override public boolean matches(Point p,String encoded,boolean allowContents) {
        var expected=Snapshot.parse(encoded);var block=block(p);var state=block.getState();
        if(!block.getBlockData().getAsString().equals(expected.data()))return false;
        if(allowContents && expected.token()!=null && state instanceof Chest chest) {
            var pdc=chest.getPersistentDataContainer();
            return pdc.has(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING)
                    && expected.token().equals(pdc.get(BukkitLootBlockInspector.LOOT_CHEST,org.bukkit.persistence.PersistentDataType.STRING));
        }
        // A matching material is insufficient for an original container: its inventory and extra
        // state must still be empty/pristine. Only a per-mutation token can authorize discarding loot.
        if(state instanceof TileState && !BukkitLootBlockInspector.plainChest(state))return false;
        return snapshot(block).encoded().equals(encoded);
    }
    public Inventory inventory(Point p) {
        if(!(block(p).getState() instanceof Chest chest)
                || !(chest.getBlockData() instanceof org.bukkit.block.data.type.Chest data)
                || data.getType()!=org.bukkit.block.data.type.Chest.Type.SINGLE)throw new IllegalStateException("Loot chest changed");
        return chest.getBlockInventory();
    }
    private void clear(Block block) {
        if(block.getState() instanceof Container container) {
            for(var viewer:List.copyOf(container.getInventory().getViewers()))viewer.closeInventory();
            (container instanceof Chest chest?chest.getBlockInventory():container.getInventory()).clear();
        }
    }
    public void restore(Point p,String data) {
        var snapshot=Snapshot.parse(Objects.requireNonNull(data));
        var original=Bukkit.createBlockData(snapshot.data());var block=block(p);
        clear(block);block.setType(Material.AIR,false);block.setBlockData(original,false);
        if(snapshot.token()!=null || snapshot.legacy())mark(block,snapshot.token());
        if(!block.getBlockData().getAsString().equals(original.getAsString()))throw new IllegalStateException("Loot restoration not applied");
        release(p);
    }
    public void release(Point p) {
        for(var key:List.copyOf(needed.keySet())) {
            var points=needed.get(key);points.remove(p);
            if(!points.isEmpty())continue;
            needed.remove(key);var chunk=retained.remove(key);if(chunk!=null)manager.releaseChunk(chunk);
        }
    }
    public void close() {closed=true;for(var chunk:retained.values())manager.releaseChunk(chunk);retained.clear();needed.clear();}

}
