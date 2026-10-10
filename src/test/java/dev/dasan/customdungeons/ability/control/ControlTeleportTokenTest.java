package dev.dasan.customdungeons.ability.control;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ControlTeleportTokenTest {
 static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"identical","destination","cause"})
 void movementCorrectionRecognizesOnlyItsExactToken(String route) {
  try(var f=new ControlServiceTest.Fixture()) {
   f.start("roots");var from=f.player.getLocation();
   var move=new PlayerMoveEvent(f.player,from,from.clone().add(.2,0,0));f.service.move(move);
   // A different listener teleports before Paper performs its movement correction.
   var to=move.getTo().clone(); if(route.equals("destination"))to.add(.25,0,0);
   f.service.teleport(new PlayerTeleportEvent(f.player,from,to,route.equals("cause")?PlayerTeleportEvent.TeleportCause.COMMAND:PlayerTeleportEvent.TeleportCause.PLUGIN));
   assertEquals(route.equals("identical")?1:0,f.service.activeCount(),"An identical PLUGIN teleport is indistinguishable; a different cause or destination releases");
  }
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(strings={"identical","destination","cause"})
 void carrierCorrectionRecognizesOnlyItsExactToken(String route) throws Exception {
  try(var f=new ControlServiceTest.Fixture()) {
   f.start("drain_grab");var vehicle=f.created.getFirst();
   var carrierFrom=new Location(f.world,0,65.2,1.2);
   when(vehicle.getLocation()).thenReturn(carrierFrom);
   when(f.entity.getLocation()).thenReturn(new Location(f.world,1,64,0));
   doAnswer(call->{
    Location destination=call.getArgument(0);Location from=f.player.getLocation();
    var expected=destination.clone().add(from.toVector().subtract(carrierFrom.toVector()));
    expected.setYaw(destination.getYaw()+from.getYaw()-carrierFrom.getYaw());
    expected.setPitch(destination.getPitch()+from.getPitch()-carrierFrom.getPitch());
    // An external plugin fires a matching teleport reentrantly, before the carrier's own one.
    if(route.equals("destination"))expected.add(.25,0,0);
    f.service.teleport(new PlayerTeleportEvent(f.player,from,expected,route.equals("cause")?PlayerTeleportEvent.TeleportCause.COMMAND:PlayerTeleportEvent.TeleportCause.PLUGIN));
    return true;
   }).when(vehicle).teleport(any(Location.class),any(io.papermc.paper.entity.TeleportFlag.class));
   f.step(1);
   assertEquals(route.equals("identical")?1:0,f.service.activeCount(),"The carrier token accepts only the exact same-tick PLUGIN correction");
  }
 }
}
