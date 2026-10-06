package dev.dasan.customdungeons.gui.wizard;

import dev.dasan.customdungeons.text.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

/** Owns only its scoreboard and BossBar, restores the previous board on all exit paths. */
public final class WizardProgress implements AutoCloseable {
    private final Player player;
    private final Messages messages;
    private final Scoreboard previous;
    private final Scoreboard board;
    private final Objective objective;
    private final BossBar bar;
    private boolean closed;
    public WizardProgress(Player player,Messages messages,ScoreboardManager manager) {
        this.player=player;this.messages=messages;previous=player.getScoreboard();board=manager.getNewScoreboard();
        objective=board.registerNewObjective("cd_wizard",Criteria.DUMMY,messages.get("wizard.scoreboard"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);bar=BossBar.bossBar(Component.empty(),0,BossBar.Color.GREEN,BossBar.Overlay.PROGRESS);
        player.setScoreboard(board);player.showBossBar(bar);
    }
    public void update(WizardState state) {
        if(closed) return;
        for(int i=0;i<7;i++) {
            // Stable invisible entries allow display names to change without duplicating scores.
            var score=objective.getScore("§"+i);
            score.customName(messages.get("wizard.progress-line",Placeholder.component("state",messages.get(
                    i==state.step()?"wizard.current-mark":i<state.completed()?"wizard.done-mark":"wizard.locked-mark")),
                    Placeholder.component("name",messages.get("wizard.step-"+i))));score.setScore(7-i);
        }
        bar.name(messages.get("wizard.bossbar",Placeholder.unparsed("step",Integer.toString(state.step()+1)),
                Placeholder.component("name",messages.get("wizard.step-"+state.step()))));bar.progress((state.step()+1)/7f);
    }
    @Override public void close() {
        if(closed) return;closed=true;player.hideBossBar(bar);
        if(player.getScoreboard()==board) player.setScoreboard(previous);
        objective.unregister();
    }
}
