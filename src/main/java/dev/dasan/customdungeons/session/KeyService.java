package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.MobKeys;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class KeyService {
    public enum Cause { TICK, REMOVED, PICKED_UP, CONSUMED, RESET }
    public static boolean shouldRespawnKey(Cause cause, double y, int minY) {
        return cause == Cause.REMOVED || cause == Cause.TICK && y < minY;
    }
    private final DungeonSession session;
    private final DoorService doors;
    private Item dropped;
    private UUID holder;
    private int room = -1;
    private boolean replacing;
    private boolean opening;
    private Location carrierDeath;
    public KeyService(DungeonSession session, DoorService doors) { this.session = session; this.doors = doors; }
    public boolean matches(ItemStack item) {
        if (item == null || room < 0) return false;
        return token().equals(item.getPersistentDataContainer().get(MobKeys.KEY_ITEM,PersistentDataType.STRING));
    }
    private String token() { return session.id()+":"+session.def().rooms().get(room).id(); }
    public static boolean isKey(ItemStack item) {
        return item != null && item.getPersistentDataContainer().has(MobKeys.KEY_ITEM,PersistentDataType.STRING);
    }
    public void carrierDied(dev.dasan.customdungeons.runtime.ActiveMob mob) {
        var current = session.def().rooms().get(session.roomIndex());
        if ("*".equals(current.keyCarrierTemplateId()) || mob.template().id().equals(current.keyCarrierTemplateId())) carrierDeath = mob.entity().getLocation().clone();
    }
    public void create() {
        if (room == session.roomIndex() && (holder != null || recoverExistingKey())) return;
        room = session.roomIndex(); holder = null;
        if (recoverExistingKey()) return;
        Location at = carrierDeath;
        if (at == null || at.getY() < at.getWorld().getMinHeight() || !DungeonSessionRuntime.contains(session.currentRoomRegion(),at)) at = fallback();
        drop(at); carrierDeath = null;
    }
    private Location fallback() { return DoorService.keyRespawn(session.def().rooms().get(room)); }
    private void drop(Location at) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta();
        meta.getPersistentDataContainer().set(MobKeys.KEY_ITEM,PersistentDataType.STRING,token());
        meta.displayName(DungeonSessionRuntime.messages().get("session.key"));
        meta.setEnchantmentGlintOverride(true); key.setItemMeta(meta);
        dropped = at.getWorld().dropItem(at,key);
        mark(dropped); dropped.setGravity(false); dropped.setVelocity(new org.bukkit.util.Vector());
    }
    private void mark(Item item) {
        item.getPersistentDataContainer().set(MobKeys.SESSION,PersistentDataType.STRING,session.id().toString());
        item.setInvulnerable(true); item.setUnlimitedLifetime(true); item.setGlowing(true);
    }
    private boolean recoverExistingKey() {
        if (room < 0) return false;
        if (dropped != null && dropped.isValid() && accessible(dropped.getLocation())) return true;
        for (Player player : session.players()) {
            boolean found = matches(player.getItemOnCursor());
            for (ItemStack item : player.getInventory().getContents()) found |= matches(item);
            if (found) { holder=player.getUniqueId(); return true; }
        }
        World world = Bukkit.getWorld(session.def().rooms().get(room).region().world());
        if (world != null) for (Item item : world.getEntitiesByClass(Item.class))
            if (item.isValid() && matches(item.getItemStack()) && accessible(item.getLocation())) {
                if (dropped != null && dropped != item) dropped.remove();
                dropped=item; holder=null; mark(item); return true;
            }
        return false;
    }
    private void relocate() {
        if (room < 0 || replacing) return;
        replacing = true;
        try {
            if (recoverExistingKey()) return;
            if (dropped != null) { Item old = dropped; dropped = null; old.remove(); }
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
        removeFrom(player);
        if (player.getUniqueId().equals(holder)) relocate();
    }
    public void died(Player player) { if (player.getUniqueId().equals(holder)) holder = null; }
    public boolean use(Player player, org.bukkit.block.Block block, ItemStack key) {
        if (opening || room < 0 || room != session.roomIndex() || !session.survivors().contains(player.getUniqueId()) || !matches(key)) return false;
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
    private void removeFrom(Player player) {
        var inventory = player.getInventory();
        for (int i=0; i<inventory.getSize(); i++) if (matches(inventory.getItem(i))) inventory.setItem(i,null);
        if (matches(player.getItemOnCursor())) player.setItemOnCursor(null);
    }
    public void clear() {
        replacing = true;
        for (Player player : session.players()) removeFrom(player);
        for (Player player : Bukkit.getOnlinePlayers()) removeFrom(player);
        if (dropped != null) { dropped.remove(); dropped = null; }
        holder = null; carrierDeath = null; opening = false; room = -1; replacing = false;
    }
}
