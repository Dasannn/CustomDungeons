package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.ability.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomAbilitiesBTest {
    @Test void emptyHotbarHasNoStealableSlot() {
        assertEquals(-1, ThiefAbility.pickStealSlot(new ItemStack[9], new Random(1)));
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        assertEquals(-1, ThiefAbility.pickStealSlot(new ItemStack[]{air}, new Random(1)));
    }
    @Test void choosesOnlyNonEmptyHotbarSlotsAndCanChooseEach() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.STONE);
        when(item.getAmount()).thenReturn(1);
        ItemStack[] slots = new ItemStack[12];
        slots[2] = item; slots[7] = item; slots[10] = item;
        Set<Integer> selected = new HashSet<>();
        Random random = new Random(42);
        for (int i = 0; i < 1000; i++) selected.add(ThiefAbility.pickStealSlot(slots, random));
        assertEquals(Set.of(2, 7), selected);
    }
    @Test void registerAddsTenUniqueAbilitiesWithValidSpecs() {
        var registry = new AbilityRegistry();
        CustomAbilitiesB.register(registry);
        assertEquals(10, registry.all().size());
        assertEquals(Set.of("thief", "vampirism", "healer", "enrage", "minion_shield", "reflect",
                "meteors", "earthquake", "last_breath", "double"),
                new HashSet<>(registry.all().stream().map(Ability::id).toList()));
        for (var ability : registry.all()) {
            assertNotNull(ability.icon());
            Set<String> keys = new HashSet<>();
            for (var spec : ability.params()) {
                assertTrue(keys.add(spec.key()));
                assertNotNull(spec.type()); assertNotNull(spec.defaultValue());
                if (spec.defaultValue() instanceof Number number) {
                    assertTrue(number.doubleValue() >= spec.min());
                    assertTrue(number.doubleValue() <= spec.max());
                }
            }
        }
    }
    @Test void healingIsBoundedByMissingHealth() {
        assertEquals(20, CustomAbilitiesB.healedHealth(18, 20, 5));
        assertEquals(18, CustomAbilitiesB.healedHealth(18, 20, -2));
        assertEquals(18, CustomAbilitiesB.healedHealth(18, 20, Double.NaN));
    }
    @Test void minionShieldCancelsDamageAndEndsWhenLastMinionDies() {
        Fixture f = new Fixture();
        var ability = new MinionShieldAbility();
        var childEntity = mock(Mob.class);
        when(childEntity.isValid()).thenReturn(true);
        when(childEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        var child = new ActiveMob(childEntity, f.template, f.session);
        f.mobs.add(child);
        when(f.session.spawnMinion(anyString(), any(Location.class), eq(f.mob))).thenReturn(child);
        var damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(f.entity);
        var ctx = f.context(ability, Map.of("template", "minion", "count", 1), damage);
        ability.execute(ctx);
        verify(damage).setCancelled(true);
        verify(f.entity, atLeastOnce()).setInvulnerable(true);
        when(childEntity.isDead()).thenReturn(true);
        f.step();
        verify(f.entity).setInvulnerable(false);
        clearInvocations(damage);
        ability.execute(ctx);
        verify(damage, never()).setCancelled(true);
        verify(f.session, times(2)).spawnMinion(anyString(), any(Location.class), eq(f.mob));
    }
    @Test void failedMinionSpawnNeverShieldsCaster() {
        Fixture f = new Fixture();
        var ability = new MinionShieldAbility();
        var damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(f.entity);
        ability.execute(f.context(ability, Map.of("template", "missing"), damage));
        verify(damage, never()).setCancelled(true);
        verify(f.entity, never()).setInvulnerable(true);
    }
    @Test void doubleCannotReenterDuringSynchronousSpawn() {
        Fixture f = new Fixture();
        var ability = new DoubleAbility();
        var ctx = f.context(ability, Map.of(), null);
        when(f.session.spawnMinion(anyString(), any(Location.class), eq(f.mob))).thenAnswer(call -> {
            ability.execute(ctx);
            return null;
        });
        ability.execute(ctx);
        verify(f.session, times(1)).spawnMinion(eq("test"), any(Location.class), eq(f.mob));
    }
    @Test void reflectedProjectileCannotTargetOutsider() {
        Fixture f = new Fixture();
        var ability = new ReflectAbility();
        var projectile = mock(Arrow.class);
        var outsider = mock(Player.class);
        when(projectile.getShooter()).thenReturn(outsider);
        var damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getEntity()).thenReturn(f.entity);
        when(damage.getDamager()).thenReturn(projectile);
        ability.execute(f.context(ability, Map.of(), damage));
        verify(damage).setCancelled(true);
        verify(f.entity, never()).launchProjectile(any(), any());
    }
    @Test void thiefIgnoresUnrelatedOrCancelledHits() {
        Fixture f = new Fixture();
        var ability = new ThiefAbility();
        ability.execute(f.context(ability, Map.of(), null));
        var damage = mock(EntityDamageByEntityEvent.class);
        when(damage.isCancelled()).thenReturn(true);
        ability.execute(f.context(ability, Map.of(), damage));
        verify(f.session, never()).onItemStolen(any(), any(), any());
    }
    @Test void removalClearsShieldStateEvenIfSchedulerStops() {
        Fixture f = new Fixture();
        var ability = new MinionShieldAbility();
        var ctx = f.context(ability, Map.of("template", "minion", "count", 1), null);
        Mob childEntity = mock(Mob.class);
        when(childEntity.isValid()).thenReturn(true);
        when(childEntity.getUniqueId()).thenReturn(UUID.randomUUID());
        ActiveMob child = new ActiveMob(childEntity, f.template, f.session);
        f.mobs.add(child);
        when(f.session.spawnMinion(anyString(), any(Location.class), eq(f.mob))).thenReturn(child);
        ability.execute(ctx);
        var removed = mock(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class);
        when(removed.getEntity()).thenReturn(f.entity);
        ability.removed(removed);
        ability.execute(ctx);
        verify(f.session, times(2)).spawnMinion(eq("minion"), any(Location.class), eq(f.mob));
    }
    @Test void onHitUsesActualVictimIncludingCasterProjectiles() {
        Fixture f = new Fixture();
        Player victim = mock(Player.class);
        when(victim.isOnline()).thenReturn(true);
        when(victim.isValid()).thenReturn(true);
        when(victim.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(victim.getWorld()).thenReturn(f.world);
        when(f.session.players()).thenReturn(List.of(victim));
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(f.entity);
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(victim);
        when(event.getDamager()).thenReturn(arrow);
        var ctx = f.context(new ThiefAbility(), Map.of(), event);
        assertSame(victim, CustomAbilitiesB.hitPlayer(ctx));
        when(arrow.getShooter()).thenReturn(mock(Player.class));
        assertNull(CustomAbilitiesB.hitPlayer(ctx));
    }
    @Test void reflectedTridentPreservesRecoverablePlayerWeapon() {
        Fixture f = new Fixture();
        var ability = new ReflectAbility();
        Player shooter = mock(Player.class);
        when(shooter.isOnline()).thenReturn(true);
        when(shooter.isValid()).thenReturn(true);
        when(shooter.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(shooter.getWorld()).thenReturn(f.world);
        when(shooter.getEyeLocation()).thenReturn(new Location(f.world, 3, 65, 0));
        when(f.entity.getEyeLocation()).thenReturn(new Location(f.world, 0, 65, 0));
        when(f.session.players()).thenReturn(List.of(shooter));
        when(f.session.id()).thenReturn(UUID.randomUUID());
        Trident incoming = mock(Trident.class), reflected = mock(Trident.class);
        ItemStack weapon = mock(ItemStack.class);
        when(weapon.clone()).thenReturn(weapon);
        when(incoming.getItemStack()).thenReturn(weapon);
        when(incoming.getItem()).thenReturn(weapon);
        when(incoming.getType()).thenReturn(org.bukkit.entity.EntityType.TRIDENT);
        when(incoming.getShooter()).thenReturn(shooter);
        when(incoming.getPickupStatus()).thenReturn(AbstractArrow.PickupStatus.ALLOWED);
        when(reflected.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(f.entity.launchProjectile(eq(Trident.class), any())).thenReturn(reflected);
        var event = mock(EntityDamageByEntityEvent.class);
        when(event.getEntity()).thenReturn(f.entity);
        when(event.getDamager()).thenReturn(incoming);
        ability.execute(f.context(ability, Map.of(), event));
        verify(reflected).setItemStack(weapon);
        verify(reflected).setPickupStatus(AbstractArrow.PickupStatus.ALLOWED);
        verify(reflected).setLoyaltyLevel(0);
        verify(incoming).remove();
        verify(event).setCancelled(true);
    }
    @Test void meteorCancelsVanillaDamageAndIsRemovedWithCaster() {
        Fixture f = new Fixture();
        var ability = new MeteorsAbility();
        Player victim = mock(Player.class);
        when(victim.isOnline()).thenReturn(true);
        when(victim.isValid()).thenReturn(true);
        when(victim.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(victim.getWorld()).thenReturn(f.world);
        when(victim.getLocation()).thenAnswer(call -> new Location(f.world, 3, 64, 0));
        when(f.session.players()).thenReturn(List.of(victim));
        when(f.session.id()).thenReturn(UUID.randomUUID());
        Fireball ball = mock(Fireball.class);
        when(ball.isValid()).thenReturn(true);
        when(ball.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(f.entity.launchProjectile(eq(Fireball.class), any())).thenReturn(ball);
        ability.execute(new AbilityContext(f.mob, List.of(victim),
                new ParamValues(Map.of("count", 1, "telegraphTicks", 1), ability.params()), f.session, null));
        f.step();
        var damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(ball);
        ability.impactDamage(damage);
        verify(damage).setCancelled(true);
        var hit = mock(org.bukkit.event.entity.ProjectileHitEvent.class);
        when(hit.getEntity()).thenReturn(ball);
        ability.hit(hit);
        verify(hit).setCancelled(true);
        var removed = mock(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class);
        when(removed.getEntity()).thenReturn(f.entity);
        ability.removed(removed);
        verify(ball).remove();
    }
    private static final class Fixture {
        final World world = mock(World.class);
        final Mob entity = mock(Mob.class);
        final SessionContext session = mock(SessionContext.class);
        final List<ActiveMob> mobs = new ArrayList<>();
        final Queue<Runnable> tasks = new ArrayDeque<>();
        final MobTemplate template = new MobTemplate("test", "minecraft:zombie", "", 100, 0, 0, 0, 1,
                Map.of(), List.of(), List.of(), List.of(), false, "RED", null, List.of(), false);
        final ActiveMob mob = new ActiveMob(entity, template, session);
        Fixture() {
            when(entity.isValid()).thenReturn(true);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
            when(session.mobs()).thenReturn(mobs);
            when(session.players()).thenReturn(List.of());
            when(session.scheduler()).thenReturn(new TickScheduler() {
                public long currentTick() { return 0; }
                public void runLater(int ticks, Runnable task) { tasks.add(task); }
            });
            mobs.add(mob);
        }
        AbilityContext context(Ability ability, Map<String, Object> params, Event cause) {
            return new AbilityContext(mob, List.of(), new ParamValues(params, ability.params()), session, cause);
        }
        void step() { tasks.remove().run(); }
    }

}
