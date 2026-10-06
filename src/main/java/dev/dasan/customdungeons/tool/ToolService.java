package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.BlockPos;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
    private final PlateTool plates;
    private Supplier<Collection<dev.dasan.customdungeons.model.DungeonDef>> plateDefinitions=List::of;
    public interface PlateEditor {
        dev.dasan.customdungeons.model.DungeonDef definition();
        boolean update(List<dev.dasan.customdungeons.model.Point> points);
        default boolean updateExit(List<dev.dasan.customdungeons.model.Point> points) { return false; }
    }
    public void onPlateEdit(java.util.function.BiFunction<Player,String,PlateEditor> editors,
                            Supplier<Collection<dev.dasan.customdungeons.model.DungeonDef>> definitions) {
        plates.editors=(player,id)->{
            var editor=editors.apply(player,id);if(editor==null)return null;
            return new PlateTool.Editor() {
                public dev.dasan.customdungeons.model.DungeonDef definition(){return editor.definition();}
                public boolean update(List<dev.dasan.customdungeons.model.Point> points){return editor.update(points);}
                public boolean updateExit(List<dev.dasan.customdungeons.model.Point> points){return editor.updateExit(points);}
            };
        };
        plateDefinitions=definitions;
    }
    void plate(Player player,ItemStack item,org.bukkit.block.Block clicked,boolean right) {
        var value=item.getItemMeta().getPersistentDataContainer().get(TOOL_KEY,PersistentDataType.STRING);
        if(value!=null && value.startsWith("PLATE:"))plates.edit(player,value.substring(6),clicked,right);
        else if(value!=null && value.startsWith("EXIT_PLATE:"))plates.edit(player,value.substring(11),clicked,right,true);
    }
    /** Construction owns a writable, independently locked draft and its existing edit permission. */
    public void editBuildPlate(Player player,org.bukkit.block.Block block,boolean right,boolean exit,PlateEditor editor) {
        plates.editBuild(player,new PlateTool.Editor() {
            public dev.dasan.customdungeons.model.DungeonDef definition(){return editor.definition();}
            public boolean update(List<dev.dasan.customdungeons.model.Point> points){return editor.update(points);}
            public boolean updateExit(List<dev.dasan.customdungeons.model.Point> points){return editor.updateExit(points);}
        },block,right,exit);
    }
    public boolean restoreBuildPlates(Player player,dev.dasan.customdungeons.model.DungeonDef before,dev.dasan.customdungeons.model.DungeonDef after) {
        return plates.restoreBuild(player,before,after);
    }
    boolean protectedPlate(org.bukkit.block.Block block) {
        return plateDefinitions.get().stream().flatMap(d->java.util.stream.Stream.concat(d.plates().stream(),d.exitPlates().stream())).anyMatch(p->PlateTool.at(p,block));
    }
    Collection<dev.dasan.customdungeons.model.DungeonDef> plateDefinitions() { return plateDefinitions.get(); }
    private final PreviewRenderer previews;
    private final Supplier<FileConfiguration> config;

    public ToolService(Messages messages, PreviewRenderer previews) {
        this(messages, previews, YamlConfiguration::new);
    }
    ToolService(Messages messages, PreviewRenderer previews, Supplier<FileConfiguration> config) {
        this.messages = messages;
        this.plates = new PlateTool(messages);
        this.previews = previews;
        this.config = config;
    }
    public static void register(CustomDungeonsPlugin plugin) {
        var markers = new SpawnerMarkers(plugin);
        var previews = new PreviewRenderer(plugin, markers);
        var tools = new ToolService(plugin.messages(), previews, plugin::getConfig);
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
        var inventory = player.getInventory();
        ToolType[] contents = new ToolType[inventory.getSize()];
        for (int i = 0; i < contents.length; i++) contents[i] = type(inventory.getItem(i));
        List<Integer> slots = ToolInventory.matchingSlots(contents, type);
        boolean onCursor = type(player.getItemOnCursor()) == type;
        String key = type.name().toLowerCase(Locale.ROOT);
        Material material = ToolMaterials.resolve(type, config.get().getString("tools.items." + key));
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setMaxStackSize(1);
        meta.displayName(messages.get("tool." + key + ".name"));
        meta.lore(List.of(messages.get("tool." + key + ".lore"),
                messages.get("tool." + key + ".lore-purpose"), messages.get("tool." + key + ".lore-next"), messages.get("tool.lore-store")));
        meta.getPersistentDataContainer().set(TOOL_KEY, PersistentDataType.STRING,
                type.name() + ":" + (dungeonId == null ? "" : dungeonId));
        item.setItemMeta(meta);
        if (!slots.isEmpty()) {
            inventory.setItem(slots.getFirst(), item);
            for (int i = 1; i < slots.size(); i++) inventory.setItem(slots.get(i), null);
            if (onCursor) player.setItemOnCursor(null);
        } else if (onCursor) player.setItemOnCursor(item);
        else if (!inventory.addItem(item).isEmpty()) {
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
    /** Removes only PDC tools, including equipment/offhand slots and the cursor. */
    public void clearTools(Player player) {
        if (!allowed(player)) return;
        var inventory = player.getInventory();
        boolean[] marked = new boolean[inventory.getSize()];
        for (int i = 0; i < marked.length; i++) marked[i] = isTool(inventory.getItem(i));
        for (int slot : ToolInventory.toolSlots(marked)) inventory.setItem(slot, null);
        if (isTool(player.getItemOnCursor())) player.setItemOnCursor(null);
        clear(player.getUniqueId());
        messages.send(player, "tool.cleared");
        previews.refresh();
    }
    void stored(Player player) {
        messages.send(player, "tool.stored");
    }
    void select(Player player, Location location, boolean first) {
        String world = location.getWorld().getName();
        Selection previous = selections.get(player.getUniqueId());
        if (previous == null || !previous.world().equals(world)) previous = new Selection(world, null, null);
        var pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Selection selection = new Selection(world, first ? pos : previous.a(), first ? previous.b() : pos);
        selections.put(player.getUniqueId(), selection);
        var placeholders = new ArrayList<TagResolver>(List.of(
                Placeholder.unparsed("world", world),
                Placeholder.unparsed("x", Integer.toString(pos.x())),
                Placeholder.unparsed("y", Integer.toString(pos.y())),
                Placeholder.unparsed("z", Integer.toString(pos.z()))));
        if (selection.complete()) {
            var region = selection.toRegion();
            placeholders.add(Placeholder.unparsed("width", Long.toString((long) region.max().x() - region.min().x() + 1)));
            placeholders.add(Placeholder.unparsed("height", Long.toString((long) region.max().y() - region.min().y() + 1)));
            placeholders.add(Placeholder.unparsed("depth", Long.toString((long) region.max().z() - region.min().z() + 1)));
            placeholders.add(Placeholder.unparsed("blocks", Long.toString(region.volume())));
        }
        placeholders.add(Placeholder.component("size", messages.get(selection.complete()
                ? "tool.selection-size" : "tool.selection-incomplete", placeholders.toArray(TagResolver[]::new))));
        TagResolver[] values = placeholders.toArray(TagResolver[]::new);
        String message = first ? "tool.selected-a" : "tool.selected-b";
        messages.send(player, message, values);
        values[values.length - 1] = Placeholder.component("size", messages.get(selection.complete()
                ? "tool.selection-size-short" : "tool.selection-incomplete-short", values));
        player.sendActionBar(messages.get(message + "-actionbar", values));
        previews.refresh();
    }
    /** T40 reuses the selection feedback without exposing or changing shared model contracts. */
    public void selectBuild(Player player, Location location, boolean first) {select(player,location,first);}
    void point(Player player, Location location) {
        points.put(player.getUniqueId(), location.clone());
        TagResolver[] values = {
                Placeholder.unparsed("world", location.getWorld().getName()),
                Placeholder.unparsed("x", String.format(Locale.ROOT, "%.2f", location.getX())),
                Placeholder.unparsed("y", String.format(Locale.ROOT, "%.2f", location.getY())),
                Placeholder.unparsed("z", String.format(Locale.ROOT, "%.2f", location.getZ()))};
        String message = type(player.getInventory().getItemInMainHand()) == ToolType.SPAWNER
                ? "tool.spawner-selected" : "tool.point-selected";
        messages.send(player, message, values);
        player.sendActionBar(messages.get(message + "-actionbar", values));
        previews.refresh();
    }
    boolean allowed(Player player) {
        var services=org.bukkit.Bukkit.getServer()==null?null:org.bukkit.Bukkit.getServicesManager();
        var build=services==null?null:services.load(BuildModeService.class);
        if(build!=null&&build.protects(player.getUniqueId())) {messages.send(player,"build.exit-first");return false;}
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
