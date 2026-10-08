package dev.dasan.customdungeons.ability.impl.custom;

import dev.dasan.customdungeons.mob.MobsPlatform;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.borrowed.BorrowedAbilitiesC;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;

public final class AnchorAbility implements Ability, Listener {
    private static final NamespacedKey KEY = MobsPlatform.key("anchor");
    private record Anchor(Player player) {}
    private final Map<UUID, Anchor> anchored = new HashMap<>();

    public String id() { return "anchor"; }
    public Material icon() { return Material.IRON_CHAIN; }
    public List<ParamSpec> params() { return List.of(new ParamSpec("ticks", ParamType.TICKS, 60, 1, 1200)); }
    public void execute(AbilityContext ctx) {
        for (var target : BorrowedAbilitiesC.targets(ctx)) {
            var player = (Player) target;
            clear(player);
            for (var type : List.of(Attribute.JUMP_STRENGTH, Attribute.MOVEMENT_SPEED)) {
                var attribute = player.getAttribute(type);
                if (attribute != null) attribute.addTransientModifier(
                        new AttributeModifier(KEY, -1, AttributeModifier.Operation.ADD_SCALAR));
            }
            var uuid = player.getUniqueId();
            var anchor = new Anchor(player);
            anchored.put(uuid, anchor);
            ctx.session().scheduler().runLater(ctx.params().getInt("ticks"), () -> {
                // Reapplying anchor refreshes its duration; older callbacks must do nothing.
                if (anchored.get(uuid) == anchor) clear(player);
            });
            CustomAbilitiesA.chains(ctx, player.getLocation());
        }
    }

    /** Remove only our key, including leftovers from an interrupted server run. */
    void clear(Player player) {
        for (var type : List.of(Attribute.JUMP_STRENGTH, Attribute.MOVEMENT_SPEED)) {
            var attribute = player.getAttribute(type);
            if (attribute != null && attribute.getModifier(KEY) != null) attribute.removeModifier(KEY);
        }
        anchored.remove(player.getUniqueId());
    }

    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void join(PlayerJoinEvent event) { clear(event.getPlayer()); }
    @EventHandler public void load(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Player player) clear(player);
    }
    @EventHandler public void disable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equals("CustomDungeons")) return;
        for (var anchor : List.copyOf(anchored.values())) clear(anchor.player());
        for (var player : Bukkit.getOnlinePlayers()) clear(player);
    }
}
