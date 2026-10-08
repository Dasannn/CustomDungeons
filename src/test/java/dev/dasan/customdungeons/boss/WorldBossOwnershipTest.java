package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.SummonVexesAbility;
import dev.dasan.customdungeons.listener.AbilityProtectionListener;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.mob.MobProjectileListener;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.*;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldBossOwnershipTest {
    @TempDir Path temp;
    WorldEncounterTest fixture;
    @BeforeEach void setup() {fixture=new WorldEncounterTest();fixture.temp=temp;fixture.setup();}
    @AfterEach void close() {fixture.close();}
    private <T extends Entity> T entity(Class<T> type,EntityType entityType) {
        T entity=mock(type);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.getType()).thenReturn(entityType);
        when(entity.isValid()).thenReturn(true);when(entity.getWorld()).thenReturn(fixture.world);when(entity.getLocation()).thenReturn(new Location(fixture.world,1,64,0));
        var data=mock(PersistentDataContainer.class);var values=new HashMap<NamespacedKey,Object>();when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(),any())).thenAnswer(c->values.get(c.getArgument(0)));
        when(data.has(any(),any())).thenAnswer(c->values.containsKey(c.getArgument(0)));
        when(data.has(any(NamespacedKey.class))).thenAnswer(c->values.containsKey(c.getArgument(0)));
        doAnswer(c->{values.put(c.getArgument(0),c.getArgument(2));return null;}).when(data).set(any(),any(),any());
        if(entity instanceof Explosive explosive) {
            var yield=new java.util.concurrent.atomic.AtomicReference<>(2.5f);
            when(explosive.getYield()).thenAnswer(c->yield.get());
            doAnswer(c->{yield.set(c.getArgument(0));return null;}).when(explosive).setYield(anyFloat());
        }
        return entity;
    }
    private void observedSpawn(Entity entity) {
        assertEquals(fixture.encounter.id.toString(),entity.getPersistentDataContainer().get(WorldBossService.MARKER,PersistentDataType.STRING),"Ownership must exist before the spawn event");
        var event=mock(EntitySpawnEvent.class);when(event.getEntity()).thenReturn(entity);fixture.service.spawned(event);
        assertSame(fixture.encounter,fixture.service.owner(entity));assertTrue(fixture.encounter.entities.contains(entity));
    }
    @Test void minionFactoryMarksEncounterDuringInitializer() {
        var minion=entity(Zombie.class,EntityType.ZOMBIE);
        doAnswer(c->{((Consumer<Zombie>)c.getArgument(4)).accept(minion);observedSpawn(minion);return minion;})
                .when(fixture.world).spawn(any(Location.class),eq(Zombie.class),eq(CreatureSpawnEvent.SpawnReason.CUSTOM),eq(false),any());
        var active=fixture.encounter.spawnMinion("boss",new Location(fixture.world,1,64,0),fixture.encounter.active.get(fixture.mob.getUniqueId()));
        assertSame(minion,active.entity());fixture.encounter.close();verify(minion).remove();
    }
    @Test void abilityAndVanillaProjectilesBelongToEncounterAndCannotBreakBlocks() {
        var shot=entity(Fireball.class,EntityType.FIREBALL);
        doAnswer(c->{((Consumer<Fireball>)c.getArgument(2)).accept(shot);observedSpawn(shot);return shot;})
                .when(fixture.mob).launchProjectile(eq(Fireball.class),any(Vector.class),any());
        assertSame(shot,Effects.launch(fixture.encounter.active.get(fixture.mob.getUniqueId()),Fireball.class,new Vector(1,0,0)));
        var nativeShot=entity(Fireball.class,EntityType.FIREBALL);when(nativeShot.getShooter()).thenReturn(fixture.mob);
        launch(nativeShot);assertSame(fixture.encounter,fixture.service.owner(nativeShot));
        var explosion=mock(EntityExplodeEvent.class);var blocks=new ArrayList<>(List.of(mock(org.bukkit.block.Block.class)));
        when(explosion.getEntity()).thenReturn(nativeShot);when(explosion.blockList()).thenReturn(blocks);new AbilityProtectionListener().explode(explosion);assertTrue(blocks.isEmpty());
        verify(nativeShot,atLeastOnce()).setIsIncendiary(false);assertEquals(2.5f,nativeShot.getYield());verify(nativeShot,never()).setYield(anyFloat());
        assertEquals(0f,shot.getYield(),"Ability projectiles keep their existing configured behavior");
        fixture.encounter.close();verify(shot).remove();verify(nativeShot).remove();
    }
    @Test void vanillaExplosionPreservesPowerAndDamageButProtectsForeignPlayersBlocksAndFire() {
        var protections=new AbilityProtectionListener();
        for(var type:List.of(Fireball.class,WitherSkull.class)) {
            var shot=entity(type,EntityType.UNKNOWN);when(shot.getShooter()).thenReturn(fixture.mob);launch(shot);
            assertEquals(2.5f,shot.getYield());verify(shot,never()).setYield(anyFloat());
            assertFalse(Effects.marked(shot),"Native combat must not be replaced by ability handlers");
            var prime=new ExplosionPrimeEvent(shot,2.5f,true);protections.prime(prime);
            var ability=new dev.dasan.customdungeons.ability.impl.borrowed.WitherSkullsAbility();ability.prime(prime);
            assertFalse(prime.getFire());assertEquals(2.5f,prime.getRadius());assertFalse(prime.isCancelled());
            var explosion=mock(EntityExplodeEvent.class);var blocks=new ArrayList<>(List.of(mock(org.bukkit.block.Block.class)));
            when(explosion.getEntity()).thenReturn(shot);when(explosion.blockList()).thenReturn(blocks);
            protections.explode(explosion);ability.explode(explosion);assertTrue(blocks.isEmpty());verify(explosion,never()).setCancelled(true);
            var ignite=mock(org.bukkit.event.block.BlockIgniteEvent.class);when(ignite.getIgnitingEntity()).thenReturn(shot);
            protections.ignite(ignite);verify(ignite).setCancelled(true);
            var allowed=mock(EntityDamageByEntityEvent.class);when(allowed.getEntity()).thenReturn(fixture.a);when(allowed.getDamager()).thenReturn(shot);
            when(fixture.a.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(allowed.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);when(allowed.getFinalDamage()).thenReturn(12d);
            protections.damage(allowed);fixture.service.damage(allowed);verify(allowed,never()).setCancelled(true);verify(allowed,never()).setDamage(anyDouble());assertEquals(12d,allowed.getFinalDamage());
            var outsider=entity(Player.class,EntityType.PLAYER);when(outsider.getGameMode()).thenReturn(GameMode.SURVIVAL);when(outsider.isOnline()).thenReturn(true);
            when(outsider.getLocation()).thenReturn(new Location(fixture.world,49,64,0));
            var rejected=mock(EntityDamageByEntityEvent.class);when(rejected.getEntity()).thenReturn(outsider);when(rejected.getDamager()).thenReturn(shot);
            when(rejected.getCause()).thenReturn(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);
            protections.damage(rejected);fixture.service.damage(rejected);verify(rejected).setCancelled(true);
        }
    }
    @Test void dungeonVanillaExplosionUsesSharedTerrainProtectionWithoutSuppressingImpactOrPower() {
        var shooter=entity(Skeleton.class,EntityType.SKELETON);
        shooter.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,UUID.randomUUID().toString());
        shooter.getPersistentDataContainer().set(MobKeys.TEMPLATE,PersistentDataType.STRING,"skeleton");
        var shot=entity(Fireball.class,EntityType.FIREBALL);when(shot.getShooter()).thenReturn(shooter);launch(shot);
        assertEquals(2.5f,shot.getYield());verify(shot,never()).setYield(anyFloat());
        var protections=new AbilityProtectionListener();var prime=new ExplosionPrimeEvent(shot,2.5f,true);protections.prime(prime);
        assertFalse(prime.getFire());assertEquals(2.5f,prime.getRadius());assertFalse(prime.isCancelled());
        var explosion=mock(EntityExplodeEvent.class);var blocks=new ArrayList<>(List.of(mock(org.bukkit.block.Block.class)));
        when(explosion.getEntity()).thenReturn(shot);when(explosion.blockList()).thenReturn(blocks);protections.explode(explosion);assertTrue(blocks.isEmpty());
        var ignite=mock(org.bukkit.event.block.BlockIgniteEvent.class);when(ignite.getIgnitingEntity()).thenReturn(shot);protections.ignite(ignite);verify(ignite).setCancelled(true);
        var impact=mock(ProjectileHitEvent.class);when(impact.getEntity()).thenReturn(shot);when(impact.getHitBlock()).thenReturn(mock(org.bukkit.block.Block.class));
        protections.hit(impact);verify(impact,never()).setCancelled(true);verify(shot,never()).remove();
        var foreign=entity(Fireball.class,EntityType.FIREBALL);
        var unownedPrime=new ExplosionPrimeEvent(foreign,2.5f,true);protections.prime(unownedPrime);
        assertTrue(unownedPrime.getFire());assertEquals(2.5f,unownedPrime.getRadius());
        var unownedExplosion=mock(EntityExplodeEvent.class);var unownedBlocks=new ArrayList<>(List.of(mock(org.bukkit.block.Block.class)));
        when(unownedExplosion.getEntity()).thenReturn(foreign);when(unownedExplosion.blockList()).thenReturn(unownedBlocks);
        protections.explode(unownedExplosion);assertEquals(1,unownedBlocks.size());
        var unownedIgnite=mock(org.bukkit.event.block.BlockIgniteEvent.class);when(unownedIgnite.getIgnitingEntity()).thenReturn(foreign);
        protections.ignite(unownedIgnite);verify(unownedIgnite,never()).setCancelled(true);
    }
    private MobProjectileListener projectiles() {return new MobProjectileListener(fixture.service::trackProjectile);}
    private void launch(Projectile projectile) {
        var event=mock(ProjectileLaunchEvent.class);when(event.getEntity()).thenReturn(projectile);projectiles().launch(event);
    }
    @Test void vanillaArrowCannotDamageForeignPlayerButCanDamageParticipant() {
        var arrow=entity(Arrow.class,EntityType.ARROW);when(arrow.getShooter()).thenReturn(fixture.mob);launch(arrow);
        var foreign=entity(Player.class,EntityType.PLAYER);when(foreign.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(foreign.getLocation()).thenReturn(new Location(fixture.world,49,64,0));when(foreign.isOnline()).thenReturn(true);
        var event=mock(EntityDamageByEntityEvent.class);when(event.getDamager()).thenReturn(arrow);when(event.getEntity()).thenReturn(foreign);
        fixture.service.damage(event);verify(event).setCancelled(true);
        when(fixture.a.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        var allowed=mock(EntityDamageByEntityEvent.class);when(allowed.getDamager()).thenReturn(arrow);when(allowed.getEntity()).thenReturn(fixture.a);
        fixture.service.damage(allowed);verify(allowed,never()).setCancelled(true);
    }
    @Test void vanillaMinionArrowIsRegisteredByBowEventBeforeShooterIsSetAndDespawnRemovesIt() {
        var minion=entity(Skeleton.class,EntityType.SKELETON);
        minion.getPersistentDataContainer().set(WorldBossService.MARKER,PersistentDataType.STRING,fixture.encounter.id.toString());observedSpawn(minion);
        var arrow=entity(Arrow.class,EntityType.ARROW);
        var bow=mock(EntityShootBowEvent.class);when(bow.getEntity()).thenReturn(minion);when(bow.getProjectile()).thenReturn(arrow);
        projectiles().shoot(bow);assertSame(fixture.encounter,fixture.service.owner(arrow));
        when(arrow.getShooter()).thenReturn(minion);launch(arrow);
        assertEquals(fixture.encounter.id.toString(),arrow.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING));
        verify(arrow,atLeastOnce()).setPersistent(false);assertEquals(1,fixture.encounter.entities.stream().filter(arrow::equals).count());
        fixture.service.despawn(mock(org.bukkit.command.CommandSender.class),"boss");verify(arrow,times(1)).remove();
        assertNull(fixture.service.owner(arrow));
    }
    @Test void everyVanillaProjectileTypeInheritsEncounterAtLaunch() {
        for(var type:List.of(Arrow.class,Fireball.class,SmallFireball.class,Trident.class,ThrownPotion.class,WitherSkull.class)) {
            var shot=entity(type,EntityType.UNKNOWN);when(shot.getShooter()).thenReturn(fixture.mob);launch(shot);
            assertSame(fixture.encounter,fixture.service.owner(shot),type.getSimpleName());
            assertTrue(fixture.encounter.entities.contains(shot));
        }
    }
    @Test void dungeonProjectileUsesSameLaunchPathAndUnmarkedShootersRemainUntouched() {
        var dungeonMob=entity(Skeleton.class,EntityType.SKELETON);String session=UUID.randomUUID().toString();
        dungeonMob.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,session);
        dungeonMob.getPersistentDataContainer().set(MobKeys.TEMPLATE,PersistentDataType.STRING,"skeleton");
        var own=entity(Arrow.class,EntityType.ARROW);when(own.getShooter()).thenReturn(dungeonMob);launch(own);
        assertEquals(session,own.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING));
        assertNull(fixture.service.owner(own));
        var outsider=entity(Skeleton.class,EntityType.SKELETON);
        var foreign=entity(Arrow.class,EntityType.ARROW);when(foreign.getShooter()).thenReturn(outsider);launch(foreign);
        var player=entity(Player.class,EntityType.PLAYER);player.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,session);
        var playerArrow=entity(Arrow.class,EntityType.ARROW);when(playerArrow.getShooter()).thenReturn(player);launch(playerArrow);
        for(var untouched:List.of(foreign,playerArrow))assertNull(untouched.getPersistentDataContainer().get(MobKeys.SESSION,PersistentDataType.STRING));
        fixture.encounter.close();for(var untouched:List.of(own,foreign,playerArrow))verify(untouched,never()).remove();
    }
    @Test void launchFromRetiredEncounterCannotLeaveNewRemnants() {
        fixture.encounter.close();var arrow=entity(Arrow.class,EntityType.ARROW);when(arrow.getShooter()).thenReturn(fixture.mob);
        launch(arrow);verify(arrow).remove();assertTrue(fixture.service.encounters.isEmpty());assertNull(fixture.service.owner(arrow));
        verify(fixture.platform,never()).deliverReward(any(),any());
    }
    @Test void directAbilitySummonMarksBeforeSpawnWithoutAdoptingForeignEntitiesInTheSameCallback() {
        var vex=entity(Vex.class,EntityType.VEX);var foreign=entity(Zombie.class,EntityType.ZOMBIE);
        foreign.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,fixture.encounter.id.toString());
        doAnswer(c->{((Consumer<Vex>)c.getArgument(2)).accept(vex);observedSpawn(vex);
            var unrelated=mock(CreatureSpawnEvent.class);when(unrelated.getEntity()).thenReturn(foreign);when(unrelated.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.SPELL);
            fixture.service.spawned(unrelated);assertNull(fixture.service.owner(foreign));return vex;})
                .when(fixture.world).spawn(any(Location.class),eq(Vex.class),any(Consumer.class));
        var ability=new SummonVexesAbility();var caster=fixture.encounter.active.get(fixture.mob.getUniqueId());
        ability.execute(new AbilityContext(caster,List.of(fixture.a),new ParamValues(Map.of("count",1),ability.params()),fixture.encounter,null));
        fixture.encounter.close();verify(vex).remove();verify(foreign,never()).remove();
    }
}
