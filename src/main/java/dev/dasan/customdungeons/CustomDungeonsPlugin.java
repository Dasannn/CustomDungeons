package dev.dasan.customdungeons;

import dev.dasan.customdungeons.ability.Abilities;
import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.text.Messages;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomDungeonsPlugin extends JavaPlugin {
    private AbilityRegistry abilityRegistry;
    private Messages messages;

    public AbilityRegistry abilityRegistry() { return abilityRegistry; }
    public Messages messages() { return messages; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        abilityRegistry = new AbilityRegistry();
        Abilities.registerDefaults(abilityRegistry);
        messages = new Messages(getLogger());
        messages.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")),
                getConfig().getString("prefix", ""));

        // --- registro de servicios (una línea por tarea) ---
        getServer().getPluginManager().registerEvents(new dev.dasan.customdungeons.listener.AbilityProtectionListener(), this);
        messages.send(getServer().getConsoleSender(), "plugin.enabled");
    }

    @Override
    public void onDisable() {}
}
