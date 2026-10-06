package dev.dasan.customdungeons.ability;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A validated snapshot of declared parameters. Unknown keys are programming errors. */
public final class ParamValues {
    private final Map<String, Object> values;

    public ParamValues(Map<String, Object> raw, List<ParamSpec> specs) {
        Map<String, Object> resolved = new HashMap<>();
        for (ParamSpec spec : specs) {
            Object value = raw.getOrDefault(spec.key(), spec.defaultValue());
            value = switch (spec.type()) {
                case INT, TICKS, DOUBLE -> {
                    double number = value instanceof Number n ? n.doubleValue() : Double.NaN;
                    if (!Double.isFinite(number)) {
                        number = ((Number) spec.defaultValue()).doubleValue();
                    }
                    yield Math.clamp(number, spec.min(), spec.max());
                }
                case BOOLEAN -> value instanceof Boolean ? value : spec.defaultValue();
                default -> value instanceof String ? value : spec.defaultValue();
            };
            resolved.put(spec.key(), value);
        }
        values = Map.copyOf(resolved);
    }
    private Object value(String key) {
        if (!values.containsKey(key)) { throw new IllegalArgumentException("Unknown parameter: " + key); }
        return values.get(key);
    }
    public int getInt(String key) { return ((Number) value(key)).intValue(); }
    public double getDouble(String key) { return ((Number) value(key)).doubleValue(); }
    public boolean getBoolean(String key) { return (Boolean) value(key); }
    public String getString(String key) { return (String) value(key); }
}
