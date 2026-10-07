package dev.dasan.customdungeons.config;

/** Compatibility names for default loading; NumericRanges owns the limits. */
public final class DungeonLimits {
    public static final int MIN_LIVES=(int)NumericRanges.dungeon("lives").min();
    public static final int MAX_LIVES=(int)NumericRanges.dungeon("lives").max();
    private DungeonLimits() {}
}
