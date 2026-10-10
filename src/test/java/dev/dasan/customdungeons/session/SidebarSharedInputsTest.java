package dev.dasan.customdungeons.session;

import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SidebarSharedInputsTest {
    @Test void personalLivesAndKillsStayIndependentWhileTheCombatSnapshotIsShared() {
        var common=new SidebarData.Inputs("partida-jefe",Map.of("kills",1,"lives",3,"boss_health",58,"time_left",80L),Set.of("boss_has_phases"),"boss",3,4);
        var session=mock(DungeonSession.class);var player=mock(Player.class);var id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        when(session.sidebarKills(id)).thenReturn(7);when(session.livesLeft(id)).thenReturn(0);
        var personal=SidebarData.forPlayer(common,session,player);
        assertEquals(7,personal.values().get("kills"));assertEquals(0,personal.values().get("lives"));
        assertEquals(58,personal.values().get("boss_health"));assertEquals(80L,personal.values().get("time_left"));
        assertEquals(common.conditions(),personal.conditions());assertEquals(common.state(),personal.state());
        assertEquals(1,common.values().get("kills"));assertEquals(3,common.values().get("lives"));
        verify(session,never()).mobs();verify(session,never()).players();
        var lobby=new SidebarData.Inputs("lobby-automatico",Map.of("kills",1,"alive",5),Set.of(),null,3,4);
        assertFalse(SidebarData.forPlayer(lobby,session,player).values().containsKey("lives"));
    }
}
