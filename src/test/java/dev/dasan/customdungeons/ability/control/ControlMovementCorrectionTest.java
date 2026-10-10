package dev.dasan.customdungeons.ability.control;

import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Replays Paper movement corrections while retaining genuine teleport cancellation. */
class ControlMovementCorrectionTest {
    @ParameterizedTest
    @ValueSource(strings = {"levitation_cage", "roots"})
    void correctingNormalMovementMustNotReleaseItsOwnControl(String ability) {
        try (var f = new ControlServiceTest.Fixture()) {
            f.start(ability);
            assertEquals(1, f.service.activeCount(), "control acquired normally");
            var from = f.player.getLocation();
            var attempted = from.clone().add(.18, .1, 0);
            var move = new PlayerMoveEvent(f.player, from, attempted);
            f.service.move(move);
            assertNotEquals(attempted, move.getTo(), "control corrects residual player motion");
            // ServerGamePacketListenerImpl.handleMovePlayer: changed event.getTo()
            // invokes CraftPlayer.teleport(destination, TeleportCause.PLUGIN).
            var correction = new PlayerTeleportEvent(f.player, from, move.getTo(),
                    PlayerTeleportEvent.TeleportCause.PLUGIN);
            f.service.teleport(correction);
            assertEquals(1, f.service.activeCount(),
                    "its own movement correction must not cancel the configured 80 tick control");
        }
    }
    @ParameterizedTest @ValueSource(strings={"roots","levitation_cage"})
    void ownCorrectionRemainsValidWhenPaperHasAlreadyUpdatedTheOriginLook(String ability) {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start(ability);var from=f.player.getLocation();
            var move=new PlayerMoveEvent(f.player,from,from.clone().add(.2,0,0));move.getTo().setYaw(80);f.service.move(move);
            var actualFrom=from.clone();actualFrom.setYaw(80);
            f.service.teleport(new PlayerTeleportEvent(f.player,actualFrom,move.getTo(),PlayerTeleportEvent.TeleportCause.PLUGIN));
            assertEquals(1,f.service.activeCount());
        }
    }
    @org.junit.jupiter.api.Test void correctionTokenIsConsumedExactlyOnce() {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start("roots");var from=f.player.getLocation();
            var move=new PlayerMoveEvent(f.player,from,from.clone().add(.2,0,0));f.service.move(move);
            var event=new PlayerTeleportEvent(f.player,from,move.getTo(),PlayerTeleportEvent.TeleportCause.PLUGIN);
            f.service.teleport(event);assertEquals(1,f.service.activeCount());
            f.service.teleport(event);assertEquals(0,f.service.activeCount());
        }
    }
    @ParameterizedTest
    @ValueSource(strings = {"COMMAND", "ENDER_PEARL", "PLUGIN", "NETHER_PORTAL"})
    void anExternalTeleportStillReleasesControl(String cause) {
        try (var f = new ControlServiceTest.Fixture()) {
            f.start("roots");
            var from = f.player.getLocation();
            var movement = new PlayerMoveEvent(f.player, from, from.clone().add(.2, 0, 0));
            f.service.move(movement);
            f.service.teleport(new PlayerTeleportEvent(f.player, from, from.clone().add(8, 0, 0),
                    PlayerTeleportEvent.TeleportCause.valueOf(cause)));
            assertEquals(0, f.service.activeCount());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"roots", "levitation_cage"})
    void aStaleCorrectionCannotHideAnExternalPluginTeleport(String ability) {
        try (var f = new ControlServiceTest.Fixture()) {
            f.start(ability);
            var from = f.player.getLocation();
            var movement = new PlayerMoveEvent(f.player, from, from.clone().add(.2, .1, 0));
            f.service.move(movement);
            f.step(1);
            f.service.teleport(new PlayerTeleportEvent(f.player, from, movement.getTo(),
                    PlayerTeleportEvent.TeleportCause.PLUGIN));
            assertEquals(0, f.service.activeCount());
        }
    }
}
