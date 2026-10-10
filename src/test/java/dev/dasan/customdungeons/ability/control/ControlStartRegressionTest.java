package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.entity.Ravager;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ControlStartRegressionTest {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings={"native","command","different_plugin_destination","reused_token"})
  void vehicleCorrectionUsesAnExactSingleUseToken(String route) {
    try(var f=new ControlServiceTest.Fixture()) {
      f.start("drain_grab");var vehicle=f.created.getFirst();
      var vehicleAt=new java.util.concurrent.atomic.AtomicReference<>(new org.bukkit.Location(f.world,0,65.2,1,31.5f,-8.2f));
      var playerAt=new java.util.concurrent.atomic.AtomicReference<>(new org.bukkit.Location(f.world,1,64,0,11.85f,0));
      when(vehicle.getLocation()).thenAnswer(i->vehicleAt.get().clone());
      when(f.player.getLocation()).thenAnswer(i->playerAt.get().clone());
      when(f.entity.getLocation()).thenReturn(new org.bukkit.Location(f.world,.5,64,0,-72.85f,17.1f));
      doAnswer(i->{
        var destination=(org.bukkit.Location)i.getArgument(0);
        var from=playerAt.get().clone();var carrierFrom=vehicleAt.get().clone();
        var to=destination.clone().add(from.toVector().subtract(carrierFrom.toVector()));
        to.setYaw(destination.getYaw()+(from.getYaw()-carrierFrom.getYaw()));
        to.setPitch(destination.getPitch()+(from.getPitch()-carrierFrom.getPitch()));
        vehicleAt.set(destination.clone());playerAt.set(to.clone());
        var cause=route.equals("command")?PlayerTeleportEvent.TeleportCause.COMMAND:PlayerTeleportEvent.TeleportCause.PLUGIN;
        if(route.equals("different_plugin_destination"))to.add(.25,0,0);
        f.service.teleport(new PlayerTeleportEvent(f.player,from,to,cause));
        if(route.equals("reused_token"))f.service.teleport(new PlayerTeleportEvent(f.player,from,to,PlayerTeleportEvent.TeleportCause.PLUGIN));
        return true;
      }).when(vehicle).teleport(any(org.bukkit.Location.class),any(io.papermc.paper.entity.TeleportFlag.class));
      f.step(1);
      assertEquals(route.equals("native")?1:0,f.service.activeCount(),
          "Only the exact native correction consumes the one-use token; other teleports release even during movement");
    }
  }
  @Test void unrelatedCommandTeleportDuringVehicleCallbackReleasesControl() {
    try(var f=new ControlServiceTest.Fixture()) {
      f.start("drain_grab");var vehicle=f.created.getFirst();
      doAnswer(i->{
        var from=f.player.getLocation();
        f.service.teleport(new PlayerTeleportEvent(f.player,from,from.clone().add(8,0,0),PlayerTeleportEvent.TeleportCause.COMMAND));
        return true;
      }).when(vehicle).teleport(any(org.bukkit.Location.class),any(io.papermc.paper.entity.TeleportFlag.class));
      f.step(1);
      assertEquals(0,f.service.activeCount(),"An unrelated command teleport was masked by control.moving");
    }
  }
  @Test void comboControlRevalidatesConfiguredRangeAtTheEndOfWarning() {
    try(var f=new ControlServiceTest.Fixture()) {
      f.registry.register(new Ability() {
        public String id(){return "noop";}
        public org.bukkit.Material icon(){return org.bukkit.Material.STONE;}
        public java.util.List<ParamSpec> params(){return List.of();}
        public void execute(AbilityContext c){}
      });
      var combo=new ComboDef("escape",Trigger.ON_HIT,0,TargetMode.NEAREST,4,100,List.of(new ComboStep("noop",Map.of(),0),new ComboStep("roots",Map.of(),0)));
      f.caster.combos().add(combo);
      var engine=new AbilityEngine(f.registry,null,new Random(1));engine.fire(Trigger.ON_HIT,f.caster,null,0);
      f.step(10);when(f.player.getLocation()).thenReturn(new org.bukkit.Location(f.world,30,64,0));f.step(10);
      assertEquals(0,f.service.activeCount(),"Combo applied roots at 30 blocks despite a configured range of 4");
    }
  }
  @Test void genuinePluginTeleportToCorrectionEndpointStillReleasesControl() {
    try(var f=new ControlServiceTest.Fixture()) {
      f.start("roots");
      var from=f.player.getLocation();
      var movement=new PlayerMoveEvent(f.player,from,from.clone().add(.2,0,0));f.service.move(movement);
      f.service.teleport(new PlayerTeleportEvent(f.player,from.clone().add(8,0,0),movement.getTo(),PlayerTeleportEvent.TeleportCause.PLUGIN));
      assertEquals(0,f.service.activeCount(),"An external plugin teleport was classified as this control's correction");
    }
  }
  @Test void nativeStunThatStartsDuringWarningKeepsControlPending() {
    try(var f=new ControlServiceTest.Fixture()) {
      var mob=mock(Ravager.class);
      when(mob.getUniqueId()).thenReturn(new UUID(0,0));when(mob.isValid()).thenReturn(true);
      var mobData=ControlServiceTest.pdc();when(mob.getPersistentDataContainer()).thenReturn(mobData);
      when(mob.getLocation()).thenAnswer(i->f.entity.getLocation());when(mob.getWorld()).thenReturn(f.world);
      var active=new ActiveMob(mob,f.caster.template(),f.host);
      active.abilities().add(new AbilityInstance("roots",Trigger.ON_HIT,0,TargetMode.NEAREST,16,100,1,20,Map.of()));
      var engine=new AbilityEngine(f.registry,null,new Random(1));engine.fire(Trigger.ON_HIT,active,null,0);
      f.step(10);when(mob.getStunnedTicks()).thenReturn(40);f.step(10);
      assertEquals(0,f.service.activeCount(),"Control started while its caster was natively stunned");
      assertTrue(active.ready("roots#0",20));
      when(mob.getStunnedTicks()).thenReturn(0);engine.tick(List.of(active),20);f.step(20);
      assertEquals(1,f.service.activeCount());assertFalse(active.ready("roots#0",40));
    }
  }
}
