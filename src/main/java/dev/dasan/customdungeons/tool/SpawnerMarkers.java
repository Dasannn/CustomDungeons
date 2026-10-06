package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Ephemeral, non-persistent displays. Interaction supplies the hitbox displays lack. */
public final class SpawnerMarkers {
    private static final NamespacedKey SPAWNER = new NamespacedKey("customdungeons", "spawner_marker");
    private static final NamespacedKey DUNGEON = new NamespacedKey("customdungeons", "marker_dungeon");
    private final CustomDungeonsPlugin plugin;
    private final Map<String, List<Entity>> entities = new HashMap<>();
    private final Set<String> incomplete = new HashSet<>();
    private final Set<String> editing = new HashSet<>();
    private final Map<String, Set<UUID>> showing = new HashMap<>();
    @FunctionalInterface
    public interface MarkerEditor { void open(Player player, String dungeonId, String spawnerId); }
    private MarkerEditor editor;

    public SpawnerMarkers(CustomDungeonsPlugin plugin) { this.plugin = plugin; }
    /** T15 installs its editor callback; no GUI or draft is constructed by T08. */
    public void onEdit(MarkerEditor editor) { this.editor = Objects.requireNonNull(editor); }
    public void show(DungeonDef dungeon) {
        editing.add(dungeon.id());
        removeEntities(dungeon.id());
        create(dungeon);
        for (Player player : plugin.getServer().getOnlinePlayers()) refreshVisibility(player);
    }
    public void hide(String dungeonId) {
        editing.remove(dungeonId);
        if (!showing.containsKey(dungeonId)) removeEntities(dungeonId);
        else for (Player player : plugin.getServer().getOnlinePlayers()) refreshVisibility(player);
    }
    void showFor(DungeonDef dungeon, Player player) {
        showing.computeIfAbsent(dungeon.id(), ignored -> new HashSet<>()).add(player.getUniqueId());
        List<Entity> markers = entities.get(dungeon.id());
        if (markers == null || incomplete.contains(dungeon.id()) || markers.stream().anyMatch(entity -> !entity.isValid())) {
            removeEntities(dungeon.id());
            create(dungeon);
            // Replacement entities start hidden, including for viewers already showing this dungeon.
            for (Player online : plugin.getServer().getOnlinePlayers())
                if (!online.getUniqueId().equals(player.getUniqueId())) refreshVisibility(online);
        }
        refreshVisibility(player);
    }
    /** Remove only the unloading chunk's markers; the next showFor rebuilds incomplete sets. */
    void unload(Chunk chunk) {
        entities.forEach((id, markers) -> {
            boolean removed = markers.removeIf(entity -> {
                Location location = entity.getLocation();
                // Compare coordinates without getChunk(), which could load a chunk during unload.
                if (!chunk.getWorld().equals(location.getWorld())
                        || (location.getBlockX() >> 4) != chunk.getX()
                        || (location.getBlockZ() >> 4) != chunk.getZ()) return false;
                entity.remove();
                return true;
            });
            if (removed) incomplete.add(id);
        });
    }
    void release(String dungeonId, UUID player) {
        Set<UUID> viewers = showing.get(dungeonId);
        if (viewers == null) return;
        viewers.remove(player);
        if (viewers.isEmpty()) showing.remove(dungeonId);
        if (!editing.contains(dungeonId) && !showing.containsKey(dungeonId)) removeEntities(dungeonId);
        else {
            Player online = plugin.getServer().getPlayer(player);
            if (online != null) refreshVisibility(online);
        }
    }
    void refreshVisibility(Player player) {
        entities.forEach((id, markers) -> {
            boolean visible = player.hasPermission("customdungeons.admin.edit")
                    && (editing.contains(id) || showing.getOrDefault(id, Set.of()).contains(player.getUniqueId()));
            for (Entity entity : markers) {
                if (visible) player.showEntity(plugin, entity);
                else player.hideEntity(plugin, entity);
            }
        });
    }
    private void create(DungeonDef dungeon) {
        List<Entity> markers = new ArrayList<>();
        entities.put(dungeon.id(), markers);
        for (RoomDef room : dungeon.rooms()) for (SpawnerDef spawner : room.spawners()) {
            Point point = spawner.location();
            World world = plugin.getServer().getWorld(point.world());
            if (world == null) continue;
            Location location = new Location(world, point.x(), point.y() + 0.5, point.z());
            markers.add(world.spawn(location, ItemDisplay.class, display -> {
                tag(display, dungeon.id(), spawner.id());
                display.setItemStack(new ItemStack(Material.SPAWNER));
                display.setBillboard(Display.Billboard.CENTER);
                display.setViewRange(1);
            }));
            markers.add(world.spawn(location.clone().subtract(0, 0.5, 0), Interaction.class, hitbox -> {
                tag(hitbox, dungeon.id(), spawner.id());
                hitbox.setInteractionWidth(1);
                hitbox.setInteractionHeight(1);
                hitbox.setResponsive(true);
            }));
        }
    }
    private void tag(Entity entity, String dungeonId, String spawnerId) {
        entity.setPersistent(false);
        entity.setVisibleByDefault(false);
        entity.setInvulnerable(true);
        entity.getPersistentDataContainer().set(SPAWNER, PersistentDataType.STRING, spawnerId);
        entity.getPersistentDataContainer().set(DUNGEON, PersistentDataType.STRING, dungeonId);
    }
    public @Nullable String spawnerAt(Entity clicked) {
        // Only markers owned by this service are accepted, including their interaction hitboxes.
        String id = dungeonAt(clicked);
        if (id == null || entities.getOrDefault(id, List.of()).stream()
                .noneMatch(entity -> entity.getUniqueId().equals(clicked.getUniqueId()))) return null;
        return clicked.getPersistentDataContainer().get(SPAWNER, PersistentDataType.STRING);
    }
    public @Nullable String dungeonAt(Entity clicked) {
        return clicked.getPersistentDataContainer().get(DUNGEON, PersistentDataType.STRING);
    }
    void click(Player player, Entity clicked) {
        String spawner = spawnerAt(clicked);
        String dungeon = dungeonAt(clicked);
        if (spawner == null || !player.hasPermission("customdungeons.admin.edit")
                || !editing.contains(dungeon)) return;
        if (editor != null) editor.open(player, dungeon, spawner);
        else plugin.messages().send(player, "tool.marker-selected");
    }
    private void removeEntities(String dungeonId) {
        incomplete.remove(dungeonId);
        List<Entity> removed = entities.remove(dungeonId);
        if (removed != null) removed.forEach(Entity::remove);
    }
    void close() {
        new ArrayList<>(entities.keySet()).forEach(this::removeEntities);
        showing.clear();
        editing.clear();
    }
}
