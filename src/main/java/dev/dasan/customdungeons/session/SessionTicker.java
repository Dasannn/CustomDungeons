package dev.dasan.customdungeons.session;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
/** Exactly one repeating task per active session; deferred work uses its TickScheduler. */
public final class SessionTicker {
    private final Plugin plugin;
    private final DungeonSession session;
    private final dev.dasan.customdungeons.mob.BossController bosses;
    private BukkitTask task;
    public SessionTicker(Plugin plugin, DungeonSession session, dev.dasan.customdungeons.mob.BossController bosses) { this.plugin=plugin; this.session=session; this.bosses=bosses; }
    public void start() {
        if (task != null) return;
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,() -> {
            try {
                try { session.tick(); }
                finally { bosses.tickMusic(session.scheduler().currentTick()); }
            }
            catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"Session tick failed: "+session.def().id(),error);
                session.finish(false);
            }
        },1,1);
    }
    public void stop() { if (task != null) { task.cancel(); task=null; } }
}
