package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.MobArea;
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

/** Gameplay regression coverage for RF-HAB2-13. */
class CombatGameplayRegressionTest {
    @Test void alreadyAlignedWalkingDoesNotRewriteNativeRotationAndStillTurnsOnTheNextStep() {
        try(var f=new CombatServiceTest.Fixture()) {
            MobMovement.track(f.caster);when(f.entity.hasAI()).thenReturn(true);
            when(f.entity.getYaw()).thenReturn(0f);when(f.entity.getPitch()).thenReturn(0f);when(f.entity.getBodyYaw()).thenReturn(0f);
            var from=f.mobPosition.clone();var to=from.clone().add(0,0,.2);
            assertTrue(MobMovement.face(f.entity,from,to));assertEquals(0,to.getYaw());
            verify(f.entity,never()).setRotation(anyFloat(),anyFloat());verify(f.entity,never()).setBodyYaw(anyFloat());
            var turned=from.clone().add(.2,0,0);assertTrue(MobMovement.face(f.entity,from,turned));
            assertEquals(270,turned.getYaw());verify(f.entity).setRotation(270,0);verify(f.entity).setBodyYaw(270);
            verify(f.entity,never()).setTarget(any());verify(f.entity,never()).setVelocity(any());
        }
    }
    @Test void oneNoticeIsParsedOnceForAllViewersAndReloadIsVisibleOnTheNextCast() {
        try(var f=new CombatServiceTest.Fixture()) {
            var messages=spy(f.messages);when(f.platform.messages()).thenReturn(messages);
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.set("combat.notice.charge","<red>Primero");messages.load(yaml,"");
            f.cast("charge");verify(messages).get("combat.notice.charge");
            verify(f.player).sendActionBar(net.kyori.adventure.text.Component.text("Primero",net.kyori.adventure.text.format.NamedTextColor.RED));
            verify(f.ally).sendActionBar(net.kyori.adventure.text.Component.text("Primero",net.kyori.adventure.text.format.NamedTextColor.RED));
            CombatService.cleanup(f.caster);yaml.set("combat.notice.charge","<green>Segundo");messages.load(yaml,"");
            f.cast("charge");verify(messages,times(2)).get("combat.notice.charge");
            verify(f.player).sendActionBar(net.kyori.adventure.text.Component.text("Segundo",net.kyori.adventure.text.format.NamedTextColor.GREEN));
        }
    }
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    @ParameterizedTest @ValueSource(strings={"complete","retire","reload","disable"})
    void releasingCombatPauseCompensatesBaseChangesInTheSameTick(String route) throws ReflectiveOperationException {
        try(var f=new CombatServiceTest.Fixture()) {
            var speed=mock(org.bukkit.attribute.AttributeInstance.class);
            var base=new double[]{.3};
            var modifier=new java.util.concurrent.atomic.AtomicReference<org.bukkit.attribute.AttributeModifier>();
            when(f.entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)).thenReturn(speed);
            when(speed.getBaseValue()).thenAnswer(i->base[0]);
            when(speed.getModifier(any(org.bukkit.NamespacedKey.class))).thenAnswer(i->modifier.get());
            doAnswer(i->{modifier.set(i.getArgument(0));return null;}).when(speed).addTransientModifier(any());
            doAnswer(i->{modifier.set(null);return null;}).when(speed).removeModifier(any(org.bukkit.NamespacedKey.class));
            var configure=dev.dasan.customdungeons.mob.MobFactory.class.getDeclaredMethod("applyAttributes",Mob.class,MobAttributes.class);
            configure.setAccessible(true);configure.invoke(new dev.dasan.customdungeons.mob.MobFactory(f.platform),f.entity,new MobAttributes(Map.of("speed",.25)));
            f.cast("charge",Map.of("length",3d));assertTrue(CombatService.paused(f.caster));
            base[0]=.35;dev.dasan.customdungeons.mob.MobFactory.refreshSpeed(f.caster);
            switch(route) {
                case "complete" -> f.step(100);
                case "retire" -> CombatService.cleanup(f.caster);
                case "reload" -> CombatService.reset();
                case "disable" -> f.service.close();
            }
            assertFalse(CombatService.paused(f.caster));
            assertEquals(.25,base[0]+modifier.get().getAmount(),1e-9,
                "The room's charge recovery exposed the stale speed modifier before the next evaluation");
            dev.dasan.customdungeons.mob.MobFactory.clearSpeed(f.caster);
        }
    }
    @Test void zeroRawForceWithInheritedVelocityDoesNotHideBackwardsSteps() {
        try(var f=new CombatServiceTest.Fixture()) {
            MobMovement.track(f.caster);when(f.entity.hasAI()).thenReturn(true);
            var event=new org.bukkit.event.entity.EntityKnockbackByEntityEvent(f.entity,f.player,
                org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK,0,
                new org.bukkit.util.Vector(),new org.bukkit.util.Vector(0,.04,-.4));
            f.service.impulse(event);var from=f.mobPosition.clone();
            assertTrue(MobMovement.face(f.entity,from,from.clone().add(0,0,-.2)),"Previous velocity is not a real horizontal knockback impulse");
        }
    }
    @Test void aRoomReturnDoesNotReverseTheFacingOfTheNextPhysicalStep() {
        try(var f=new CombatServiceTest.Fixture()) {
            MobMovement.track(f.caster);when(f.entity.hasAI()).thenReturn(true);
            var from=f.mobPosition.clone();var returned=from.clone().add(0,0,-10);
            MobMovement.teleported(f.entity,from,returned);
            var physical=returned.clone().add(0,0,.2);
            assertTrue(MobMovement.face(f.entity,from,physical));
            assertEquals(0,physical.getYaw(),"The teleport discontinuity is not the next native walking direction");
        }
    }
    @Test void verticalDamageImpulseDoesNotSuppressWalkingOrientation() {
        try(var f=new CombatServiceTest.Fixture()) {
            MobMovement.track(f.caster);when(f.entity.hasAI()).thenReturn(true);
            var impulse=new org.bukkit.event.entity.EntityKnockbackByEntityEvent(f.entity,f.player,
                org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK,0,
                new org.bukkit.util.Vector(0,.04,0),new org.bukkit.util.Vector(0,.04,0));
            f.service.impulse(impulse);
            var from=f.mobPosition.clone();from.setYaw(180);var to=from.clone().add(0,0,1);
            assertTrue(MobMovement.face(f.entity,from,to),"The review's zero horizontal knockback concealed walking backwards");
        }
    }
    @Test void horizontalImpulseDoesNotSuppressAdvanceTowardAttackerOrBeyondTenTicks() {
        try(var f=new CombatServiceTest.Fixture()) {
            MobMovement.track(f.caster);when(f.entity.hasAI()).thenReturn(true);
            var impulse=new org.bukkit.event.entity.EntityKnockbackByEntityEvent(f.entity,f.player,
                org.bukkit.event.entity.EntityKnockbackEvent.KnockbackCause.ENTITY_ATTACK,1,
                new org.bukkit.util.Vector(0,.1,-.4),new org.bukkit.util.Vector(0,.1,-.4));
            f.service.impulse(impulse);
            var from=f.mobPosition.clone();var away=from.clone().add(0,0,-.2);
            assertFalse(MobMovement.face(f.entity,from,away),"A real recoil away from the attacker preserves its orientation");
            assertTrue(MobMovement.face(f.entity,from,from.clone().add(0,0,.2)),"Pursuit does not count as recoil");
            f.step(11);
            assertTrue(MobMovement.face(f.entity,from,away),"Recoil cannot hide backwards movement after half a second");
        }
    }
    @Test void nativeStunKeepsPhysicalFacingWithoutWritingTargetOrVelocity() {
        try(var f=new CombatServiceTest.Fixture()) {
            var ravager=mock(Ravager.class);
            when(ravager.getUniqueId()).thenReturn(UUID.randomUUID());when(ravager.hasAI()).thenReturn(true);
            when(ravager.getStunnedTicks()).thenReturn(10);
            var caster=new ActiveMob(ravager,f.caster.template(),f.host);MobMovement.track(caster);
            when(ravager.getBodyYaw()).thenReturn(180f);
            var from=f.mobPosition.clone();from.setYaw(180);var to=from.clone().add(0,0,1);
            assertTrue(MobMovement.face(ravager,from,to));
            verify(ravager).setBodyYaw(0);verify(ravager,never()).setTarget(any());verify(ravager,never()).setVelocity(any());
        }
    }
    @Test void walkingFacingPreservesPositionAndDoesNotOverrideChargePause() {
        try(var f=new CombatServiceTest.Fixture()) {
            MobMovement.track(f.caster);when(f.entity.hasAI()).thenReturn(true);clearInvocations(f.entity);
            when(f.entity.getBodyYaw()).thenReturn(180f);
            var from=f.mobPosition.clone();from.setYaw(180);var to=from.clone().add(0,0,1);
            var move=new io.papermc.paper.event.entity.EntityMoveEvent(f.entity,from,to);f.service.facing(move);
            assertEquals(0,move.getTo().getYaw());assertEquals(to.getZ(),move.getTo().getZ());
            verify(f.entity).setBodyYaw(0);verify(f.entity,never()).setVelocity(any());verify(f.entity,never()).setTarget(any());
            f.cast("charge");clearInvocations(f.entity);f.service.facing(move);
            verify(f.entity,never()).setBodyYaw(anyFloat());verify(f.entity,never()).setRotation(anyFloat(),anyFloat());
        }
    }

    @Test void periodicChargeMustNotPermanentlyStarveTheFollowingAbility() {
        try(var f=new CombatServiceTest.Fixture()) {
            var abilities=List.of(
                    new AbilityInstance("charge",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,1,20,Map.of()),
                    new AbilityInstance("purge",Trigger.EVERY_X_SECONDS,5,TargetMode.NEAREST,16,100,1,20,Map.of()));
            var template=new MobTemplate("regression","HUSK","",100,1,0,0,1,Map.of(),List.of(),abilities,
                    List.of(),false,"PURPLE",null,List.of(),false);
            var caster=new ActiveMob(f.entity,template,f.host);
            var engine=new AbilityEngine(f.registry,f.platform,new Random(1));
            // Keep the player near the charge's start each cycle, as in a pursuing fight.
            for(int tick=0;tick<=310;tick++) {
                f.effects.put(f.player,new PotionEffect(PotionEffectType.STRENGTH,600,0));
                f.mobPosition=new Location(f.world,0,64,0);
                engine.tick(List.of(caster),f.tick.get());f.step(1);
            }
            verify(f.entity,atLeast(2)).setAI(false);
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
                when(copy.getWorld()).thenReturn(f.world);when(copy.getLocation()).thenReturn(new Location(f.world,3,64,0));when(copy.getBoundingBox()).thenReturn(new BoundingBox(2.7,64,-.3,3.3,65.8,.3));when(copy.spawnAt(any(),any())).thenReturn(true);f.created.add(copy);return copy;
            }).when(f.entity).copy();
            f.cast("decoys");ai.set(false);f.step(20);
            assertEquals(2,f.created.size());
            assertTrue(f.created.stream().allMatch(entity->((Mob)entity).hasAI()),
                    "Owner was copied while setAI(false); decoys never restore their copied AI");
        }
    }
}
