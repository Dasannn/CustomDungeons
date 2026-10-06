package dev.dasan.customdungeons.command;

import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/** T14 registers its reward service under this Bukkit service contract. Called on the main thread. */
public interface ClaimHandler {
    CompletableFuture<Integer> claim(Player player);
}
