package dev.dasan.customdungeons.update;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SemVerTest {
    @Test void ordersVersionsAndPrereleases() {
        String[] ordered = {"1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta",
                "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0"};
        for (int i = 1; i < ordered.length; i++)
            assertTrue(SemVer.parse(ordered[i - 1]).compareTo(SemVer.parse(ordered[i])) < 0);
        assertEquals(0, SemVer.parse("1.2.3+build.01").compareTo(SemVer.parse("1.2.3+other")));
        assertTrue(SemVer.parse("999999999999999999999.0.0").compareTo(SemVer.parse("2.0.0")) > 0);
    }
    @Test void rejectsInvalidVersions() {
        for (String invalid : new String[]{"1.2", "v1.2.3", "01.2.3", "1.2.3-01", "1.2.3-", "../2.0.0", "1.2.3+"})
            assertThrows(IllegalArgumentException.class, () -> SemVer.parse(invalid), invalid);
    }
}
