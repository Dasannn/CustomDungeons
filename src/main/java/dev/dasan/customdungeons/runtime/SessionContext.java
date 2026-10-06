package dev.dasan.customdungeons.runtime;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import dev.dasan.customdungeons.model.Region;

public interface SessionContext {
    UUID id();
    boolean isLiveTest();
    /** Únicos objetivos válidos de habilidades. */
    Collection<Player> players();
    @Nullable Region currentRoomRegion();
    Collection<ActiveMob> mobs();
    @Nullable ActiveMob spawnMinion(String templateId, Location at, ActiveMob owner);
    TempBlocks tempBlocks();
    TickScheduler scheduler();
    /** Ladrón: registra el ítem robado para devolverlo si no se recupera. */
    void onItemStolen(UUID owner, ItemStack item, ActiveMob thief);
}
