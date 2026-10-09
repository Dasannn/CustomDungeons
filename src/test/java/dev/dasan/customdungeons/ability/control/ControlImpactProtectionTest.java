package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.AbilityContext;
import dev.dasan.customdungeons.ability.ParamValues;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Entity;
import org.bukkit.Location;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A throw passes through protected participants and can still hit the next valid one. */
class ControlImpactProtectionTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    private static void controlInterceptor(ControlServiceTest.Fixture f,String id) {
        // Paper returns fresh location snapshots; control visuals must not move the mock caster.
        when(f.entity.getLocation()).thenAnswer(i->new Location(f.world,0,64,0));
        var ability=f.registry.get(id).orElseThrow();
        ability.execute(new AbilityContext(f.caster,List.of(f.ally),
            new ParamValues(Map.of("ticks",100,"damage",0),ability.params()),f.host,null));
        f.step(20);
        if(id.equals("anchor_spear")) {
            var hit=mock(ProjectileHitEvent.class);when(hit.getEntity()).thenReturn((Trident)f.created.getLast());
            when(hit.getHitEntity()).thenReturn(f.ally);f.service.hit(hit);
        }
        assertEquals(1,f.service.activeCount(),"the interceptor must actually be controlled");
    }
    private static void launchThroughInterceptor(ControlServiceTest.Fixture f) {
        f.start("grab_throw");
        doReturn(f.player.getBoundingBox()).when(f.ally).getBoundingBox();f.step(30);
        verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
        verify(f.ally,never()).setVelocity(any(Vector.class));
    }
    private static void assertNextParticipantGetsImpact(ControlServiceTest.Fixture f) {
        var next=f.player(4);f.players.add(next);
        doReturn(new BoundingBox(5,64,0,5.6,66,.6)).when(f.player).getBoundingBox();f.step(1);
        verify(f.player).damage(6,f.entity);verify(next).damage(6,f.entity);verify(next).setVelocity(any(Vector.class));
        verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
        verify(f.ally,never()).setVelocity(any(Vector.class));
    }
    @ParameterizedTest @ValueSource(strings={"grab_throw","levitation_cage","drain_grab","roots","anchor_spear","soul_chain"})
    void everyActiveControlMakesInterceptorTransparentToThrow(String id) {
        try(var f=new ControlServiceTest.Fixture()) {
            controlInterceptor(f,id);launchThroughInterceptor(f);
            assertEquals(1,f.service.activeCount());assertNextParticipantGetsImpact(f);
            assertEquals(1,f.service.activeCount(),"the existing control is undisturbed");
        }
    }
    @Test void immuneInterceptorIsTransparentAndTheNextParticipantGetsImpact() {
        try(var f=new ControlServiceTest.Fixture()) {
            controlInterceptor(f,"roots");ControlService.releasePlayer(f.ally);
            assertEquals(0,f.service.activeCount());launchThroughInterceptor(f);assertNextParticipantGetsImpact(f);
        }
    }
    @Test void interceptorBecomesHittableExactlySixtyTicksAfterRelease() {
        try(var f=new ControlServiceTest.Fixture()) {
            controlInterceptor(f,"roots");ControlService.releasePlayer(f.ally); // tick 20, immune until 80
            launchThroughInterceptor(f); // tick 70, flight remains active
            f.step(9);verify(f.ally,never()).damage(anyDouble(),any(Entity.class));
            verify(f.ally,never()).setVelocity(any(Vector.class));
            f.step(1);verify(f.player).damage(6,f.entity);verify(f.ally).damage(6,f.entity);
            verify(f.ally).setVelocity(any(Vector.class));
        }
    }
}
