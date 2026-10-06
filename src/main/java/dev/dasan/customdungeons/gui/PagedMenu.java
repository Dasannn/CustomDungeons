package dev.dasan.customdungeons.gui;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public abstract class PagedMenu<T> extends Menu {
    private int page;
    private int renderedPages = 1;

    protected PagedMenu(Player viewer, Component title, int rows) { super(viewer, title, rows); }
    protected abstract List<T> items();
    protected abstract Button button(T item);
    protected final int page() { return page; }
    protected final int pageSize() { return (getInventory().getSize() / 9 - 2) * 7; }

    @Override protected final void render() {
        List<T> snapshot = List.copyOf(items());
        int capacity = pageSize();
        renderedPages = pageCount(snapshot.size(), capacity);
        page = Math.clamp(page, 0, renderedPages - 1);
        int start = startIndex(snapshot.size(), capacity, page);
        int end = endIndex(snapshot.size(), capacity, page);
        for (int index = start; index < end; index++) {
            int offset = index - start;
            set((offset / 7 + 1) * 9 + offset % 7 + 1, button(snapshot.get(index)));
        }
    }
    @Override protected final boolean hasPreviousPage() { return page > 0; }
    @Override protected final boolean hasNextPage() { return page + 1 < renderedPages; }
    @Override protected final void previousPage() { if (hasPreviousPage()) { page--; refresh(); } }
    @Override protected final void nextPage() { if (hasNextPage()) { page++; refresh(); } }

    public static int pageCount(int count, int capacity) {
        if (count < 0 || capacity <= 0) { throw new IllegalArgumentException("Invalid page dimensions"); }
        return (int) Math.max(1, ((long) count + capacity - 1) / capacity);
    }
    public static int startIndex(int count, int capacity, int page) {
        return Math.clamp(page, 0, pageCount(count, capacity) - 1) * capacity;
    }
    public static int endIndex(int count, int capacity, int page) {
        return (int) Math.min(count, (long) startIndex(count, capacity, page) + capacity);
    }
}
