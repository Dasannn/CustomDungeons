package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.mob.MobHost;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MobFactoryScaleTest {
    @Test void scaleZeroNeverTouchesAttributeAndPositiveScalesAreAppliedExactly() {
        PaperApiTestBootstrap.initialize();
        var config=mock(PluginConfig.class);
        var factory=new MobFactory(dev.dasan.customdungeons.mob.TestMobsPlatform.of(config));
        var world=mock(World.class);
        var entity=mock(Zombie.class);
        var attribute=mock(AttributeInstance.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(entity.getAttribute(Attribute.SCALE)).thenReturn(attribute);
        var session=mock(MobHost.class); when(session.id()).thenReturn(UUID.randomUUID());
        doAnswer(call -> {
            java.util.function.Consumer<Zombie> configure=call.getArgument(4);
            configure.accept(entity); return entity;
        }).when(world).spawn(any(Location.class),eq(Zombie.class),eq(SpawnReason.CUSTOM),eq(false),any());
        factory.spawn(template(0),new Location(world,0,64,0),session,1);
        verify(entity,never()).getAttribute(Attribute.SCALE);
        verify(attribute,never()).setBaseValue(anyDouble());
        for(double scale:new double[]{.01,.05,1,7.06,10,10.01,16}) {
            factory.spawn(template(scale),new Location(world,0,64,0),session,1);
            verify(attribute).setBaseValue(scale);
        }
    }
    @Test void invalidScalesAreRejectedBeforeWorldMutation() {
        PaperApiTestBootstrap.initialize();
        var factory=new MobFactory(mock(dev.dasan.customdungeons.mob.MobsPlatform.class));
        var world=mock(World.class);
        for(double scale:new double[]{-.01,16.01,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->factory.spawn(template(scale),new Location(world,0,64,0),mock(MobHost.class),1));
        verifyNoInteractions(world);
    }
    private MobTemplate template(double scale) {
        return new MobTemplate("zombie","ZOMBIE","",0,0,0,0,scale,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
    }
}
