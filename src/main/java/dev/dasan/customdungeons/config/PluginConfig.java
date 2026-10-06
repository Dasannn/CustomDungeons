package dev.dasan.customdungeons.config;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import dev.dasan.customdungeons.model.ScalingDef;

public record PluginConfig(String prefix, String language, DatabaseSettings database, String dungeonWorld,
                           boolean autoCreateWorld, DungeonDefaults defaults, PerformanceLimits limits,
                           Set<EntityType> armorCapable, List<String> commandAliases, GuiSounds guiSounds,
                           Material doorMaterial, int liveTestMaxSeconds, Map<String, Integer> musicLengthTicks) {
    public PluginConfig {
        armorCapable = Set.copyOf(armorCapable);
        commandAliases = List.copyOf(commandAliases);
        musicLengthTicks = Map.copyOf(musicLengthTicks);
    }
    public record DatabaseSettings(String type, String host, int port, String database, String user,
                                   String password, int poolSize) {}
    public record DungeonDefaults(int lives, boolean keepInventory, int lobbyCountdownSeconds,
                                  int cooldownSeconds, int minPlayers, int maxPlayers, ScalingDef scaling) {}
    public record PerformanceLimits(int maxAliveMobsPerSession, double particleDensity, double effectViewRadius) {}
    public record GuiSounds(String click, String open, String save, String error) {}
}
