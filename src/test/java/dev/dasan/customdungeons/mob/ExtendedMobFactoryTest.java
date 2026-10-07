package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.SessionContext;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExtendedMobFactoryTest {
    @Test void allSupportedOptionalAttributesApplyIncludingZerosAndNegativeGravity() {
        PaperApiTestBootstrap.initialize();var mob=mock(Mob.class);
        when(mob.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        String[] keys={"damage","speed","knockback-resistance","scale","armor","armor-toughness","follow-range",
                "attack-knockback","jump-strength","gravity","step-height","explosion-knockback-resistance"};
        Attribute[] attributes={Attribute.ATTACK_DAMAGE,Attribute.MOVEMENT_SPEED,Attribute.KNOCKBACK_RESISTANCE,Attribute.SCALE,
                Attribute.ARMOR,Attribute.ARMOR_TOUGHNESS,Attribute.FOLLOW_RANGE,Attribute.ATTACK_KNOCKBACK,
                Attribute.JUMP_STRENGTH,Attribute.GRAVITY,Attribute.STEP_HEIGHT,Attribute.EXPLOSION_KNOCKBACK_RESISTANCE};
        var values=new HashMap<String,Double>();var instances=new ArrayList<AttributeInstance>();
        for(int i=0;i<keys.length;i++) {
            values.put(keys[i],keys[i].equals("gravity")?-1d:0d);
            var instance=mock(AttributeInstance.class);instances.add(instance);when(mob.getAttribute(attributes[i])).thenReturn(instance);
        }
        new MobFactory(mock(PluginConfig.class)).applyAttributes(mob,new MobAttributes(values));
        for(int i=0;i<keys.length;i++) verify(instances.get(i)).setBaseValue(values.get(keys[i]));
        assertDoesNotThrow(()->new MobFactory(mock(PluginConfig.class)).applyAttributes(mock(Mob.class),new MobAttributes(Map.of("jump-strength",2d))));
    }
    @Test void factoryCapsPhysicalHealthAndAttackAndMarksVirtualValues() {
        PaperApiTestBootstrap.initialize();
        var entity=mock(Zombie.class);var health=mock(AttributeInstance.class);var damage=mock(AttributeInstance.class);
        var pdc=mock(PersistentDataContainer.class);when(entity.getPersistentDataContainer()).thenReturn(pdc);
        when(entity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);when(health.getValue()).thenReturn(1024d);
        when(entity.getAttribute(Attribute.ATTACK_DAMAGE)).thenReturn(damage);
        var session=mock(SessionContext.class);when(session.id()).thenReturn(UUID.randomUUID());
        var world=mock(World.class);
        doAnswer(c->{((java.util.function.Consumer<Zombie>)c.getArgument(4)).accept(entity);return entity;})
            .when(world).spawn(any(Location.class),eq(Zombie.class),eq(SpawnReason.CUSTOM),eq(false),any());
        var template=new MobTemplate("boss","ZOMBIE","",1000,3000,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        new MobFactory(mock(PluginConfig.class)).spawn(template,new Location(world,0,64,0),session,5);
        verify(health).setBaseValue(1024);verify(damage).setBaseValue(2048);
        verify(pdc).set(new NamespacedKey("customdungeons","virtual_max_health"),PersistentDataType.DOUBLE,5000d);
        verify(pdc).set(new NamespacedKey("customdungeons","virtual_attack_damage"),PersistentDataType.DOUBLE,3000d);
    }
}
