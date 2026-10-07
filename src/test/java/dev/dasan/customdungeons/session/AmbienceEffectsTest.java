package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.persistence.*;
import org.bukkit.potion.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AmbienceEffectsTest {
    @BeforeAll static void registry(){PaperApiTestBootstrap.initialize();}
    private final Player player=mock(Player.class);
    private final PersistentDataContainer data=mock(PersistentDataContainer.class);
    private final Map<PotionEffectType,PotionEffect> current=new HashMap<>();
    private String journal;
    private AmbienceEffects effects;
    @BeforeEach void setup() {
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class),eq(PersistentDataType.STRING))).thenAnswer(c->journal);
        doAnswer(c->{journal=c.getArgument(2);return null;}).when(data).set(any(NamespacedKey.class),eq(PersistentDataType.STRING),anyString());
        doAnswer(c->{journal=null;return null;}).when(data).remove(any(NamespacedKey.class));
        when(player.getPotionEffect(any())).thenAnswer(c->current.get(c.getArgument(0)));
        when(player.addPotionEffect(any())).thenAnswer(c->{PotionEffect e=c.getArgument(0);current.put(e.getType(),e);return true;});
        doAnswer(c->{current.remove(c.getArgument(0));return null;}).when(player).removePotionEffect(any());
        effects=new AmbienceEffects(player);
    }
    static PotionEffect lease(){return new PotionEffect(PotionEffectType.DARKNESS,30,0,true,false,false);}
    @Test void ownLeaseRenewsAndIsRemovedWithoutTouchingPlayersEffect() {
        var own=lease();var external=new PotionEffect(PotionEffectType.SPEED,100,3,false,true,true);
        current.put(external.getType(),external);
        effects.apply(external.withDuration(30));effects.apply(own);
        assertSame(external,current.get(external.getType()));assertNotNull(journal);
        current.put(own.getType(),own.withDuration(20));effects.apply(own);
        effects.clear();assertEquals(Map.of(external.getType(),external),current);assertNull(journal);
    }
    @Test void identicalExternalReplacementRevokesOwnership() {
        var own=lease();effects.apply(own);
        var event=mock(EntityPotionEffectEvent.class);when(event.getOldEffect()).thenReturn(own);when(event.getNewEffect()).thenReturn(own);
        effects.changed(event);effects.clear();
        assertEquals(own,current.get(own.getType()));assertNull(journal);
    }
    @Test void crashRecoveryRemovesOnlyPersistedLeaseAndDropsJournal() {
        var own=lease();effects.apply(own);
        current.put(PotionEffectType.SPEED,new PotionEffect(PotionEffectType.SPEED,500,2));
        AmbienceEffects.recover(player);
        assertFalse(current.containsKey(own.getType()));assertTrue(current.containsKey(PotionEffectType.SPEED));assertNull(journal);
    }
    @Test void crashRecoveryPreservesReplacementWithDifferentDurationOrFlags() {
        var own=lease();effects.apply(own);
        var foreign=own.withDuration(600);current.put(own.getType(),foreign);
        AmbienceEffects.recover(player);assertSame(foreign,current.get(own.getType()));assertNull(journal);
    }
    @Test void cancelledAddDoesNotLeaveJournalOrRemoveEffect() {
        doReturn(false).when(player).addPotionEffect(any());
        effects.apply(lease());assertNull(journal);effects.clear();verify(player,never()).removePotionEffect(any());
    }
    @Test void corruptJournalIsDiscardedWithoutRemovingAnything() {
        journal="bad;garbage,99,2147483647,true;minecraft:darkness,0,-1,false";
        current.put(PotionEffectType.DARKNESS,lease());
        AmbienceEffects.recover(player);assertEquals(1,current.size());assertNull(journal);
    }
}
