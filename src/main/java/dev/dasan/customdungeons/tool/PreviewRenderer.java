package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** One shared ticker for held tools and explicitly requested, time-limited dungeon previews. */
public final class PreviewRenderer {
    private final CustomDungeonsPlugin plugin;
    private final SpawnerMarkers markers;
    private final Map<UUID, TimedPreview> timed = new HashMap<>();
    private final Map<UUID, Integer> budgets = new HashMap<>();
    private record RegionPreview(Region region,Color color,long deadline) {}
    private final Map<UUID,RegionPreview> regionPreviews=new HashMap<>();
    private BukkitTask ticker;
    private boolean closed;
    private final Map<UUID,java.util.concurrent.CompletableFuture<List<WizardParticles.Dot>>> wizard=new HashMap<>();
    ToolService tools;
    private record TimedPreview(DungeonDef dungeon, long deadline) {}

    public PreviewRenderer(CustomDungeonsPlugin plugin, SpawnerMarkers markers) {
        this.plugin = plugin;
        this.markers = markers;
    }
    public void showRegion(Player player, Region region, Color color) {
        drawRegion(player, region, color, 20);
    }
    public void showRegion(Player player,Region region,Color color,int seconds) {
        if(closed || !player.hasPermission("customdungeons.admin.edit"))return;
        regionPreviews.put(player.getUniqueId(),new RegionPreview(region,color,System.nanoTime()+seconds*1_000_000_000L));refresh();
    }
    private void drawRegion(Player player, Region region, Color color, int maxSteps) {
        if (!player.hasPermission("customdungeons.admin.tools") && !player.hasPermission("customdungeons.admin.edit")) return;
        if (!player.getWorld().getName().equals(region.world())) return;
        double[] min = {region.min().x(), region.min().y(), region.min().z()};
        double[] max = {(double) region.max().x() + 1, (double) region.max().y() + 1, (double) region.max().z() + 1};
        double density = plugin.getConfig().getDouble("performance.particle-density", 1);
        if (!Double.isFinite(density) || density <= 0) return;
        density = Math.min(density, 4);
        for (int axis = 0; axis < 3; axis++) {
            int other = (axis + 1) % 3, third = (axis + 2) % 3;
            int steps = (int) Math.max(1, Math.min(maxSteps, Math.ceil((max[axis] - min[axis]) * density)));
            for (int edge = 0; edge < 4; edge++) for (int step = 0; step <= steps; step++) {
                double[] pos = min.clone();
                pos[axis] += (max[axis] - min[axis]) * step / steps;
                pos[other] = (edge & 1) == 0 ? min[other] : max[other];
                pos[third] = (edge & 2) == 0 ? min[third] : max[third];
                particle(player, new Location(player.getWorld(), pos[0], pos[1], pos[2]), color);
            }
        }
    }
    public void showDungeon(Player player, DungeonDef dungeon, int seconds) {
        if (!player.hasPermission("customdungeons.admin.edit")) {
            plugin.messages().send(player, "tool.no-permission");
            return;
        }
        clear(player.getUniqueId());
        if (seconds <= 0) return;
        timed.put(player.getUniqueId(), new TimedPreview(dungeon, System.nanoTime() + seconds * 1_000_000_000L));
        markers.showFor(dungeon, player);
        refresh();
    }
    private void particle(Player player, Location location, Color color) {
        double density = plugin.getConfig().getDouble("performance.particle-density", 1);
        if (!Double.isFinite(density) || density <= 0) return;
        double radius = plugin.getConfig().getDouble("performance.effect-view-radius", 48);
        if (!Double.isFinite(radius) || radius <= 0 || player.getLocation().distanceSquared(location) > radius * radius) return;
        int remaining = budgets.getOrDefault(player.getUniqueId(), 256);
        if (remaining <= 0) return;
        budgets.put(player.getUniqueId(), remaining - 1);
        player.spawnParticle(Particle.DUST, location, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1));
    }
    private boolean holding(Player player) {
        return player.hasPermission("customdungeons.admin.tools")
                && ToolService.isTool(player.getInventory().getItemInMainHand());
    }
    public void wizard(Player player,DungeonDef dungeon) {
        if(closed) return;
        if(!player.hasPermission("customdungeons.admin.edit")) return;
        wizard.put(player.getUniqueId(),java.util.concurrent.CompletableFuture.supplyAsync(()->WizardParticles.prepare(dungeon)));
        refresh();
    }
    public void stopWizard(UUID player) {
        wizard.remove(player);budgets.remove(player);refresh();
    }
    public boolean running() { return ticker != null; }
    /** Called after inventory/held-slot events, when their final inventory state is available. */
    void refresh() {
        if(closed) return;
        boolean active = !regionPreviews.isEmpty() || !wizard.isEmpty() || !timed.isEmpty() || plugin.getServer().getOnlinePlayers().stream().anyMatch(this::holding);
        if (active && ticker == null) {
            ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0, 10);
            plugin.getLogger().fine("Tool preview ticker started");
        } else if (!active && ticker != null) {
            ticker.cancel();
            ticker = null;
            budgets.clear();
            plugin.getLogger().fine("Tool preview ticker stopped");
        }
    }
    private void tick() {
        budgets.clear();
        long now = System.nanoTime();
        for(var entry:new ArrayList<>(regionPreviews.entrySet())) {
            var player=plugin.getServer().getPlayer(entry.getKey());var preview=entry.getValue();
            if(player==null || !player.isOnline() || !player.hasPermission("customdungeons.admin.edit") || now>=preview.deadline())regionPreviews.remove(entry.getKey());
            else drawRegion(player,preview.region(),preview.color(),20);
        }
        for(var entry:new ArrayList<>(wizard.entrySet())) {
            Player player=plugin.getServer().getPlayer(entry.getKey());
            if(player==null || !player.isOnline() || !player.hasPermission("customdungeons.admin.edit")) {
                wizard.remove(entry.getKey());continue;
            }
            // Geometry is ready on a worker; the main thread only filters recipients and sends dots.
            if(!entry.getValue().isDone() || entry.getValue().isCompletedExceptionally()) continue;
            for(var dot:entry.getValue().getNow(List.of())) if(player.getWorld().getName().equals(dot.world()))
                particle(player,new Location(player.getWorld(),dot.x(),dot.y(),dot.z()),dot.color());
        }
        for (var entry : new ArrayList<>(timed.entrySet())) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            TimedPreview preview = entry.getValue();
            if (player == null || !player.isOnline() || !player.hasPermission("customdungeons.admin.edit") || now >= preview.deadline()) {
                clear(entry.getKey());
                continue;
            }
            // Share the frame's particle budget across room and door outlines.
            long regions = preview.dungeon().rooms().stream().mapToLong(room -> room.door() == null ? 1 : 2).sum();
            int steps = (int) Math.max(1, Math.min(20, 240 / Math.max(1, regions) / 12 - 1));
            for (RoomDef room : preview.dungeon().rooms()) {
                if(room.region()!=null) drawRegion(player, room.region(), Color.LIME, steps);
                if (room.door() != null) drawRegion(player, room.door(), Color.ORANGE, steps);
                for (SpawnerDef spawner : room.spawners()) {
                    Point point = spawner.location();
                    if (point!=null && point.world().equals(player.getWorld().getName()))
                        particle(player, new Location(player.getWorld(), point.x(), point.y() + 0.5, point.z()), Color.AQUA);
                }
            }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            markers.refreshVisibility(player);
            if (!holding(player) || tools == null) continue;
            ToolType type = ToolService.type(player.getInventory().getItemInMainHand());
            if (type == ToolType.REGION || type == ToolType.DOOR) {
                Color color = type == ToolType.DOOR ? Color.ORANGE : Color.LIME;
                tools.selection(player.getUniqueId()).ifPresent(selection -> {
                    if (selection.complete()) showRegion(player, selection.toRegion(), color);
                    else {
                        BlockPos pos = selection.a() != null ? selection.a() : selection.b();
                        if (pos != null) showRegion(player, Region.of(selection.world(), pos, pos), color);
                    }
                });
            } else {
                tools.lastPoint(player.getUniqueId()).filter(point -> point.getWorld().equals(player.getWorld()))
                        .ifPresent(point -> particle(player, point, Color.AQUA));
            }
        }
        refresh();
    }
    void clear(UUID player) {
        regionPreviews.remove(player);
        TimedPreview previous = timed.remove(player);
        budgets.remove(player);
        if (previous != null) markers.release(previous.dungeon().id(), player);
    }
    void close() {
        closed=true;
        if (ticker != null) ticker.cancel();
        ticker = null;
        timed.clear();regionPreviews.clear();
        wizard.clear();
        budgets.clear();
        markers.close();
    }
}
