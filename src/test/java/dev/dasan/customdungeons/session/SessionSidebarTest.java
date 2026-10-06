package dev.dasan.customdungeons.session;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionSidebarTest {
    static {
        try(var bukkit=mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(()->org.bukkit.Bukkit.getScoreboardCriteria(anyString())).thenAnswer(call->mock(Criteria.class));
            assertNotNull(Criteria.DUMMY);
        }
    }
    final Player player=mock(Player.class);
    final Scoreboard old=mock(Scoreboard.class), own=mock(Scoreboard.class);
    final Objective objective=mock(Objective.class);
    final Map<String,Score> scores=new HashMap<>();
    final SessionSidebar sidebar=new SessionSidebar(()->own,20,p->{});
    SessionSidebarTest() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getScoreboard()).thenReturn(old);
        when(own.registerNewObjective(eq("cd-session"),eq(Criteria.DUMMY),any(Component.class))).thenReturn(objective);
        when(objective.getScore(anyString())).thenAnswer(i->scores.computeIfAbsent(i.getArgument(0),k->mock(Score.class)));
        doAnswer(i->{when(player.getScoreboard()).thenReturn(i.getArgument(0));return null;}).when(player).setScoreboard(any());
    }
    ScoreboardTemplates.Frame frame(String... rows) {return new ScoreboardTemplates.Frame(Component.text("Title"),Arrays.stream(rows).<Component>map(Component::text).toList());}
    @Test void restoresPreviousBoardAndFreesReferencesOnEveryRemoval() {
        sidebar.join(player,frame("row"),0);assertSame(own,player.getScoreboard());
        sidebar.remove(player.getUniqueId());assertSame(old,player.getScoreboard());assertEquals(0,sidebar.size());
        sidebar.remove(player.getUniqueId());verify(player,times(2)).setScoreboard(any());
    }
    @Test void shutdownRestoresAndForeignReplacementIsNeverOverwritten() {
        sidebar.join(player,frame("row"),0);var foreign=mock(Scoreboard.class);when(player.getScoreboard()).thenReturn(foreign);
        sidebar.refresh(20,p->frame("changed"));sidebar.refresh(40,p->frame("changed again"));
        sidebar.clear();assertSame(foreign,player.getScoreboard());verify(player,times(1)).setScoreboard(any());
        sidebar.join(player,frame("row"),60);sidebar.clear();assertSame(foreign,player.getScoreboard());
    }
    @Test void cappedRefreshAndUnchangedContentSendNoScoreboardOperations() {
        var same=frame("row","","row");sidebar.join(player,same,0);assertEquals(3,scores.size());
        clearInvocations(objective,own,player);scores.values().forEach(s->clearInvocations(s));
        var renders=new AtomicInteger();
        for(int tick=1;tick<=40;tick++)sidebar.refresh(tick,p->{renders.incrementAndGet();return same;});
        assertEquals(2,renders.get());verifyNoInteractions(objective,own);scores.values().forEach(s->verifyNoInteractions(s));
        verify(player,never()).setScoreboard(any());
    }
    @Test void publishesOnlyChangedRowAndResetsRemovedRows() {
        sidebar.join(player,frame("a","b"),0);scores.values().forEach(s->clearInvocations(s));clearInvocations(objective,own);
        sidebar.refresh(20,p->frame("changed","b"));
        verify(scores.get("cd-row-0")).customName(Component.text("changed"));verifyNoInteractions(scores.get("cd-row-1"));
        sidebar.refresh(40,p->frame("changed"));verify(scores.get("cd-row-1")).resetScore();
        verify(objective,never()).displayName(any());
    }
}
