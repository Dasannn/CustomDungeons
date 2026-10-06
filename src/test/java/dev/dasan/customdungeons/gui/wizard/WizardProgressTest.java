package dev.dasan.customdungeons.gui.wizard;
import dev.dasan.customdungeons.text.Messages;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.bossbar.BossBar;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class WizardProgressTest {
    @org.junit.jupiter.api.BeforeAll static void criteria() {
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(()->org.bukkit.Bukkit.getScoreboardCriteria(anyString())).thenAnswer(call->mock(Criteria.class));
            org.junit.jupiter.api.Assertions.assertNotNull(Criteria.DUMMY);
        }
    }
    @Test void leavingOrDisconnectingRestoresThePriorBoardAndHidesBossBarExactlyOnce() {
        var player=mock(Player.class);var previous=mock(Scoreboard.class);var board=mock(Scoreboard.class);
        var manager=mock(ScoreboardManager.class);var objective=mock(Objective.class);var messages=mock(Messages.class);
        when(manager.getNewScoreboard()).thenReturn(board);
        when(player.getScoreboard()).thenReturn(previous,board);
        when(messages.get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class))).thenReturn(net.kyori.adventure.text.Component.empty());
        when(board.registerNewObjective(eq("cd_wizard"),eq(Criteria.DUMMY),any(net.kyori.adventure.text.Component.class))).thenReturn(objective);
        when(objective.getScore(anyString())).thenReturn(mock(Score.class));
        var progress=new WizardProgress(player,messages,manager);progress.update(new WizardState(3,3));progress.close();progress.close();
        verify(player).setScoreboard(board);verify(player).setScoreboard(previous);
        verify(player,times(1)).showBossBar(any(BossBar.class));verify(player,times(1)).hideBossBar(any(BossBar.class));
        verify(objective,times(7)).getScore(anyString());verify(objective).unregister();
    }
    @Test void cleanupDoesNotReplaceABoardInstalledByAnotherPlugin() {
        var player=mock(Player.class);var manager=mock(ScoreboardManager.class);var board=mock(Scoreboard.class);
        var previous=mock(Scoreboard.class);var other=mock(Scoreboard.class);var objective=mock(Objective.class);var messages=mock(Messages.class);
        when(messages.get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class))).thenReturn(net.kyori.adventure.text.Component.empty());
        when(player.getScoreboard()).thenReturn(previous,other);when(manager.getNewScoreboard()).thenReturn(board);
        when(board.registerNewObjective(eq("cd_wizard"),eq(Criteria.DUMMY),any(net.kyori.adventure.text.Component.class))).thenReturn(objective);
        var progress=new WizardProgress(player,messages,manager);progress.close();
        verify(player,never()).setScoreboard(previous);verify(player,never()).setScoreboard(other);
    }
}
