package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.BlockPos;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.jspecify.annotations.Nullable;

/** Main-thread-only selection state and tool issuance. Does not mutate dungeon definitions. */
public final class ToolService {
    static final NamespacedKey TOOL_KEY = new NamespacedKey("customdungeons", "tool");
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Location> points = new HashMap<>();
    private final Messages messages;
    private final PreviewRenderer previews;

    public ToolService(Messages messages, PreviewRenderer previews) {
        this.messages = messages;
        this.previews = previews;
    }
    public static void register(CustomDungeonsPlugin plugin) {
        var markers = new SpawnerMarkers(plugin);
        var previews = new PreviewRenderer(plugin, markers);
        var tools = new ToolService(plugin.messages(), previews);
        previews.tools = tools;
        var services = plugin.getServer().getServicesManager();
        services.register(ToolService.class, tools, plugin, ServicePriority.Normal);
        services.register(PreviewRenderer.class, previews, plugin, ServicePriority.Normal);
        services.register(SpawnerMarkers.class, markers, plugin, ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(new ToolListener(plugin, tools, previews, markers), plugin);
        previews.refresh();
    }
    public void give(Player player, ToolType type, @Nullable String dungeonId) {
        if (!allowed(player)) return;
        // Reissuing updates the dungeon context of an existing tool, never creates a second copy.
        int slot = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (type(player.getInventory().getItem(i)) == type) { slot = i; break; }
        }
        if (type(player.getItemOnCursor()) == type) return;
        Material material = switch (type) {
            case REGION -> Material.BLAZE_ROD;
            case DOOR -> Material.STICK;
            case SPAWNER -> Material.SPAWNER;
            case POINT -> Material.COMPASS;
        };
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        String key = type.name().toLowerCase(Locale.ROOT);
        meta.displayName(messages.get("tool." + key + ".name"));
        meta.lore(List.of(messages.get("tool." + key + ".lore")));
        meta.getPersistentDataContainer().set(TOOL_KEY, PersistentDataType.STRING,
                type.name() + ":" + (dungeonId == null ? "" : dungeonId));
        item.setItemMeta(meta);
        if (slot >= 0) player.getInventory().setItem(slot, item);
        else if (!player.getInventory().addItem(item).isEmpty()) {
            messages.send(player, "tool.inventory-full");
            return; // Never drop an overflow tool into the world.
        }
        messages.send(player, "tool.given", Placeholder.component("type", messages.get("tool." + key + ".name")));
        previews.refresh();
    }
    public Optional<Selection> selection(UUID admin) { return Optional.ofNullable(selections.get(admin)); }
    public Optional<Location> lastPoint(UUID admin) { return Optional.ofNullable(points.get(admin)).map(Location::clone); }
    public void clear(UUID admin) {
        selections.remove(admin);
        points.remove(admin);
        previews.clear(admin);
    }
    void select(Player player, Location location, boolean first) {
        String world = location.getWorld().getName();
        Selection previous = selections.get(player.getUniqueId());
        if (previous == null || !previous.world().equals(world)) previous = new Selection(world, null, null);
        var pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        selections.put(player.getUniqueId(), new Selection(world, first ? pos : previous.a(), first ? previous.b() : pos));
        messages.send(player, first ? "tool.selected-a" : "tool.selected-b");
        previews.refresh();
    }
    void point(Player player, Location location) {
        points.put(player.getUniqueId(), location.clone());
        messages.send(player, "tool.point-selected");
        previews.refresh();
    }
    boolean allowed(Player player) {
        if (player.hasPermission("customdungeons.admin.tools")) return true;
        messages.send(player, "tool.no-permission");
        return false;
    }
    static boolean isTool(@Nullable ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(TOOL_KEY);
    }
    static @Nullable ToolType type(@Nullable ItemStack item) {
        if (!isTool(item)) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(TOOL_KEY, PersistentDataType.STRING);
        if (value == null) return null;
        try { return ToolType.valueOf(value.split(":", 2)[0]); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    void close() { selections.clear(); points.clear(); previews.close(); }
}
