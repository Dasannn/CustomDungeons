package dev.dasan.customdungeons.ability.zone;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.MobArea;
import dev.dasan.customdungeons.mob.SafeTerrain;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permanent reproductions of RF-HAB2-13 h-l from the 2026-10-09 audit. */
class ZoneGameplayRegressionTest {
    @Test void rainKeepsItsFloorReferenceWhenTheCasterStepsDownOntoAHalfSlab() throws Exception {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-8,60,-8,8,80,8)));
            for(int y:new int[]{64,65,66,67,73}) {
                var block=f.world.getBlockAt(5,y,0);
                when(block.isEmpty()).thenReturn(false);when(block.isPassable()).thenReturn(false);
                when(block.isSolid()).thenReturn(true);when(block.getType()).thenReturn(Material.STONE);
            }
            f.cast("arrow_rain");
            when(f.entity.getLocation()).thenReturn(new Location(f.world,0,63.5,0));
            assertTrue(SafeTerrain.interior(f.world,5,0,63,.2,1,f.host.area()).isEmpty(),
                    "The later caster height cannot locate this four-block raised floor");
            var owners=ZoneService.class.getDeclaredField("owners");owners.setAccessible(true);
            Object zone=((Set<?>)((Map<?,?>)owners.get(f.service)).values().iterator().next()).iterator().next();
            var launch=ZoneService.class.getDeclaredMethod("launchPoint",zone.getClass(),Location.class,int.class);launch.setAccessible(true);
            @SuppressWarnings("unchecked") var at=(Optional<Location>)launch.invoke(f.service,zone,new Location(f.world,5.5,64,.5),10);
            assertEquals(72.9,at.orElseThrow().getY(),1e-8);
        }
    }
    @Test void riftDoesNotTeleportAPlayerIntoAFloorWithoutAWalkableExit() {
        try(var f=new ZoneServiceTest.Fixture();var terrain=mockStatic(SafeTerrain.class,CALLS_REAL_METHODS)) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-24,40,-24,25,89,25)));
            terrain.when(()->SafeTerrain.hasWalkableExit(any(Location.class),anyDouble(),anyDouble(),any(),anyDouble())).thenReturn(false);
            f.cast("rift");f.step(20);
            terrain.verify(()->SafeTerrain.hasWalkableExit(any(Location.class),anyDouble(),anyDouble(),any(),anyDouble()),atLeastOnce());
            verify(f.player,never()).teleport(any(Location.class));
        }
    }
    @Test void beamReachesTheClippedRoomBoundaryWithoutAnUnwarnedGap() {
        try(var f=new ZoneServiceTest.Fixture()) {
            var area=new MobArea("test",new BoundingBox(-3,60,-3,3,80,3));
            when(f.host.area()).thenReturn(area);
            f.positions.put(f.player,new Location(f.world,0,64,2.99));
            f.positions.put(f.ally,new Location(f.world,0,64,2.99));
            f.cast("charged_beam",Map.of("length",8,"width",2));f.step(100);
            assertTrue(f.warningMarks(f.player).stream().anyMatch(at->at.getZ()>2.99),
                    "The clipped end cap must reach the actual room boundary");
            verify(f.player).damage(anyDouble(),eq(f.entity));
        }
    }
    @Test void coincidentPlayersStillProduceThreeDistinctPillarTargets() throws Exception {
        try(var f=new ZoneServiceTest.Fixture()) {
            var third=f.player(0,2);f.players.add(third);
            f.positions.put(f.ally,f.player.getLocation());
            f.cast("falling_pillars",Map.of("count",3));
            var owners=ZoneService.class.getDeclaredField("owners");owners.setAccessible(true);
            var targets=new HashSet<java.util.UUID>();
            for(var zones:((Map<?,Set<?>>)owners.get(f.service)).values())for(var zone:zones) {
                var anchor=zone.getClass().getDeclaredField("anchorPlayer");anchor.setAccessible(true);targets.add((java.util.UUID)anchor.get(zone));
            }
            assertEquals(Set.of(f.player.getUniqueId(),f.ally.getUniqueId(),third.getUniqueId()),targets);
        }
    }
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @ParameterizedTest
    @ValueSource(strings={"vortex","inverted_gravity","sweep","poison_pools","charged_beam","arrow_rain"})
    void validPlayersInsideSmallRoomStillReceiveTheWarnedAbility(String id) {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-3,60,-3,3,80,3)));
            f.positions.put(f.player,new Location(f.world,2.5,64,id.equals("sweep")?2:0));
            f.positions.put(f.ally,new Location(f.world,2.5,64,id.equals("sweep")?2:0));
            f.cast(id);f.step(100);
            switch(id) {
                case "inverted_gravity" -> verify(f.player).addPotionEffect(any());
                case "arrow_rain" -> assertFalse(f.created.isEmpty(),"No arrows despite a valid player inside the room");
                default -> verify(f.player,atLeastOnce()).damage(anyDouble(),eq(f.entity));
            }
        }
    }

    @Test void riftCanTeleportWithinTheLiveTestCube() {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-24,40,-24,25,89,25)));
            f.cast("rift");f.step(20);
            verify(f.player).teleport(any(Location.class));
        }
    }

    @Test void riftFindsTheRoomFloorInsteadOfRejectingTheRoofSurface() {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-40,60,-40,40,69,40)));
            when(f.world.getHighestBlockYAt(anyInt(),anyInt(),any(HeightMap.class))).thenReturn(70);
            // A roof across the room. The walking floor at y=64 is still entirely safe.
            when(f.world.getBlockAt(anyInt(),eq(70),anyInt())).thenAnswer(i->{
                var roof=mock(org.bukkit.block.Block.class);
                when(roof.isSolid()).thenReturn(true);when(roof.isPassable()).thenReturn(false);
                when(roof.getType()).thenReturn(Material.STONE);return roof;
            });
            f.cast("rift");f.step(20);
            verify(f.player).teleport(argThat((Location at)->at.getY()==64&&f.host.area().contains(at)));
        }
    }

    @Test void rainArrowsStartBelowTheRoomRoofSoTheyCanReachTheMarkedFloor() {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-40,60,-40,40,69,40)));
            f.cast("arrow_rain");f.step(35);
            assertFalse(f.created.isEmpty());
            var spawns=mockingDetails(f.world).getInvocations().stream()
                    .filter(call->call.getMethod().getName().equals("spawn")&&call.getArgument(1)==Arrow.class)
                    .map(call->(Location)call.getArgument(0)).toList();
            assertTrue(spawns.stream().allMatch(at->at.getY()<69),
                    "All arrows start at y=74, above a roof at y=69 and outside the room");
        }
    }

    @Test void vortexPullAndBurstKeepTheInitiallyWarnedCenterWhenCasterMoves() {
        try(var f=new ZoneServiceTest.Fixture()) {
            f.cast("vortex");
            when(f.entity.getLocation()).thenReturn(new Location(f.world,0,64,6));
            f.positions.put(f.player,new Location(f.world,0,64,2));
            f.positions.put(f.ally,new Location(f.world,0,64,5));
            f.step(61);
            verify(f.player,atLeastOnce()).setVelocity(argThat(v->v.getZ()<0));
            verify(f.player).damage(6,f.entity);
            verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
        }
    }

    @Test void configuredThreePillarsSelectThreeDistinctAvailablePlayers() {
        try(var f=new ZoneServiceTest.Fixture()) {
            f.players.add(f.player(2,2));
            var ability=f.registry.get("falling_pillars").orElseThrow();
            // GUI defaults to NEAREST: AbilityEngine supplies only the selected anchor.
            ability.execute(new AbilityContext(f.caster,List.of(f.player),
                    new ParamValues(Map.of(),ability.params()),f.host,null));
            assertEquals(3,f.service.activeCount(),"count=3 must not silently become 1 with NEAREST");
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"vortex","inverted_gravity","cracked_floor","sweep","falling_pillars","poison_pools","charged_beam","arrow_rain","rift"})
    void clippedWarningsStayInsideAndNeverAuthorizeEffectsOnPlayersOutsideTheRoom(String id) {
        try(var f=new ZoneServiceTest.Fixture()) {
            var area=new MobArea("test",new BoundingBox(-3,60,-3,3,80,3));
            when(f.host.area()).thenReturn(area);
            f.positions.put(f.player,new Location(f.world,0,64,1));
            f.positions.put(f.ally,new Location(f.world,0,64,1));
            f.positions.put(f.foreign,new Location(f.world,3.1,64,1));f.players.add(f.foreign);
            f.cast(id,Map.of("damage",2));f.step(100);
            var marks=f.warningMarks(f.player);
            assertFalse(marks.isEmpty(),"clipped boundary still has a visible warning");
            assertTrue(marks.stream().allMatch(area::contains));
            verify(f.foreign,never()).damage(anyDouble(),any(Entity.class));
            verify(f.foreign,never()).teleport(any(Location.class));
            verify(f.foreign,never()).addPotionEffect(any());verify(f.foreign,never()).setVelocity(any());
            if(id.equals("arrow_rain")&&!f.created.isEmpty()) {
                var hit=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
                when(hit.getDamager()).thenReturn(f.created.getFirst());when(hit.getEntity()).thenReturn(f.foreign);
                f.service.arrowDamage(hit);verify(hit).setCancelled(true);
            }
        }
    }
    @Test void lowCeilingCancelsRainRatherThanCreatingArrowsOnTheRoof() {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.world.getBlockAt(anyInt(),eq(67),anyInt())).thenAnswer(i->{var b=mock(org.bukkit.block.Block.class);when(b.getType()).thenReturn(Material.STONE);return b;});
            f.cast("arrow_rain");f.step(150);assertTrue(f.created.isEmpty());
            verify(f.world,never()).getHighestBlockYAt(anyInt(),anyInt(),any());
        }
    }
    @Test void pillarPreferenceIsDistinctAndAdditionalAnchorsRespectTheActualSelectorRange() {
        try(var f=new ZoneServiceTest.Fixture()) {
            var outside=f.player(6,0);f.players.add(outside);
            var a=f.registry.get("falling_pillars").orElseThrow();
            var context=new AbilityContext(f.caster,List.of(f.player,f.player),new ParamValues(Map.of(),a.params()),f.host,null);
            var watch=AbilityCasts.class.getDeclaredMethod("watch",AbilityContext.class,double.class,java.util.function.BooleanSupplier.class,Runnable.class,java.util.function.Consumer.class);
            watch.setAccessible(true);watch.invoke(null,context,3d,(java.util.function.BooleanSupplier)()->true,(Runnable)()->{},(java.util.function.Consumer<Boolean>)retry->{});
            a.execute(context);assertEquals(2,f.service.activeCount());
            f.step(40);verify(outside,never()).damage(anyDouble(),any(Entity.class));
        } catch(ReflectiveOperationException error){throw new AssertionError(error);}
    }

    @Test void aCircleWhoseAnchorIsJustOutsideStillWarnsAndDamagesItsRoomIntersection() {
        try(var f=new ZoneServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-3,60,-3,3,80,3)));
            f.positions.put(f.player,new Location(f.world,3.2,64,0));
            f.positions.put(f.ally,new Location(f.world,2.5,64,0));
            var a=f.registry.get("poison_pools").orElseThrow();
            a.execute(new AbilityContext(f.caster,List.of(f.player),new ParamValues(Map.of("count",1),a.params()),f.host,null));
            f.step(20);verify(f.ally).damage(1,f.entity);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        }
    }

    @Test void persistentPoolAnimationHasABoundedPacketRateWithoutReducingWarningOrDamage() {
        try(var f=new ZoneServiceTest.Fixture()) {
            new AbilityEngine(f.registry,f.platform);
            f.cast("poison_pools",Map.of("count",1));f.step(19);
            assertTrue(f.warningMarks(f.player).size()>=12,"the complete advance-warning contour remains visible");
            clearInvocations(f.world,f.player);f.step(21);
            var packets=mockingDetails(f.world).getInvocations().stream()
                    .filter(i->i.getMethod().getName().equals("spawnParticle")&&i.getArgument(0)==Particle.WITCH).toList();
            assertFalse(packets.isEmpty(),"active pools remain visible");
            assertTrue(packets.size()<=24,"persistent animation must fit the packet-rate budget across concurrent pools");
            verify(f.player,times(2)).damage(1,f.entity);
        }
    }

    @Test void zoneGeometrySharesPacketsWithoutLosingPerPointAudienceOrItsAdvanceWarning() {
        try(var f=new ZoneServiceTest.Fixture()) {
            new AbilityEngine(f.registry,f.platform);
            f.players.add(f.foreign);f.positions.put(f.foreign,new Location(f.world,51,64,0));
            f.cast("poison_pools",Map.of("count",1,"radius",5));f.step(25);
            var packets=mockingDetails(f.world).getInvocations().stream()
                    .filter(i->i.getMethod().getName().equals("spawnParticle")&&i.getArgument(1) instanceof List<?>).toList();
            assertFalse(packets.isEmpty(),"one shared packet per point rather than one per player");
            assertTrue(packets.stream().anyMatch(i->i.getArgument(0)==Particle.ENCHANT));
            assertTrue(packets.stream().anyMatch(i->i.getArgument(0)==Particle.WITCH));
            for(var packet:packets) {
                var at=new Location(f.world,packet.getArgument(3),packet.getArgument(4),packet.getArgument(5));
                var expected=f.players.stream().filter(p->f.positions.get(p).distanceSquared(at)<=48*48).toList();
                assertEquals(expected,packet.getArgument(1),"audience belongs to this point, including the view-radius edge");
            }
            verify(f.player,never()).spawnParticle(any(Particle.class),any(Location.class),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
            verify(f.player,atLeastOnce()).damage(anyDouble(),eq(f.entity));
        }
    }

}
