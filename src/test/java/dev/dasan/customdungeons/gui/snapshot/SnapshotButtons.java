package dev.dasan.customdungeons.gui.snapshot;

/** Inspect only the final Button.of argument; nested callbacks can legitimately be empty. */
public final class SnapshotButtons {
    private SnapshotButtons() {}
    public static boolean noopCreation(String source) {
        int begin = source.indexOf("Button.of(");
        if (begin < 0) throw new IllegalArgumentException("No Button.of expression");
        int start = begin + "Button.of(".length(), depth = 1, braces = 0, brackets = 0;
        char quote = 0; boolean escaped = false;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '(') depth++;
            if (c == ')') {
                depth--;
                if (depth == 0) return source.substring(start, i).replaceAll("\\s+", "")
                        .matches("\\([^)]*\\)->\\{\\}");
            }
            if (c == '{') braces++;
            if (c == '}') braces--;
            if (c == '[') brackets++;
            if (c == ']') brackets--;
            if (c == ',' && depth == 1 && braces == 0 && brackets == 0) start = i + 1;
        }
        throw new IllegalArgumentException("Incomplete Button.of expression");
    }
}
