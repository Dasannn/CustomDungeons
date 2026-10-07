package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;

/** One last-resort spawn policy; selection itself needs no server or chunk access. */
final class RespawnDestinations {
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
        var spawns=new ArrayList<Point>();
        for(World world:Bukkit.getWorlds()) {
            Location at=world.getSpawnLocation();
            if(at!=null && at.getWorld()!=null)spawns.add(point(at));
        }
        return selectSpawn(configured,dungeonWorld,spawns,
                p->inside.test(DungeonSessionRuntime.location(p)),this::warn);
    }
    static Point defaultSpawn() {return new RespawnDestinations("","",at->false,key->{}).spawn();}
    private static String selectWorld(String configured,List<String> worlds,Consumer<String> warning) {
        if(!configured.isBlank() && worlds.contains(configured))return configured;
        if(!configured.isBlank())warning.accept("respawn.invalid-world");
        return worlds.isEmpty()?null:worlds.getFirst();
    }
    static Point selectSpawn(String configured,String dungeonWorld,List<Point> spawns,
            Predicate<Point> inside,Consumer<String> warning) {
        String preferred=selectWorld(configured,spawns.stream().map(Point::world).toList(),warning);
        Point primary=spawns.stream().filter(p->Objects.equals(p.world(),preferred)).findFirst().orElse(null);
        Predicate<Point> outside=p->valid(p) && !p.world().equals(dungeonWorld) && !inside.test(p);
        if(primary!=null && outside.test(primary))return primary;
        if(primary!=null)warning.accept("respawn.unsafe-spawn");
        return spawns.stream().filter(outside).findFirst().orElse(null);
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
