package dev.dasan.customdungeons.text;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Messages {
    private final Logger logger;
    private final Set<String> warned = new HashSet<>();
    private Map<String, String> messages = Map.of();
    private String prefix = "";

    public Messages() { this(Logger.getLogger("CustomDungeons")); }
    public Messages(Logger logger) { this.logger = logger; }

    public void load(YamlConfiguration yaml, String prefix) {
        Map<String, String> snapshot = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) { snapshot.put(key, yaml.getString(key)); }
        }
        messages = Map.copyOf(snapshot);
        this.prefix = prefix;
        warned.clear();
    }
    public Component get(String key, TagResolver... placeholders) {
        String message = messages.get(key);
        if (message == null) {
            if (warned.add(key)) { logger.warning("Missing message key: " + key); }
            return Component.text("<" + key + ">", NamedTextColor.RED);
        }
        return Text.parse(message, placeholders);
    }
    public void send(CommandSender to, String key, TagResolver... placeholders) {
        to.sendMessage(Text.parse(prefix, placeholders).append(get(key, placeholders)));
    }
}
