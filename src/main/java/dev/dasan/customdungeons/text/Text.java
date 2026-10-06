package dev.dasan.customdungeons.text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public final class Text {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern LEGACY = Pattern.compile("(?i)&(#([0-9a-f]{6})|[0-9a-fk-or])");
    // Explicit decoration resets preserve the surrounding MiniMessage tag stack.
    private static final String RESET_DECORATIONS =
            "<!bold><!italic><!underlined><!strikethrough><!obfuscated>";
    private static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua",
            "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua",
            "red", "light_purple", "yellow", "white"};

    private Text() {}
    public static Component parse(String input) { return parse(input, new TagResolver[0]); }

    static Component parse(String input, TagResolver... placeholders) {
        Matcher matcher = LEGACY.matcher(input);
        StringBuilder converted = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase(Locale.ROOT);
            String tag;
            if (code.startsWith("#")) {
                tag = "<color:" + code + ">" + RESET_DECORATIONS;
            } else {
                int color = "0123456789abcdef".indexOf(code);
                tag = color >= 0 ? "<" + COLORS[color] + ">" + RESET_DECORATIONS : switch (code) {
                    case "k" -> "<obfuscated>";
                    case "l" -> "<bold>";
                    case "m" -> "<strikethrough>";
                    case "n" -> "<underlined>";
                    case "o" -> "<italic>";
                    case "r" -> "<white>" + RESET_DECORATIONS;
                    default -> throw new IllegalStateException(code);
                };
            }
            matcher.appendReplacement(converted, Matcher.quoteReplacement(tag));
        }
        matcher.appendTail(converted);
        return MINI.deserialize(converted.toString(), placeholders);
    }
}
