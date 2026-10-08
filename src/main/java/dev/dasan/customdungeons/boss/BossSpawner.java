package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.config.EntityHeights;
import dev.dasan.customdungeons.config.NumericRanges;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.model.WorldBossDef;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

/** Only asynchronous chunk requests; all terrain inspection runs on the main thread. */
public final class BossSpawner {
    private final Plugin plugin;
    private final EntityHeights heights;
    private final RandomGenerator random;
    private int attempts;
    public BossSpawner(Plugin plugin,EntityHeights heights,int attempts) {
        this(plugin,heights,Math.clamp(attempts,1,200),RandomGenerator.getDefault());
    }
    BossSpawner(Plugin plugin,EntityHeights heights,int attempts,RandomGenerator random) {
        this.plugin=plugin;this.heights=heights;this.attempts=attempts;this.random=random;
    }
    public int attempts() {return attempts;}
    void attempts(int value) {attempts=Math.clamp(value,1,200);}
    static boolean safeColumn(boolean solid,boolean fluid,boolean leaves,double clearance,double height,boolean border) {
        return solid&&!fluid&&!leaves&&border&&Double.isFinite(height)&&height>0&&clearance>=height;
    }
    static boolean hazardous(Block b) {
        var type=b.getType();
        return b.isLiquid() || b.getBlockData() instanceof Waterlogged w&&w.isWaterlogged()
                || type==Material.WATER || type==Material.LAVA || type==Material.KELP || type==Material.KELP_PLANT
                || type==Material.SEAGRASS || type==Material.TALL_SEAGRASS || type==Material.BUBBLE_COLUMN
                || type.name().endsWith("_LEAVES");
    }
    public CompletableFuture<Optional<Location>> find(MobTemplate template,BooleanSupplier current) {
        var result=new CompletableFuture<Optional<Location>>();
        World world=Bukkit.getWorld(template.worldBoss().world());
        if(world==null) {result.complete(Optional.empty());return result;}
        var b=template.worldBoss();
        var type=EntityType.valueOf(template.entityType().toUpperCase(Locale.ROOT).replace("MINECRAFT:",""));
        // Public unspawned entity supplies exact vanilla width; config supplies height when declared.
        Entity dimensions=world.createEntity(new Location(world,0,world.getMinHeight()+1,0),Objects.requireNonNull(type.getEntityClass()));
        Double override=template.attributes().values().get("scale");
        double scale=override==null?NumericRanges.effectiveScale(template.scale()):Math.max(NumericRanges.SCALE_ATTRIBUTE_MIN,override);
        double baseHeight=heights.height(type);
        double height=(Double.isFinite(baseHeight)?baseHeight:dimensions.getHeight())*scale;
        double width=dimensions.getWidth()*scale;
        search(world,b,width,height,0,current,result);
        return result;
    }
    private void search(World world,WorldBossDef b,double width,double height,int tried,BooleanSupplier current,
                        CompletableFuture<Optional<Location>> result) {
        if(!plugin.isEnabled()||!current.getAsBoolean())return;
        if(tried>=attempts) {result.complete(Optional.empty());return;}
        int x=random.nextInt(b.xMin(),b.xMax()), z=random.nextInt(b.zMin(),b.zMax());
        double half=width/2;
        int minX=(int)Math.floor(x+.5-half),maxX=(int)Math.ceil(x+.5+half)-1;
        int minZ=(int)Math.floor(z+.5-half),maxZ=(int)Math.ceil(z+.5+half)-1;
        if(!insideBorder(world,x+.5,z+.5,half)) {search(world,b,width,height,tried+1,current,result);return;}
        var chunks=new ArrayList<CompletableFuture<Chunk>>();
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)chunks.add(world.getChunkAtAsync(cx,cz,true));
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((unused,error)->{
            if(!plugin.isEnabled()||!current.getAsBoolean())return;
            try {plugin.getServer().getScheduler().runTask(plugin,()->{
                if(!plugin.isEnabled()||!current.getAsBoolean())return;
                if(Bukkit.getWorld(world.getUID())!=world) {result.complete(Optional.empty());return;}
                List<Chunk> loaded=error==null?chunks.stream().map(CompletableFuture::join).toList():List.of();
                var tickets=new ArrayList<Chunk>();
                try {
                    // Short tickets cover validation only, never the lifetime of an encounter.
                    loaded.forEach(c->{if(c.addPluginChunkTicket(plugin))tickets.add(c);});
                    Optional<Location> point=error==null?validate(world,x,z,width,height):Optional.empty();
                    if(point.isPresent())result.complete(point);
                    else search(world,b,width,height,tried+1,current,result);
                } catch(RuntimeException failure) {result.completeExceptionally(failure);}
                finally {tickets.forEach(c->c.removePluginChunkTicket(plugin));}
            });} catch(org.bukkit.plugin.IllegalPluginAccessException disabled) { /* Shutdown raced scheduling: discard. */ }
        });
    }
    static boolean insideBorder(World world,double x,double z,double half) {
        var border=world.getWorldBorder();var center=border.getCenter();double radius=border.getSize()/2;
        return x-half>=center.getX()-radius && x+half<=center.getX()+radius
                && z-half>=center.getZ()-radius && z+half<=center.getZ()+radius;
    }
    static Optional<Location> validate(World world,int x,int z,double width,double height) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0||!insideBorder(world,x+.5,z+.5,width/2))return Optional.empty();
        if(!world.isChunkLoaded(x>>4,z>>4))return Optional.empty();
        int y=world.getHighestBlockYAt(x,z,HeightMap.WORLD_SURFACE);
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
