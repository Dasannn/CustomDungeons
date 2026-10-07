package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;

/** Shared, bounded spawn search. Never inspects an unloaded chunk. */
final class RespawnDestinations {
    static final int RADIUS=16;
    record Column(int x,int z) {}
    private final String configured,dungeonWorld;
    private final Predicate<Location> inside;
    private final Consumer<String> warning;
    private final Set<String> warned=new HashSet<>();

    RespawnDestinations(String configured,String dungeonWorld,Predicate<Location> inside,Consumer<String> warning) {
        this.configured=configured;this.dungeonWorld=dungeonWorld;this.inside=inside;this.warning=warning;
        selectWorld(configured,Bukkit.getWorlds().stream().map(World::getName).toList(),this::warn);
    }
    private void warn(String key) {if(warned.add(key))warning.accept(key);}
    Point spawn() {
        List<World> worlds=orderedWorlds();
        for(World world:worlds) {
            Point safe=search(world);if(safe!=null)return safe;
        }
        return emergency();
    }
    CompletableFuture<Point> spawnAsync(Consumer<Runnable> main,Consumer<Chunk> retain,Consumer<Chunk> release) {
        var result=new CompletableFuture<Point>();
        List<World> worlds=orderedWorlds();
        var loads=new ArrayList<CompletableFuture<Chunk>>();
        for(World world:worlds) {
            Location at=world.getSpawnLocation();var chunks=new LinkedHashSet<Column>();
            for(Column column:spiral(at.getBlockX(),at.getBlockZ(),RADIUS))
                chunks.add(new Column(column.x()>>4,column.z()>>4));
            for(Column chunk:chunks)try {
                loads.add(world.getChunkAtAsync(chunk.x(),chunk.z()).thenApply(value->value)
                        .orTimeout(10,TimeUnit.SECONDS).exceptionally(error->null));
            } catch(RuntimeException error) {loads.add(CompletableFuture.completedFuture(null));}
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((unused,error)->main.accept(()->{
            var retained=new ArrayList<Chunk>();
            try {
                for(var load:loads) {Chunk chunk=load.getNow(null);if(chunk!=null){retain.accept(chunk);retained.add(chunk);}}
                result.complete(spawn());
            } catch(RuntimeException failure) {result.completeExceptionally(failure);}
            finally {for(Chunk chunk:retained)release.accept(chunk);}
        }));
        return result;
    }
    private List<World> orderedWorlds() {
        List<World> worlds=Bukkit.getWorlds();
        String preferred=selectWorld(configured,worlds.stream().map(World::getName).toList(),this::warn);
        var candidates=new ArrayList<World>();
        worlds.stream().filter(world->Objects.equals(world.getName(),preferred)).findFirst().ifPresent(candidates::add);
        if(!worlds.isEmpty() && !candidates.contains(worlds.getFirst()))candidates.add(worlds.getFirst());
        return candidates;
    }
    private Point search(World world) {
        if(world.getName().equals(dungeonWorld))return null;
        Location spawn=world.getSpawnLocation();
        for(Column column:spiral(spawn.getBlockX(),spawn.getBlockZ(),RADIUS)) {
            if(!world.isChunkLoaded(column.x()>>4,column.z()>>4))continue;
            Location at=new Location(world,column.x()+.5,spawn.getY(),column.z()+.5,spawn.getYaw(),spawn.getPitch());
            if(!world.getWorldBorder().isInside(at))continue;
            if(column.x()==spawn.getBlockX() && column.z()==spawn.getBlockZ() && safe(spawn))return point(spawn);
            at.setY(world.getHighestBlockYAt(column.x(),column.z())+1);
            if(safe(at))return point(at);
        }
        return null;
    }
    private boolean safe(Location at) {return !inside.test(at) && DungeonSessionRuntime.safePrevious(point(at));}
    private Point emergency() {
        warn("respawn.unsafe-spawn");
        // Bukkit always has a world while handling a player event. Keep the first-world
        // destination even when every candidate is inside a dungeon or has no floor.
        World world=Bukkit.getWorlds().stream().findFirst().orElse(null);
        if(world==null)return null;
        Location at=world.getSpawnLocation().clone();
        if(world.isChunkLoaded(at.getBlockX()>>4,at.getBlockZ()>>4))
            at.setY(Math.max(world.getMinHeight()+1,Math.min(world.getMaxHeight()-2,
                    world.getHighestBlockYAt(at.getBlockX(),at.getBlockZ())+1)));
        return point(at);
    }
    static List<Column> spiral(int x,int z,int radius) {
        var columns=new ArrayList<Column>();columns.add(new Column(x,z));
        for(int r=1;r<=radius;r++) {
            for(int dx=-r;dx<r;dx++)columns.add(new Column(x+dx,z-r));
            for(int dz=-r;dz<r;dz++)columns.add(new Column(x+r,z+dz));
            for(int dx=r;dx>-r;dx--)columns.add(new Column(x+dx,z+r));
            for(int dz=r;dz>-r;dz--)columns.add(new Column(x-r,z+dz));
        }
        return columns;
    }
    static Point defaultSpawn() {return new RespawnDestinations("","",at->false,key->{}).spawn();}
    static String selectWorld(String configured,List<String> worlds,Consumer<String> warning) {
        if(!configured.isBlank() && worlds.contains(configured))return configured;
        if(!configured.isBlank())warning.accept("respawn.invalid-world");
        return worlds.isEmpty()?null:worlds.getFirst();
    }
    static Point personalSpawn(boolean bed,boolean anchor,Point vanilla,
            Predicate<Point> inside,Supplier<Point> spawn) {
        return (bed || anchor) && valid(vanilla) && !inside.test(vanilla)?vanilla:spawn.get();
    }
    static boolean valid(Point p) {
        return p!=null && p.world()!=null && Double.isFinite(p.x()) && Double.isFinite(p.y())
                && Double.isFinite(p.z()) && Float.isFinite(p.yaw()) && Float.isFinite(p.pitch());
    }
    static Point point(Location at) {
        return new Point(at.getWorld().getName(),at.getX(),at.getY(),at.getZ(),at.getYaw(),at.getPitch());
    }
}
