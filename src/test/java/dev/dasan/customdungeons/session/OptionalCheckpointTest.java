package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.WizardParticles;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalCheckpointTest {
    final Point lobby=new Point("world",100,64,1,90,10);
    final Point previous=new Point("world",33,64,1,45,5);
    RoomDef room(String id,int x,Point checkpoint) {
        return new RoomDef(id,Region.of("world",new BlockPos(x,60,0),new BlockPos(x+15,70,15)),checkpoint,null,UnlockMode.AUTOMATIC,null,List.of());
    }
    DungeonDef definition(boolean inherited,boolean startTp) {
        return new DungeonDef("test","",true,lobby,new Point("world",160,64,1,0,0),1,2,0,1,false,0,0,false,
                new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),
                List.of(room("first",0,inherited?previous:null),room("second",16,null),room("third",32,null)))
                .withStart(StartMode.AUTO,List.of(),3,null,startTp,true,false,10);
    }
    Player player() {
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());return player;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void entireRunInheritsPointThroughRoomsAndCompletes(boolean inherited) {
        var def=definition(inherited,false);var teleports=new ArrayList<Point>();var results=new ArrayList<dev.dasan.customdungeons.storage.RunResult>();
        var session=new DungeonSession(def,false,new SessionServices(){public void teleport(Player p,Point point){teleports.add(point);}});
        session.addListener(new SessionLifecycleListener(){public void onFinished(DungeonSession s,dev.dasan.customdungeons.storage.RunResult result,Set<UUID> survivors){results.add(result);assertEquals(1,survivors.size());}});
        session.join(player());session.tick();assertEquals(List.of(lobby),teleports);
        for(int i=0;i<3;i++) {
            assertEquals(i,session.roomIndex());assertEquals(inherited?previous:lobby,session.checkpoint());
            session.enterRoom(i);session.tick();
        }
        assertEquals(List.of(dev.dasan.customdungeons.storage.RunResult.COMPLETED),results);
        assertEquals(SessionState.FREE,session.state().state());
    }
    @Test void validDungeonWithWavesAndNoCheckpointsCompletesAfterEveryMobDies() {
        var base=definition(false,false);var template=new MobTemplate("zombie","ZOMBIE","",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var rooms=new ArrayList<RoomDef>();
        for(int i=0;i<base.rooms().size();i++) {
            var r=base.rooms().get(i);var p=new Point("world",i*16+1,64,1,0,0);
            var spawner=new SpawnerDef("spawn",p,1,List.of(new WaveDef(List.of(new WaveEntry(template.id(),1,0)),SpawnMode.SIMULTANEOUS,0,0)));
            rooms.add(new RoomDef(r.id(),r.region(),null,i==base.rooms().size()-1?null:r.region(),UnlockMode.AUTOMATIC,null,List.of(spawner)));
        }
        var def=new DungeonDef(base.id(),"",true,lobby,base.exit(),1,2,5,1,false,0,0,false,base.scaling(),Map.of(),base.reward(),rooms)
                .withStart(StartMode.AUTO,List.of(),3,null,false,true,false,10);
        var errors=new dev.dasan.customdungeons.config.Validator().validate(def,Map.of(template.id(),template));assertTrue(errors.isEmpty(),errors::toString);
        var session=new DungeonSession(def,false,new SessionServices(){
            public dev.dasan.customdungeons.runtime.ActiveMob spawn(DungeonSession s,String id,Location at) {
                var entity=mock(org.bukkit.entity.Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.isValid()).thenReturn(true);
                return new dev.dasan.customdungeons.runtime.ActiveMob(entity,template,s);
            }
        });
        session.join(player());session.forceStart();session.tick();
        for(int i=0;i<rooms.size();i++) {
            assertEquals(i,session.roomIndex());assertEquals(lobby,session.checkpoint());session.enterRoom(i);session.tick();
            assertEquals(1,session.mobs().size());var mob=session.mobs().iterator().next();session.mobRemoved(mob.entity().getUniqueId(),null);session.tick();
        }
        assertEquals(SessionState.FREE,session.state().state());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void blockedMovementAndReentryUseInheritedPointWithOrientation(boolean inherited) {
        var def=definition(inherited,false);var session=new DungeonSession(def,false,new SessionServices(){});var p=player();
        session.join(p);session.tick();session.enterRoom(0);session.tick();assertEquals(1,session.roomIndex());
        var manager=mock(SessionManager.class);when(manager.sessionOf(p.getUniqueId())).thenReturn(Optional.of(session));
        var listener=new SessionListener(manager);var world=mock(World.class);when(world.getName()).thenReturn("world");
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            for(int fromX:new int[]{16,200}) {
                var event=new PlayerMoveEvent(p,new Location(world,fromX,64,1),new Location(world,34,64,1));listener.move(event);
                Point expected=inherited?previous:lobby;
                assertEquals(DungeonSessionRuntime.location(expected),event.getTo());
            }
        }
    }
    @Test void startTeleportUsesFirstPointAndDoesNotBorrowAFutureCheckpoint() {
        var def=definition(true,true);var teleports=new ArrayList<Point>();
        var session=new DungeonSession(def,false,new SessionServices(){public void teleport(Player p,Point point){teleports.add(point);}});
        session.join(player());session.tick();assertEquals(List.of(lobby,previous),teleports);
        var future=definition(false,false);assertEquals(lobby,future.checkpoint(0));
    }
    @Test void latestCheckpointTakesOverOnlyWhenItsRoomIsUnlocked() {
        var later=new Point("world",49,66,3,120,20);
        var base=definition(true,false);
        var def=new DungeonDef(base.id(),"",true,lobby,base.exit(),1,2,0,1,false,0,0,false,base.scaling(),Map.of(),base.reward(),
                List.of(room("first",0,null),room("second",16,previous),room("third",32,null),room("fourth",48,later),room("fifth",64,null)))
                .withStart(StartMode.AUTO,List.of(),3,null,false,true,false,10);
        var session=new DungeonSession(def,false,new SessionServices(){});session.join(player());session.tick();
        for(var expected:List.of(lobby,previous,previous,later,later)) {
            assertEquals(expected,session.checkpoint());int index=session.roomIndex();session.enterRoom(index);session.tick();
        }
    }
    @Test void keysUseRoomCenterWhenNoCheckpointAndKeepLegacyPoint() {
        var def=definition(false,false);var room=def.rooms().get(1);var center=new Point("world",24,65.5,8,0,0);
        assertEquals(center,room.keyFallback());
        assertEquals(center,DoorService.keyPosition(room.region(),null,room.keyFallback(),p->false));
        var door=Region.of("world",new BlockPos(31,60,0),new BlockPos(31,70,15));
        assertEquals(center,DoorService.keyPosition(room.region(),door,room.keyFallback(),p->false));
        assertEquals(previous,definition(true,false).rooms().getFirst().keyFallback());
        var world=mock(World.class);when(world.getName()).thenReturn("world");
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            assertEquals(new Location(world,24,65.5,8),DoorService.keyRespawn(room));
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void chunkPreparationLoadsResolvedPointAsynchronouslyAndReleasesTickets(boolean inherited) {
        var def=definition(inherited,false);var world=mock(World.class);when(world.getUID()).thenReturn(UUID.randomUUID());
        var manager=mock(SessionManager.class);var chunk=mock(Chunk.class);var pending=new CompletableFuture<Chunk>();
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(pending);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            assertTrue(SessionManager.worldsReady(def,"world"::equals));assertFalse(SessionManager.worldsReady(def,w->false));
            var chunks=new SessionChunks(manager);assertFalse(chunks.prepare(def));
            verify(world).getChunkAtAsync(inherited?2:6,0);verify(world,never()).getChunkAt(anyInt(),anyInt());
            when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);pending.complete(chunk);
            assertTrue(chunks.prepare(def));verify(manager,atLeastOnce()).retainChunk(chunk);
            chunks.close();verify(manager,atLeastOnce()).releaseChunk(chunk);
        }
    }
    @Test void particlesUseResolvedPointWithoutDuplicatingInheritedMarkers() {
        var dots=WizardParticles.prepare(definition(true,false));
        assertEquals(1,dots.stream().filter(d->d.x()==previous.x()&&d.y()==previous.y()+.15&&d.z()==previous.z()).count());
        assertDoesNotThrow(()->WizardParticles.prepare(definition(false,false)));
    }
}
