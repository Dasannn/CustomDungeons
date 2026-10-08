package dev.dasan.customdungeons.boss;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

/** Shared async write-ahead journal. Recovery uses async chunks even for worlds loaded later. */
final class BossBlockJournal implements AutoCloseable {
    private final Plugin plugin;
    private final Path file;
    private final Map<String,String> entries=new LinkedHashMap<>();
    private CompletableFuture<Void> writes=CompletableFuture.completedFuture(null);
    BossBlockJournal(Plugin plugin) {
        this.plugin=plugin;file=plugin.getDataFolder().toPath().resolve("world-boss-blocks.journal");
        try {
            if(Files.exists(file))for(String line:Files.readAllLines(file)) {
                int tab=line.indexOf('\t');if(tab>0)entries.put(line.substring(0,tab),line.substring(tab+1));
            }
        } catch(java.io.IOException e) {throw new java.io.UncheckedIOException(e);}
    }
    private String key(Block b) {return b.getWorld().getUID()+";"+b.getX()+";"+b.getY()+";"+b.getZ();}
    boolean reserved(Block b) {return entries.containsKey(key(b));}
    CompletableFuture<Void> add(Block b,BlockData original,BlockData placed) {
        entries.put(key(b),original.getAsString()+"\t"+placed.getAsString());return persist();
    }
    void remove(Block b) {if(entries.remove(key(b))!=null)persist();}
    void recover(World world) {
        for(String key:List.copyOf(entries.keySet())) {
            String[] p=key.split(";");if(!p[0].equals(world.getUID().toString()))continue;
            int x=Integer.parseInt(p[1]), y=Integer.parseInt(p[2]), z=Integer.parseInt(p[3]);
            String recorded=entries.get(key);
            world.getChunkAtAsync(x>>4,z>>4,true).whenComplete((chunk,error)->{
                if(error!=null||!plugin.isEnabled())return;
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(!Objects.equals(entries.get(key),recorded)||!world.isChunkLoaded(x>>4,z>>4))return;
                    Block block=world.getBlockAt(x,y,z);String[] data=recorded.split("\t",2);
                    if(data.length==2&&block.getBlockData().getAsString().equals(data[1]))block.setBlockData(Bukkit.createBlockData(data[0]),false);
                    entries.remove(key);persist();
                });
            });
        }
    }
    void recoverLoadedChunk(World world,int cx,int cz) {
        if(!world.isChunkLoaded(cx,cz))return;
        boolean changed=false;
        for(String key:List.copyOf(entries.keySet())) {
            String[] p=key.split(";");if(!p[0].equals(world.getUID().toString()))continue;
            int x=Integer.parseInt(p[1]),y=Integer.parseInt(p[2]),z=Integer.parseInt(p[3]);if((x>>4)!=cx||(z>>4)!=cz)continue;
            String[] data=entries.remove(key).split("\t",2);var block=world.getBlockAt(x,y,z);
            if(data.length==2&&block.getBlockData().getAsString().equals(data[1]))block.setBlockData(Bukkit.createBlockData(data[0]),false);
            changed=true;
        }
        if(changed)persist();
    }
    private CompletableFuture<Void> persist() {
        String content=entries.entrySet().stream().map(e->e.getKey()+"\t"+e.getValue()+"\n").collect(java.util.stream.Collectors.joining());
        writes=writes.handle((v,e)->null).thenRunAsync(()->{
            try {
                Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
                try(var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
                    var bytes=StandardCharsets.UTF_8.encode(content);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);
                }
                Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            } catch(java.io.IOException e) {throw new CompletionException(e);}
        });return writes;
    }
    @Override public void close() {writes.join();}
}
