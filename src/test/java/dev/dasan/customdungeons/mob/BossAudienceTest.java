package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class BossAudienceTest {
    static { PaperApiTestBootstrap.initialize(); }
    @Test void livePhaseAndMusicReachNearbyPlayersAndReconcileMovementAndCleanup() {
        var f = new Fixture(true);
        f.controller.startMusic(f.boss);
        verify(f.near).playSound(new Location(f.world, 48, 64, 0), "minecraft:music_disc.13", SoundCategory.RECORDS, 1, 1);
        verify(f.far, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
        f.controller.onDamaged(f.boss, 1);
        verify(f.near).playSound(f.at, "minecraft:block.anvil.land", SoundCategory.HOSTILE, 1, 1);
        verify(f.near).spawnParticle(Particle.TOTEM_OF_UNDYING, f.at, 30, .5, 1, .5, .1);
        verify(f.near).showTitle(any(net.kyori.adventure.title.Title.class));
        verify(f.near).stopSound("minecraft:music_disc.13", SoundCategory.RECORDS);
        verify(f.near).playSound(new Location(f.world, 48, 64, 0), "minecraft:music_disc.cat", SoundCategory.RECORDS, 1, 1);
        when(f.near.getLocation()).thenReturn(new Location(f.world, 49, 64, 0));
        when(f.far.getLocation()).thenReturn(new Location(f.world, 1, 64, 0));
        f.controller.tickMusic(2);
        verify(f.near).stopSound("minecraft:music_disc.cat", SoundCategory.RECORDS);
        verify(f.far).playSound(new Location(f.world, 1, 64, 0), "minecraft:music_disc.cat", SoundCategory.RECORDS, 1, 1);
        f.controller.cleanup(f.boss);
        verify(f.far).stopSound("minecraft:music_disc.cat", SoundCategory.RECORDS);
    }
    @Test void dungeonPhaseAndMusicReachAllParticipantsButNeverWorldOutsiders() {
        var f = new Fixture(false);
        when(f.session.players()).thenReturn(List.of(f.admin, f.far));
        f.controller.startMusic(f.boss);
        f.controller.onDamaged(f.boss, 1);
        verify(f.far).playSound(f.at, "minecraft:block.anvil.land", SoundCategory.HOSTILE, 1, 1);
        verify(f.far).playSound(new Location(f.world, 100, 64, 0), "minecraft:music_disc.cat", SoundCategory.RECORDS, 1, 1);
        verify(f.far, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(f.near, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
        f.controller.cleanup(f.boss);
        verify(f.admin).stopSound("minecraft:music_disc.cat", SoundCategory.RECORDS);
        verify(f.far).stopSound("minecraft:music_disc.cat", SoundCategory.RECORDS);
    }
    private static class Fixture {
        final World world = mock(World.class);
        final Location at = new Location(world, 0, 64, 0);
        final Player admin = player(2), near = player(48), far = player(100);
        final MobHost session = mock(MobHost.class);
        final ActiveMob boss;
        final BossController controller;
        Fixture(boolean live) {
            when(session.audience(any(Location.class))).thenAnswer(call -> live
                    ? dev.dasan.customdungeons.ability.EffectAudience.nearby(world.getPlayers(), call.getArgument(0), 48) : session.players());
            when(session.players()).thenReturn(List.of(admin));
            when(session.scheduler()).thenReturn(new LiveTestService.Clock());
            when(world.getPlayers()).thenReturn(List.of(admin, near, far));
            var entity = mock(Mob.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getLocation()).thenReturn(at);
            when(entity.isValid()).thenReturn(true);
            when(entity.getHealth()).thenReturn(40d);
            when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            var health = mock(AttributeInstance.class);
            when(health.getValue()).thenReturn(100d);
            when(entity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
            var phase = new PhaseDef(.5, false, List.of(), List.of(), Map.of(), List.of(), 0, List.of(),
                    "Phase", null, "minecraft:block.anvil.land", "minecraft:music_disc.cat", 10);
            var template = new MobTemplate("boss", "HUSK", "Boss", 100, 1, 0, 0, 0, Map.of(),
                    List.of(), List.of(), List.of(), true, "PURPLE", "minecraft:music_disc.13", List.of(phase), false);
            boss = new ActiveMob(entity, template, session);
            var config = new PluginConfig("", "es", null, "world", false, null,
                    new PluginConfig.PerformanceLimits(50, 1, 48), Set.of(), List.of(), null, null, 300, Map.of());
            controller = new BossController(new MobFactory(dev.dasan.customdungeons.mob.TestMobsPlatform.of(config)), Map.of());
        }
        Player player(double x) {
            var player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world, x, 64, 0));
            return player;
        }
    }
}
