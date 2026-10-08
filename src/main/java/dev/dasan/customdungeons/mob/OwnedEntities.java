package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.runtime.ActiveMob;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;

/** Called in entity initialization, before spawn events can observe a system summon. */
public final class OwnedEntities {
    private OwnedEntities() {}
    public static void mark(Entity entity, ActiveMob caster) {
        String encounter = caster.entity().getPersistentDataContainer().get(MobsPlatform.key("world_boss"), PersistentDataType.STRING);
        mark(entity, caster.session().id().toString(), encounter);
    }
    /** Vanilla launches inherit explicit shooter ownership, never proximity or a player's session. */
    public static boolean markProjectile(Projectile projectile, Entity shooter) {
        var data = shooter.getPersistentDataContainer();
        String encounter = data.get(MobsPlatform.key("world_boss"), PersistentDataType.STRING);
        if (encounter == null && !MobKeys.isDungeonMob(shooter)) return false;
        mark(projectile, data.get(MobKeys.SESSION, PersistentDataType.STRING), encounter);
        return true;
    }
    private static void mark(Entity entity, String session, String encounter) {
        if (session != null) entity.getPersistentDataContainer().set(MobKeys.SESSION, PersistentDataType.STRING, session);
        if (encounter != null) entity.getPersistentDataContainer().set(MobsPlatform.key("world_boss"), PersistentDataType.STRING, encounter);
    }
}
