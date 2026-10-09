package dev.dasan.customdungeons.ability.control;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.intelligence.IntelligenceDef;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.*;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlServiceTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    static final String[] CONTROL={"grab_throw","levitation_cage","drain_grab","roots","anchor_spear","soul_chain"};
    static java.util.stream.Stream<String> controls(){return Arrays.stream(CONTROL);}
    static java.util.stream.Stream<Arguments> releases(){return Arrays.stream(new String[]{"grab_throw","levitation_cage","drain_grab","roots","anchor_spear","soul_chain","bomb_mark","tactical_summon"}).flatMap(id->Arrays.stream(new String[]{"death","retire","quit","playerDeath","reload","disable","leave","world"}).map(reason->Arguments.of(id,reason)));}
    static final class Fixture implements AutoCloseable {
        final AtomicLong tick=new AtomicLong();final Map<Long,List<Runnable>> tasks=new TreeMap<>();
        final World world=mock(World.class);final Mob entity=mock(Mob.class);final MobHost host=mock(MobHost.class);
        final List<Player> players=new ArrayList<>();final List<Entity> created=new ArrayList<>();
        final MobsPlatform platform=mock(MobsPlatform.class);final AbilityRegistry registry=new AbilityRegistry();
        final TickScheduler scheduler=mock(TickScheduler.class);final org.mockito.MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final ControlService service;final ActiveMob caster;final Player player,ally;final Messages messages=new Messages();
        Fixture() {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(i->(int)tick.get());
            when(world.getName()).thenReturn("test");when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.isValid()).thenReturn(true);
            when(entity.getLocation()).thenReturn(new Location(world,0,64,0));when(entity.getEyeLocation()).thenReturn(new Location(world,0,65.5,0));when(entity.getWorld()).thenReturn(world);
            doReturn(pdc()).when(entity).getPersistentDataContainer();when(entity.addPassenger(any())).thenReturn(true);
            var health=mock(AttributeInstance.class);when(health.getValue()).thenReturn(100.0);when(entity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);when(entity.getHealth()).thenReturn(100.0);
            when(host.id()).thenReturn(UUID.randomUUID());when(host.players()).thenReturn(players);when(host.scheduler()).thenReturn(scheduler);when(host.audience(any())).thenReturn(List.of());
            when(scheduler.currentTick()).thenAnswer(i->tick.get());doAnswer(i->{tasks.computeIfAbsent(tick.get()+Math.max(1,(int)i.getArgument(0)),k->new ArrayList<>()).add(i.getArgument(1));return null;}).when(scheduler).runLater(anyInt(),any());
            when(platform.messages()).thenReturn(messages);messages.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/messages.yml")),"");
            var template=new MobTemplate("test","HUSK","",100,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
            caster=new ActiveMob(entity,template,host);when(host.mobs()).thenReturn(List.of(caster));
            player=player(1);ally=player(2);players.add(player);players.add(ally);
            ControlAbilities.register(registry);service=new ControlService(platform,tick::get);
            when(world.spawn(any(Location.class),any(Class.class),any(Consumer.class))).thenAnswer(i->{Class<? extends Entity> type=i.getArgument(1);Entity result=mock(type);when(result.getUniqueId()).thenReturn(UUID.randomUUID());doReturn(pdc()).when(result).getPersistentDataContainer();when(result.isValid()).thenReturn(true);((Consumer<Entity>)i.getArgument(2)).accept(result);when(result.addPassenger(any())).thenReturn(true);created.add(result);return result;});
            when(entity.launchProjectile(eq(Trident.class),any(Vector.class),any())).thenAnswer(i->{Trident shot=mock(Trident.class);when(shot.getUniqueId()).thenReturn(UUID.randomUUID());doReturn(pdc()).when(shot).getPersistentDataContainer();((Consumer<Trident>)i.getArgument(2)).accept(shot);created.add(shot);return shot;});
        }
        Player player(int x) {
            var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);when(p.getLocation()).thenAnswer(i->new Location(world,x,64,0));when(p.getEyeLocation()).thenReturn(new Location(world,x,65.5,0));when(p.getVelocity()).thenReturn(new Vector());when(p.getBoundingBox()).thenReturn(new BoundingBox(x,64,0,x+.6,66,.6));
            when(p.isOnline()).thenReturn(true);when(p.isValid()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.getHealth()).thenReturn(20.0);doReturn(pdc()).when(p).getPersistentDataContainer();
            for(var type:List.of(Attribute.MAX_HEALTH,Attribute.MOVEMENT_SPEED,Attribute.JUMP_STRENGTH,Attribute.ATTACK_DAMAGE)) {
                var a=mock(AttributeInstance.class);when(a.getValue()).thenReturn(type==Attribute.MAX_HEALTH?20.0:5.0);
                Map<NamespacedKey,AttributeModifier> mods=new HashMap<>();when(a.getModifier(any(NamespacedKey.class))).thenAnswer(i->mods.get(i.getArgument(0)));
                doAnswer(i->{AttributeModifier m=i.getArgument(0);mods.put(m.getKey(),m);return null;}).when(a).addTransientModifier(any());
                doAnswer(i->{mods.remove(i.getArgument(0));return null;}).when(a).removeModifier(any(NamespacedKey.class));when(p.getAttribute(type)).thenReturn(a);
            }
            var effects=new HashMap<org.bukkit.potion.PotionEffectType,org.bukkit.potion.PotionEffect>();
            var appliedAt=new HashMap<org.bukkit.potion.PotionEffectType,Long>();
            doAnswer(i->{org.bukkit.potion.PotionEffect effect=i.getArgument(0);effects.put(effect.getType(),effect);appliedAt.put(effect.getType(),tick.get());return true;}).when(p).addPotionEffect(any());
            when(p.getPotionEffect(any())).thenAnswer(i->{
                var effect=effects.get(i.getArgument(0));
                return effect==null||effect.isInfinite()?effect:new org.bukkit.potion.PotionEffect(effect.getType(),Math.max(0,effect.getDuration()-(int)(tick.get()-appliedAt.get(effect.getType()))),effect.getAmplifier(),effect.isAmbient(),effect.hasParticles(),effect.hasIcon(),effect.getHiddenPotionEffect());
            });
            when(p.hasPotionEffect(any())).thenAnswer(i->effects.containsKey(i.getArgument(0)));
            doAnswer(i->{effects.remove(i.getArgument(0));return null;}).when(p).removePotionEffect(any());
            bukkit.when(()->Bukkit.getPlayer(p.getUniqueId())).thenReturn(p);return p;
        }
        void cast(String id){cast(id,Map.of());}
        void cast(String id,Map<String,Object> raw) {var a=registry.get(id).orElseThrow();a.execute(new AbilityContext(caster,id.equals("soul_chain")?List.of(player,ally):List.of(player),new ParamValues(raw,a.params()),host,null));}
        void step(int count){for(int i=0;i<count;i++){long t=tick.incrementAndGet();var queue=tasks.remove(t);if(queue!=null)queue.forEach(Runnable::run);}}
        void start(String id) {
            cast(id);step(20);
            if(id.equals("anchor_spear")) {var e=mock(ProjectileHitEvent.class);when(e.getEntity()).thenReturn((Trident)created.getFirst());when(e.getHitEntity()).thenReturn(player);service.hit(e);}
        }
        void input(boolean down){var e=mock(PlayerInputEvent.class);var input=mock(Input.class);when(input.isJump()).thenReturn(down);when(e.getInput()).thenReturn(input);when(e.getPlayer()).thenReturn(player);service.input(e);}
        void helperAttack(Player p) {var e=mock(io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class);when(e.getAttacked()).thenReturn(created.stream().filter(x->x instanceof Interaction).reduce((a,b)->b).orElseThrow());when(e.getPlayer()).thenReturn(p);service.preAttack(e);verify(e).setCancelled(true);}
        @Override public void close(){service.close();FallProtection.shared().close();bukkit.close();}
    }
    static PersistentDataContainer pdc() {
        var p=mock(PersistentDataContainer.class);Map<NamespacedKey,Object> data=new HashMap<>();
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(2));return null;}).when(p).set(any(),any(),any());
        when(p.get(any(),any())).thenAnswer(i->data.get(i.getArgument(0)));when(p.has(any(),any())).thenAnswer(i->data.containsKey(i.getArgument(0)));return p;
    }
    @ParameterizedTest @MethodSource("controls") void warningAlwaysPrecedesControlAndDurationIsBounded(String id) {
        try(var f=new Fixture()) {
            f.cast(id);assertEquals(0,f.service.activeCount());f.step(19);assertEquals(0,f.service.activeCount());
            f.step(1);if(id.equals("anchor_spear")){var e=mock(ProjectileHitEvent.class);when(e.getEntity()).thenReturn((Trident)f.created.getFirst());when(e.getHitEntity()).thenReturn(f.player);f.service.hit(e);}
            assertEquals(1,f.service.activeCount());f.step(401);assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());
            verify(f.player,atLeastOnce()).sendActionBar(any(net.kyori.adventure.text.Component.class));
        }
    }
    @ParameterizedTest @MethodSource("releases") void everyReleaseRouteCleansEachControl(String id,String reason) {
        try(var f=new Fixture()) {
            f.start(id);assertEquals(Arrays.asList(CONTROL).contains(id)?1:0,f.service.activeCount());
            switch(reason) {
                case "death" -> {var e=mock(EntityDeathEvent.class);when(e.getEntity()).thenReturn(f.entity);f.service.death(e);}
                case "retire" -> ControlService.cleanup(f.caster);
                case "quit" -> {var e=mock(PlayerQuitEvent.class);when(e.getPlayer()).thenReturn(f.player);f.service.quit(e);}
                case "playerDeath" -> {var e=mock(PlayerDeathEvent.class);when(e.getEntity()).thenReturn(f.player);f.service.playerDeath(e);}
                case "reload" -> ControlService.reset();
                case "disable" -> f.service.close();
                case "leave" -> {f.players.remove(f.player);ControlService.releasePlayer(f.player);f.step(1);}
                case "world" -> f.service.world(new PlayerChangedWorldEvent(f.player,f.world));
            }
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());
            if(id.equals("grab_throw"))verify(f.entity).removePassenger(f.player);
            if(id.equals("drain_grab"))verify(f.created.getFirst()).removePassenger(f.player);
            assertNull(f.player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(MobsPlatform.key("control_immobile")));
            assertNull(f.player.getAttribute(Attribute.JUMP_STRENGTH).getModifier(MobsPlatform.key("control_immobile")));
            f.step(500);assertEquals(0,f.service.activeCount());
        }
    }
    @Test void realJumpInputCountsEdgesAndCancelsOnlyOwnedDismount() {
        try(var f=new Fixture()) {
            f.start("drain_grab");var e=mock(EntityDismountEvent.class);when(e.getEntity()).thenReturn(f.player);when(e.getDismounted()).thenReturn(f.created.getFirst());f.service.dismount(e);verify(e).setCancelled(true);
            for(int i=0;i<30;i++)f.input(true);assertEquals(1,f.service.activeCount());
            for(int i=0;i<7;i++){f.input(false);f.input(true);}assertEquals(0,f.service.activeCount());
            var free=mock(EntityDismountEvent.class);when(free.getEntity()).thenReturn(f.player);f.service.dismount(free);verify(free,never()).setCancelled(true);
        }
    }
    @Test void teammatesBreakCageButHeldPlayerAndOutsidersCannot() {
        try(var f=new Fixture()) {
            f.start("levitation_cage");for(Player p:List.of(f.player,f.player(3),f.ally)) {
                var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(p);
                f.service.mobDamage(new MobDamageAppliedEvent(f.entity,hit,20,80));
                if(p!=f.ally)assertEquals(1,f.service.activeCount());
            }
            assertEquals(0,f.service.activeCount());assertTrue(FallProtection.shared().hasPending());
        }
    }
    @ParameterizedTest @ValueSource(strings={"grab_throw","drain_grab","levitation_cage"}) void everyCompanionEscapeUsesAppliedDamageThreshold(String id) {
        try(var f=new Fixture()) {
            f.start(id);var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(f.ally);
            f.service.mobDamage(new MobDamageAppliedEvent(f.entity,hit,19,81));assertEquals(1,f.service.activeCount());
            f.service.mobDamage(new MobDamageAppliedEvent(f.entity,hit,1,80));assertEquals(0,f.service.activeCount());
        }
    }
    @Test void rootsBlockMovementAndJumpAndCanBeBrokenByTheirVictim() {
        try(var f=new Fixture()) {
            f.start("roots");var from=f.player.getLocation();var to=from.clone().add(1,1,1);var move=new PlayerMoveEvent(f.player,from,to);f.service.move(move);assertEquals(from,move.getTo());
            assertNotNull(f.player.getAttribute(Attribute.JUMP_STRENGTH).getModifier(MobsPlatform.key("control_immobile")));
            f.helperAttack(f.player);f.helperAttack(f.player);assertEquals(0,f.service.activeCount());
        }
    }
    @Test void levitationBlocksHorizontalMovementAndClearsOnlyItsOwnPotion() {
        try(var f=new Fixture()) {
            f.start("levitation_cage");var from=f.player.getLocation();var to=from.clone().add(1,2,1);var move=new PlayerMoveEvent(f.player,from,to);f.service.move(move);assertEquals(from.getX(),move.getTo().getX());assertEquals(from.getY()+2,move.getTo().getY());
            f.step(20);verify(f.player).damage(1,f.entity);ControlService.cleanup(f.caster);verify(f.player).removePotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION);
        }
        try(var f=new Fixture()) {
            f.player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION,500,3));f.start("levitation_cage");assertEquals(0,f.service.activeCount());verify(f.player,never()).removePotionEffect(any());
        }
    }
    @Test void spearIsDodgeableAndNativeProjectileDamageIsCancelled() {
        try(var f=new Fixture()) {
            f.cast("anchor_spear");f.step(20);var shot=(Trident)f.created.getFirst();var damage=mock(EntityDamageByEntityEvent.class);when(damage.getDamager()).thenReturn(shot);f.service.projectileDamage(damage);verify(damage).setCancelled(true);
            var hit=mock(ProjectileHitEvent.class);when(hit.getEntity()).thenReturn(shot);f.service.hit(hit);assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void rootsAndSpearAreOwnedBreakableAndCrashCleaned() {
        for(String id:List.of("roots","anchor_spear"))try(var f=new Fixture()) {
            f.start(id);for(Entity e:f.created)assertTrue(e.getPersistentDataContainer().has(ControlService.AUXILIARY,PersistentDataType.BYTE));
            for(int i=0;i<(id.equals("roots")?2:5);i++)f.helperAttack(f.ally);
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());
            var load=mock(EntitiesLoadEvent.class);when(load.getEntities()).thenReturn(f.created);f.service.load(load);for(Entity e:f.created)verify(e,atLeastOnce()).remove();
        }
    }
    @Test void immunityAndSingleControlDoNotStripExistingModifiers() {
        try(var f=new Fixture()) {
            f.start("roots");f.cast("levitation_cage");f.step(20);assertEquals(1,f.service.activeCount());
            assertNotNull(f.player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(MobsPlatform.key("control_immobile")));
            ControlService.cleanup(f.caster);f.cast("drain_grab");f.step(20);assertEquals(0,f.service.activeCount());
            f.step(40);f.cast("drain_grab");f.step(20);assertEquals(1,f.service.activeCount());
        }
    }
    @Test void bombCountdownDamagesOnlyParticipantsAndProtectsFullHealthSameTick() {
        try(var f=new Fixture()) {
            f.cast("bomb_mark",Map.of("damage",40));f.step(119);verify(f.player,never()).damage(anyDouble(),any(Entity.class));
            f.step(1);verify(f.player).damage(19,f.entity);verify(f.ally).damage(19,f.entity);assertEquals(0,f.service.auxiliaryCount());
            var event=mock(EntityDamageEvent.class);when(event.getEntity()).thenReturn(f.player);when(f.player.getHealth()).thenReturn(1.0);when(event.getDamage()).thenReturn(10.0);when(event.getFinalDamage()).thenReturn(10.0);f.service.nonlethal(event);verify(event).setDamage(0);
        }
    }
    @Test void spearCanBeBrokenByAnyPlayerButRootsRequireParticipants() {
        for(String id:List.of("roots","anchor_spear"))try(var f=new Fixture()) {
            f.start(id);var outside=f.player(3);for(int i=0;i<5;i++)f.helperAttack(outside);
            assertEquals(id.equals("roots")?1:0,f.service.activeCount());
        }
    }
    @Test void ownDismountTeleportDoesNotCancelAnIndependentBomb() {
        try(var f=new Fixture()) {
            f.start("grab_throw");f.cast("bomb_mark");f.step(20);
            doAnswer(i->{var event=mock(PlayerTeleportEvent.class);when(event.getPlayer()).thenReturn(f.player);f.service.teleport(event);return true;}).when(f.entity).removePassenger(f.player);
            var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(f.ally);
            f.service.mobDamage(new MobDamageAppliedEvent(f.entity,hit,20,80));assertEquals(1,f.service.auxiliaryCount());f.step(100);verify(f.player).damage(12,f.entity);
        }
    }
    @Test void startupSweepsOnlyMarkedEntitiesInAlreadyLoadedChunks() {
        try(var f=new Fixture()) {
            var stale=mock(Interaction.class);doReturn(pdc()).when(stale).getPersistentDataContainer();stale.getPersistentDataContainer().set(ControlService.AUXILIARY,PersistentDataType.BYTE,(byte)1);
            var foreign=mock(ItemDisplay.class);doReturn(pdc()).when(foreign).getPersistentDataContainer();
            var chunk=mock(Chunk.class);when(chunk.getEntities()).thenReturn(new Entity[]{stale,foreign});when(f.world.getLoadedChunks()).thenReturn(new Chunk[]{chunk});
            f.service.recoverLoaded(List.of(f.world));verify(stale).remove();verify(foreign,never()).remove();
        }
    }
    @Test void ownMountTeleportMustNotReleaseTheControl() {
        try(var f=new Fixture()) {
            doAnswer(i->{var event=mock(PlayerTeleportEvent.class);when(event.getPlayer()).thenReturn(f.player);f.service.teleport(event);return true;}).when(f.entity).addPassenger(f.player);
            f.start("grab_throw");assertEquals(1,f.service.activeCount());
        }
    }
    @Test void damagePerSecondIncludesTheLastSecondAtTheDurationDeadline() {
        try(var f=new Fixture()) {f.start("levitation_cage");f.step(80);verify(f.player,times(4)).damage(1,f.entity);assertEquals(0,f.service.activeCount());}
    }
    @Test void throwDamagesBothOnCollisionAndUsesConfiguredVerticalForceWhenAlone() {
        try(var f=new Fixture()) {
            f.start("grab_throw");doReturn(f.player.getBoundingBox()).when(f.ally).getBoundingBox();f.step(30);
            verify(f.player).damage(6,f.entity);verify(f.ally).damage(6,f.entity);verify(f.ally).setVelocity(any(Vector.class));assertTrue(FallProtection.shared().hasPending());
        }
        try(var f=new Fixture()) {
            f.players.remove(f.ally);f.cast("grab_throw",Map.of("force",2));f.step(50);verify(f.player).setVelocity(new Vector(0,2,0));
        }
    }
    @Test void intelligentTargetsAreChosenOnlyAmongEligibleHostParticipants() {
        try(var f=new Fixture()) {
            var isolated=f.player(10);f.players.add(isolated);
            var outside=f.player(30);when(outside.getGameMode()).thenReturn(GameMode.CREATIVE);f.players.add(outside);
            var smart=new ActiveMob(f.entity,f.caster.template().withIntelligence(IntelligenceDef.level(3)),f.host);
            for(String id:List.of("grab_throw","roots","anchor_spear"))assertEquals(List.of(isolated),TargetSelector.selectAbility(smart,id,TargetMode.NEAREST,32));
            assertEquals(List.of(f.player),TargetSelector.selectAbility(smart,"bomb_mark",TargetMode.NEAREST,32));
            assertEquals(Set.of(f.player,isolated),Set.copyOf(TargetSelector.selectAbility(smart,"soul_chain",TargetMode.NEAREST,32)));
            assertEquals(List.of(f.player),TargetSelector.selectAbility(f.caster,"roots",TargetMode.NEAREST,32));
        }
    }
    @Test void engineDoesNotDoubleIntrinsicWarningAndAllowsLongerConfiguredWarning() {
        try(var f=new Fixture()) {
            f.caster.abilities().add(new AbilityInstance("drain_grab",Trigger.ON_SPAWN,0,TargetMode.NEAREST,32,100,1,20,Map.of()));
            var engine=new AbilityEngine(f.registry,null);engine.fire(Trigger.ON_SPAWN,f.caster,null,0);
            f.step(19);assertEquals(0,f.service.activeCount());f.step(1);assertEquals(1,f.service.activeCount());
        }
        try(var f=new Fixture()) {
            f.caster.abilities().add(new AbilityInstance("drain_grab",Trigger.ON_SPAWN,0,TargetMode.NEAREST,32,100,1,40,Map.of()));
            var engine=new AbilityEngine(f.registry,null);engine.fire(Trigger.ON_SPAWN,f.caster,null,0);
            f.step(39);assertEquals(0,f.service.activeCount());f.step(1);assertEquals(1,f.service.activeCount());
        }
    }
    @Test void bombAndProjectileWarningsAreInvalidatedByReload() {
        for(String id:List.of("bomb_mark","anchor_spear","tactical_summon"))try(var f=new Fixture()) {
            f.cast(id);ControlService.reset();f.step(500);assertEquals(0,f.service.auxiliaryCount());verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.host,never()).spawnMinion(any(),any(),any());
        }
    }
    @Test void tacticalSummonHasWarningAndHonorsAreaAndMaxAlive() {
        try(var f=new Fixture()) {
            var area=new MobArea("test",new BoundingBox(-2,60,-2,2,70,2));when(f.host.area()).thenReturn(area);
            f.cast("tactical_summon",Map.of("template","ally","radius",8));f.step(20);verify(f.host,never()).spawnMinion(any(),any(),any());
            f.cast("tactical_summon",Map.of("template","ally","radius",0,"count",10,"max-alive",1));f.step(19);verify(f.host,never()).spawnMinion(any(),any(),any());f.step(1);verify(f.host).spawnMinion(eq("ally"),any(),eq(f.caster));
        }
    }
    @Test void chainTogetherAvoidsDamageAndSinglePlayerGetsPulledWithoutTeleport() {
        try(var f=new Fixture()) {f.start("soul_chain");f.step(40);verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.ally,never()).damage(anyDouble(),any(Entity.class));}
        try(var f=new Fixture()) {
            var a=f.registry.get("soul_chain").orElseThrow();when(f.player.getLocation()).thenReturn(new Location(f.world,20,64,0));
            a.execute(new AbilityContext(f.caster,List.of(f.player),new ParamValues(Map.of(),a.params()),f.host,null));f.step(40);verify(f.player,atLeastOnce()).setVelocity(any(Vector.class));verify(f.player).damage(2,f.entity);verify(f.player,never()).teleport(any(Location.class));
        }
    }
}
