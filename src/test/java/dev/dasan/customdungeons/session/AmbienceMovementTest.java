package dev.dasan.customdungeons.session;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class AmbienceMovementTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    final SessionManager manager=mock(SessionManager.class);
    final SessionListener listener=new SessionListener(manager);
    final Player player=mock(Player.class);
    final World world=mock(World.class);
    AmbienceMovementTest(){when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(manager.sessionOf(any())).thenReturn(Optional.empty());}
    @Test void withinBlockMovementAndRotationCannotCrossAnIntegerRoomBoundary() {
        listener.ambienceMove(new PlayerMoveEvent(player,new Location(world,-.9,64.1,.1),new Location(world,-.1,64.9,.9,180,45)));
        verifyNoInteractions(manager);
    }
    @Test void everyBlockAxisAndWorldBoundaryStillRevalidatesMembership() {
        var from=new Location(world,-.1,64.1,.1);
        for(var to:List.of(new Location(world,.1,64.1,.1),new Location(world,-.1,65,.1),new Location(world,-.1,64.1,1),new Location(mock(World.class),-.1,64.1,.1)))
            listener.ambienceMove(new PlayerMoveEvent(player,from,to));
        verify(manager,times(4)).sessionOf(player.getUniqueId());
    }
    @Test void sameBlockTeleportsStillRevalidateTheCurrentVisit() {
        var from=new Location(world,.1,64,.1);var to=new Location(world,.9,64,.9);
        listener.ambienceTeleport(new PlayerTeleportEvent(player,from,to,PlayerTeleportEvent.TeleportCause.COMMAND));
        verify(manager).sessionOf(player.getUniqueId());
    }
    @Test void missingDestinationStillIgnoresTheEventWithoutQueryingMembership() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(()->listener.ambienceMove(new PlayerMoveEvent(player,new Location(world,0,64,0),null)));
        verifyNoInteractions(manager);
    }
}
