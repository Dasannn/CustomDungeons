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
    private dev.dasan.customdungeons.storage.Storage storage;
    private dev.dasan.customdungeons.session.SessionManager sessionManager;
    public dev.dasan.customdungeons.storage.Storage storage() { return storage; }
    public dev.dasan.customdungeons.session.SessionManager sessionManager() { return sessionManager; }

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
        dev.dasan.customdungeons.config.DefinitionStore.register(this);
        registerSessions();
        dev.dasan.customdungeons.tool.ToolService.register(this);
        dev.dasan.customdungeons.gui.MenuListener.register(this);
        dev.dasan.customdungeons.mob.LiveTestService.register(this);
        dev.dasan.customdungeons.gui.menu.DungeonListMenu.register(this);
        messages.send(getServer().getConsoleSender(), "plugin.enabled");
    }

    @Override
    public void onDisable() {
        try { if (sessionManager != null) sessionManager.shutdown(); }
        finally { if (storage != null) storage.close(); }
    }
    private void registerSessions() {
        var config = java.util.Objects.requireNonNull(getServer().getServicesManager().load(dev.dasan.customdungeons.config.PluginConfig.class));
        var definitions = java.util.Objects.requireNonNull(getServer().getServicesManager().load(dev.dasan.customdungeons.config.DefinitionStore.class));
        storage = dev.dasan.customdungeons.storage.SqlStorage.create(config.database(), getDataFolder().toPath());
        sessionManager = new dev.dasan.customdungeons.session.SessionManager(this, definitions, config, storage);
    }
}
