package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Messages;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SidebarDataTest {
    final DungeonSessionFlowTest fixture=new DungeonSessionFlowTest();
    final Player player=fixture.player;
    final UUID uuid=fixture.p1;
    final Messages messages=messages();
    static Messages messages() {
        try(var in=SidebarDataTest.class.getResourceAsStream("/messages.yml")) {
            var y=new YamlConfiguration();y.load(new InputStreamReader(in,StandardCharsets.UTF_8));
            var messages=new Messages();messages.load(y,"");return messages;
        } catch(Exception e) {throw new AssertionError(e);}
    }
    DungeonSession session(DungeonDef def) {
        when(player.getUniqueId()).thenReturn(uuid);
        var session=new DungeonSession(def,false,new SessionServices() {
            public boolean inside(DungeonSession s,Player p) {return true;}
            public void roomCleared(DungeonSession s) {}
        });session.join(player);session.forceStart();return session;
    }
    String value(SidebarData.Snapshot data,String key) {return ScoreboardTemplatesTest.plain(data.values().get(key));}
    @Test void objectivesFollowEntryCombatKeyPuzzleAndExitWithoutInventingTimers() {
        for(var mode:RoomDef.OpeningMode.values()) {
            var def=fixture.definition(3);var original=def.rooms().getFirst();
            var room=new RoomDef(original.id(),original.region(),original.checkpoint(),original.door(),UnlockMode.AUTOMATIC,null,List.of(),mode);
            def=new DungeonDef(def.id(),def.displayName(),true,def.lobby(),def.exit(),1,4,0,3,false,0,0,false,def.scaling(),Map.of(),def.reward(),List.of(room,def.rooms().getLast()))
                    .withFinish(FinishMode.DELAYED,60,FinishDestination.EXIT,List.of());
            var s=session(def);
            assertEquals("Entra en la sala",value(SidebarData.capture(s,player,messages,0,false),"objective"));
            s.enterRoom(0);
            assertEquals("Limpia la sala",value(SidebarData.capture(s,player,messages,0,false),"objective"));
            s.tick();
            String expected=switch(mode) {case KEY->"Recoge la llave";case EXTERNAL_KEY->"Resuelve el puzzle";case AUTOMATIC->"Sigue a la sala";};
            assertEquals(expected,value(SidebarData.capture(s,player,messages,0,false),"objective"));
            if(mode!=RoomDef.OpeningMode.AUTOMATIC)assertEquals("Usa la llave en la puerta",value(SidebarData.capture(s,player,messages,0,true),"objective"));
            s.finish(true);
            var finished=SidebarData.capture(s,player,messages,0,false);
            assertEquals("completada",finished.state());assertEquals("Saliendo en 1:00",value(finished,"objective"));
            String duration=value(finished,"time_total");for(int i=0;i<40;i++)s.tick();
            assertEquals(duration,value(SidebarData.capture(s,player,messages,0,false),"time_total"));
        }
    }
    @Test void livesUseFullAndEmptyHeartsAndInitialGroupSurvivesDepartures() {
        var s=session(fixture.definition(3).withFinish(FinishMode.DELAYED,60,FinishDestination.EXIT,List.of()));
        s.playerDied(uuid);
        var data=SidebarData.capture(s,player,messages,0,false);
        assertEquals("❤❤♡",value(data,"lives"));assertEquals("1",value(data,"players"));assertEquals("1",value(data,"alive"));
        s.leave(uuid);
        data=SidebarData.capture(s,player,messages,0,false);
        assertEquals("1",value(data,"players"));assertEquals("0",value(data,"alive"));
    }
    @Test void killsCountOnceOnlyForSessionPlayersAndSurviveDeparture() {
        var s=session(fixture.definition(3).withFinish(FinishMode.DELAYED,60,FinishDestination.EXIT,List.of()));
        var mob=mock(Mob.class);when(mob.getUniqueId()).thenReturn(UUID.randomUUID());when(mob.getKiller()).thenReturn(player);
        var active=mock(ActiveMob.class);when(active.entity()).thenReturn(mob);s.track(active,"spawner");
        var death=mock(EntityDeathEvent.class);when(death.getEntity()).thenReturn(mob);
        s.mobRemoved(mob.getUniqueId(),death);s.mobRemoved(mob.getUniqueId(),death);
        var data=SidebarData.capture(s,player,messages,0,false);assertEquals("1",value(data,"kills"));assertEquals("1",value(data,"kills_total"));
        s.leave(uuid);assertEquals("1",value(SidebarData.capture(s,player,messages,0,false),"kills_total"));
    }
    @Test void timerBelowMinuteIsRedAndIndependentSpawnerWavesAreHidden() {
        var def=fixture.definition(3);var p=def.lobby();
        var first=def.rooms().getFirst();var spawn=new SpawnerDef("one",p,1,List.of());
        var room=new RoomDef(first.id(),first.region(),p,first.door(),UnlockMode.AUTOMATIC,null,List.of(spawn,new SpawnerDef("two",p,1,List.of())),RoomDef.OpeningMode.AUTOMATIC);
        def=new DungeonDef(def.id(),"Dungeon",true,p,def.exit(),1,4,0,3,false,60,0,false,def.scaling(),Map.of(),def.reward(),List.of(room,def.rooms().getLast()));
        var s=session(def);s.enterRoom(0);for(int i=0;i<20;i++)s.tick();
        var data=SidebarData.capture(s,player,messages,0,false);
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED,data.values().get("time_left").color());
        assertFalse(data.values().containsKey("wave"));assertTrue(data.conditions().contains("no_wave_summary"));
    }
    @Test void bossUsesScaledMaximumActivatedPhasesStableOrderAndOnlyLocalMobs() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        var world=mock(org.bukkit.World.class);when(world.getName()).thenReturn("world");
        var s=session(fixture.definition(3));s.enterRoom(0);
        var template=mock(MobTemplate.class);when(template.boss()).thenReturn(true);
        when(template.phases()).thenReturn(List.of(mock(PhaseDef.class),mock(PhaseDef.class)));
        var maximum=mock(org.bukkit.attribute.AttributeInstance.class);when(maximum.getValue()).thenReturn(2000.0);
        var first=mock(Mob.class);when(first.getUniqueId()).thenReturn(UUID.randomUUID());when(first.isValid()).thenReturn(true);
        when(first.getLocation()).thenReturn(new org.bukkit.Location(world,1,64,1));when(first.getHealth()).thenReturn(1150.0);
        when(first.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(maximum);
        var boss=new ActiveMob(first,template,s);boss.phaseIndex(0);s.track(boss,"spawner");
        var second=mock(Mob.class);when(second.getUniqueId()).thenReturn(UUID.randomUUID());when(second.isValid()).thenReturn(true);
        when(second.getLocation()).thenReturn(new org.bukkit.Location(world,2,64,2));
        when(second.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(maximum);when(second.getHealth()).thenReturn(400.0);
        s.track(new ActiveMob(second,template,s),"spawner");
        var outsider=mock(Mob.class);when(outsider.getUniqueId()).thenReturn(UUID.randomUUID());when(outsider.isValid()).thenReturn(true);
        when(outsider.getLocation()).thenReturn(new org.bukkit.Location(world,100,64,100));s.track(new ActiveMob(outsider,template,s),"other");
        var data=SidebarData.capture(s,player,messages,0,false);
        assertEquals("partida-jefe",data.state());assertEquals("2",value(data,"mobs_left"));
        assertEquals("58",value(data,"boss_health"));assertEquals("2",value(data,"boss_phase"));assertEquals("3",value(data,"boss_phases"));
        assertEquals("Derrota al jefe",value(data,"objective"));
        when(first.getHealth()).thenReturn(1800.0);assertEquals("2",value(SidebarData.capture(s,player,messages,0,false),"boss_phase"));
        when(first.isDead()).thenReturn(true);assertEquals("20",value(SidebarData.capture(s,player,messages,0,false),"boss_health"));
    }
    @Test void plateProgressCountsDistinctOccupiedPlatesOnly() {
        var p=new Point("world",0,64,0,0,0);var other=new Point("world",1,64,0,0,0);
        assertEquals(1,PlateOccupancy.occupiedCount(List.of(p,other),List.of(p,p)));
        assertEquals(0,PlateOccupancy.occupiedCount(List.of(p),List.of(new Point("other",0,64,0,0,0))));
    }
    @Test void largeLifeCountsHaveConstantSizeAndSmallCountsKeepHollowHearts() {
        assertEquals("❤❤♡",ScoreboardTemplatesTest.plain(SidebarData.hearts(messages,2,3)));
        assertEquals("❤".repeat(10),ScoreboardTemplatesTest.plain(SidebarData.hearts(messages,10,10)));
        assertEquals("❤ ×100000",ScoreboardTemplatesTest.plain(SidebarData.hearts(messages,100000,100000)));
        assertTrue(SidebarData.hearts(messages,Integer.MAX_VALUE,Integer.MAX_VALUE).children().size()<10);
    }
    @Test void unchangedInputCaptureBuildsNoMessageComponentsAndReloadChangesItsIdentity() {
        var s=session(fixture.definition(3));var counted=spy(messages);
        var first=SidebarData.inputs(s,player,counted,0,false);
        assertEquals(first,SidebarData.inputs(s,player,counted,0,false));verify(counted,never()).get(anyString(),any());
        counted.load(new YamlConfiguration(),"");assertNotEquals(first,SidebarData.inputs(s,player,counted,0,false));
    }

}
