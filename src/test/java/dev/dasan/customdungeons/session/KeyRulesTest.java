package dev.dasan.customdungeons.session;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KeyRulesTest {
    @Test void wildcardRemembersLastRemovalInsteadOfAConcreteTemplate() throws Exception {
        var session = org.mockito.Mockito.mock(DungeonSession.class);
        var def = new SessionRuntimeRegressionTest().definition();
        var r = def.rooms().getFirst();
        var first = new dev.dasan.customdungeons.model.RoomDef(r.id(), r.region(), r.checkpoint(), r.door(),
                dev.dasan.customdungeons.model.UnlockMode.KEY, "*", r.spawners());
        var rooms = new java.util.ArrayList<>(def.rooms()); rooms.set(0, first);
        var configured = new dev.dasan.customdungeons.model.DungeonDef(def.id(), def.displayName(), def.enabled(), def.lobby(), def.exit(),
                def.minPlayers(), def.maxPlayers(), def.lobbyCountdownSeconds(), def.lives(), def.keepInventory(), def.timeLimitSeconds(),
                def.cooldownSeconds(), def.requirePermission(), def.scaling(), def.hooks(), def.reward(), rooms);
        org.mockito.Mockito.when(session.def()).thenReturn(configured);
        var keys = new KeyService(session, org.mockito.Mockito.mock(DoorService.class));
        var field = KeyService.class.getDeclaredField("carrierDeath"); field.setAccessible(true);
        for (int x : new int[]{1, 7}) {
            var mob = org.mockito.Mockito.mock(dev.dasan.customdungeons.runtime.ActiveMob.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            var at = new org.bukkit.Location(null, x, 64, 1);
            org.mockito.Mockito.when(mob.template().id()).thenReturn("zombie");
            org.mockito.Mockito.when(mob.entity().getLocation()).thenReturn(at);
            keys.carrierDied(mob);
            assertEquals(at, field.get(keys));
        }
    }
    @Test void missingOrBelowWorldRespawns() {
        assertTrue(KeyService.shouldRespawnKey(KeyService.Cause.REMOVED,64,-64));
        assertTrue(KeyService.shouldRespawnKey(KeyService.Cause.TICK,-65,-64));
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.TICK,-64,-64));
    }
    @Test void pickupAndConsumptionNeverDuplicate() {
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.PICKED_UP,-100,-64));
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.CONSUMED,-100,-64));
        assertFalse(KeyService.shouldRespawnKey(KeyService.Cause.RESET,-100,-64));
    }
}
