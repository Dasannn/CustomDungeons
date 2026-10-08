package dev.dasan.customdungeons.config;

import dev.dasan.customdungeons.model.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossCodecTest {
    @Test void sectionIsAdditiveAndOldYamlRemainsIdentical() throws Exception {
        var codec=new DefinitionCodec();var old=DefinitionCodecTest.mob();
        var encoded=codec.encode(old);
        assertFalse(encoded.containsKey("world-boss"));
        var round=codec.decodeMob(old.id(),DefinitionCodecTest.yaml(encoded));
        assertNull(round.worldBoss());assertEquals(encoded,codec.encode(round));
        var boss=old.withWorldBoss(new WorldBossDef("world",-2000,2000,-2000,2000,1,48,5,
                new RewardDef(List.of(),250,500,List.of("give {player} emerald 2"))));
        assertEquals(boss,codec.decodeMob(boss.id(),DefinitionCodecTest.yaml(codec.encode(boss))));
    }
    @Test void missingOptionalFieldsUseApprovedDefaults() throws Exception {
        var y=DefinitionCodecTest.yaml(new DefinitionCodec().encode(DefinitionCodecTest.mob()));
        y.set("world-boss",Map.of("world","world","x-min",-10,"x-max",10,"z-min",-10,"z-max",10));
        var boss=new DefinitionCodec().decodeMob("boss",y).worldBoss();
        assertEquals(1,boss.maxAlive());assertEquals(48,boss.radius());assertEquals(5,boss.minimumDamage());
    }
    @Test void zoneValidationRejectsEqualReversedMissingWorldAndBadBounds() {
        var v=new Validator();var reward=new RewardDef(List.of(),0,0,List.of());
        var valid=new WorldBossDef("world",-10,10,-10,10,1,48,5,reward);
        assertTrue(v.validateWorldBoss(valid,Set.of("world")).isEmpty());
        assertTrue(v.validateWorldBoss(valid,Set.of()).stream().anyMatch(e->e.path().equals("world-boss.world")));
        assertTrue(v.validateWorldBoss(new WorldBossDef("world",10,10,20,10,17,0,0,reward),Set.of("world")).size()>=5);
        assertFalse(v.validateWorldBoss(new WorldBossDef("world",-30000000,10,-10,10,1,48,5,reward),Set.of("world")).isEmpty());
    }
}
