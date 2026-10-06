package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.*;

/** Async loading only; the existing ticker acquires/releases shared tickets on the main thread. */
final class SessionChunks {
    private record Position(UUID world,int x,int z) {}
    private static final class Request {
        final World world;
        final int x,z;
        Chunk chunk;
        boolean pending;
        Request(World world,int x,int z) { this.world=world; this.x=x; this.z=z; }
    }
    private record Loaded(Request request,Chunk chunk,Throwable error) {}
    private final SessionManager manager;
    private final Map<Position,Request> requests=new LinkedHashMap<>();
    private final Queue<Loaded> loaded=new ConcurrentLinkedQueue<>();
    private volatile boolean closed;
    private boolean requested;
    SessionChunks(SessionManager manager) { this.manager=manager; }

    boolean prepare(DungeonDef def) {
        if (closed) return false;
        if (!requested) {
            requested=true;
            for (RoomDef room : def.rooms()) {
                request(room.region());
                if (room.door()!=null) {
                    request(room.door());
                    // The key fallback can be in the chunk immediately beside the door.
                    Region door=room.door();
                    request(new Point(door.world(),door.min().x()+.5,door.min().y()+.5,door.min().z()-.5,0,0));
                }
                request(room.checkpoint());
                for (SpawnerDef spawner : room.spawners()) request(spawner.location());
            }
            if(def.entranceDoor()!=null)request(def.entranceDoor());
            request(def.exit());
        }
        tick();
        boolean ready=true;
        for (Request request : requests.values()) ready &= ready(request);
        return ready;
    }
    private void request(Region region) {
        World world=Objects.requireNonNull(Bukkit.getWorld(region.world()));
        for (int x=region.min().x()>>4;x<=region.max().x()>>4;x++)
            for (int z=region.min().z()>>4;z<=region.max().z()>>4;z++) request(world,x,z);
    }
    private void request(Point point) {
        request(Objects.requireNonNull(Bukkit.getWorld(point.world())),((int)Math.floor(point.x()))>>4,((int)Math.floor(point.z()))>>4);
    }
    private Request request(World world,int x,int z) {
        Position position=new Position(world.getUID(),x,z);
        Request request=requests.computeIfAbsent(position,key -> new Request(world,x,z));
        if (request.chunk==null && !request.pending) load(request);
        return request;
    }
    private void load(Request request) {
        request.pending=true;
        request.world.getChunkAtAsync(request.x,request.z).whenComplete((chunk,error) -> {
            if (!closed) loaded.add(new Loaded(request,chunk,error));
        });
    }
    void tick() {
        if (closed) return;
        Loaded result;
        while ((result=loaded.poll())!=null) {
            Request request=result.request(); request.pending=false;
            if (result.error()!=null) throw new IllegalStateException("Session chunk preload failed",result.error());
            if (!request.world.isChunkLoaded(request.x,request.z)) continue;
            Chunk chunk=Objects.requireNonNull(result.chunk());
            manager.retainChunk(chunk); request.chunk=chunk;
        }
    }
    private boolean ready(Request request) {
        if (request.chunk!=null && request.world.isChunkLoaded(request.x,request.z)) return true;
        if (request.chunk!=null) { manager.releaseChunk(request.chunk); request.chunk=null; }
        if (!request.pending) load(request);
        return false;
    }
    boolean ready(Location at) {
        if (closed) return false;
        tick();
        return ready(request(Objects.requireNonNull(at.getWorld()),at.getBlockX()>>4,at.getBlockZ()>>4));
    }
    void close() {
        closed=true;
        for (Request request : requests.values()) if (request.chunk!=null) manager.releaseChunk(request.chunk);
        requests.clear(); loaded.clear();
    }
}
