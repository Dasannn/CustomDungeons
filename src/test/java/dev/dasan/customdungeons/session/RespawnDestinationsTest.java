package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.Point;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RespawnDestinationsTest {
    final Point primary=new Point("primary",80,70,80,30,10);
    final Point secondary=new Point("secondary",90,70,90,0,0);
    final Point dungeon=new Point("dungeons",0,64,0,0,0);
    final List<String> warnings=new ArrayList<>();
    String world(String configured,List<String> worlds) {
        return RespawnDestinations.selectWorld(configured,worlds,warnings::add);
    }
    @Test void emptySettingUsesFirstLoadedWorld() {
        assertEquals("primary",world("",List.of("primary","secondary","dungeons")));assertTrue(warnings.isEmpty());
    }
    @Test void configuredLoadedWorldOverridesOrder() {
        assertEquals("secondary",world("secondary",List.of("primary","secondary","dungeons")));assertTrue(warnings.isEmpty());
    }
    @Test void missingConfiguredWorldUsesFirstLoadedWorldWithWarning() {
        assertEquals("primary",world("missing",List.of("primary","secondary","dungeons")));
        assertEquals(List.of("respawn.invalid-world"),warnings);
    }
    @Test void noLoadedWorldIsUnavailableOutsidePlayerEvents() {
        assertNull(world("",List.of()));
    }
    @Test void nonFinitePersonalSpawnIsRejected() {
        assertFalse(RespawnDestinations.valid(new Point("invalid",Double.NaN,64,0,0,0)));
    }
    @Test void validBedAndAnchorKeepVanillaDestination() {
        for(boolean bed:List.of(true,false))assertEquals(secondary,RespawnDestinations.personalSpawn(
                bed,!bed,secondary,p->false,()->{fail("Personal spawn must not resolve fallback");return null;}));
    }
    @Test void bedAndAnchorInsideAnyDungeonUsePrimarySpawn() {
        for(boolean bed:List.of(true,false))assertEquals(primary,RespawnDestinations.personalSpawn(
                bed,!bed,secondary,secondary::equals,()->primary));
    }
    @Test void noBedAlwaysResolvesFallbackEvenIfVanillaSpawnIsOutsideDungeon() {
        assertEquals(primary,RespawnDestinations.personalSpawn(false,false,dungeon,p->false,()->primary));
    }
}
