package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.TickScheduler;
import dev.dasan.customdungeons.text.Messages;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionAmbienceTest {
    @BeforeAll static void registry(){PaperApiTestBootstrap.initialize();}
    final World world=mock(World.class);final Player player=mock(Player.class);
    final DungeonSession session=mock(DungeonSession.class);final TickScheduler clock=mock(TickScheduler.class);
    final SessionStateMachine state=new SessionStateMachine();
    final Region region=Region.of("world",new BlockPos(0,60,0),new BlockPos(15,75,15));
    final RoomDef room=new RoomDef("Cubil",region,new Point("world",2,64,2,0,0),region,UnlockMode.AUTOMATIC,null,List.of())
            .withAmbience(new RoomAmbience(Map.of("effects",List.of(new PotionDef("minecraft:darkness",0,false)),"music","custom:room","particle","ASH","density",1000)));
    SessionAmbience ambience;
    @BeforeEach void setup() {
        state.openLobby();state.start();when(session.state()).thenReturn(state);
        when(session.players()).thenReturn(List.of(player));when(session.scheduler()).thenReturn(clock);when(clock.currentTick()).thenReturn(20L);
        var room=this.room.withAmbience(this.room.ambience().with("density",32));
        var def=mock(DungeonDef.class);when(def.rooms()).thenReturn(List.of(room));when(session.def()).thenReturn(def);
        when(session.roomStarted()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(world.getName()).thenReturn("world");when(player.getWorld()).thenReturn(world);when(player.getLocation()).thenReturn(new Location(world,2,64,2));
        when(player.addPotionEffect(any())).thenReturn(true);
        var config=mock(PluginConfig.class);when(config.limits()).thenReturn(new PluginConfig.PerformanceLimits(100,1000,100));when(config.musicLengthTicks()).thenReturn(Map.of());
        var messages=new Messages();messages.load(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile()),"");
        ambience=new SessionAmbience(messages,AmbienceSettings.load(new YamlConfiguration(),p->{}),config,Map.of());
    }
    @Test void entryUsesActivationMusicStopsOnMoveAndCleanup() {
        when(session.roomStarted()).thenReturn(false);ambience.tick(session,false);
        verify(player,never()).showTitle(any(net.kyori.adventure.title.Title.class));verify(player,never()).addPotionEffect(any());
        when(session.roomStarted()).thenReturn(true);ambience.tick(session,false);
        verify(player).showTitle(any(net.kyori.adventure.title.Title.class));verify(player).playSound(any(Location.class),eq("custom:room"),eq(SoundCategory.RECORDS),eq(1f),eq(1f));
        ambience.tick(session,true);verify(player).stopSound("custom:room",SoundCategory.RECORDS);
        ambience.tick(session,false);
        ambience.moved(session,player,new Location(world,25,64,0));
        verify(player,times(2)).stopSound("custom:room",SoundCategory.RECORDS);
        ambience.clear();
    }
    @Test void bossMusicStopsRoomFirstIncludingWhenItUsesTheSameSoundKey() {
        ambience.tick(session,false);clearInvocations(player);
        var template=new MobTemplate("boss","ZOMBIE","",100,1,.2,0,1,Map.of(),List.of(),List.of(),List.of(),true,"RED","custom:room",List.of(),false);
        var entity=mock(org.bukkit.entity.Mob.class);when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.isValid()).thenReturn(true);
        var boss=new dev.dasan.customdungeons.runtime.ActiveMob(entity,template,session);
        var config=mock(PluginConfig.class);when(config.musicLengthTicks()).thenReturn(Map.of());
        var controller=new dev.dasan.customdungeons.mob.BossController(new dev.dasan.customdungeons.mob.MobFactory(config),Map.of(),ambience::pauseMusic);
        controller.startMusic(boss);
        var order=inOrder(player);
        order.verify(player).stopSound("custom:room",SoundCategory.RECORDS);
        order.verify(player).playSound(any(Location.class),eq("custom:room"),eq(SoundCategory.RECORDS),eq(1f),eq(1f));
        assertTrue(controller.musicPlaying());
        ambience.tick(session,true);
        verify(player,times(1)).stopSound("custom:room",SoundCategory.RECORDS);
        controller.cleanup(boss);assertFalse(controller.musicPlaying());
    }
    @Test void particlesHaveHardCapEvenWithExcessiveGlobalDensity() {
        ambience.tick(session,false);
        verify(player,times(32)).spawnParticle(eq(Particle.ASH),any(Location.class),eq(1),eq(.15),eq(.15),eq(.15),eq(.01));
    }
    @Test void clearStopsMusicOnceAndNeverRestartsIt() {
        ambience.tick(session,false);ambience.cleared(session);ambience.cleared(session);ambience.tick(session,false);
        verify(player,times(1)).stopSound("custom:room",SoundCategory.RECORDS);
        verify(player,times(1)).playSound(any(Location.class),eq("custom:room"),eq(SoundCategory.RECORDS),eq(1f),eq(1f));
    }
    @Test void cleanupStopsMusicOnDisconnectAndSessionFinish() {
        ambience.tick(session,false);ambience.remove(player);ambience.clear();
        verify(player).stopSound("custom:room",SoundCategory.RECORDS);
    }
    @Test void doorEffectsAreLocalAndShakeIsOptIn() {
        when(player.getLocation()).thenReturn(new Location(world,1000,64,1000));
        ambience.door(session,region,session.def().rooms().getFirst());verifyNoInteractions(player.getPersistentDataContainer());
        verify(player,never()).playSound(any(Location.class),anyString(),anyFloat(),anyFloat());
        when(player.getLocation()).thenReturn(new Location(world,2,64,2));
        ambience.door(session,region,session.def().rooms().getFirst());
        verify(player,never()).addPotionEffect(any());
    }
}
