package dev.dasan.customdungeons.intelligence;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporaryCooldownsTest {
    @Test void removalRestoresPriorRemainingTimeAndOtherMobsContributions() {
        var now=new AtomicLong();var until=new AtomicLong(100);var p=mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getCooldown(Material.SHIELD)).thenAnswer(c->(int)Math.max(0,until.get()-now.get()));
        doAnswer(c->{until.set(now.get()+(int)c.getArgument(1));return null;}).when(p).setCooldown(eq(Material.SHIELD),anyInt());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(c->(int)now.get());var cooldowns=new IntelligenceService.Cooldowns();var a=new Object();var b=new Object();
            cooldowns.add(p,a,Material.SHIELD,160);cooldowns.add(p,b,Material.SHIELD,200);assertEquals(200,until.get());
            now.set(50);cooldowns.remove(b);assertEquals(160,until.get());cooldowns.remove(a);assertEquals(100,until.get());
            now.set(110);cooldowns.add(p,a,Material.SHIELD,80);cooldowns.remove(a);assertEquals(110,until.get());
        }
    }
    @Test void removalDoesNotOverwriteCooldownInstalledByAnotherPlugin() {
        var now=new AtomicLong();var until=new AtomicLong();var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.getCooldown(Material.GOLDEN_APPLE)).thenAnswer(c->(int)Math.max(0,until.get()-now.get()));
        doAnswer(c->{until.set(now.get()+(int)c.getArgument(1));return null;}).when(p).setCooldown(eq(Material.GOLDEN_APPLE),anyInt());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(c->(int)now.get());var cooldowns=new IntelligenceService.Cooldowns();var a=new Object();cooldowns.add(p,a,Material.GOLDEN_APPLE,160);
            now.set(20);until.set(300);var b=new Object();cooldowns.add(p,b,Material.GOLDEN_APPLE,80);assertEquals(300,until.get());cooldowns.remove(a);cooldowns.remove(b);assertEquals(300,until.get());
        }
    }
    @Test void identicalAdaptationsFromDifferentMobsHaveIndependentOwnership() {
        var until=new AtomicLong();var player=mock(Player.class);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);
        when(player.getCooldown(Material.SHIELD)).thenAnswer(c->(int)until.get());
        doAnswer(c->{until.set((int)c.getArgument(1));return null;}).when(player).setCooldown(eq(Material.SHIELD),anyInt());
        var rule=new IntelligenceRules.Rule("shield","shield",3,false,IntelligenceRules.Response.SHIELD);
        var first=new IntelligenceBrain.Adaptation(rule,id,"",0,1,300);
        var second=new IntelligenceBrain.Adaptation(rule,id,"",0,1,300);assertEquals(first,second);assertNotSame(first,second);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getCurrentTick).thenReturn(0);var cooldowns=new IntelligenceService.Cooldowns();
            cooldowns.add(player,first,Material.SHIELD,160);cooldowns.add(player,second,Material.SHIELD,160);
            cooldowns.remove(first);assertEquals(160,player.getCooldown(Material.SHIELD));
            cooldowns.remove(second);assertEquals(0,player.getCooldown(Material.SHIELD));
        }
    }
}
