package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.gui.MenuListener;
import dev.dasan.customdungeons.gui.menu.BuildMenu;
import dev.dasan.customdungeons.tool.construction.BuildJournal;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;

/** Main-thread inventory leases. Disk writes finish before the first inventory mutation. */
public final class BuildModeService implements AutoCloseable {
    public static final NamespacedKey RECOVERY=new NamespacedKey("customdungeons","build-inventory");
    private final CustomDungeonsPlugin plugin;
    private final BuildJournal journal;
    private final Executor executor;
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final Map<UUID,UUID> recovering=new HashMap<>();
    private boolean closed;
    private static final class Session {
        final Player player;final BuildMenu menu;final UUID token=UUID.randomUUID();
        final ItemStack[] contents;final ItemStack cursor;final int held;
        boolean active;
        volatile boolean abandoned;
        Session(Player player,BuildMenu menu) {
            this.player=player;this.menu=menu;
            contents=copy(player.getInventory().getContents());
            cursor=player.getItemOnCursor()==null?null:player.getItemOnCursor().clone();held=player.getInventory().getHeldItemSlot();
        }
    }
    public BuildModeService(CustomDungeonsPlugin plugin,BuildJournal journal) {this(plugin,journal,ForkJoinPool.commonPool());}
    BuildModeService(CustomDungeonsPlugin plugin,BuildJournal journal,Executor executor) {this.plugin=plugin;this.journal=journal;this.executor=executor;}
    public static void register(CustomDungeonsPlugin plugin) {
        var journal=new BuildJournal(plugin.getDataFolder().toPath(),ForkJoinPool.commonPool());
        var service=new BuildModeService(plugin,journal);
        plugin.getServer().getServicesManager().register(BuildModeService.class,service,plugin,ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(new BuildModeListener(plugin,service),plugin);
        for(var player:plugin.getServer().getOnlinePlayers()) service.recover(player);
    }
    public BuildJournal journal() {return journal;}
    public boolean protects(UUID player) {return sessions.containsKey(player)||recovering.containsKey(player);}
    public BuildMenu menu(UUID player) {var session=sessions.get(player);return session==null?null:session.menu;}
    public boolean active(UUID player,BuildMenu menu) {var session=sessions.get(player);return session!=null&&session.active&&session.menu==menu&&!closed;}
    public void enter(Player player,String id) {
        if(closed) return;
        var current=sessions.get(player.getUniqueId());
        if(current!=null) {
            if(current.active&&current.menu.definition().id().equals(id)) current.menu.open();
            else plugin.messages().send(player,"build.exit-first");return;
        }
        if(recovering.containsKey(player.getUniqueId())) {plugin.messages().send(player,"build.preparing");return;}
        if(!player.hasPermission("customdungeons.admin.edit")) {plugin.messages().send(player,"command.no-permission");return;}
        if(MenuListener.instance().rejectReload(player)) return;
        // Carried cursor items are not persisted in vanilla player-data. Require the admin
        // to store them before entry, rather than risking a drop with a full inventory.
        var carried=player.getItemOnCursor();
        if(carried!=null&&!carried.getType().isAir()) {plugin.messages().send(player,"build.cursor-occupied");return;}
        if(plugin.sessionManager().sessionOf(player.getUniqueId()).isPresent()) {plugin.messages().send(player,"build.session-busy");return;}
        BuildMenu menu=BuildMenu.prepare(player,id,this);
        if(menu==null) return;
        // Snapshot the cursor too. Keep the view open until the backup is durable, so a full
        // inventory cannot drop its carried item during a premature server-side close.
        var session=new Session(player,menu);sessions.put(player.getUniqueId(),session);
        UUID admin=player.getUniqueId();
        long order=journal.reserveSequence();
        plugin.messages().send(player,"build.preparing");
        var initialDraft=menu.state().snapshot();
        CompletableFuture.supplyAsync(()->new BuildJournal.Inventory(admin,session.token,
                ItemStack.serializeItemsAsBytes(session.contents),ItemStack.serializeItemsAsBytes(new ItemStack[]{session.cursor}),session.held,order),executor)
                .thenCompose(journal::backup).thenCompose(v->session.abandoned?journal.acknowledge(admin,session.token):journal.save(admin,initialDraft))
                .whenComplete((v,failure)->later(()->{
                    if(sessions.get(player.getUniqueId())!=session) {
                        if(failure!=null) journal.acknowledge(admin,session.token);return;
                    }
                    if(failure!=null||!player.isOnline()||!menu.ready()) {
                        sessions.remove(player.getUniqueId());menu.release();
                        if(player.isOnline()) plugin.messages().send(player,failure!=null?"build.backup-failed":"build.cancelled");
                        // No inventory mutation or marker ever occurred for this token.
                        journal.acknowledge(player.getUniqueId(),session.token);return;
                    }
                    player.getPersistentDataContainer().set(RECOVERY,PersistentDataType.STRING,"active:"+session.token);
                    // Escrow is already on disk: prevent vanilla close from reinserting/dropping the cursor.
                    player.setItemOnCursor(null);player.closeInventory();
                    session.active=true;player.getInventory().clear();player.setItemOnCursor(null);player.getInventory().setHeldItemSlot(0);
                    menu.refreshTools();menu.preview();plugin.messages().send(player,"build.entered");
                }));
    }
    public void exit(Player player) {
        var session=sessions.get(player.getUniqueId());if(session==null) return;
        session.abandoned=true;
        player.closeInventory();
        if(session.active) {
            // Keep the backup. A crash before vanilla saves this marker restores the original again.
            restore(player,session.contents,session.cursor,session.held);
            player.getPersistentDataContainer().set(RECOVERY,PersistentDataType.STRING,"restored:"+session.token);
        }
        sessions.remove(player.getUniqueId());session.menu.release();
        if(player.isOnline()) plugin.messages().send(player,"build.exited");
    }
    public void exitAll() {for(var session:List.copyOf(sessions.values())) exit(session.player);}
    public void interact(org.bukkit.event.player.PlayerInteractEvent event) {
        var session=sessions.get(event.getPlayer().getUniqueId());
        if(session!=null&&session.active) session.menu.interact(event);
    }
    /** Vanilla persists the marker and the inventory in the same player-data record. */
    public void recover(Player player) {
        UUID admin=player.getUniqueId();
        String marker=player.getPersistentDataContainer().get(RECOVERY,PersistentDataType.STRING);
        var latest=journal.latestInventory(admin);
        if(marker==null&&latest.isEmpty()) return;
        UUID operation=UUID.randomUUID();recovering.put(admin,operation);
        try {
            BuildJournal.Inventory backup;
            if(marker==null) {
                // The server may have crashed before its first player-data save after entry.
                backup=latest.orElseThrow();
            } else {
                var parts=marker.split(":",2);UUID token=UUID.fromString(parts[1]);
                if(parts[0].equals("restored")&&(latest.isEmpty()||latest.get().token().equals(token))) {
                    // Keep the restored marker until disk acknowledgement finishes, including failures.
                    journal.acknowledgeAll(admin).whenComplete((v,failure)->later(()->{
                        if(!Objects.equals(recovering.get(admin),operation))return;
                        if(failure!=null) {recoveryFailed(player);return;}
                        recovering.remove(admin);
                        if(player.isOnline()&&Objects.equals(marker,player.getPersistentDataContainer().get(RECOVERY,PersistentDataType.STRING)))
                            player.getPersistentDataContainer().remove(RECOVERY);
                    }));return;
                }
                if(!parts[0].equals("active")&&!parts[0].equals("restored")) throw new IllegalArgumentException("Invalid recovery marker");
                var marked=journal.inventory(admin,token);
                if(parts[0].equals("active")&&marked.isEmpty()) throw new IllegalStateException("Missing inventory backup");
                // A new entry may have committed its backup before its marker reached player-data.
                backup=latest.orElseGet(marked::orElseThrow);
            }
            CompletableFuture.supplyAsync(()->new Restored(ItemStack.deserializeItemsFromBytes(backup.contents()),
                    ItemStack.deserializeItemsFromBytes(backup.cursor())[0],backup.held()),executor)
                    .whenComplete((items,failure)->later(()->{
                        if(!Objects.equals(recovering.get(admin),operation))return;
                        if(!player.isOnline()) {recovering.remove(admin);return;}
                        if(failure!=null) {recoveryFailed(player);return;}
                        player.closeInventory();restore(player,items.contents(),items.cursor(),items.held());
                        player.getPersistentDataContainer().set(RECOVERY,PersistentDataType.STRING,"restored:"+backup.token());
                        recovering.remove(admin);plugin.messages().send(player,"build.recovered");
                    }));
        } catch(RuntimeException failure) {recoveryFailed(player);}
    }
    private void recoveryFailed(Player player) {
        // Fail closed: do not let the player overwrite a recovery record or remove the originals.
        recovering.put(player.getUniqueId(),UUID.randomUUID());player.kick(plugin.messages().get("build.recovery-failed"));
    }
    public void disconnected(Player player) {exit(player);recovering.remove(player.getUniqueId());}
    private record Restored(ItemStack[] contents,ItemStack cursor,int held) {}
    private static void restore(Player player,ItemStack[] contents,ItemStack cursor,int held) {
        player.getInventory().setContents(copy(contents));player.setItemOnCursor(cursor==null?null:cursor.clone());
        player.getInventory().setHeldItemSlot(held);
    }
    private static ItemStack[] copy(ItemStack[] contents) {return Arrays.stream(contents).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
    private void later(Runnable action) {
        if(closed||!plugin.isEnabled()) return;
        try {plugin.getServer().getScheduler().runTask(plugin,()->{if(!closed) action.run();});}
        catch(org.bukkit.plugin.IllegalPluginAccessException ignored) { /* Recovery remains on disk. */ }
    }
    @Override public void close() {if(closed)return;exitAll();closed=true;journal.close();}
}
