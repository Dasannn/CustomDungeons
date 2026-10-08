package dev.dasan.customdungeons.boss;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class T55ReviewRegressionTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    WorldEncounterTest fixture;
    @BeforeEach void setup() throws Exception {
        fixture=new WorldEncounterTest();
        fixture.temp=temp;
        fixture.setup();
    }
    @AfterEach void teardown() {fixture.close();}

    @Test void unrelatedVanillaSpellMustNotBecomeBossProperty() {
        var foreign=mock(Mob.class);
        when(foreign.getUniqueId()).thenReturn(UUID.randomUUID());
        when(foreign.getType()).thenReturn(org.bukkit.entity.EntityType.HUSK);
        when(foreign.isValid()).thenReturn(true);
        when(foreign.getWorld()).thenReturn(fixture.world);
        when(foreign.getLocation()).thenReturn(new Location(fixture.world,1,64,0));
        when(foreign.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        var event=mock(CreatureSpawnEvent.class);
        when(event.getEntity()).thenReturn(foreign);
        var location=new Location(fixture.world,1,64,0);
        when(event.getLocation()).thenReturn(location);
        when(event.getSpawnReason()).thenReturn(CreatureSpawnEvent.SpawnReason.SPELL);
        fixture.service.spawned(event);
        assertNull(fixture.service.owner(foreign), "An unrelated vanilla caster's summon was adopted");
    }

    @Test void bossTickMustNotEnumerateAllWorldPlayersRepeatedly() {
        var far=mock(org.bukkit.entity.Player.class);
        when(fixture.world.getPlayers()).thenReturn(java.util.Collections.nCopies(10_000,far));
        clearInvocations(fixture.world);
        fixture.encounter.tick();
        verify(fixture.world,never()).getPlayers();
        fixture.encounter.players();fixture.encounter.audience(fixture.mob.getLocation());fixture.encounter.bosses.tickMusic(fixture.encounter.clock.currentTick());
        verify(fixture.world,times(1)).getNearbyPlayers(any(Location.class),eq(48d));
        verifyNoInteractions(far);
        fixture.encounter.tick();
        verify(fixture.world,times(2)).getNearbyPlayers(any(Location.class),eq(48d));
    }

    @Test void listMustKeepLivingEncounterAfterItsTemplateWasDeleted() {
        when(fixture.platform.templates()).thenReturn(Map.of());
        var messages=mock(dev.dasan.customdungeons.text.Messages.class);
        when(messages.get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class))).thenReturn(net.kyori.adventure.text.Component.empty());
        when(fixture.platform.messages()).thenReturn(messages);
        var sender=mock(org.bukkit.command.CommandSender.class);
        assertEquals(1,fixture.service.alive("boss"));
        assertTrue(fixture.service.orphaned("boss"));
        assertEquals(fixture.template,fixture.service.listed().get("boss"));
        fixture.service.list(sender);
        verify(messages).send(eq(sender),eq("gui.world-boss.command-list-line"),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        verify(messages).get(eq("gui.world-boss.orphan-marker"),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        assertEquals(1,fixture.service.despawn(sender,"boss"));
        assertTrue(fixture.service.listed().isEmpty());
    }

    @Test void disablingDuringChunkLoadMustKeepServiceCallbackOnMainThread() throws Exception {
        var terrain=new BossSpawnerTest();terrain.setup();
        fixture.api.when(()->org.bukkit.Bukkit.getWorld("world")).thenReturn(terrain.world);
        var dimensions=mock(org.bukkit.entity.Zombie.class);
        when(dimensions.getWidth()).thenReturn(.6);when(dimensions.getHeight()).thenReturn(2d);
        doReturn(dimensions).when(terrain.world).createEntity(any(Location.class),any());
        var pending=new java.util.concurrent.CompletableFuture<org.bukkit.Chunk>();
        when(terrain.world.getChunkAtAsync(anyInt(),anyInt(),eq(true))).thenReturn(pending);
        var callback=new java.util.concurrent.atomic.AtomicReference<Thread>();
        var messages=mock(dev.dasan.customdungeons.text.Messages.class);
        when(fixture.platform.messages()).thenReturn(messages);
        doAnswer(c->{if(c.getArgument(1).equals("gui.world-boss.spawn-no-location"))callback.set(Thread.currentThread());return null;})
            .when(messages).send(any(),anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        var result=fixture.service.spawn(mock(org.bukkit.command.CommandSender.class),"boss");
        assertFalse(result.isDone());
        when(fixture.service.plugin.isEnabled()).thenReturn(false);
        var chunk=mock(org.bukkit.Chunk.class);
        var worker=new Thread(()->pending.complete(chunk),"chunk-completion-worker");worker.start();worker.join();
        assertFalse(result.isDone(),"A disabled chunk callback must be discarded without completing service state");
        assertNull(callback.get(),"A disabled chunk callback must not send messages");
        fixture.service.close();
        assertFalse(result.join());
    }

    @Test void healingOnDamageMustNotEraseQualifyingPlayerDamage() {
        when(fixture.a.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        var data=fixture.mob.getPersistentDataContainer();
        when(data.has(dev.dasan.customdungeons.mob.MobKeys.VIRTUAL_MAX_HEALTH,org.bukkit.persistence.PersistentDataType.DOUBLE)).thenReturn(true);
        when(data.get(dev.dasan.customdungeons.mob.MobKeys.VIRTUAL_MAX_HEALTH,org.bukkit.persistence.PersistentDataType.DOUBLE)).thenReturn(5000d);
        var hp=new java.util.concurrent.atomic.AtomicReference<Double>(4500d);
        when(data.get(dev.dasan.customdungeons.mob.MobKeys.VIRTUAL_HEALTH,org.bukkit.persistence.PersistentDataType.DOUBLE)).thenAnswer(c->hp.get());
        doAnswer(c->{hp.set(c.getArgument(2));return null;}).when(data).set(eq(dev.dasan.customdungeons.mob.MobKeys.VIRTUAL_HEALTH),eq(org.bukkit.persistence.PersistentDataType.DOUBLE),anyDouble());
        fixture.platform.abilityRegistry().register(new dev.dasan.customdungeons.ability.impl.custom.HealerAbility());
        fixture.encounter.active.get(fixture.mob.getUniqueId()).abilities().add(new dev.dasan.customdungeons.model.AbilityInstance(
            "healer",dev.dasan.customdungeons.model.Trigger.ON_DAMAGED,0,dev.dasan.customdungeons.model.TargetMode.NEAREST,48,0,1,0,java.util.Map.of("heal",300d)));
        var hit=mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(hit.getEntity()).thenReturn(fixture.mob);when(hit.getDamager()).thenReturn(fixture.a);when(hit.getFinalDamage()).thenReturn(250d);
        when(hit.getCause()).thenReturn(org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK);
        fixture.service.damage(hit);
        assertEquals(4800d,hp.get(),"The real ON_DAMAGED Healer ability must have healed its caster");
        new dev.dasan.customdungeons.mob.MobCombatListener(fixture.service.plugin).damaged(hit);
        assertEquals(4550d,hp.get(),"The real damage listener must have applied 250 HP after healing");
        assertEquals(250,fixture.encounter.damage.damage(fixture.a.getUniqueId()));
        fixture.service.damage(hit);new dev.dasan.customdungeons.mob.MobCombatListener(fixture.service.plugin).damaged(hit);
        assertEquals(500,fixture.encounter.damage.damage(fixture.a.getUniqueId()));
        dev.dasan.customdungeons.mob.MobHealth.heal(fixture.mob,1000);
        assertEquals(500,fixture.encounter.damage.damage(fixture.a.getUniqueId()),"Later healing must not reduce earned credit");
        fixture.encounter.reward(true);
        verify(fixture.platform).deliverReward(fixture.a,fixture.template.worldBoss().reward());
    }
}
