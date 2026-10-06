package dev.dasan.customdungeons.gui;

import java.util.Objects;

/** Edits immutable definitions by replacing the value, retaining the original snapshot. */
public final class Draft<T> {
    private final T original;
    private T value;

    public Draft(T original) { this.original = original; this.value = original; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
    public boolean dirty() { return !Objects.equals(original, value); }
    public T original() { return original; }
}
