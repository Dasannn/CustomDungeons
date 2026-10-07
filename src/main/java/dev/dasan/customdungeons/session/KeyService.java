package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.model.RoomDef;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class KeyService {
    public enum GiveResult { GIVEN, NO_SESSION, NO_DOOR }
    /** Command delivery shares the T28 item factory, token, tracking and cleanup. */
    public static GiveResult give(SessionManager manager, Player player, String dungeon) {
        var session = manager.sessionOf(player.getUniqueId()).orElse(null);
        if (session == null || session.state().state() != SessionState.RUNNING
                || !session.survivors().contains(player.getUniqueId())
                || dungeon != null && !dungeon.equals(session.def().id())) return GiveResult.NO_SESSION;
        var current = session.def().rooms().get(session.roomIndex());
        if (session.roomIndex() == session.def().rooms().size()-1 || current.door() == null) return GiveResult.NO_DOOR;
        manager.runtime(session).keys.give(player);
        return GiveResult.GIVEN;
    }
    public enum Cause { TICK, REMOVED, PICKED_UP, CONSUMED, RESET }
    public static boolean shouldRespawnKey(Cause cause, double y, int minY) {
        return cause == Cause.REMOVED || cause == Cause.TICK && y < minY;
    }
    private final DungeonSession session;
    private final DoorService doors;
    private Item dropped;
    private UUID holder;
    private int room = -1;
    private int clearedRoom = -1;
    private boolean replacing;
    private boolean opening;
    private Location carrierDeath;
    public KeyService(DungeonSession session, DoorService doors) { this.session = session; this.doors = doors; }
    boolean heldInCurrentRoom() { return room==session.roomIndex() && holder!=null; }
    public boolean matches(ItemStack item) {
        if (item == null || room < 0) return false;
        return token().equals(item.getPersistentDataContainer().get(MobKeys.KEY_ITEM,PersistentDataType.STRING));
    }
    /** Ownership does not depend on which room is currently tracked. */
    boolean belongsToSession(ItemStack item) {
        if (item == null) return false;
        String token = item.getPersistentDataContainer().get(MobKeys.KEY_ITEM,PersistentDataType.STRING);
        return token != null && token.startsWith(session.id()+":");
    }
    private String token() { return session.id()+":"+session.def().rooms().get(room).id(); }
    public static boolean isKey(ItemStack item) {
        return item != null && item.getPersistentDataContainer().has(MobKeys.KEY_ITEM,PersistentDataType.STRING);
    }
    public void carrierDied(dev.dasan.customdungeons.runtime.ActiveMob mob) {
        var current = session.def().rooms().get(session.roomIndex());
        if (current.openingMode() != RoomDef.OpeningMode.KEY) return;
        if ("*".equals(current.keyCarrierTemplateId()) || mob.template().id().equals(current.keyCarrierTemplateId())) carrierDeath = mob.entity().getLocation().clone();
    }
    public void create() {
        if (session.def().rooms().get(session.roomIndex()).openingMode() != RoomDef.OpeningMode.KEY) return;
        prepareRoom();
        if (recoverExistingKey()) return;
        Location at = carrierDeath;
        if (at == null || at.getY() < at.getWorld().getMinHeight() || !DungeonSessionRuntime.contains(session.currentRoomRegion(),at)) at = fallback();
        drop(at); carrierDeath = null;
    }
    private Location fallback() { return DoorService.keyRespawn(session.def().rooms().get(room)); }
    private ItemStack item() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(MobKeys.KEY_ITEM,PersistentDataType.STRING,token());
        meta.displayName(DungeonSessionRuntime.messages().get("session.key"));
        meta.setEnchantmentGlintOverride(true); key.setItemMeta(meta);
        return key;
    }
    /** A room awaiting its first entry is not ready, even though roomStarted is false. */
    void roomCleared() { clearedRoom = session.roomIndex(); }
    private void prepareRoom() {
        if (room == session.roomIndex()) return;
        int ready = clearedRoom;
        Location death = carrierDeath;
        clear(); room = session.roomIndex(); clearedRoom = ready; carrierDeath = death;
    }
    private void give(Player player) {
        prepareRoom();
        var remaining = player.getInventory().addItem(item());
        if (remaining.isEmpty()) holder = player.getUniqueId();
        else drop(fallback(),remaining.values().iterator().next());
    }
    private void drop(Location at) { drop(at,item()); }
    private void drop(Location at, ItemStack key) {
        dropped = at.getWorld().dropItem(at,key);
        mark(dropped); dropped.setGravity(false); dropped.setVelocity(new org.bukkit.util.Vector());
    }
    private void mark(Item item) {
        item.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,session.id().toString());
        item.setInvulnerable(true); item.setUnlimitedLifetime(true); item.setGlowing(true);
    }
    private boolean recoverExistingKey() {
        if (room < 0) return false;
        if (dropped != null) {
            if (dropped.isValid() && matches(dropped.getItemStack()) && accessible(dropped.getLocation())) return true;
            discardTrackedDrop();
        }
        holder = null;
        for (Player player : session.players()) {
            boolean found = matches(player.getItemOnCursor());
            for (ItemStack item : player.getInventory().getContents()) found |= matches(item);
            if (found) { holder=player.getUniqueId(); return true; }
        }
        World world = Bukkit.getWorld(session.def().rooms().get(room).region().world());
        if (world != null) for (Item item : world.getEntitiesByClass(Item.class))
            if (item.isValid() && matches(item.getItemStack()) && accessible(item.getLocation())) {
                dropped=item; holder=null; mark(item); return true;
            } else if (belongsToSession(item.getItemStack()) && !matches(item.getItemStack())) item.remove();
        return false;
    }
    private void discardTrackedDrop() {
        Item old = dropped; dropped = null;
        boolean previous = replacing;
        replacing = true;
        try { if (old != null && belongsToSession(old.getItemStack())) old.remove(); }
        finally { replacing = previous; }
    }
    private void relocate() {
        if (room < 0 || room != session.roomIndex() || replacing) return;
        replacing = true;
        try {
            if (recoverExistingKey()) return;
            holder = null; drop(fallback());
        } finally { replacing = false; }
    }
    public void removed(Item item) {
        if (!replacing && dropped != null && dropped.getUniqueId().equals(item.getUniqueId())) { dropped = null; relocate(); }
    }
    public void pickedUp(Item item, Player player) {
        if (dropped != null && dropped.getUniqueId().equals(item.getUniqueId())) { dropped = null; holder = player.getUniqueId(); }
    }
    public void playerDropped(Item item, Player player) {
        if (matches(item.getItemStack())) { holder = null; dropped = item; mark(item); }
    }
    void spawned(Item item) {
        if (matches(item.getItemStack())) { holder = null; dropped = item; mark(item); }
    }
    public void tick() {
        if (room >= 0 && room != session.roomIndex()) {
            int ready = clearedRoom;
            clear(); clearedRoom = ready;
            return;
        }
        if (holder != null) {
            Player player=Bukkit.getPlayer(holder);
            if (player != null && player.isOnline()) {
                boolean found=matches(player.getItemOnCursor());
                for (ItemStack item : player.getInventory().getContents()) found |= matches(item);
                if (!found) relocate();
            }
        }
        if (room >= 0 && holder == null && (dropped == null || !dropped.isValid() || shouldRespawnKey(Cause.TICK,dropped.getLocation().getY(),dropped.getWorld().getMinHeight()))) relocate();
        if (room >= 0 && holder == null && dropped != null && session.scheduler().currentTick()%20 == 0 && !accessible(dropped.getLocation())) relocate();
    }
    private boolean accessible(Location at) {
        for (int index=0; index<=session.roomIndex(); index++)
            if (DungeonSessionRuntime.contains(session.def().rooms().get(index).region(),at)) return true;
        return false;
    }
    public void leave(Player player) {
        boolean removedCurrent = removeFrom(player);
        boolean wasHolder = player.getUniqueId().equals(holder);
        if (wasHolder) holder = null;
        if ((removedCurrent || wasHolder) && !session.survivors().isEmpty()) relocate();
    }
    /** Death drops are filtered by the listener before recovering the issued key. */
    public void died(Player player) {
        boolean removedCurrent = removeFrom(player);
        boolean wasHolder = player.getUniqueId().equals(holder);
        if (wasHolder) holder = null;
        boolean continues = session.livesLeft(player.getUniqueId()) > 1
                || session.survivors().stream().anyMatch(id -> !id.equals(player.getUniqueId()));
        if ((removedCurrent || wasHolder) && continues) relocate();
    }
    public boolean use(Player player, org.bukkit.block.Block block, ItemStack key) {
        if (session.state().state() != SessionState.RUNNING || session.roomStarted() || opening || room < 0 || clearedRoom != room || room != session.roomIndex() || !session.survivors().contains(player.getUniqueId()) || !matches(key)) return false;
        Location at = player.getLocation();
        if (!withinDoorRange(session.def().rooms().get(room).door(), at.getWorld() == null ? null : at.getWorld().getName(), at.getX(), at.getY(), at.getZ())) {
            DungeonSessionRuntime.messages().send(player,"session.key-too-far");
            return false;
        }
        int opened = room;
        opening=true;
        doors.open(opened,this::clear).whenComplete((success,error) -> {
            opening=false;
            if (room == opened && session.survivors().contains(player.getUniqueId()) && (error != null || !Boolean.TRUE.equals(success)))
                DungeonSessionRuntime.messages().send(player,"session.key-open-failed");
        });
        return true;
    }
    /** Distance to the full block cuboid, including the outer faces of its maximum blocks. */
    static boolean withinDoorRange(dev.dasan.customdungeons.model.Region door, String world, double x, double y, double z) {
        if (door == null || !door.world().equals(world)) return false;
        double dx = Math.max(Math.max(door.min().x()-x,0), x-(door.max().x()+1.0));
        double dy = Math.max(Math.max(door.min().y()-y,0), y-(door.max().y()+1.0));
        double dz = Math.max(Math.max(door.min().z()-z,0), z-(door.max().z()+1.0));
        return dx*dx+dy*dy+dz*dz <= 16;
    }
    private boolean removeFrom(Player player) {
        boolean current = false;
        var inventory = player.getInventory();
        for (int i=0; i<inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (belongsToSession(item)) { current |= matches(item); inventory.setItem(i,null); }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (belongsToSession(cursor)) { current |= matches(cursor); player.setItemOnCursor(null); }
        return current;
    }
    public void clear() {
        boolean previous = replacing;
        replacing = true;
        try {
            Set<Player> players = new LinkedHashSet<>(session.players());
            players.addAll(Bukkit.getOnlinePlayers());
            for (Player player : players) removeFrom(player);
            discardTrackedDrop();
            // Selectors and earlier rooms may leave multiple copies; remove all session tokens.
            for (World world : Bukkit.getWorlds()) for (Item item : world.getEntitiesByClass(Item.class))
                if (belongsToSession(item.getItemStack())) item.remove();
            holder = null; carrierDeath = null; opening = false; room = -1; clearedRoom = -1;
        } finally { replacing = previous; }
    }
}
