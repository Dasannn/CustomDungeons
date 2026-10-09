package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.mob.MobKeys;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.UUID;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecoySessionRegressionTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}

    @Test void sessionMarkedDecoyIsNeverARealMobOrWaveMemberOrSidebarKill() {
        var fixture=new DungeonSessionFlowTest();var session=fixture.session(3);
        session.join(fixture.player);session.tick();session.enterRoom(0);
        var entity=mock(Mob.class);var data=mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.has(CombatService.DECOY,PersistentDataType.BYTE)).thenReturn(true);
        when(data.has(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(true);
        when(data.has(MobKeys.TEMPLATE,PersistentDataType.STRING)).thenReturn(true);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.getKiller()).thenReturn(fixture.player);
        var mob=mock(ActiveMob.class);when(mob.entity()).thenReturn(entity);
        session.track(mob,"spawner");
        assertFalse(MobKeys.isDungeonMob(entity));
        assertTrue(session.mobs().isEmpty());assertNull(session.origin(entity.getUniqueId()));
        var death=mock(EntityDeathEvent.class);when(death.getEntity()).thenReturn(entity);
        session.mobRemoved(entity.getUniqueId(),death);assertEquals(0,session.sidebarKills(fixture.p1));
        session.tick();assertFalse(session.roomStarted());
    }

    @Test void sessionListenerDoesNotRouteDecoyEventsAsRealMobEvents() {
        var entity=mock(Mob.class);var data=mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(data);
        when(data.has(CombatService.DECOY,PersistentDataType.BYTE)).thenReturn(true);
        when(data.get(MobKeys.SESSION,PersistentDataType.STRING)).thenReturn(UUID.randomUUID().toString());
        var manager=mock(SessionManager.class);var listener=new SessionListener(manager);
        var death=mock(EntityDeathEvent.class);when(death.getEntity()).thenReturn(entity);listener.mobDeath(death);
        verifyNoInteractions(manager);
    }
}
