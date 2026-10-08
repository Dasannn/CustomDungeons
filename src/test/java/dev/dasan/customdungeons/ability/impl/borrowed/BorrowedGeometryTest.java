package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.runtime.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BorrowedGeometryTest {
    @Test void lineKeepsOnlyPositionsInsideInclusiveRoomBoundaries() {
        var room = new dev.dasan.customdungeons.mob.MobArea("dungeon", new org.bukkit.util.BoundingBox(-3, 60, -1, 1, 71, 2));
        assertEquals(List.of(new Vector(-1.5, 64, 0), new Vector(-3, 64, 0)),
                EvokerFangsAbility.positions("LINE", 4, new Vector(0, 64, 0),
                        new Vector(-1, 9, 0), "dungeon", room));
        assertTrue(EvokerFangsAbility.positions("LINE", 4, new Vector(0, 64, 0),
                new Vector(-1, 0, 0), "other", room).isEmpty());
        assertTrue(EvokerFangsAbility.positions("LINE", 4, new Vector(0, 71, 0),
                new Vector(-1, 0, 0), "dungeon", room).isEmpty());
    }
    @Test void circleFiltersRegionAndKeepsCardinalPositions() {
        var room = new dev.dasan.customdungeons.mob.MobArea("dungeon", new org.bukkit.util.BoundingBox(-1, 64, -3, 4, 65, 4));
        var points = EvokerFangsAbility.positions("CIRCLE", 4, new Vector(0, 64, 0),
                new Vector(), "dungeon", room);
        assertEquals(3, points.size());
        var expected = List.of(new Vector(3, 64, 0), new Vector(0, 64, 3), new Vector(0, 64, -3));
        for (int i = 0; i < expected.size(); i++)
            assertEquals(0, expected.get(i).distance(points.get(i)), 1e-9);
        assertEquals(4, EvokerFangsAbility.positions("CIRCLE", 4, new Vector(0, 64, 0),
                new Vector(), "dungeon", null).size());
    }
    @Test void negativeFractionalCoordinatesUseBlockFloorAndHeightIsFiltered() {
        var room = new dev.dasan.customdungeons.mob.MobArea("dungeon", new org.bukkit.util.BoundingBox(0, 64, 0, 4, 65, 4));
        assertTrue(EvokerFangsAbility.positions("LINE", 1, new Vector(1.4, 64, 0),
                new Vector(-1, 0, 0), "dungeon", room).isEmpty());
        assertTrue(EvokerFangsAbility.positions("LINE", 1, new Vector(0, 65, 0),
                new Vector(1, 0, 0), "dungeon", room).isEmpty());
    }
    @Test void pulsePlanHonorsImmunityAndExpiresWithoutAnExtraHit() {
        assertPulsePlan(31, 1, List.of(0, 10, 20, 30));
        assertPulsePlan(35, 17, List.of(0, 17, 34));
        assertPulsePlan(40, 20, List.of(0, 20));
        assertPulsePlan(1, 20, List.of(0));
        assertPulsePlan(0, 20, List.of());
    }
    private void assertPulsePlan(int duration, int delay, List<Integer> expected) {
        var plan = new DragonBreathAbility.PulsePlan(duration, delay);
        var hits = new ArrayList<Integer>();
        for (int tick = 0; tick <= duration + 20; tick++)
            if (plan.hitsAt(tick)) hits.add(tick);
        assertEquals(expected, hits);
        int elapsed = 0;
        while (elapsed < duration) {
            int next = plan.nextDelay(elapsed);
            assertTrue(next > 0);
            elapsed += next;
        }
        assertEquals(duration, elapsed);
        assertEquals(0, plan.nextDelay(elapsed));
        assertFalse(plan.hitsAt(-1));
    }
    @Test void radiusSelectionUsesSphereIncludesBoundaryAndRejectsInvalidRadius() {
        var origin = new Location(null, 0, 64, 0);
        var center = origin.clone();
        var boundary = new Location(null, 3, 68, 0);
        var above = new Location(null, 0, 69.01, 0);
        var corner = new Location(null, 4, 64, 4);
        var candidates = List.of(center, boundary, above, corner);
        assertEquals(List.of(center, boundary), BorrowedAbilitiesA.withinRadius(candidates, p -> p, origin, 5));
        assertEquals(List.of(center), BorrowedAbilitiesA.withinRadius(candidates, p -> p, origin, 0));
        for (double radius : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertTrue(BorrowedAbilitiesA.withinRadius(candidates, p -> p, origin, radius).isEmpty());
    }
    @Test void radiusSelectionRejectsOtherWorldEvenAtIdenticalCoordinates() {
        var world = org.mockito.Mockito.mock(org.bukkit.World.class);
        var origin = new Location(world, 0, 64, 0);
        var elsewhere = new Location(org.mockito.Mockito.mock(org.bukkit.World.class), 0, 64, 0);
        assertEquals(List.of(origin), BorrowedAbilitiesA.withinRadius(List.of(origin, elsewhere), p -> p, origin, 5));
    }

    @Test void roarAndDarknessRadiusSelectionRetainsOnlyEligibleDistinctContextParticipants() {
        var world = mock(World.class);
        var mob = mock(Mob.class);
        var caster = mock(ActiveMob.class);
        var session = mock(MobHost.class);
        when(caster.entity()).thenReturn(mob);
        when(caster.session()).thenReturn(session);
        when(mob.getWorld()).thenReturn(world);
        when(mob.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        var center = participant(world, 0, 64, 0);
        var boundary = participant(world, 3, 68, 0);
        var outside = participant(world, 0, 69.01, 0);
        var otherWorld = participant(mock(World.class), 0, 64, 0);
        var spectator = participant(world, 0, 64, 0);
        var creative = participant(world, 0, 64, 0);
        var dead = participant(world, 0, 64, 0);
        var offline = participant(world, 0, 64, 0);
        var invalid = participant(world, 0, 64, 0);
        var omitted = participant(world, 0, 64, 0);
        var outsider = participant(world, 0, 64, 0);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(creative.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(dead.isDead()).thenReturn(true);
        when(offline.isOnline()).thenReturn(false);
        when(invalid.isValid()).thenReturn(false);
        when(session.players()).thenReturn(List.of(center, boundary, outside, otherWorld,
                spectator, creative, dead, offline, invalid, omitted));
        var targets = List.<LivingEntity>of(center, boundary, outside, otherWorld, spectator,
                creative, dead, offline, invalid, outsider, boundary);
        for (Ability ability : List.of(new DragonRoarAbility(), new DarknessPulseAbility())) {
            var params = new ParamValues(Map.of("radius", 5.0), ability.params());
            var ctx = new AbilityContext(caster, targets, params, session, null);
            assertEquals(List.of(center, boundary), BorrowedAbilitiesA.targets(ctx, params.getDouble("radius")));
        }
    }
    private Player participant(World world, double x, double y, double z) {
        var player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
        when(player.isValid()).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        return player;
    }

}
