package dev.dasan.customdungeons.gui;

import java.util.List;
import java.util.stream.IntStream;

/** Pure chest-grid positions, shared by paginated lists and aligned controls. */
public final class GuiLayout {
    private GuiLayout() {}
    public static List<Integer> centeredRow(int row, int count) {
        if (row < 0 || row > 5 || count < 0 || count > 7)
            throw new IllegalArgumentException("Invalid chest row dimensions");
        if (count == 0) return List.of();
        if (count == 3) return List.of(row * 9 + 2, row * 9 + 4, row * 9 + 6);
        return IntStream.range(0, count).map(i -> {
            int column = 4 - count / 2 + i;
            if (count % 2 == 0 && i >= count / 2) column++;
            return row * 9 + column;
        }).boxed().toList();
    }
    public static int pageSlot(int offset, int count, int firstRow) {
        if (offset < 0 || offset >= count) throw new IndexOutOfBoundsException(offset);
        int row = offset / 7;
        return centeredRow(firstRow + row, Math.min(7, count - row * 7)).get(offset % 7);
    }
    /** Percentage increase for an illustrative group of four players. */
    public static double scalingExample(double increment, int minimum) {
        return Math.max(0, 4 - minimum) * increment * 100;
    }
}
