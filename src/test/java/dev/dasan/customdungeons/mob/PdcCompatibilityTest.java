package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.session.DungeonSession;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PdcCompatibilityTest {
    static { PaperApiTestBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(classes = {DungeonSession.class, LiveTestService.class})
    void factoryMobsOfBothHostsRetainLegacyKeysAndRecognition(Class<? extends MobHost> type) {
        var host=mock(type);
        UUID id=UUID.randomUUID(); when(host.id()).thenReturn(id);
        var world=mock(World.class);
        var entity=mock(Zombie.class);
        var data=mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(data);
        var strings=new HashMap<NamespacedKey,String>();
        doAnswer(call -> { strings.put(call.getArgument(0),call.getArgument(2)); return null; })
                .when(data).set(any(NamespacedKey.class),eq(PersistentDataType.STRING),anyString());
        when(data.has(any(NamespacedKey.class),eq(PersistentDataType.STRING)))
                .thenAnswer(call -> strings.containsKey(call.getArgument(0)));
        doAnswer(call -> { ((java.util.function.Consumer<Zombie>)call.getArgument(4)).accept(entity); return entity; })
                .when(world).spawn(any(Location.class),eq(Zombie.class),eq(SpawnReason.CUSTOM),eq(false),any());
        var template=new MobTemplate("legacy","ZOMBIE","",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var active=new MobFactory(mock(MobsPlatform.class)).spawn(template,new Location(world,0,64,0),host,1);
        assertSame(host,active.session());
        assertEquals(id.toString(),strings.get(new NamespacedKey("customdungeons","session")));
        assertEquals("legacy",strings.get(new NamespacedKey("customdungeons","template")));
        assertTrue(MobKeys.isDungeonMob(entity));
    }

    @Test void everyMobKeyRetainsTheLegacyNamespace() throws Exception {
        for (var field:MobKeys.class.getFields()) {
            if(field.getType()!=NamespacedKey.class) continue;
            assertEquals("customdungeons",((NamespacedKey)field.get(null)).getNamespace(),field.getName());
        }
        assertEquals(new NamespacedKey("customdungeons","live_test"),MobsPlatform.key("live_test"));
        assertEquals(MobKeys.ABILITY_PROJECTILE,dev.dasan.customdungeons.ability.Effects.PROJECTILE_KEY);
    }
}
