package dev.dasan.customdungeons;

import dev.dasan.customdungeons.ability.Abilities;
import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.text.Messages;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomDungeonsPlugin extends JavaPlugin implements dev.dasan.customdungeons.mob.MobsPlatform {
    private AbilityRegistry abilityRegistry;
    private final dev.dasan.customdungeons.intelligence.IntelligenceRules intelligenceRules=dev.dasan.customdungeons.intelligence.IntelligenceRules.defaults();
    private dev.dasan.customdungeons.intelligence.IntelligenceService intelligence;
    public dev.dasan.customdungeons.intelligence.IntelligenceRules intelligenceRules() {return intelligenceRules;}
    private dev.dasan.customdungeons.ability.control.ControlService controls;
    private dev.dasan.customdungeons.ability.zone.ZoneService zones;
    private dev.dasan.customdungeons.ability.combat.CombatService combat;
    private Messages messages;
    private final dev.dasan.customdungeons.boss.BossRegistry bossRegistry=new dev.dasan.customdungeons.boss.BossRegistry();
    private dev.dasan.customdungeons.boss.WorldBossService worldBosses;
    public dev.dasan.customdungeons.boss.BossRegistry bossRegistry() {return bossRegistry;}
    public dev.dasan.customdungeons.boss.WorldBossService worldBosses() {return worldBosses;}
    private dev.dasan.customdungeons.storage.Storage storage;
    private dev.dasan.customdungeons.session.SessionManager sessionManager;
    public dev.dasan.customdungeons.storage.Storage storage() { return storage; }
    public dev.dasan.customdungeons.session.SessionManager sessionManager() { return sessionManager; }

    public AbilityRegistry abilityRegistry() { return abilityRegistry; }
    public Messages messages() { return messages; }

    private dev.dasan.customdungeons.config.PluginConfig mobConfig() {
        return java.util.Objects.requireNonNull(getServer().getServicesManager()
                .load(dev.dasan.customdungeons.config.PluginConfig.class));
    }
    @Override public dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits limits() {
        var current = getConfig();
        return new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(
                current.getInt("performance.max-alive-mobs-per-session", 50),
                current.getDouble("performance.particle-density", 1),
                current.getDouble("performance.effect-view-radius", 48));
    }
    @Override public java.util.Set<org.bukkit.entity.EntityType> armorCapable() { return mobConfig().armorCapable(); }
    @Override public int musicLengthTicks(String key) { return Math.max(1, mobConfig().musicLengthTicks().getOrDefault(key, 2400)); }
    @Override public java.util.Map<String, dev.dasan.customdungeons.model.MobTemplate> templates() {
        return java.util.Objects.requireNonNull(getServer().getServicesManager()
                .load(dev.dasan.customdungeons.config.DefinitionStore.class)).mobs();
    }
    @Override public void deliverReward(org.bukkit.entity.Player player, dev.dasan.customdungeons.model.RewardDef reward) {
        java.util.Objects.requireNonNull(getServer().getServicesManager()
                .load(dev.dasan.customdungeons.reward.RewardService.class)).deliver(player, reward);
    }

    @Override
    public void onEnable() {
        dev.dasan.customdungeons.config.ConfigMigration.run(this);
        saveDefaultConfig();
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        abilityRegistry = new AbilityRegistry();
        Abilities.registerDefaults(abilityRegistry);
        messages = new Messages(getLogger());
        messages.load(YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")),
                getConfig().getString("prefix", ""));

        controls=new dev.dasan.customdungeons.ability.control.ControlService(this);
        getServer().getPluginManager().registerEvents(controls,this);
        controls.recoverLoaded(getServer().getWorlds());
        zones=new dev.dasan.customdungeons.ability.zone.ZoneService(this);
        getServer().getPluginManager().registerEvents(zones,this);
        zones.recoverLoaded(getServer().getWorlds());
        combat=new dev.dasan.customdungeons.ability.combat.CombatService(this);
        getServer().getPluginManager().registerEvents(combat,this);
        combat.recoverLoaded(getServer().getWorlds());
        getServer().getPluginManager().registerEvents(dev.dasan.customdungeons.ability.FallProtection.shared(),this);
        intelligence=new dev.dasan.customdungeons.intelligence.IntelligenceService(this,intelligenceRules);
        getServer().getPluginManager().registerEvents(intelligence,this);

        // --- registro de servicios (una línea por tarea) ---
        getServer().getPluginManager().registerEvents(new dev.dasan.customdungeons.listener.AbilityProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new dev.dasan.customdungeons.mob.MobCombatListener(this), this);
        dev.dasan.customdungeons.config.DefinitionStore.register(this);
        registerSessions();
        dev.dasan.customdungeons.reward.RewardService.register(this);
        var definitions=getServer().getServicesManager().load(dev.dasan.customdungeons.config.DefinitionStore.class);
        worldBosses=new dev.dasan.customdungeons.boss.WorldBossService(this,this,bossRegistry,
                getServer().getServicesManager().load(dev.dasan.customdungeons.config.EntityHeights.class),
                getConfig().getInt("world-boss.spawn-attempts",20),
                p->sessionManager.sessionOf(p.getUniqueId()).isEmpty(),definitions::isReloading);
        getServer().getServicesManager().register(dev.dasan.customdungeons.boss.WorldBossService.class,worldBosses,this,org.bukkit.plugin.ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new dev.dasan.customdungeons.mob.MobProjectileListener(worldBosses::trackProjectile),this);
        dev.dasan.customdungeons.tool.ToolService.register(this);
        var previews=getServer().getServicesManager().load(dev.dasan.customdungeons.tool.PreviewRenderer.class);
        sessionManager.recoveryTicker(previews);
        intelligence.fallTicker(previews::refreshRecoveries);
        dev.dasan.customdungeons.gui.MenuListener.register(this);
        dev.dasan.customdungeons.session.LiveTestIntegration.register(this);
        dev.dasan.customdungeons.gui.menu.DungeonListMenu.register(this);
        dev.dasan.customdungeons.gui.menu.WizardMenu.register(this);
        dev.dasan.customdungeons.tool.BuildModeService.register(this);
        dev.dasan.customdungeons.mob.ThiefReturns.constructionMode(player->{
            var build=getServer().getServicesManager().load(dev.dasan.customdungeons.tool.BuildModeService.class);
            return build!=null&&build.protects(player.getUniqueId());
        });
        dev.dasan.customdungeons.update.UpdateService.register(this);
        dev.dasan.customdungeons.command.CustomDungeonCommand.register(this);
        messages.send(getServer().getConsoleSender(), "plugin.enabled");
    }

    @Override
    public void onDisable() {
        if(worldBosses!=null)worldBosses.cancelSearches();
        try {
            var build=getServer().getServicesManager().load(dev.dasan.customdungeons.tool.BuildModeService.class);
            if(build!=null) {
                try {build.close();} catch(RuntimeException failure) {
                    getLogger().warning(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(messages.get("build.draft-save-failed")));
                }
            }
            if (worldBosses != null) worldBosses.close();
            if (sessionManager != null) sessionManager.shutdown();
        }
        finally { dev.dasan.customdungeons.mob.ThiefReturns.constructionMode(player->false);if(combat!=null)combat.close();if(zones!=null)zones.close();if(controls!=null)controls.close();if(intelligence!=null)intelligence.close();if (storage != null) storage.close(); }
    }
    private void registerSessions() {
        var config = java.util.Objects.requireNonNull(getServer().getServicesManager().load(dev.dasan.customdungeons.config.PluginConfig.class));
        var definitions = java.util.Objects.requireNonNull(getServer().getServicesManager().load(dev.dasan.customdungeons.config.DefinitionStore.class));
        storage = dev.dasan.customdungeons.storage.SqlStorage.create(config.database(), getDataFolder().toPath());
        sessionManager = new dev.dasan.customdungeons.session.SessionManager(this, definitions, config, storage);
    }
}
