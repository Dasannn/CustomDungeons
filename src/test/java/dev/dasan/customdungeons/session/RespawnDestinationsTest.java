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
    Point spawn(String configured,List<Point> worlds) {
        return RespawnDestinations.selectSpawn(configured,"dungeons",worlds,p->false,warnings::add);
    }
    @Test void emptySettingUsesFirstLoadedWorld() {
        assertEquals(primary,spawn("",List.of(primary,secondary,dungeon)));assertTrue(warnings.isEmpty());
    }
    @Test void configuredLoadedWorldOverridesOrder() {
        assertEquals(secondary,spawn("secondary",List.of(primary,secondary,dungeon)));assertTrue(warnings.isEmpty());
    }
    @Test void missingConfiguredWorldUsesFirstLoadedWorldWithWarning() {
        assertEquals(primary,spawn("missing",List.of(primary,secondary,dungeon)));
        assertEquals(List.of("respawn.invalid-world"),warnings);
    }
    @Test void dedicatedDungeonWorldIsNeverAFallbackEvenOutsideRegions() {
        assertEquals(primary,spawn("dungeons",List.of(dungeon,primary)));
        assertEquals(List.of("respawn.unsafe-spawn"),warnings);
    }
    @Test void primarySpawnInsideAnyDungeonUsesNextOutsideSpawn() {
        assertEquals(secondary,RespawnDestinations.selectSpawn("","dungeons",List.of(primary,secondary),
                primary::equals,warnings::add));
        assertEquals(List.of("respawn.unsafe-spawn"),warnings);
    }
    @Test void noOutsideSpawnRemainsUnavailable() {
        assertNull(spawn("",List.of(dungeon)));assertNull(spawn("",List.of()));
    }
    @Test void nonFiniteSpawnIsRejected() {
        assertEquals(primary,spawn("",List.of(new Point("invalid",Double.NaN,64,0,0,0),primary)));
    }
    @Test void validBedAndAnchorKeepVanillaDestination() {
        for(boolean bed:List.of(true,false))assertEquals(secondary,RespawnDestinations.personalSpawn(
                bed,!bed,secondary,p->false,()->{fail("Personal spawn must not resolve fallback");return null;}));
    }
    @Test void bedAndAnchorInsideAnyDungeonUsePrimarySpawn() {
        for(boolean bed:List.of(true,false))assertEquals(primary,RespawnDestinations.personalSpawn(
                bed,!bed,secondary,secondary::equals,()->primary));
    }
    @Test void noBedAlwaysUsesPrimaryEvenIfVanillaSpawnIsOutsideDungeon() {
        assertEquals(primary,RespawnDestinations.personalSpawn(false,false,dungeon,p->false,()->primary));
    }
}
