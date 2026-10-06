package dev.dasan.customdungeons.reward;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.integration.*;
import dev.dasan.customdungeons.session.*;
import dev.dasan.customdungeons.storage.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.ServicePriority;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class RewardService implements SessionLifecycleListener, org.bukkit.event.Listener, dev.dasan.customdungeons.command.ClaimHandler {
    private final Storage storage;
    private final Optional<VaultHook> vault;
    private final Messages messages;
    private final Executor main;
    private final Consumer<String> dispatch;
    private final Logger logger;
    private static final class Claim {
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        CompletableFuture<List<ItemStack>> take;
        Player player;
        boolean prepared, finished;
        List<ItemStack> remaining = List.of();
        Throwable returnReason;
        CompletableFuture<Void> saved;
        int count;
    }
    private final Map<UUID,Claim> claiming = new ConcurrentHashMap<>();
    private volatile boolean closing;
    private CustomDungeonsPlugin plugin;
    public RewardService(Storage storage, Optional<VaultHook> vault, Messages messages,
                         Executor main, Consumer<String> dispatch, Logger logger) {
        this.storage=storage; this.vault=vault; this.messages=messages;
        this.main=main; this.dispatch=dispatch; this.logger=logger;
    }
    /** T14 registration; recovery completes before SessionManager can accept commands. */
    public static void register(CustomDungeonsPlugin plugin) {
        var manager=plugin.sessionManager();
        new RecoveryService(plugin,plugin.storage(),manager).recoverOnEnable();
        Optional<VaultHook> vault = Optional.empty();
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) vault=VaultHook.tryCreate();
        Consumer<String> dispatch = command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),command);
        var rewards = new RewardService(plugin.storage(),vault,plugin.messages(),task -> {
            if (!plugin.isEnabled()) throw new RejectedExecutionException("Plugin is disabled");
            if (Bukkit.isPrimaryThread()) task.run(); else Bukkit.getScheduler().runTask(plugin,task);
        },dispatch,plugin.getLogger());
        rewards.plugin=plugin;
        plugin.getServer().getPluginManager().registerEvents(rewards,plugin);
        manager.addListener(rewards);
        manager.addListener(new CommandHooks(dispatch));
        var recorder = new RunRecorder(plugin.storage(),manager,plugin.getLogger(),plugin);
        manager.addListener(recorder);
        plugin.getServer().getPluginManager().registerEvents(recorder,plugin);
        plugin.getServer().getServicesManager().register(RewardService.class,rewards,plugin,ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(dev.dasan.customdungeons.command.ClaimHandler.class,rewards,plugin,ServicePriority.Normal);
    }
    @Override public void onFinished(DungeonSession s, RunResult result, Set<UUID> survivors) {
        if (result != RunResult.COMPLETED || s.testMode()) return;
        var reward = s.def().reward();
        for (Player p : s.players()) {
            if (!survivors.contains(p.getUniqueId())) continue;
            try {
                List<ItemStack> items = reward.items();
                List<ItemStack> rest = p.isOnline() ? leftovers(p,items) : items;
                if (!rest.isEmpty()) observe(storage.addClaims(p.getUniqueId(),rest));
                if (reward.money() > 0) {
                    if (vault.isPresent()) vault.get().deposit(p,reward.money());
                    else logger.warning("Vault economy unavailable; money reward ignored for dungeon " + s.def().id());
                }
                if (reward.xp() > 0) p.giveExp(reward.xp());
                for (String command : reward.commands()) dispatch.accept(command.replace("{player}",p.getName()));
                messages.send(p,"reward.received");
                if (!rest.isEmpty()) messages.send(p,"reward.pending");
            } catch (RuntimeException error) {
                logger.log(java.util.logging.Level.WARNING,"Reward delivery failed",error);
                messages.send(p,"reward.failed");
            }
        }
    }
    private static List<ItemStack> leftovers(Player p, List<ItemStack> items) {
        if (items.isEmpty()) return List.of();
        return List.copyOf(p.getInventory().addItem(items.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).values());
    }
    /** Must be called on the game thread. One destructive take per player until saving or fallback finishes. */
    public CompletableFuture<Integer> claim(Player p) {
        UUID id=p.getUniqueId();
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Rewards are closing"));
        Claim existing=claiming.get(id);
        if (existing != null) return existing.result;
        Claim claim=new Claim(); claim.player=p; claiming.put(id,claim);
        claim.result.whenComplete((unused,error) -> claiming.remove(id,claim));
        claim.take=storage.takeClaims(id);
        claim.take.whenComplete((items,error) -> {
            try { main.execute(() -> deliverClaim(claim,items,error)); }
            catch (RuntimeException rejected) { prepareReturn(claim,rejected); }
        });
        return claim.result;
    }
    private void deliverClaim(Claim claim, List<ItemStack> items, Throwable error) {
        synchronized (claim) {
            if (claim.prepared || claim.finished || closing) return;
            if (error != null) { completeClaim(claim,error); return; }
            int total=items.stream().mapToInt(ItemStack::getAmount).sum();
            List<ItemStack> rest;
            try { rest=claim.player.isOnline() ? leftovers(claim.player,items) : items; }
            catch (RuntimeException failure) { prepareReturn(claim,failure); return; }
            claim.prepared=true;
            claim.remaining=new ArrayList<>(rest);
            claim.count=total-rest.stream().mapToInt(ItemStack::getAmount).sum();
            saveRemaining(claim);
        }
    }
    private void prepareReturn(Claim claim, Throwable reason) {
        synchronized (claim) {
            if (claim.prepared || claim.finished) return;
            if (claim.take.isCompletedExceptionally()) {
                claim.take.whenComplete((items,error) -> completeClaimWithoutMessage(claim,error));
                return;
            }
            claim.prepared=true; claim.returnReason=reason;
            // Called only after take completes, or by closeClaims after draining take.
            claim.remaining=new ArrayList<>(claim.take.join());
            saveRemaining(claim);
        }
    }
    private void saveRemaining(Claim claim) {
        var items=List.copyOf(claim.remaining);
        claim.saved=items.isEmpty() ? CompletableFuture.completedFuture(null)
                : saveWithRetries(claim.player.getUniqueId(),items);
        claim.saved.whenComplete((unused,error) -> {
            if (closing) return; // onDisable drains the future and performs any Bukkit fallback itself.
            try { main.execute(() -> settleClaim(claim,error)); }
            catch (RuntimeException rejected) {
                // Retain ownership: disabling the plugin must not discard items awaiting main-thread delivery.
                logger.warning("Claim completion awaits shutdown recovery for " + claim.player.getUniqueId());
            }
        });
    }
    /** The timer never does database work: each retry enters Storage's own executor through addClaims. */
    private CompletableFuture<Void> saveWithRetries(UUID player, List<ItemStack> items) {
        var saved=new CompletableFuture<Void>();
        attemptSave(player,items,0,saved);
        return saved;
    }
    private void attemptSave(UUID player, List<ItemStack> items, int retries, CompletableFuture<Void> saved) {
        CompletableFuture<Void> operation;
        try { operation=storage.addClaims(player,items); }
        catch (RuntimeException error) { operation=CompletableFuture.failedFuture(error); }
        operation.whenComplete((unused,error) -> {
            if (error == null) saved.complete(null);
            else if (retries < 3) {
                long delay=100L << retries; // Three retries, at 100, 200 and 400 ms; never sleep on the game thread.
                CompletableFuture.delayedExecutor(delay,TimeUnit.MILLISECONDS)
                        .execute(() -> attemptSave(player,items,retries+1,saved));
            } else saved.completeExceptionally(error);
        });
    }
    /** Main thread only, including shutdown. Remaining items stay owned until each drop succeeds. */
    private void settleClaim(Claim claim, Throwable saveError) {
        synchronized (claim) {
            if (claim.finished) return;
            if (saveError != null) {
                logger.warning("Claim persistence failed after three retries; dropping remaining items beside " + claim.player.getUniqueId());
                try {
                    var at=claim.player.getLocation();
                    while (!claim.remaining.isEmpty()) {
                        ItemStack item=claim.remaining.getFirst();
                        Objects.requireNonNull(at.getWorld()).dropItemNaturally(at,item.clone());
                        claim.remaining.removeFirst();
                    }
                } catch (RuntimeException dropError) {
                    logger.log(java.util.logging.Level.WARNING,"Claim fallback failed; items remain owned for shutdown recovery",dropError);
                    return;
                }
            } else claim.remaining.clear();
            completeClaim(claim,saveError == null ? claim.returnReason : saveError);
        }
    }
    private void completeClaim(Claim claim, Throwable error) {
        if (!closing && claim.player.isOnline()) {
            if (error != null) messages.send(claim.player,"claim.failed");
            else messages.send(claim.player,claim.count == 0 ? "claim.empty" : "claim.delivered",
                    Placeholder.unparsed("count",Integer.toString(claim.count)));
        }
        completeClaimWithoutMessage(claim,error);
    }
    private void completeClaimWithoutMessage(Claim claim, Throwable error) {
        claim.finished=true;
        if (error != null) claim.result.completeExceptionally(error); else claim.result.complete(claim.count);
    }
    /** Drain takes and retry chains before Storage.close; Bukkit fallback runs here even when tasks are cancelled. */
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.LOWEST)
    public void disable(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        closeClaims();
    }
    void closeClaims() {
        closing=true;
        for (Claim claim : List.copyOf(claiming.values())) {
            try {
                claim.take.handle((items,error) -> null).join();
                prepareReturn(claim,new IllegalStateException("Plugin disabled before claim delivery"));
                if (claim.saved != null) {
                    Throwable error=claim.saved.handle((unused,failure) -> failure).join();
                    settleClaim(claim,error);
                }
            } catch (RuntimeException error) { logger.log(java.util.logging.Level.WARNING,"Claim shutdown failed",error); }
        }
    }
    private void observe(CompletableFuture<?> operation) {
        operation.exceptionally(error -> { logger.log(java.util.logging.Level.WARNING,"Reward persistence failed",error); return null; });
    }
}
