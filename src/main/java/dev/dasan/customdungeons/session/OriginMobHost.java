package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.*;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/** A stable area for abilities, intelligence and summons while the mandatory path advances. */
final class OriginMobHost implements MobHost {
    private final DungeonSession session;
    private final SpawnOrigin origin;
    OriginMobHost(DungeonSession session,SpawnOrigin origin) { this.session=session;this.origin=origin; }
    public UUID id() { return session.id(); }
    public Collection<Player> players() { return session.players(); }
    public boolean excludesTarget(LivingEntity target) { var at=target.getLocation();return at==null || !area().contains(at); }
    public Collection<Player> audience(Location at) { return session.audience(at); }
    public MobArea area() {
        var r=session.region(origin);
        return new MobArea(r.world(),new BoundingBox(r.min().x(),r.min().y(),r.min().z(),
                (double)r.max().x()+1,(double)r.max().y()+1,(double)r.max().z()+1));
    }
    public Collection<ActiveMob> mobs() { return session.originMobs(origin); }
    public MobCapacity.Reservation reserveMobSlot() { return session.reserveMobSlot(); }
    public ActiveMob spawnMinion(String template,Location at,ActiveMob owner) { return session.spawnMinion(template,at,owner); }
    public ActiveMob spawnMinionTemplate(MobTemplate template,Location at,ActiveMob owner) { return session.spawnMinionTemplate(template,at,owner); }
    public TempBlocks tempBlocks() { return session.tempBlocks(); }
    public TickScheduler scheduler() { return session.scheduler(); }
    public void onItemStolen(UUID owner,ItemStack item,ActiveMob thief) { session.onItemStolen(owner,item,thief); }
    public void markOrigin(Entity entity) {
        entity.getPersistentDataContainer().set(MobKeys.ORIGIN,PersistentDataType.STRING,origin.regionKey());
        entity.getPersistentDataContainer().set(MobKeys.SPAWNER,PersistentDataType.STRING,origin.spawner());
    }
}
