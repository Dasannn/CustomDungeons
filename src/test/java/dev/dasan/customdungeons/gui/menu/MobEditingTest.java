package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.model.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MobEditingTest {
    @Test void messageKeysAreUnique() throws Exception {
        String yaml;
        try (var stream=MobEditingTest.class.getResourceAsStream("/messages.yml")) {
            yaml=new String(java.util.Objects.requireNonNull(stream).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        var parents=new java.util.ArrayList<String>(); var keys=new java.util.HashSet<String>();
        for(String line:yaml.split("\\R")) {
            if(line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int level=(line.length()-line.stripLeading().length())/2;
            String key=line.strip().split(":",2)[0];
            while(parents.size()>level) parents.removeLast();
            String path=String.join(".",parents)+"."+key;
            assertTrue(keys.add(path),"Duplicate message key: "+path);
            if(line.stripTrailing().endsWith(":")) parents.add(key);
        }
    }

    @Test void draftRoundTripPreservesAllFieldsAndEditsAreIsolated() {
        var phase = new PhaseDef(.66, false, List.of(), List.of(), Map.of(), List.of(), 5,
                List.of(new WaveEntry("minion", 2, 20)), "title", "sub", "sound", "music", 20);
        var mob = new MobTemplate("boss", "WARDEN", "Boss", 500, 20, .3, .5, 2,
                Map.of(), List.of(new PotionDef("minecraft:speed", 1, false)), List.of(), List.of(), true,
                "RED", "minecraft:music_disc.cat", List.of(phase), true);
        var draft = new MobMenu.MobDraft(mob);
        assertEquals(mob, draft.snapshot());
        draft.health = 100; draft.phases.clear();
        assertEquals(500, mob.maxHealth()); assertEquals(1, mob.phases().size());
        assertEquals(100, draft.snapshot().maxHealth());
    }
    @Test void combosRejectOutsideTwoToFiveAndPreserveOrder() {
        var step = new ComboStep("lightning", Map.of("damage", 2), 10);
        assertFalse(ComboMenu.validSteps(List.of(step)));
        assertTrue(ComboMenu.validSteps(List.of(step, step)));
        assertTrue(ComboMenu.validSteps(java.util.Collections.nCopies(5, step)));
        assertFalse(ComboMenu.validSteps(java.util.Collections.nCopies(6, step)));
    }
}
