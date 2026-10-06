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
        if (mob.template().id().equals(current.keyCarrierTemplateId())) carrierDeath = mob.entity().getLocation().clone();
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
        if (room < 0 || !session.survivors().contains(player.getUniqueId()) || !matches(key) || !DungeonSessionRuntime.contains(session.def().rooms().get(room).door(),block.getLocation())) return false;
        int opened = room;
        clear(); doors.open(opened); return true;
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
        holder = null; room = -1; replacing = false;
    }
}
