import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.io.*;
import java.util.zip.*;
import org.bukkit.*;
import org.bukkit.block.*;

/** Fixed acceptance fixture; its block-data journal is durable before the first mutation. */
final class T58Arena {
    record Recipe(int version,String world,int[] center,int[] bounds,List<String> features,List<List<Object>> fills) {}
    final Recipe recipe;
    final Map<Block,BlockState> original=new LinkedHashMap<>();
    record Cell(int x,int y,int z,String data) {}
    record Snapshot(int version,String owner,String world,String worldId,List<Cell> cells) {}
    private final Path journal;
    private World world;
    private String owner;
    boolean ownedBy(String nonce) {return nonce.equals(owner);}
    T58Arena(org.bukkit.plugin.java.JavaPlugin plugin) {
        try(var input=plugin.getResource("t58-arena-v1.json")) {
            if(input==null)throw new IllegalStateException("Missing arena recipe");
            recipe=new Gson().fromJson(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8),Recipe.class);
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
        if(recipe.version()!=1)throw new IllegalStateException("Arena version");
        world=Bukkit.getWorld(recipe.world());
        journal=Path.of(Objects.requireNonNull(System.getenv("T58_RESULTS"),"Missing runner results directory")).resolve("arena-original.json.gz");
        if(Files.exists(journal))load();
    }
    private boolean ownedCell(int x,int y,int z) {
        var b=recipe.bounds();return x>=b[0]&&x<=b[3]&&y>=b[1]&&y<=b[4]&&z>=b[2]&&z<=b[5]
                ||x>=54&&x<=58&&y==-1&&z>=-2&&z<=2;
    }
    private void capture(boolean flat) {
        var b=recipe.bounds();
        for(int x=b[0];x<=b[3];x++)for(int z=b[2];z<=b[5];z++)for(int y=b[1];y<=b[4];y++)captureCell(x,y,z,flat);
        for(int x=54;x<=58;x++)for(int z=-2;z<=2;z++)captureCell(x,-1,z,flat);
        save();
    }
    private void captureCell(int x,int y,int z,boolean flat) {
        var c=recipe.center();var block=world.getBlockAt(c[0]+x,c[1]+y,c[2]+z);var state=block.getState();
        // This private void-world fixture has no containers. Never destroy a tile
        // whose inventory/state a block-data journal cannot recover after a crash.
        if(state instanceof TileState)throw new IllegalStateException("Tile entity inside acceptance fixture");
        if(flat)state.setBlockData(Bukkit.createBlockData(y==-1?Material.STONE:Material.AIR));
        original.put(block,state);
    }
    private void save() {
        var c=recipe.center();var cells=original.values().stream().map(s->new Cell(s.getX()-c[0],s.getY()-c[1],s.getZ()-c[2],s.getBlockData().getAsString())).toList();
        Path staging=journal.resolveSibling(journal.getFileName()+".tmp");
        try(var channel=FileChannel.open(staging,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            var gzip=new GZIPOutputStream(Channels.newOutputStream(channel));
            var writer=new OutputStreamWriter(gzip,java.nio.charset.StandardCharsets.UTF_8)) {
            new Gson().toJson(new Snapshot(1,owner,recipe.world(),world.getUID().toString(),cells),writer);
            writer.flush();gzip.finish();gzip.flush();channel.force(true);
        }catch(IOException error){throw new IllegalStateException("Cannot persist acceptance terrain",error);}
        try{Files.move(staging,journal,StandardCopyOption.ATOMIC_MOVE);}catch(IOException error){throw new IllegalStateException("Cannot publish acceptance terrain",error);}
    }
    private void load() {
        try(var reader=new InputStreamReader(new GZIPInputStream(Files.newInputStream(journal)),java.nio.charset.StandardCharsets.UTF_8)) {
            var saved=new Gson().fromJson(reader,Snapshot.class);
            if(saved.version()!=1||!saved.owner().matches("t58b-[0-9a-f]{6}")||!recipe.world().equals(saved.world())
                    ||world==null||!world.getUID().toString().equals(saved.worldId())||saved.cells().size()!=(recipe.bounds()[3]-recipe.bounds()[0]+1)*(recipe.bounds()[4]-recipe.bounds()[1]+1)*(recipe.bounds()[5]-recipe.bounds()[2]+1)+25)throw new IllegalStateException("Foreign or incomplete terrain journal");
            var restored=new LinkedHashMap<Block,BlockState>();var c=recipe.center();
            for(var cell:saved.cells()) {
                if(!ownedCell(cell.x(),cell.y(),cell.z()))throw new IllegalStateException("Terrain journal outside fixture");
                var block=world.getBlockAt(c[0]+cell.x(),c[1]+cell.y(),c[2]+cell.z());var state=block.getState();
                if(state instanceof TileState)throw new IllegalStateException("Unexpected tile entity during recovery");
                state.setBlockData(Bukkit.createBlockData(cell.data()));
                if(restored.put(block,state)!=null)throw new IllegalStateException("Duplicate terrain journal cell");
            }
            original.putAll(restored);owner=saved.owner();
        }catch(IOException error){throw new IllegalStateException("Cannot load acceptance terrain",error);}
    }
    int restoreFlat(String nonce) {
        if(!nonce.matches("t58b-[0-9a-f]{6}")||!original.isEmpty())throw new IllegalStateException("Exact journal takes priority over flat recovery");
        if(world==null)world=Bukkit.getWorld(recipe.world());if(world==null)throw new IllegalStateException("Arena world unavailable");
        owner=nonce;capture(true);return restore(true);
    }
    private void set(int x,int y,int z,org.bukkit.block.data.BlockData data) {
        Block block=world.getBlockAt(recipe.center()[0]+x,recipe.center()[1]+y,recipe.center()[2]+z);
        original.computeIfAbsent(block,Block::getState);
        block.setBlockData(data,false);
    }
    private void fill(int x1,int y1,int z1,int x2,int y2,int z2,String data) {
        var state=Bukkit.createBlockData("minecraft:"+data);
        for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++)for(int y=y1;y<=y2;y++)set(x,y,z,state);
    }
    void build(String nonce,String mode) {
        if(!nonce.matches("t58b-[0-9a-f]{6}")||owner!=null&&!owner.equals(nonce))throw new IllegalArgumentException("Arena owner");
        if(world==null)world=Bukkit.getWorld(recipe.world());
        if(world==null)throw new IllegalStateException("Arena world unavailable");
        owner=nonce;if(original.isEmpty())capture(false);restore(false);
        fill(-32,-7,-32,32,15,32,"air");
        fill(-32,-7,-32,32,-1,32,"stone");
        fill(54,-1,-2,58,-1,2,"stone");
        if(!mode.equals("warden"))fill(-32,8,-32,32,8,32,"stone");
        int edge=mode.equals("room")?12:28;
        for(int side:new int[]{-edge,edge}) {
            fill(side,0,-edge,side,7,edge,"stone");fill(-edge,0,side,edge,7,side,"stone");
        }
        fill(-3,0,edge,3,7,edge,"air");
        if(mode.startsWith("irregular")) {
            for(var f:recipe.fills())fill(((Number)f.get(0)).intValue(),((Number)f.get(1)).intValue(),((Number)f.get(2)).intValue(),
                    ((Number)f.get(3)).intValue(),((Number)f.get(4)).intValue(),((Number)f.get(5)).intValue(),(String)f.get(6));
            if(mode.equals("irregular-native"))fill(-3,-2,8,-2,-1,10,"stone");
            if(mode.equals("irregular-warden"))fill(-12,8,-12,12,15,12,"air");
        }
    }
    int restore(boolean release) {
        int changed=0;
        for(var state:original.values()) {
            if(!state.update(true,false))throw new IllegalStateException("Arena restoration failed");
            if(!state.getBlock().getBlockData().getAsString().equals(state.getBlockData().getAsString()))changed++;
        }
        if(release){
            if(!original.isEmpty())world.save(); // Keep the journal until restored chunks are saved.
            try{Files.deleteIfExists(journal);}catch(IOException error){throw new IllegalStateException("Cannot release terrain journal",error);}
            original.clear();owner=null;
        }
        return changed;
    }
    int restoreFor(String nonce) {
        if(owner!=null&&!owner.equals(nonce))throw new IllegalArgumentException("Arena owner");
        return restore(true);
    }
}
