package dev.dasan.customdungeons.config;

import java.math.BigDecimal;
import java.util.Objects;

/** Authoring bounds, precision and provenance; vanillaZero is a separate sentinel. */
public record NumericRange(double min, double max, int decimals, Origin origin, boolean vanillaZero) {
    public enum Origin { MINECRAFT, PLUGIN }
    public NumericRange(double min, double max, int decimals, Origin origin) {
        this(min,max,decimals,origin,false);
    }
    public NumericRange {
        Objects.requireNonNull(origin);
        if(!Double.isFinite(min) || !Double.isFinite(max) || min>max || decimals<0 || decimals>8)
            throw new IllegalArgumentException("Invalid numeric range");
    }
    public boolean unbounded() { return max == NumericRanges.UNBOUNDED_MAX; }
    public boolean contains(double value) {
        return Double.isFinite(value) && ((vanillaZero && value==0) || (value>=min && value<=max));
    }
    public boolean containsPrecise(double value) {
        return contains(value) && BigDecimal.valueOf(value).stripTrailingZeros().scale()<=decimals;
    }
    public double clamp(double value) {
        if(vanillaZero && (value==0 || Double.isNaN(value))) return 0;
        return Double.isNaN(value) ? min : Math.clamp(value,min,max);
    }
    public double inputMin() { return vanillaZero ? Math.min(0,min) : min; }
    public String format(double value) { return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }
}
