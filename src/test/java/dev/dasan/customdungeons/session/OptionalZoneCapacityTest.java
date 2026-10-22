package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.combat.*;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OptionalZoneCapacityTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    static class Fixture implements AutoCloseable {
        final OptionalZoneRuntimeTest base=new OptionalZoneRuntimeTest();
        final DungeonSession session;
        final ActiveMob caster;
        final List<Mob> copies=new ArrayList<>();
        final CombatService combat;
        final MobsPlatform platform=mock(MobsPlatform.class);
        final org.mockito.MockedStatic<SafeTerrain> terrain=mockStatic(SafeTerrain.class);
        final org.mockito.MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        boolean spawn=true;
        Fixture(int cap,boolean waves) {
            var p=base.player(1);when(p.getWorld()).thenReturn(base.world);when(p.isValid()).thenReturn(true);when(p.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(0,64,0,1,66,1));
            var def=base.definition(1,false);
            if(waves) {
                var r=def.rooms().getFirst();var sp=r.spawners().getFirst();var wave=sp.waves().getFirst();
                var room=new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),
                        List.of(new SpawnerDef(sp.id(),sp.location(),sp.radius(),List.of(wave,wave))));
                def=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(def,List.of(room,def.rooms().getLast()));
            }
            session=base.start(def,p);session.maxAlive(cap);base.ticks(session,10);caster=base.zone(session,"a").getFirst();
            when(caster.entity().getWorld()).thenReturn(base.world);when(caster.entity().getLocation()).thenReturn(new Location(base.world,5,64,5));
            when(caster.entity().getWidth()).thenReturn(.6);when(caster.entity().getHeight()).thenReturn(1.8);when(caster.entity().teleport(any(Location.class))).thenReturn(true);
            when(caster.entity().copy()).thenAnswer(c->{
                var copy=mock(Mob.class);when(copy.getUniqueId()).thenReturn(UUID.randomUUID());when(copy.isValid()).thenReturn(true);
                when(copy.getLocation()).thenReturn(new Location(base.world,5,64,5));
                when(copy.getBoundingBox()).thenReturn(new org.bukkit.util.BoundingBox(4,64,4,6,66,6));
                var data=mock(PersistentDataContainer.class);when(copy.getPersistentDataContainer()).thenReturn(data);
                when(data.has(CombatService.DECOY,PersistentDataType.BYTE)).thenReturn(true);
                when(copy.spawnAt(any(Location.class),any())).thenAnswer(i->spawn);copies.add(copy);return copy;
            });
            when(platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(cap,1,48));
            when(platform.messages()).thenReturn(SidebarDataTest.messages());new AbilityEngine(new AbilityRegistry(),platform);combat=new CombatService(platform);
            when(base.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(c->(int)session.scheduler().currentTick());
            terrain.when(()->SafeTerrain.interior(any(),anyInt(),anyInt(),anyInt(),anyDouble(),anyDouble(),any()))
                    .thenAnswer(c->Optional.of(new Location(base.world,(int)c.getArgument(1)+.5,64,(int)c.getArgument(2)+.5)));
            terrain.when(()->SafeTerrain.fitsArea(any(),anyDouble(),anyDouble(),any())).thenCallRealMethod();
            terrain.when(()->SafeTerrain.hasWalkableExit(any(),anyDouble(),anyDouble(),any(),anyDouble())).thenReturn(true);
        }
        void cast() {var a=new DecoysAbility();a.execute(new AbilityContext(caster,List.copyOf(session.players()),new ParamValues(Map.of("count",2),a.params()),caster.session(),null));base.ticks(session,21);}
        void remove(Mob copy) {
            var event=mock(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class);when(event.getEntity()).thenReturn(copy);combat.removed(event);
        }
        @Override public void close() {combat.close();terrain.close();bukkit.close();}
    }
    @Test void filledByThreeOriginsRejectsDecoysBeforeCopyingAnything() {
        try(var f=new Fixture(3,false)) {f.cast();assertTrue(f.copies.isEmpty());assertEquals(3,f.session.mobs().size());}
    }
    @Test void lastSlotIsSharedWithOtherOriginsAndCopiesNeverEnterProgressOrKillCredit() {
        try(var f=new Fixture(4,false)) {
            f.cast();assertEquals(1,f.copies.size());assertEquals(3,f.session.mobs().size());var copy=f.copies.getFirst();
            assertNull(f.session.origin(copy.getUniqueId()));assertEquals(1,f.base.zone(f.session,"a").size());
            var other=f.base.zone(f.session,"b").getFirst();assertNull(other.session().spawnMinion("zombie",other.entity().getLocation(),other));
            var death=mock(org.bukkit.event.entity.EntityDeathEvent.class);when(death.getEntity()).thenReturn(copy);when(death.getDrops()).thenReturn(new ArrayList<>());
            f.combat.death(death);f.remove(copy);f.remove(copy);
            assertEquals(0,f.session.sidebarKillsTotal());
            assertNotNull(other.session().spawnMinion("zombie",other.entity().getLocation(),other));assertEquals(4,f.session.mobs().size());
            assertNull(f.session.reserveMobSlot());
        }
    }
    @Test void queuedWaveWaitsForDecoyLeaseAndResumesAfterCleanup() {
        try(var f=new Fixture(3,true)) {
            f.base.chunks=false;f.base.remove(f.session,f.session.roomMobs());f.cast();assertEquals(1,f.copies.size());
            f.base.chunks=true;f.session.tick();assertTrue(f.session.roomMobs().isEmpty());assertTrue(f.session.roomStarted());assertEquals(0,f.session.roomIndex());
            f.combat.close();f.session.tick();assertEquals(1,f.session.roomMobs().size());assertEquals(3,f.session.mobs().size());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"retire","reload","disable","silent","failed-spawn","exception"})
    void everyRetirementAndFailureReleasesTheGlobalReservation(String reason) {
        try(var f=new Fixture(4,false)) {
            if(reason.equals("failed-spawn"))f.spawn=false;
            if(reason.equals("exception"))when(f.caster.entity().copy()).thenThrow(new IllegalStateException("spawn"));
            if(reason.equals("exception"))assertThrows(IllegalStateException.class,f::cast);else f.cast();
            switch(reason) {
                case "retire" -> CombatService.cleanup(f.caster);
                case "reload" -> CombatService.reset();
                case "disable" -> f.combat.close();
                case "silent" -> {when(f.copies.getFirst().isValid()).thenReturn(false);f.base.ticks(f.session,2);}
            }
            try(var slot=f.session.reserveMobSlot()) {assertNotNull(slot,reason);assertNull(f.session.reserveMobSlot());}
        }
    }
    @Test void tacticalSummonUsesTheSameBudgetAsLeasesHeldByAnotherOrigin() {
        try(var f=new Fixture(4,false);var controls=new dev.dasan.customdungeons.ability.control.ControlService(f.platform)) {
            var other=f.base.zone(f.session,"b").getFirst();var slot=other.session().reserveMobSlot();assertNotNull(slot);
            var ability=new dev.dasan.customdungeons.ability.control.TacticalSummonAbility();
            var ctx=new AbilityContext(f.caster,List.of(),new ParamValues(Map.of("template","zombie","count",5,"radius",0),ability.params()),f.caster.session(),null);
            ability.execute(ctx);f.base.ticks(f.session,21);assertEquals(3,f.session.mobs().size());
            slot.close();ability.execute(ctx);f.base.ticks(f.session,21);
            assertEquals(4,f.session.mobs().size());assertEquals(2,f.base.zone(f.session,"a").size());assertNull(f.session.reserveMobSlot());
        }
    }

}
