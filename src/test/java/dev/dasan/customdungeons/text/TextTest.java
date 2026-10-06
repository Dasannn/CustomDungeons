package dev.dasan.customdungeons.text;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TextTest {
    @Test void textParsesLegacyHexAndMiniMessage() {
        Component c = Text.parse("&8[&6Custom&#FF8800Dungeons&8] <bold>hola</bold>");
        assertEquals("[CustomDungeons] hola", PlainTextComponentSerializer.plainText().serialize(c));
        assertTrue(hasSpan(c, "Dungeons", TextColor.color(0xFF8800)));
    }
    @Test void legacyColorsInsideMiniMessageRemainMarkup() {
        assertEquals("red tail", PlainTextComponentSerializer.plainText().serialize(Text.parse("<bold>&cred</bold> tail")));
    }
    @Test void messagesResolvePlaceholdersPrefixAndWarnOnce() {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("hello", "&aHola <player>");
        var logger = java.util.logging.Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        var warnings = new java.util.ArrayList<java.util.logging.LogRecord>();
        logger.addHandler(new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord r) { warnings.add(r); }
            public void flush() {}
            public void close() {}
        });
        Messages messages = new Messages(logger);
        messages.load(yaml, "&6[P] ");
        yaml.set("hello", "changed");
        var placeholder = net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("player", "<admin>&c");
        assertEquals("Hola <admin>&c", PlainTextComponentSerializer.plainText().serialize(messages.get("hello", placeholder)));
        assertEquals(Component.text("<missing>", NamedTextColor.RED), messages.get("missing"));
        messages.get("missing");
        assertEquals(1, warnings.size());
        var sent = new java.util.ArrayList<Component>();
        var sender = (org.bukkit.command.CommandSender) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{org.bukkit.command.CommandSender.class},
            (proxy, method, args) -> { if (method.getName().equals("sendMessage") && args[0] instanceof Component c) sent.add(c); return null; });
        messages.send(sender, "hello", placeholder);
        assertEquals("[P] Hola <admin>&c", PlainTextComponentSerializer.plainText().serialize(sent.getFirst()));
    }
    private boolean hasSpan(Component c, String text, TextColor color) {
        return c instanceof TextComponent t && t.content().contains(text) && color.equals(c.color())
            || c.children().stream().anyMatch(child -> hasSpan(child, text, color));
    }
    @Test void legacyResetAndDecorations() {
        assertEquals("ABC & unknown", PlainTextComponentSerializer.plainText().serialize(Text.parse("&lA&rB&oC & unknown")));
    }
}
