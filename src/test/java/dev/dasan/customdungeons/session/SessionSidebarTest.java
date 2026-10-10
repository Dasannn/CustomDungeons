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
    final Map<String,Score> scores=new LinkedHashMap<>();
    final Map<String,Team> teams=new HashMap<>();
    final SessionSidebar sidebar=new SessionSidebar(()->own,20,p->{});
    SessionSidebarTest() {
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getScoreboard()).thenReturn(old);
        when(own.registerNewObjective(eq("cd-session"),eq(Criteria.DUMMY),any(Component.class))).thenReturn(objective);
        when(objective.getScore(anyString())).thenAnswer(i->scores.computeIfAbsent(i.getArgument(0),k->mock(Score.class)));
        when(own.registerNewTeam(anyString())).thenAnswer(i->teams.computeIfAbsent(i.getArgument(0),k->mock(Team.class)));
        doAnswer(i->{when(player.getScoreboard()).thenReturn(i.getArgument(0));return null;}).when(player).setScoreboard(any());
    }
    ScoreboardTemplates.Frame frame(String... rows) {return new ScoreboardTemplates.Frame(Component.text("Title"),Arrays.stream(rows).<Component>map(Component::text).toList());}
    Score row(int index){return List.copyOf(scores.values()).get(index);}
    @Test void privateRowsKeepTheirFullComponentsAndUseInvisibleDistinctEntries() {
        var text=Component.text("Fila",net.kyori.adventure.text.format.NamedTextColor.GOLD);
        sidebar.join(player,new ScoreboardTemplates.Frame(Component.text("Title"),List.of(text,text)),0);
        verify(own).registerNewTeam("cd-row-0");verify(teams.get("cd-row-0")).prefix(text);
        assertEquals(2,scores.size());
        var serializer=net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection();
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for(var entry:scores.keySet())assertEquals("",plain.serialize(serializer.deserialize(entry)),"Internal row identities must never become visible");
        scores.values().forEach(s->verify(s,never()).customName(any()));
    }
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
        teams.values().forEach(t->clearInvocations(t));
        var renders=new AtomicInteger();
        for(int tick=1;tick<=40;tick++)sidebar.refresh(tick,p->{renders.incrementAndGet();return same;});
        assertEquals(2,renders.get());verifyNoInteractions(objective,own);scores.values().forEach(s->verifyNoInteractions(s));
        teams.values().forEach(t->verifyNoInteractions(t));
        verify(player,never()).setScoreboard(any());
    }
    @Test void publishesOnlyChangedRowAndResetsRemovedRows() {
        sidebar.join(player,frame("a","b"),0);scores.values().forEach(s->clearInvocations(s));clearInvocations(objective,own);
        teams.values().forEach(t->clearInvocations(t));
        sidebar.refresh(20,p->frame("changed","b"));
        verify(teams.get("cd-row-0")).prefix(Component.text("changed"));verifyNoInteractions(row(1),teams.get("cd-row-1"));
        sidebar.refresh(40,p->frame("changed"));verify(row(1)).resetScore();
        verify(objective,never()).displayName(any());
    }
    @Test void beforeRefreshDeadlineDoesNotReadBoardsOrCaptureAnyInputs() {
        sidebar.join(player,frame("row"),0);clearInvocations(player);
        var captures=new AtomicInteger();
        for(int tick=1;tick<20;tick++)sidebar.refresh(tick,p->{captures.incrementAndGet();return frame("row");});
        assertEquals(0,captures.get());verifyNoInteractions(player);
    }
    @Test void rawInputsAreCapturedAtIntervalButComponentsOnlyBuildAfterActualChange() {
        var captures=new AtomicInteger();var renders=new AtomicInteger();
        java.util.function.Function<String,ScoreboardTemplates.Frame> render=input->{renders.incrementAndGet();return frame(input);};
        sidebar.join(player,0,()->"unchanged",render);
        for(int tick=1;tick<=40;tick++)sidebar.refresh(tick,p->{captures.incrementAndGet();return "unchanged";},render);
        assertEquals(2,captures.get());assertEquals(1,renders.get());
        sidebar.refresh(60,p->"changed",render);assertEquals(2,renders.get());
    }
    @Test void scoreboardApiAndRenderFailuresNeverEscapeLifecycleAndRestoreOwnership() {
        var errors=new AtomicInteger();var safe=new SessionSidebar(()->own,20,p->{},e->errors.incrementAndGet());
        assertDoesNotThrow(()->safe.join(player,0,()->{throw new IllegalStateException("capture");},input->frame("x")));
        assertEquals(0,safe.size());assertEquals(1,errors.get());
        safe.join(player,0,()->"raw",input->frame("x"));
        assertDoesNotThrow(()->safe.refresh(20,p->"change",input->{throw new IllegalStateException("render");}));
        assertSame(old,player.getScoreboard());assertEquals(0,safe.size());
        doThrow(new IllegalStateException("restore")).when(player).setScoreboard(old);
        safe.join(player,frame("x"),40);assertDoesNotThrow(safe::clear);assertEquals(0,safe.size());
    }

    @Test void evenUnvalidatedFramesUseOnlyFifteenScoresAndWarnOnce() {
        var errors=new AtomicInteger();var safe=new SessionSidebar(()->own,20,p->{},e->errors.incrementAndGet());
        var tooMany=Collections.<Component>nCopies(20,Component.text("row"));
        safe.join(player,new ScoreboardTemplates.Frame(Component.text("title"),tooMany),0);
        assertEquals(15,scores.size());assertEquals(1,errors.get());
        safe.refresh(20,p->new ScoreboardTemplates.Frame(Component.text("changed"),tooMany));assertEquals(1,errors.get());
        assertSame(own,player.getScoreboard());
    }
    @Test void throwingConflictAndDiagnosticConsumersCannotEscapeRefresh() {
        var safe=new SessionSidebar(()->own,20,p->{throw new IllegalStateException("conflict hook");},e->{throw new IllegalStateException("diagnostic");});
        safe.join(player,frame("x"),0);var foreign=mock(Scoreboard.class);when(player.getScoreboard()).thenReturn(foreign);
        assertDoesNotThrow(()->safe.refresh(20,p->frame("changed")));assertSame(foreign,player.getScoreboard());assertEquals(0,safe.size());
    }

    @Test void equalPlayerInputsShareRenderingOnlyWithinTheCurrentRefresh() {
        var second=mock(Player.class);when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getScoreboard()).thenReturn(old);
        doAnswer(i->{when(second.getScoreboard()).thenReturn(i.getArgument(0));return null;}).when(second).setScoreboard(any());
        sidebar.join(player,0,()->"initial",this::frame);
        sidebar.join(second,0,()->"initial",this::frame);
        var renders=new AtomicInteger();
        java.util.function.Function<String,ScoreboardTemplates.Frame> render=s->{renders.incrementAndGet();return frame(s);};
        sidebar.refresh(20,p->"common",render);assertEquals(1,renders.get());
        sidebar.refresh(40,p->p==player?"personal":"different",render);assertEquals(3,renders.get());
        sidebar.refresh(60,p->"common",render);assertEquals(4,renders.get());
    }

    @Test void nativeRowsReceiveCompactComponentsWithTheSameAppearance() {
        var color=net.kyori.adventure.text.format.NamedTextColor.GOLD;
        var row=Component.empty().color(color).append(Component.text("A")).append(Component.text("B"));
        sidebar.join(player,new ScoreboardTemplates.Frame(Component.text("Title"),List.of(row)),0);
        var capture=org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(teams.get("cd-row-0")).prefix(capture.capture());
        assertEquals(row.compact(),capture.getValue());
        assertEquals("AB",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(capture.getValue()));
        assertEquals(color,capture.getValue().color());
    }

}
