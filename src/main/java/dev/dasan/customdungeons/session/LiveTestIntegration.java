package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.config.Validator;
import dev.dasan.customdungeons.mob.LiveTestService;
import java.util.Objects;
import java.util.stream.Collectors;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Dungeon validation and membership policies injected into the independent live-test host. */
public final class LiveTestIntegration {
    private LiveTestIntegration() {}

    public static void register(CustomDungeonsPlugin plugin) {
        var config = Objects.requireNonNull(plugin.getServer().getServicesManager().load(PluginConfig.class));
        LiveTestService.register(plugin, plugin, config.liveTestMaxSeconds(), validation(plugin, config),
                player -> plugin.sessionManager() == null
                        || plugin.sessionManager().sessionOf(player.getUniqueId()).isEmpty());
        plugin.getServer().getPluginManager().registerEvents(
                new WardenSessionListener(plugin, LiveTestService::owns), plugin);
    }
    /** The same validation policy is exercised by offline live-test fixtures. */
    public static java.util.function.BiPredicate<org.bukkit.entity.Player,dev.dasan.customdungeons.model.MobTemplate>
            validation(CustomDungeonsPlugin plugin, PluginConfig config) {
        return (player, template) -> {
            var ids = plugin.abilityRegistry().all().stream().map(a -> a.id()).collect(Collectors.toSet());
            var errors = new Validator().validate(template, config, ids);
            if (errors.isEmpty()) return true;
            plugin.messages().send(player, "livetest.invalid");
            for (var error : errors) plugin.messages().send(player, "livetest.validation-error",
                    Placeholder.component("error", Validator.describe(error, plugin.messages())));
            return false;
        };
    }

}
