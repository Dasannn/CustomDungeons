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
        if (count == 4) return List.of(row * 9 + 1, row * 9 + 3, row * 9 + 5, row * 9 + 7);
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
    /** Smallest chest with summary and footer, capped at a full paginated page. */
    public static int rowsFor(int entries, int columns, int headerRows) {
        if (entries < 0 || columns < 1 || columns > 7 || headerRows < 0 || headerRows > 3)
            throw new IllegalArgumentException("Invalid layout dimensions");
        return (int) Math.clamp(2L + headerRows + ((long) entries + columns - 1) / columns, 3, 6);
    }
    /** Relative footer slots: page arrows only when the list has multiple pages. */
    public static List<Integer> footerSlots(boolean parent, boolean pages, boolean save) {
        var slots = new java.util.ArrayList<Integer>();
        if (parent) slots.add(0);
        if (pages) slots.add(3);
        if (save) slots.add(4);
        if (pages) slots.add(5);
        slots.add(8);
        return List.copyOf(slots);
    }
    /** Percentage increase for an illustrative group of four players. */
    public static double scalingExample(double increment, int minimum) {
        return Math.max(0, 4 - minimum) * increment * 100;
    }
}
