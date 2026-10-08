package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.ability.impl.SummonMinionsAbility;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.List;
import java.util.Map;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BorrowedAreaTest {
    static { PaperApiTestBootstrap.initialize(); }
    final World world = mock(World.class);
    final Mob entity = mock(Mob.class);
    final ActiveMob caster = mock(ActiveMob.class);
    final MobHost host = mock(MobHost.class);
    final Player player = mock(Player.class);
    final Location origin = new Location(world, 0, 64, 0);

    BorrowedAreaTest() {
        when(world.getName()).thenReturn("other");
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(origin);
        when(caster.entity()).thenReturn(entity);
        when(caster.session()).thenReturn(host);
        when(host.area()).thenReturn(new MobArea("dungeon", new BoundingBox(-10, 60, -10, 11, 71, 11)));
        when(host.players()).thenReturn(List.of(player));
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(origin.clone().add(1, 0, 0));
        when(player.isOnline()).thenReturn(true);
        when(player.isValid()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    }
    AbilityContext context(Ability ability) {
        return new AbilityContext(caster, List.of(player), new ParamValues(Map.of("template", "minion", "radius", 0.0),
                ability.params()), host, null);
    }
    @Test void helperAUsesRoomWorldRatherThanCasterWorld() {
        assertFalse(BorrowedAbilitiesA.inRoom(caster, origin));
        when(world.getName()).thenReturn("dungeon");
        assertTrue(BorrowedAbilitiesA.inRoom(caster, origin));
        assertFalse(BorrowedAbilitiesA.inRoom(caster, origin.clone().add(0, 7, 0)));
    }
    @Test void helperBRejectsDamageAndPushFromAnotherRoomWorld() {
        var ctx = context(new WitherShockwaveAbility());
        BorrowedAbilitiesB.radial(ctx, origin, 5, 6, 1);
        verify(player, never()).damage(anyDouble(), any(Entity.class));
        verify(player, never()).setVelocity(any());
        when(world.getName()).thenReturn("dungeon");
        BorrowedAbilitiesB.radial(ctx, origin, 5, 6, 1);
        verify(player).damage(6, entity);
        verify(player).setVelocity(any());
    }
    @Test void helperCRejectsDestinationsInAnotherRoomWorldBeforeReadingTerrain() {
        var ctx = context(new EndermanBlinkAbility());
        assertFalse(BorrowedAbilitiesC.inRoom(ctx, origin));
        assertFalse(BorrowedAbilitiesC.safe(ctx, origin));
        verify(world, never()).getBlockAt(any(Location.class));
        when(world.getName()).thenReturn("dungeon");
        assertTrue(BorrowedAbilitiesC.inRoom(ctx, origin));
    }
    @Test void summonMinionsRejectsAnotherWorldAndAllowsOriginalRoom() {
        var ability = new SummonMinionsAbility();
        ability.execute(context(ability));
        verify(host, never()).spawnMinion(anyString(), any(Location.class), any());
        when(world.getName()).thenReturn("dungeon");
        ability.execute(context(ability));
        verify(host, times(2)).spawnMinion("minion", origin, caster);
    }
    @Test void summonVexesRejectsAnotherWorldBeforeSpawningOrReadingTerrain() {
        var ability = new SummonVexesAbility();
        ability.execute(context(ability));
        verify(host, never()).spawnMinion(anyString(), any(Location.class), any());
        verify(world, never()).spawn(any(Location.class), eq(Vex.class), any(java.util.function.Consumer.class));
    }
    @Test void noAreaPreservesLiveTestDestinations() {
        when(host.area()).thenReturn(null);
        assertTrue(BorrowedAbilitiesA.inRoom(caster, origin));
        assertTrue(BorrowedAbilitiesC.inRoom(context(new EndermanBlinkAbility()), origin));
    }
}
