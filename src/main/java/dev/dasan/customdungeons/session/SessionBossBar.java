package dev.dasan.customdungeons.session;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
public final class SessionBossBar {
    private final BossBar bar = BossBar.bossBar(net.kyori.adventure.text.Component.empty(),1,BossBar.Color.YELLOW,BossBar.Overlay.PROGRESS);
    private final Set<Player> viewers = new HashSet<>();
    private final Messages messages;
    private String snapshot;
    public SessionBossBar(Messages messages) { this.messages = messages; }
    public void update(DungeonSession session) {
        Set<Player> players = new HashSet<>(session.players());
        for (Player player : List.copyOf(viewers)) if (!players.contains(player)) { player.hideBossBar(bar); viewers.remove(player); }
        String next = session.state().state()+":"+session.roomIndex()+":"+session.mobs().size()+":"+session.waveNumber();
        if (!next.equals(snapshot)) {
            snapshot = next;
            bar.name(messages.get("session.bar",Placeholder.unparsed("room",Integer.toString(session.roomIndex()+1)),
                Placeholder.unparsed("wave",Integer.toString(session.waveNumber())),Placeholder.unparsed("mobs",Integer.toString(session.mobs().size()))));
        }
        for (Player player : players) if (viewers.add(player)) player.showBossBar(bar);
    }
    public void exiting(DungeonSession session,int seconds) {
        Set<Player> current=new HashSet<>(session.players());
        for(Player player:List.copyOf(viewers))if(!current.contains(player)){player.hideBossBar(bar);viewers.remove(player);}
        var text=messages.get("session.exiting",Placeholder.unparsed("time",String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60)));
        bar.name(text).progress(Math.clamp((float)seconds/session.def().exitGraceSeconds(),0,1));
        for(Player player:current){if(viewers.add(player))player.showBossBar(bar);player.sendActionBar(text);}
    }
    public void clear() { for (Player player : viewers) player.hideBossBar(bar); viewers.clear(); }
}
