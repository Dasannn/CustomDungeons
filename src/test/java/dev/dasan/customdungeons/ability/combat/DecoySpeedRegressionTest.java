package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.mob.MobFactory;
import dev.dasan.customdungeons.model.MobAttributes;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecoySpeedRegressionTest {
    static { dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize(); }

    private static final class Speed {
        final AttributeInstance attribute=mock(AttributeInstance.class);
        final Map<NamespacedKey,AttributeModifier> modifiers=new HashMap<>();
        Speed() {
            when(attribute.getBaseValue()).thenReturn(.23);
            when(attribute.getModifier(any(NamespacedKey.class))).thenAnswer(i->modifiers.get(i.getArgument(0)));
            doAnswer(i->{AttributeModifier m=i.getArgument(0);modifiers.put(m.getKey(),m);return null;}).when(attribute).addTransientModifier(any());
            doAnswer(i->{modifiers.remove(i.getArgument(0));return null;}).when(attribute).removeModifier(any(NamespacedKey.class));
            when(attribute.getValue()).thenAnswer(i->attribute.getBaseValue()+modifiers.values().stream().mapToDouble(AttributeModifier::getAmount).sum());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"expire","retire","reload","disable","death","removed","invalid","loaded","recover","failed-spawn"})
    void copiedEntitiesRecoverOwnerSpeedAndRemoveItOnEveryExit(String route) throws Exception {
        try(var f=new CombatServiceTest.Fixture()) {
            var ownerSpeed=new Speed();when(f.entity.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(ownerSpeed.attribute);
            var apply=MobFactory.class.getDeclaredMethod("applyAttributes",Mob.class,MobAttributes.class);apply.setAccessible(true);
            // A current phase overrides the original template's speed of 1.0.
            apply.invoke(new MobFactory(f.platform),f.entity,new MobAttributes(Map.of("speed",.5)));
            var copiedSpeeds=new ArrayList<Speed>();
            doAnswer(i->{
                var copy=mock(Mob.class);var speed=new Speed();copiedSpeeds.add(speed);
                when(copy.getUniqueId()).thenReturn(UUID.randomUUID());when(copy.isValid()).thenReturn(true);
                doReturn(CombatServiceTest.pdc()).when(copy).getPersistentDataContainer();
                when(copy.getAttribute(any())).thenReturn(mock(AttributeInstance.class));
                when(copy.getAttribute(Attribute.MOVEMENT_SPEED)).thenReturn(speed.attribute);
                when(copy.getWorld()).thenReturn(f.world);when(copy.getLocation()).thenReturn(new Location(f.world,3,64,0));
                when(copy.getBoundingBox()).thenReturn(new BoundingBox(2.7,64,-.3,3.3,65.8,.3));
                when(copy.spawnAt(any(),any())).thenAnswer(call->{
                    assertEquals(.5,speed.attribute.getValue(),1e-9,"Entity.copy() loses transient modifiers; restore before spawning");
                    return !route.equals("failed-spawn");
                });
                f.created.add(copy);return copy;
            }).when(f.entity).copy();
            try {
                f.cast("decoys");f.step(20);
                assertEquals(route.equals("failed-spawn")?1:2,f.created.size());
                if(!route.equals("failed-spawn")) {
                    for(var speed:copiedSpeeds)assertEquals(.5,speed.attribute.getValue(),1e-9);
                    for(var speed:copiedSpeeds)when(speed.attribute.getBaseValue()).thenReturn(.35);
                    f.step(5);
                    for(var speed:copiedSpeeds)assertEquals(.5,speed.attribute.getValue(),1e-9,"Copies must remain registered when vanilla rewrites their base");
                    switch(route) {
                        case "expire" -> f.step(300);
                        case "retire" -> CombatService.cleanup(f.caster);
                        case "reload" -> CombatService.reset();
                        case "disable" -> f.service.close();
                        case "death" -> {
                            for(Entity copy:f.created) {
                                var event=mock(EntityDeathEvent.class);when(event.getEntity()).thenReturn((Mob)copy);when(event.getDrops()).thenReturn(new ArrayList<>());f.service.death(event);
                            }
                        }
                        case "removed" -> {
                            for(Entity copy:f.created) {
                                var event=mock(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class);when(event.getEntity()).thenReturn(copy);f.service.removed(event);
                            }
                        }
                        case "invalid" -> {for(Entity copy:f.created)when(copy.isValid()).thenReturn(false);f.step(1);}
                        case "loaded" -> {var event=mock(EntitiesLoadEvent.class);when(event.getEntities()).thenReturn(f.created);f.service.loaded(event);}
                        case "recover" -> {var chunk=mock(Chunk.class);when(f.world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});when(chunk.getEntities()).thenReturn(f.created.toArray(Entity[]::new));f.service.recoverLoaded(List.of(f.world));}
                    }
                }
                for(var speed:copiedSpeeds)assertTrue(speed.modifiers.isEmpty(),"The copy's transient speed must be removed on "+route);
                assertEquals(.5,ownerSpeed.attribute.getValue(),1e-9,"Removing copies must preserve their owner's modifier");
            } finally {MobFactory.clearSpeed(f.caster);}
        }
    }
}
