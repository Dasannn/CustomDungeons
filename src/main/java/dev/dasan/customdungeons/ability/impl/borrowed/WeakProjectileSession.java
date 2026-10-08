package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.mob.MobArea;
import dev.dasan.customdungeons.runtime.*;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Non-owning session view used only by Effects' projectile participant guard. */
final class WeakProjectileSession implements MobHost {
    private final UUID id;
    private final WeakReference<MobHost> session;

    WeakProjectileSession(MobHost session) {
        this.id = session.id();
        this.session = new WeakReference<>(session);
    }

    public UUID id() { return id; }
    public Collection<Player> players() {
        var active = session.get();
        return active == null ? List.of() : active.players();
    }
    // The projectile guard only needs id and players. Do not let this view become
    // a general session adapter that starts scheduling work or mutating gameplay.
    public Collection<Player> audience(Location at) { throw new UnsupportedOperationException(); }
    public MobArea area() { throw new UnsupportedOperationException(); }
    public Collection<ActiveMob> mobs() { throw new UnsupportedOperationException(); }
    public ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner) { throw new UnsupportedOperationException(); }
    public TempBlocks tempBlocks() { throw new UnsupportedOperationException(); }
    public TickScheduler scheduler() { throw new UnsupportedOperationException(); }
    public void onItemStolen(UUID owner, ItemStack item, ActiveMob thief) { throw new UnsupportedOperationException(); }
}
