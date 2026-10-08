package dev.dasan.customdungeons.listener;

import dev.dasan.customdungeons.ability.Effects;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.mob.MobHost;
import java.util.*;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbilityProtectionListenerTest {
    @Test void markedPotionHittingParticipantExcludesOutsidersAndMobsFromSplash() throws Exception {
        var caster = mock(ActiveMob.class);
        var mob = mock(Mob.class);
        var session = mock(MobHost.class);
        var potion = mock(ThrownPotion.class);
        var pdc = mock(PersistentDataContainer.class);
        var participant = mock(Player.class);
        var outsider = mock(Player.class);
        var foreignMob = mock(Mob.class);
        when(caster.entity()).thenReturn(mob);
        when(caster.session()).thenReturn(session);
        when(session.id()).thenReturn(UUID.randomUUID());
        when(session.players()).thenReturn(List.of(participant));
        when(participant.isOnline()).thenReturn(true);
        when(participant.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(potion.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.has(Effects.PROJECTILE_KEY, PersistentDataType.BYTE)).thenReturn(true);
        var velocity = new Vector(1, 0, 0);
        when(mob.launchProjectile(ThrownPotion.class, velocity)).thenReturn(potion);
        Effects.launch(caster, ThrownPotion.class, velocity);
        var event = new PotionSplashEvent(potion, participant, null, null,
                new HashMap<>(Map.of(participant, 0.75, outsider, 0.5, foreignMob, 1.0)));

        dispatchSplash(event);

        assertAll(
                () -> assertEquals(0, event.getIntensity(outsider)),
                () -> assertEquals(0, event.getIntensity(foreignMob)),
                () -> assertEquals(0.75, event.getIntensity(participant)),
                () -> assertFalse(event.isCancelled()));
    }
    @Test void unmarkedPotionRetainsVanillaSplash() throws Exception {
        var potion = mock(ThrownPotion.class);
        when(potion.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        var outsider = mock(Player.class);
        var event = new PotionSplashEvent(potion, outsider, null, null,
                new HashMap<>(Map.of(outsider, 0.5)));
        dispatchSplash(event);
        assertEquals(0.5, event.getIntensity(outsider));
        assertFalse(event.isCancelled());
    }
    // Dispatch annotated splash handlers without requiring a running Bukkit server.
    private void dispatchSplash(PotionSplashEvent event) throws Exception {
        var listener = new AbilityProtectionListener();
        for (var method : AbilityProtectionListener.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == PotionSplashEvent.class) method.invoke(listener, event);
        }
    }
}
