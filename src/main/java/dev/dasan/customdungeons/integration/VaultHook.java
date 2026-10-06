package dev.dasan.customdungeons.integration;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import net.milkbowl.vault.economy.Economy;

/** The only class referencing Vault. Call tryCreate only after the optional plugin check. */
public final class VaultHook {
    private final Economy economy;
    private VaultHook(Economy economy) { this.economy = economy; }
    public static Optional<VaultHook> tryCreate() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return Optional.empty();
        try {
            var provider = Bukkit.getServicesManager().getRegistration(Economy.class);
            return provider == null ? Optional.empty() : Optional.of(new VaultHook(provider.getProvider()));
        } catch (LinkageError unavailable) { return Optional.empty(); }
    }
    public void deposit(OfflinePlayer p, double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid reward money");
        var response = economy.depositPlayer(p,amount);
        if (!response.transactionSuccess()) throw new IllegalStateException("Vault deposit failed: " + response.errorMessage);
    }
}
