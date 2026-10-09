package dev.dasan.customdungeons.ability;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

/** UUID + deadline, independent of a host. Uses the existing recovery ticker, never teleports. */
public final class FallProtection implements Listener, AutoCloseable {
    private static final FallProtection SHARED=new FallProtection();
    private final Map<UUID,Long> falls=new HashMap<>();
    private Runnable changed=()->{};
    public static FallProtection shared() {return SHARED;}
    public void ticker(Runnable wake) {changed=Objects.requireNonNull(wake);}
    public boolean hasPending() {return !falls.isEmpty();}
    public int size() {return falls.size();}
    public void protect(Player player) {
        falls.put(player.getUniqueId(),Bukkit.getCurrentTick()+200L);
        changed.run();
    }
    public void finish(Player player) {if(falls.remove(player.getUniqueId())!=null)player.setFallDistance(0);}
    public void tick() {
        if(falls.isEmpty())return;
        long now=Bukkit.getCurrentTick();
        for(var entry:List.copyOf(falls.entrySet()))if(now>=entry.getValue()) {
            falls.remove(entry.getKey());var player=Bukkit.getPlayer(entry.getKey());if(player!=null)player.setFallDistance(0);
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void fall(EntityDamageEvent e) {
        if(falls.isEmpty()||e.getCause()!=EntityDamageEvent.DamageCause.FALL||!(e.getEntity() instanceof Player p))return;
        var expires=falls.get(p.getUniqueId());if(expires==null)return;
        if(Bukkit.getCurrentTick()>=expires){finish(p);return;}
        e.setCancelled(true);p.setFallDistance(0);
        if(p.isOnGround())falls.remove(p.getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        if(falls.isEmpty())return;var p=e.getPlayer();var expires=falls.get(p.getUniqueId());if(expires==null)return;
        if(p.isOnGround()||Bukkit.getCurrentTick()>=expires)finish(p);
    }
    @EventHandler public void quit(PlayerQuitEvent e){finish(e.getPlayer());}
    /** Death/world changes end the control, not the UUID landing guard. */
    public void death(PlayerDeathEvent e){}
    public void world(PlayerChangedWorldEvent e){}
    @Override public void close() {
        for(var id:List.copyOf(falls.keySet())){var p=Bukkit.getPlayer(id);if(p!=null)p.setFallDistance(0);}
        falls.clear();changed=()->{};
    }
}
