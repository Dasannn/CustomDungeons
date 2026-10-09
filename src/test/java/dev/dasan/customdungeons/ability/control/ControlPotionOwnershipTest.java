package dev.dasan.customdungeons.ability.control;

import org.bukkit.entity.Player;
import org.bukkit.potion.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlPotionOwnershipTest {
    private static boolean owned(Player player,PotionEffect effect) {
        return ControlService.ownsPotion(player,effect);
    }
    @Test void ownershipUsesTheActiveControlSnapshotAndNaturalAgeOnly() {
        try(var f=new ControlServiceTest.Fixture()) {
            f.start("levitation_cage");
            assertTrue(owned(f.player,f.player.getPotionEffect(PotionEffectType.LEVITATION)));
            assertFalse(owned(f.player,new PotionEffect(PotionEffectType.STRENGTH,200,1)));
            f.step(3);assertTrue(owned(f.player,f.player.getPotionEffect(PotionEffectType.LEVITATION)));
            f.player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,500,3));
            assertFalse(owned(f.player,f.player.getPotionEffect(PotionEffectType.LEVITATION)));
            ControlService.cleanup(f.caster);assertFalse(owned(f.player,new PotionEffect(PotionEffectType.LEVITATION,80,0)));
        }
    }
}
