package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobHost;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobHostTest {
    @Test void roomBoxIncludesMaximumBlocksAndExcludesTheNextBlock() {
        var fixture = new DungeonSessionFlowTest();
        MobHost host = fixture.session(3);
        var area = host.area();
        assertNotNull(area);
        assertTrue(area.bounds().contains(0, 60, 0));
        assertTrue(area.bounds().contains(4.99, 70.99, 4.99));
        assertFalse(area.bounds().contains(5, 70, 4));
        assertFalse(area.bounds().contains(4, 71, 4));
        assertFalse(area.bounds().contains(-.01, 60, 0));
    }

    @Test void boxIsAnIndependentViewOfTheImmutableRoom() {
        MobHost host = new DungeonSessionFlowTest().session(3);
        host.area().bounds().shift(100, 100, 100);
        assertTrue(host.area().contains("world", 0, 60, 0));
    }

    @Test void dungeonAudienceIncludesAllMembersRegardlessOfEmissionPoint() {
        var fixture = new DungeonSessionFlowTest();
        var session = fixture.session(3);
        session.join(fixture.player);
        MobHost host = session;
        assertEquals(host.players(), host.audience(new Location(null, 10000, 64, 10000)));
        assertTrue(host.audience(new Location(null, 10000, 64, 10000)).contains(fixture.player));
    }
}
