package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.MobArea;
import dev.dasan.customdungeons.mob.SafeTerrain;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.potion.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Permanent audit reproductions; T58a behavior remains protected alongside T58b. */
class CombatZoneGameplayRegressionTest {
    private static void chargeHole(CombatServiceTest.Fixture f) {
        for(int x=-1;x<=1;x++)for(int z=3;z<=5;z++)for(int y=61;y<=63;y++) {
            var block=f.world.getBlockAt(x,y,z);
            when(block.getType()).thenReturn(Material.AIR);when(block.isSolid()).thenReturn(false);
            when(block.isPassable()).thenReturn(true);when(block.isEmpty()).thenReturn(true);
        }
    }
    @Test void chargeWarningAndTravelStopBeforeAnUnescapableThreeBlockHole() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.positions.put(f.player,new Location(f.world,0,64,10));chargeHole(f);
            f.cast("charge");f.step(60);
            assertTrue(f.mobPosition.getZ()<3.5,"Charge moved across the unsupported floor into the closed hole");
            var marks=mockingDetails(f.world).getInvocations().stream()
                    .filter(i->i.getMethod().getName().equals("spawnParticle")&&i.getArgument(0)==Particle.ENCHANT)
                    .map(i->(double)i.getArgument(5)).toList();
            assertFalse(marks.isEmpty());assertTrue(marks.stream().allMatch(z->z<3.5));
            assertFalse(CombatService.paused(f.caster),"An unsafe drop is not a physical wall stun");
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void aHoleOpeningAfterTheWarningStopsChargeWithoutGrantingAFalseWallStun() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.positions.put(f.player,new Location(f.world,0,64,10));f.cast("charge");chargeHole(f);f.step(60);
            assertTrue(f.mobPosition.getZ()<3.5);assertFalse(CombatService.paused(f.caster));
        }
    }
    @ParameterizedTest @ValueSource(strings={"boss_totem","decoys","blink_behind"})
    void combatDestinationsWithoutAWalkableExitAreNeverUsed(String id) {
        try(var f=new CombatServiceTest.Fixture();var terrain=mockStatic(SafeTerrain.class,CALLS_REAL_METHODS)) {
            terrain.when(()->SafeTerrain.hasWalkableExit(any(Location.class),anyDouble(),anyDouble(),any(),anyDouble())).thenReturn(false);
            f.cast(id);f.step(40);
            terrain.verify(()->SafeTerrain.hasWalkableExit(any(Location.class),anyDouble(),anyDouble(),any(),anyDouble()),atLeastOnce());
            assertTrue(f.created.isEmpty());verify(f.entity,never()).teleport(any(Location.class));
        }
    }
    @Test void ultimateCannotDamageAnUndrawnCircleCap() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.positions.put(f.ally,new Location(f.world,1.3,64,2.7));
            f.cast("interruptible_ultimate",Map.of("radius",3));f.step(200);
            verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
            verify(f.player,atLeastOnce()).damage(anyDouble(),any(Entity.class));
        }
    }
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @Test void periodicChargeMustNotPermanentlyStarveTheFollowingAbility() {
        try(var f=new CombatServiceTest.Fixture()) {
            var abilities=List.of(
                    new AbilityInstance("charge",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,1,20,Map.of()),
                    new AbilityInstance("purge",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,1,20,Map.of()));
            var template=new MobTemplate("audit","HUSK","",100,1,0,0,1,Map.of(),List.of(),abilities,
                    List.of(),false,"PURPLE",null,List.of(),false);
            var caster=new ActiveMob(f.entity,template,f.host);
            var engine=new AbilityEngine(f.registry,f.platform,new Random(1));
            // Keep the player near the charge's start each cycle, as in a pursuing fight.
            for(int tick=0;tick<=310;tick++) {
                f.effects.put(f.player,new PotionEffect(PotionEffectType.STRENGTH,600,0));
                f.mobPosition=new Location(f.world,0,64,0);
                engine.tick(List.of(caster),f.tick.get());f.step(1);
            }
            verify(f.entity,atLeast(3)).setAI(false);
            verify(f.player,atLeastOnce()).removePotionEffect(PotionEffectType.STRENGTH);
        }
    }

    @Test void chargeDoesNotStunAgainstTheSlabItIsStandingOn() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.mobPosition=new Location(f.world,0,64.5,0);
            // Bottom slabs occupy [64,64.5]; the mob body starts at 64.5, without overlap.
            for(int x=-1;x<=0;x++)for(int z=-1;z<=0;z++) {
                var slab=f.world.getBlockAt(x,64,z);
                when(slab.isSolid()).thenReturn(true);when(slab.isPassable()).thenReturn(false);
                when(slab.getType()).thenReturn(Material.STONE_SLAB);
                when(slab.getBoundingBox()).thenReturn(new BoundingBox(x,64,z,x+1,64.5,z+1));
            }
            f.cast("charge");f.step(21);
            assertTrue(f.mobPosition.getZ()>0,"Charge wrongly treats its supporting slab as a wall");
        }
    }

    @Test void chargeFacesItsActualTravelDirectionInsteadOfMovingBackwards() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.mobPosition=new Location(f.world,0,64,0,180,0); // Facing -Z; the player is at +Z.
            f.cast("charge");f.step(21);
            assertTrue(f.mobPosition.getZ()>0);
            assertTrue(f.mobPosition.getDirection().getZ()>0,"Teleports preserve the old opposite yaw");
        }
    }

    @Test void buffStealKeepsTheWarnedAnchorWhenPlayerEscapes() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.effects.put(f.player,new PotionEffect(PotionEffectType.STRENGTH,600,0));
            f.cast("buff_steal");
            for(int i=1;i<=20;i++) {
                f.positions.put(f.player,new Location(f.world,i*.15,64,2));f.step(1);
            }
            var marks=mockingDetails(f.world).getInvocations().stream()
                    .filter(i->i.getMethod().getName().equals("spawnParticle")&&i.getArgument(0)==Particle.ENCHANT)
                    .map(i->new Location(f.world,(double)i.getArgument(3),(double)i.getArgument(4),(double)i.getArgument(5))).toList();
            assertFalse(marks.isEmpty());
            assertTrue(marks.stream().allMatch(at->Math.abs(at.getX())<=1.001&&Math.abs(at.getZ()-2)<=1.001));
            verify(f.player,never()).removePotionEffect(PotionEffectType.STRENGTH);
            verify(f.entity,never()).addPotionEffect(any());
        }
    }

    @ParameterizedTest @ValueSource(strings={"boss_totem","decoys"})
    void indoorCombatSummonsUseTheRoomFloorInsteadOfTheRoof(String id) {
        try(var f=new CombatServiceTest.Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-40,60,-40,40,69,40)));
            when(f.world.getHighestBlockYAt(anyInt(),anyInt(),any(HeightMap.class))).thenReturn(70);
            when(f.world.getBlockAt(anyInt(),eq(70),anyInt())).thenAnswer(i->{
                var roof=mock(org.bukkit.block.Block.class);when(roof.isSolid()).thenReturn(true);
                when(roof.isPassable()).thenReturn(false);when(roof.getType()).thenReturn(Material.STONE);return roof;
            });
            f.cast(id);f.step(20);
            assertFalse(f.created.isEmpty(),"Safe room floor exists, but all candidates are taken from the roof");
        }
    }

    @Test void decoysMustNotInheritTheOwnersTemporaryChargeLock() {
        try(var f=new CombatServiceTest.Fixture()) {
            var ai=new AtomicBoolean(true);
            when(f.entity.hasAI()).thenAnswer(i->ai.get());
            doAnswer(i->{ai.set(i.getArgument(0));return null;}).when(f.entity).setAI(anyBoolean());
            doAnswer(i->{
                Mob copy=mock(Mob.class);var copyAi=new AtomicBoolean(ai.get());
                when(copy.hasAI()).thenAnswer(call->copyAi.get());
                doAnswer(call->{copyAi.set(call.getArgument(0));return null;}).when(copy).setAI(anyBoolean());
                when(copy.getUniqueId()).thenReturn(UUID.randomUUID());when(copy.isValid()).thenReturn(true);
                doReturn(CombatServiceTest.pdc()).when(copy).getPersistentDataContainer();
                when(copy.getAttribute(any())).thenReturn(mock(org.bukkit.attribute.AttributeInstance.class));
                when(copy.getWorld()).thenReturn(f.world);when(copy.getLocation()).thenReturn(new Location(f.world,3,64,0));
                when(copy.getBoundingBox()).thenReturn(new BoundingBox(2.7,64,-.3,3.3,65.8,.3));
                when(copy.spawnAt(any(),any())).thenReturn(true);f.created.add(copy);return copy;
            }).when(f.entity).copy();
            f.entity.setAI(false);f.cast("decoys");f.step(20);
            assertEquals(2,f.created.size());
            assertTrue(f.created.stream().allMatch(entity->((Mob)entity).hasAI()),
                    "Owner was copied while setAI(false); decoys never restore their copied AI");
        }
    }
    @Test void nativeDecoyMeleeCannotDamageAParticipantOutsideTheWarnedHost() {
        try(var f=new CombatServiceTest.Fixture()) {
            f.cast("decoys");f.step(20);
            var copy=f.created.stream().filter(Mob.class::isInstance).findFirst().orElseThrow();
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-3,60,-3,3,80,3)));
            f.positions.put(f.player,new Location(f.world,3.1,64,0));
            var event=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
            when(event.getEntity()).thenReturn(f.player);when(event.getDamager()).thenReturn(copy);
            when(event.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK);
            f.service.auxiliaryDamage(event);verify(event).setCancelled(true);
        }
    }

}
