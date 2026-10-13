package dev.dasan.customdungeons.intelligence;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Template-wide movement options; phases only change their effective level. */
public record MovementDef(Style style, Set<Option> disabled) {
    public static final MovementDef DEFAULT = new MovementDef(Style.AUTO, Set.of());
    public enum Style {
        AUTO, MELEE, RANGED;
        public Style resolve(String entityType) {
            if (this != AUTO) return this;
            return switch (entityType.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "")) {
                case "SKELETON", "STRAY", "BOGGED", "PARCHED", "PILLAGER", "BLAZE", "BREEZE", "EVOKER", "ILLUSIONER", "GHAST",
                     "SHULKER", "WITCH", "GUARDIAN", "ELDER_GUARDIAN", "WITHER", "SNOW_GOLEM" -> RANGED;
                default -> MELEE;
            };
        }
    }
    public enum Option {
        CIRCLE(3), SHIELD(3), RETREAT(3), DODGE(4), SPREAD(4);
        private final int level;
        Option(int level) { this.level = level; }
        public int minimumLevel() { return level; }
    }
    public MovementDef { java.util.Objects.requireNonNull(style); disabled = Set.copyOf(disabled); }
    public boolean enabled(Option option, int level) { return level >= option.minimumLevel() && !disabled.contains(option); }
    public MovementDef withStyle(Style value) { return new MovementDef(value, disabled); }
    public MovementDef toggle(Option option) {
        var copy = new HashSet<>(disabled);
        if (!copy.remove(option)) copy.add(option);
        return new MovementDef(style, copy);
    }
}
