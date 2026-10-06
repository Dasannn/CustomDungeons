package dev.dasan.customdungeons.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagerMathTest {
    @Test void pagerMath() {
        assertEquals(2, PagedMenu.pageCount(45, 28));
        assertEquals(0, PagedMenu.startIndex(45, 28, 0));
        assertEquals(28, PagedMenu.endIndex(45, 28, 0));
        assertEquals(28, PagedMenu.startIndex(45, 28, 1));
        assertEquals(45, PagedMenu.endIndex(45, 28, 1));
    }
    @Test void emptyExactAndOutOfRangePages() {
        assertEquals(1, PagedMenu.pageCount(0, 28));
        assertEquals(1, PagedMenu.pageCount(28, 28));
        assertEquals(0, PagedMenu.endIndex(0, 28, 100));
        assertEquals(0, PagedMenu.startIndex(45, 28, -1));
        assertEquals(28, PagedMenu.startIndex(45, 28, 99));
        assertEquals(45, PagedMenu.endIndex(45, 28, 99));
        assertEquals(76_695_845, PagedMenu.pageCount(Integer.MAX_VALUE, 28));
        assertThrows(IllegalArgumentException.class, () -> PagedMenu.pageCount(1, 0));
        assertThrows(IllegalArgumentException.class, () -> PagedMenu.pageCount(-1, 28));
    }
}
