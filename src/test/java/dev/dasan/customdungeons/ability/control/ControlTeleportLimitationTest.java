package dev.dasan.customdungeons.ability.control;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ControlTeleportLimitationTest {
 @Test void identicalExternalPluginTeleportKeepsMovementControl() {
  try(var f=new ControlServiceTest.Fixture()) {
   f.start("roots");var from=f.player.getLocation();
   var move=new PlayerMoveEvent(f.player,from,from.clone().add(.2,0,0));f.service.move(move);
   // A different listener teleports before Paper performs its movement correction.
   f.service.teleport(new PlayerTeleportEvent(f.player,from,move.getTo(),PlayerTeleportEvent.TeleportCause.PLUGIN));
   assertEquals(1,f.service.activeCount(),"RF-HAB2-13 p accepts an indistinguishable same-tick PLUGIN correction");
  }
 }
 @Test void identicalExternalPluginTeleportKeepsDrainControl() throws Exception {
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
    f.service.teleport(new PlayerTeleportEvent(f.player,from,expected,PlayerTeleportEvent.TeleportCause.PLUGIN));
    return true;
   }).when(vehicle).teleport(any(Location.class),any(io.papermc.paper.entity.TeleportFlag.class));
   f.step(1);
   assertEquals(1,f.service.activeCount(),"RF-HAB2-13 p also accepts the indistinguishable carrier correction");
  }
 }
}
