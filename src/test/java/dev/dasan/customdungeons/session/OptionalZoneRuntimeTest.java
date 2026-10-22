package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.config.SpawnerPresets;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalZoneRuntimeTest {
    final World world=mock(World.class);
    final List<ActiveMob> spawned=new ArrayList<>();
    boolean intro=true, chunks=true;
    int removed, finishes;
    java.util.function.BiConsumer<DungeonSession,ActiveMob> onRemoved=(s,m)->{};
    final SessionServices services=new SessionServices() {
        public boolean introTick(DungeonSession s) { return intro; }
        public boolean canSpawnAt(Location at) { return chunks; }
        public Location spawnLocation(DungeonSession s,SpawnOrigin origin) { return new Location(world,1,64,1); }
        public ActiveMob spawn(DungeonSession s,SpawnOrigin origin,String template,Location at) {
            var entity=mock(Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.isValid()).thenReturn(true);when(entity.getLocation()).thenReturn(at);
            when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            var def=new MobTemplate(template,"ZOMBIE","",20,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
            var mob=new ActiveMob(entity,def,s.host(origin));spawned.add(mob);return mob;
        }
        public void removed(DungeonSession s,ActiveMob m,org.bukkit.event.Event event) { removed++;onRemoved.accept(s,m); }
        public void finish(DungeonSession s) { finishes++; }
    };
    OptionalZoneRuntimeTest() { when(world.getName()).thenReturn("world"); }
    Region region(int x) { return Region.of("world",new BlockPos(x,60,0),new BlockPos(x+9,70,9)); }
    Point point(int x) { return new Point("world",x,64,1,0,0); }
    List<SpawnerDef> spawners(int count) {
        return List.of(new SpawnerDef("same",point(1),0,List.of(new WaveDef(List.of(new WaveEntry("zombie",count,0)),SpawnMode.SIMULTANEOUS,0,0))));
    }
    DungeonDef definition(int count,boolean cinematic) {
        var base=new DungeonSessionFlowTest().definition(3);
        var rooms=List.of(new RoomDef("first",region(0),point(1),region(9),UnlockMode.AUTOMATIC,null,spawners(count)),
                new RoomDef("last",region(20),point(21),null,UnlockMode.AUTOMATIC,null,spawners(1)));
        return SpawnerPresets.withRooms(base,rooms).withOptionalZones(List.of(
                new OptionalZoneDef("a",region(0),spawners(count)),new OptionalZoneDef("b",region(0),spawners(count))))
                .withStart(StartMode.AUTO,List.of(),3,null,false,true,cinematic,5);
    }
    Player player(int x) {
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isOnline()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(p.getLocation()).thenReturn(new Location(world,x,64,1));return p;
    }
    DungeonSession start(DungeonDef def,Player... players) {
        var s=new DungeonSession(def,true,services);for(var p:players)s.join(p);s.forceStart();return s;
    }
    void ticks(DungeonSession s,int count) { for(int i=0;i<count;i++)s.tick(); }
    List<ActiveMob> zone(DungeonSession s,String id) {
        return s.mobs().stream().filter(m->s.origin(m.entity().getUniqueId()).equals(new SpawnOrigin(SpawnOrigin.Kind.ZONE,id,"same"))).toList();
    }
    void remove(DungeonSession s,Collection<ActiveMob> mobs) { for(var m:mobs)s.mobRemoved(m.entity().getUniqueId(),null); }

    @Test void overlappingZonesAndRoomActivateOnceForPlayersAlreadyInsideAfterGroupIntro() {
        var s=start(definition(1,true),player(1),player(2));ticks(s,20);assertTrue(spawned.isEmpty());
        intro=false;ticks(s,10);assertEquals(6,spawned.size()); // ceil(1 * 1.25) in each origin
        assertEquals(2,s.roomMobs().size());assertEquals(2,zone(s,"a").size());assertEquals(2,zone(s,"b").size());
        remove(s,zone(s,"a"));ticks(s,30);assertEquals(6,spawned.size());assertEquals(2,zone(s,"b").size());
        assertEquals(0,s.roomIndex());
    }
    @Test void sameSpawnerIdsDoNotBlockRoomAdvanceAndHostStaysAtItsOrigin() {
        var s=start(definition(1,false),player(1));ticks(s,10);
        var zone=zone(s,"a").getFirst();var host=zone.session();
        remove(s,s.roomMobs());s.tick();assertEquals(1,s.roomIndex());assertEquals(2,s.mobs().size());
        assertTrue(host.area().contains("world",1,64,1));assertFalse(host.area().contains("world",21,64,1));
        assertEquals(List.of(zone),host.mobs());
        s.enterRoom(1);s.tick();assertEquals(3,s.mobs().size());
        remove(s,s.roomMobs());s.tick();assertEquals(SessionState.COMPLETED,s.state().state());assertTrue(s.mobs().isEmpty());assertEquals(1,finishes);
    }
    @Test void globalLimitRetainsEveryOrderAndNeverClearsAQueuedZone() {
        var s=start(definition(2,false),player(1));s.maxAlive(1);ticks(s,10);assertEquals(1,spawned.size());
        for(int i=0;i<6;i++) {
            assertEquals(1,s.mobs().size());remove(s,s.mobs());s.tick();
        }
        assertEquals(6,spawned.size());assertTrue(s.mobs().isEmpty());assertEquals(1,s.roomIndex());
        ticks(s,20);assertEquals(6,spawned.size()); // both zones cleared exactly once
    }
    @Test void unavailableChunksKeepOrdersAndProgressPending() {
        chunks=false;var s=start(definition(1,false),player(1));ticks(s,30);
        assertTrue(spawned.isEmpty());assertTrue(s.roomStarted());assertEquals(0,s.roomIndex());
        chunks=true;s.tick();assertEquals(3,spawned.size());
    }
    @Test void lateActivationUsesInitialGroupEvenAfterSomeoneLeaves() {
        var p=player(100);var ally=player(100);var s=start(definition(1,false),p,ally);
        s.leave(ally.getUniqueId());when(p.getLocation()).thenReturn(new Location(world,1,64,1));ticks(s,10);
        assertEquals(6,spawned.size());assertEquals(2,zone(s,"a").size());
    }
    @Test void silentRemovalAndDeathSummonsBelongToTheirZone() {
        var s=start(definition(1,false),player(1));ticks(s,10);var owner=zone(s,"a").getFirst();
        var minion=owner.session().spawnMinion("zombie",new Location(world,1,64,1),owner);
        assertNotNull(minion);assertEquals(s.origin(owner.entity().getUniqueId()),s.origin(minion.entity().getUniqueId()));
        when(owner.entity().isValid()).thenReturn(false);s.tick();assertEquals(1,removed);
        assertEquals(List.of(minion),zone(s,"a"));assertEquals(1,s.roomMobs().size());
    }
    @Test void testSkipWaveAffectsOnlyMandatoryRoom() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var s=start(definition(1,false),player(1));ticks(s,10);s.skipWave();
        for(var m:s.roomMobs())verify(m.entity()).setHealth(0);
        for(var m:zone(s,"a"))verify(m.entity(),never()).setHealth(anyDouble());
        for(var m:zone(s,"b"))verify(m.entity(),never()).setHealth(anyDouble());
    }
    @Test void spectatorsDeadAndOfflineParticipantsDoNotActivateZones() {
        var p=player(1);when(p.getGameMode()).thenReturn(GameMode.SPECTATOR);
        var s=start(definition(1,false),p);ticks(s,10);assertTrue(spawned.isEmpty());
        when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.isDead()).thenReturn(true);ticks(s,10);assertTrue(spawned.isEmpty());
        when(p.isDead()).thenReturn(false);when(p.isOnline()).thenReturn(false);ticks(s,10);assertTrue(spawned.isEmpty());
        when(p.isOnline()).thenReturn(true);ticks(s,10);assertEquals(3,spawned.size());
    }
    @Test void zonesCountAsDungeonOccupancyWithoutGlobalArea() {
        var def=definition(1,false).withOptionalZones(List.of(new OptionalZoneDef("remote",region(100),spawners(1))));
        assertNull(def.area());assertTrue(DungeonSessionRuntime.containsDungeon(def,new Location(world,101,64,1)));
        assertFalse(DungeonSessionRuntime.containsDungeon(def,new Location(world,111,64,1)));
    }
    @Test void ignoringEveryZoneStillCompletes() {
        var s=start(definition(1,false),player(100));s.enterRoom(0);s.tick();remove(s,s.mobs());s.tick();
        s.enterRoom(1);s.tick();remove(s,s.mobs());s.tick();assertEquals(SessionState.FREE,s.state().state());
        assertEquals(2,spawned.size());
    }
    @Test void deathSummonsKeepOriginUntilCallbackReturnsAndRespectGlobalLimit() {
        var s=start(definition(1,false),player(1));ticks(s,10);s.maxAlive(3);
        var owner=zone(s,"a").getFirst();
        assertNull(owner.session().spawnMinion("zombie",owner.entity().getLocation(),owner));
        onRemoved=(session,m)->assertNotNull(m.session().spawnMinion("zombie",new Location(world,50,64,1),m));
        s.mobRemoved(owner.entity().getUniqueId(),null);
        assertEquals(3,s.mobs().size());assertEquals(1,zone(s,"a").size());
        var child=zone(s,"a").getFirst();assertTrue(child.session().area().contains(child.entity().getLocation()));
        assertNull(s.origin(owner.entity().getUniqueId()));
    }
    @Test void leavingAndReenteringDoesNotRestartEitherClearedZone() {
        var p=player(1);var s=start(definition(1,false),p);ticks(s,10);
        remove(s,zone(s,"a"));remove(s,zone(s,"b"));s.tick();
        when(p.getLocation()).thenReturn(new Location(world,100,64,1));ticks(s,20);
        when(p.getLocation()).thenReturn(new Location(world,1,64,1));ticks(s,20);
        assertEquals(3,spawned.size());assertEquals(1,s.mobs().size());
    }
    @Test void lateZoneActivatesWhileWaitingForNextRoomEntry() {
        var p=player(100);var s=start(definition(1,false),p);s.enterRoom(0);s.tick();remove(s,s.roomMobs());s.tick();
        assertEquals(1,s.roomIndex());when(p.getLocation()).thenReturn(new Location(world,1,64,1));ticks(s,10);
        assertFalse(s.roomStarted());assertEquals(2,s.mobs().size());assertEquals(1,zone(s,"b").size());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"complete","fail","stop"})
    void endingCancelsZoneQueuesAndScheduledEffectsAndNextRunStartsFresh(String mode) {
        var p=player(1);var def=definition(3,false);var s=start(def,p);s.maxAlive(1);ticks(s,10);
        int[] effects={0};s.scheduler().runLater(2,()->effects[0]++);
        s.finish(mode.equals("complete"),mode.equals("stop"));ticks(s,20);
        assertTrue(s.mobs().isEmpty());assertEquals(0,effects[0]);assertEquals(1,spawned.size());
        assertNull(s.spawnMinion("zombie",new Location(world,1,64,1),spawned.getFirst()));
        var next=start(def,player(1));ticks(next,10);assertEquals(9,next.mobs().size());
    }
    @Test void zoneKillsCountPersonallyAndGloballyButNeverReplaceSidebarGoal() {
        var p=player(1);var s=start(definition(1,false),p);ticks(s,10);
        var inputs=SidebarData.inputs(s,p,SidebarDataTest.messages(),0,false);
        assertEquals(1,inputs.values().get("mobs_left"));assertEquals("clear",inputs.objectiveKey());
        var victim=zone(s,"a").getFirst();when(victim.entity().getKiller()).thenReturn(p);
        var death=mock(org.bukkit.event.entity.EntityDeathEvent.class);when(death.getEntity()).thenReturn(victim.entity());
        s.mobRemoved(victim.entity().getUniqueId(),death);s.mobRemoved(victim.entity().getUniqueId(),death);
        assertEquals(1,s.sidebarKills(p.getUniqueId()));assertEquals(1,s.sidebarKillsTotal());
    }

    @Test void zoneWavesAndPausesProgressWhileRoomAndOtherZoneStayAlive() {
        var def=definition(1,false);var first=spawners(1).getFirst().waves().getFirst();
        var waves=List.of(new WaveDef(first.entries(),first.mode(),0,5),first);
        var zone=new OptionalZoneDef("a",region(0),List.of(new SpawnerDef("same",point(1),0,waves)));
        def=def.withOptionalZones(List.of(zone,def.optionalZones().getLast()));
        var s=start(def,player(1));ticks(s,10);remove(s,zone(s,"a"));s.tick();ticks(s,4);
        assertEquals(3,spawned.size());s.tick();assertEquals(4,spawned.size());
        assertEquals(1,zone(s,"a").size());assertEquals(1,zone(s,"b").size());assertEquals(1,s.roomMobs().size());
        remove(s,zone(s,"a"));ticks(s,20);assertEquals(4,spawned.size());assertEquals(0,s.roomIndex());
    }
    @Test void bossBarCountsOnlyTheMandatoryOriginWhenRegionsOverlap() {
        var p=player(1);var s=start(definition(1,false),p);ticks(s,10);
        var messages=SidebarDataTest.messages();new SessionBossBar(messages).update(s);
        var bar=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.bossbar.BossBar.class);verify(p).showBossBar(bar.capture());
        assertEquals(messages.get("session.bar",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("room","1"),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("wave","1"),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("mobs","1")),bar.getValue().name());
    }

}
