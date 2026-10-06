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

public final class RewardService implements SessionLifecycleListener, org.bukkit.event.Listener {
    private final Storage storage;
    private final Optional<VaultHook> vault;
    private final Messages messages;
    private final Executor main;
    private final Consumer<String> dispatch;
    private final Logger logger;
    private static final class Claim {
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        CompletableFuture<List<ItemStack>> take;
        boolean settled;
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
        return List.copyOf(p.getInventory().addItem(items.toArray(ItemStack[]::new)).values());
    }
    /** Must be called on the game thread. Concurrent requests for one player share one take. */
    public CompletableFuture<Integer> claim(Player p) {
        UUID id=p.getUniqueId();
        if (closing) return CompletableFuture.failedFuture(new IllegalStateException("Rewards are closing"));
        Claim existing=claiming.get(id); if (existing != null) return existing.result;
        Claim claim=new Claim(); claiming.put(id,claim);
        claim.result.whenComplete((unused,error) -> claiming.remove(id,claim));
        claim.take=storage.takeClaims(id);
        claim.take.whenComplete((items,error) -> {
            try { main.execute(() -> deliverClaim(p,claim,items,error)); }
            catch (RuntimeException rejected) { returnClaim(id,claim,rejected); }
        });
        return claim.result;
    }
    private void deliverClaim(Player p, Claim claim, List<ItemStack> items, Throwable error) {
        synchronized (claim) {
            if (claim.settled || closing) return;
            if (error != null) { claim.settled=true; completeClaim(p,claim,0,error); return; }
            List<ItemStack> rest;
            try { rest=p.isOnline() ? leftovers(p,items) : items; }
            catch (RuntimeException failure) { returnClaim(p.getUniqueId(),claim,failure); return; }
            claim.settled=true;
            int count=items.stream().mapToInt(ItemStack::getAmount).sum()-rest.stream().mapToInt(ItemStack::getAmount).sum();
            var saved=rest.isEmpty() ? CompletableFuture.<Void>completedFuture(null) : storage.addClaims(p.getUniqueId(),rest);
            claim.saved=saved; claim.count=count;
            saved.whenComplete((unused,saveError) -> {
                if (closing) {
                    if (saveError == null) claim.result.complete(count); else claim.result.completeExceptionally(saveError);
                    return;
                }
                try { main.execute(() -> completeClaim(p,claim,count,saveError)); }
                catch (RuntimeException rejected) { claim.result.completeExceptionally(saveError == null ? rejected : saveError); }
            });
        }
    }
    private void returnClaim(UUID id, Claim claim, Throwable reason) {
        synchronized (claim) {
            if (claim.settled) return;
            claim.settled=true;
            claim.take.thenCompose(items -> items.isEmpty() ? CompletableFuture.<Void>completedFuture(null) : storage.addClaims(id,items))
                .whenComplete((unused,error) -> claim.result.completeExceptionally(error == null ? reason : error));
        }
    }
    private void completeClaim(Player p, Claim claim, int count, Throwable error) {
        if (error != null) {
            if (p.isOnline()) messages.send(p,"claim.failed");
            claim.result.completeExceptionally(error);
        } else {
            if (p.isOnline()) messages.send(p,count == 0 ? "claim.empty" : "claim.delivered",Placeholder.unparsed("count",Integer.toString(count)));
            claim.result.complete(count);
        }
    }
    /** Drain destructive takes before Storage.close; scheduled Bukkit callbacks may never run after disable. */
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.LOWEST)
    public void disable(org.bukkit.event.server.PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        closeClaims();
    }
    void closeClaims() {
        closing=true;
        for (var entry : List.copyOf(claiming.entrySet())) {
            Claim claim=entry.getValue();
            try {
                claim.take.handle((items,error) -> null).join();
                synchronized (claim) {
                    if (claim.saved != null) {
                        try { claim.saved.join(); claim.result.complete(claim.count); }
                        catch (RuntimeException error) { claim.result.completeExceptionally(error); }
                    }
                }
                returnClaim(entry.getKey(),claim,new IllegalStateException("Plugin disabled before claim delivery"));
                claim.result.handle((unused,error) -> null).join();
            } catch (RuntimeException error) { logger.log(java.util.logging.Level.WARNING,"Claim shutdown failed",error); }
        }
    }
    private void observe(CompletableFuture<?> operation) {
        operation.exceptionally(error -> { logger.log(java.util.logging.Level.WARNING,"Reward persistence failed",error); return null; });
    }
}
