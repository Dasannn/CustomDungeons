package dev.dasan.customdungeons.mob;

import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Only stolen-item returns need to avoid a construction inventory. */
public final class ThiefReturns {
    private static Predicate<Player> construction=player->false;
    private ThiefReturns() {}
    /** Root integration supplies construction ownership without coupling mobs to dungeon services. */
    public static void constructionMode(Predicate<Player> protectedInventory) {construction=Objects.requireNonNull(protectedInventory);}
    public static boolean dropIfBuilding(Player owner,ItemStack item) {
        if(!construction.test(owner))return false;
        owner.getWorld().dropItemNaturally(owner.getLocation(),item);
        return true;
    }
}
