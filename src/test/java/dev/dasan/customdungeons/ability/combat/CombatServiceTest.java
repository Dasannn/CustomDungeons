package dev.dasan.customdungeons.ability.combat;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.*;
import dev.dasan.customdungeons.text.Messages;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatServiceTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    static final List<String> IDS=List.of("charge","boss_totem","decoys","interruptible_ultimate","purge","buff_steal","silence","pain_link","blink_behind");
    static java.util.stream.Stream<Arguments> releases(){return IDS.stream().flatMap(id->List.of("death","retire","reload","disable").stream().map(reason->Arguments.of(id,reason)));}
    static final class Fixture implements AutoCloseable {
        final AtomicLong tick=new AtomicLong();final Map<Long,List<Runnable>> tasks=new TreeMap<>();
        final World world=mock(World.class);final Mob entity=mock(Mob.class);final MobHost host=mock(MobHost.class);
        final List<Player> players=new ArrayList<>();final List<Entity> created=new ArrayList<>();
        final MobsPlatform platform=mock(MobsPlatform.class);final AbilityRegistry registry=new AbilityRegistry();
        final TickScheduler scheduler=mock(TickScheduler.class);final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final RestorableTempBlocks blocks=mock(RestorableTempBlocks.class);final Map<String,Block> terrain=new HashMap<>();
        final CombatService service;final ActiveMob caster;final Player player,ally,foreign;final Messages messages=new Messages();
        final Map<Player,Location> positions=new HashMap<>();final Map<Player,PotionEffect> effects=new HashMap<>();
        Location mobPosition=new Location(world,0,64,0);
        Fixture() {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(i->(int)tick.get());
            var max=mock(AttributeInstance.class);when(max.getValue()).thenReturn(100.0);when(entity.getAttribute(Attribute.MAX_HEALTH)).thenReturn(max);when(entity.getHealth()).thenReturn(100.0);
            var attack=mock(AttributeInstance.class);when(attack.getValue()).thenReturn(10.0);when(entity.getAttribute(Attribute.ATTACK_DAMAGE)).thenReturn(attack);
            when(entity.hasAI()).thenReturn(true);when(entity.getWidth()).thenReturn(.6);when(entity.getHeight()).thenReturn(1.8);
            when(entity.teleport(any(Location.class))).thenAnswer(i->{Location at=i.getArgument(0);if(at!=null)mobPosition=at.clone();return true;});
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.isValid()).thenReturn(true);when(entity.getLocation()).thenAnswer(i->mobPosition.clone());when(entity.getWorld()).thenReturn(world);doReturn(pdc()).when(entity).getPersistentDataContainer();
            when(world.getName()).thenReturn("test");when(world.getUID()).thenReturn(UUID.randomUUID());when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);
            var border=mock(WorldBorder.class);when(world.getWorldBorder()).thenReturn(border);when(border.getCenter()).thenReturn(new Location(world,0,64,0));when(border.getSize()).thenReturn(1000.0);
            when(world.getHighestBlockYAt(anyInt(),anyInt(),any(HeightMap.class))).thenReturn(63);
            when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenAnswer(i->terrain.computeIfAbsent(i.getArgument(0)+":"+i.getArgument(1)+":"+i.getArgument(2),key->{
                int x=i.getArgument(0),y=i.getArgument(1),z=i.getArgument(2);Block b=mock(Block.class);when(b.getWorld()).thenReturn(world);when(b.getX()).thenReturn(x);when(b.getY()).thenReturn(y);when(b.getZ()).thenReturn(z);
                when(b.isEmpty()).thenReturn(y>=64);when(b.isPassable()).thenReturn(y>=64);when(b.isSolid()).thenReturn(y<64);when(b.getType()).thenReturn(y<64?Material.STONE:Material.AIR);when(b.getBlockData()).thenReturn(mock(BlockData.class));return b;
            }));
            when(world.getBlockAt(any(Location.class))).thenAnswer(i->{Location at=i.getArgument(0);return world.getBlockAt(at.getBlockX(),at.getBlockY(),at.getBlockZ());});
            bukkit.when(()->Bukkit.createBlockData(Material.STONE_BRICKS)).thenReturn(mock(BlockData.class));
            when(blocks.place(any(),any(),anyInt())).thenReturn(true);when(host.tempBlocks()).thenReturn(blocks);when(host.area()).thenReturn(new MobArea("test",new BoundingBox(-40,60,-40,40,100,40)));
            when(host.id()).thenReturn(UUID.randomUUID());when(host.players()).thenReturn(players);when(host.scheduler()).thenReturn(scheduler);when(host.audience(any())).thenReturn(players);
            when(scheduler.currentTick()).thenAnswer(i->tick.get());doAnswer(i->{tasks.computeIfAbsent(tick.get()+Math.max(1,(int)i.getArgument(0)),k->new ArrayList<>()).add(i.getArgument(1));return null;}).when(scheduler).runLater(anyInt(),any());
            when(platform.messages()).thenReturn(messages);when(platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(50,1,48));messages.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/messages.yml")),"");
            var template=new MobTemplate("test","HUSK","",100,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false);
            caster=new ActiveMob(entity,template,host);when(host.mobs()).thenReturn(List.of(caster));
            player=player(0,2);ally=player(1,2);foreign=player(0,2);players.add(player);players.add(ally);
            CombatAbilities.register(registry);service=new CombatService(platform);
            for(Class<? extends Entity> type:List.of(ItemDisplay.class,Interaction.class))
                when(world.spawn(any(Location.class),eq(type),any(Consumer.class))).thenAnswer(i->{
                    Entity result=mock(type);when(result.getUniqueId()).thenReturn(UUID.randomUUID());when(result.isValid()).thenReturn(true);
                    doReturn(pdc()).when(result).getPersistentDataContainer();((Consumer<Entity>)i.getArgument(2)).accept(result);created.add(result);return result;
                });
            when(entity.copy()).thenAnswer(i->{
                Mob copy=mock(Mob.class);when(copy.getUniqueId()).thenReturn(UUID.randomUUID());when(copy.isValid()).thenReturn(true);
                doReturn(pdc()).when(copy).getPersistentDataContainer();when(copy.getAttribute(any())).thenReturn(mock(AttributeInstance.class));
                when(copy.spawnAt(any(),any())).thenReturn(true);created.add(copy);return copy;
            });
        }
        Player player(double x,double z) {
            var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);positions.put(p,new Location(world,x,64,z));when(p.getLocation()).thenAnswer(i->positions.get(p).clone());when(p.getVelocity()).thenAnswer(i->new Vector());when(p.getEyeLocation()).thenAnswer(i->positions.get(p).clone().add(0,1.62,0));
            when(p.isOnline()).thenReturn(true);when(p.isValid()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.getHealth()).thenReturn(20.0);when(p.getWidth()).thenReturn(.6);when(p.getHeight()).thenReturn(1.8);
            var health=mock(AttributeInstance.class);when(health.getValue()).thenReturn(20.0);when(p.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
            when(p.teleport(any(Location.class))).thenReturn(true);
            when(p.addPotionEffect(any())).thenAnswer(i->{effects.put(p,i.getArgument(0));return true;});when(p.getPotionEffect(any())).thenAnswer(i->effects.get(p));when(p.hasPotionEffect(any())).thenAnswer(i->effects.containsKey(p));when(p.getActivePotionEffects()).thenAnswer(i->effects.containsKey(p)?List.of(effects.get(p)):List.of());doAnswer(i->{effects.remove(p);return null;}).when(p).removePotionEffect(any());return p;
        }
        void cast(String id){cast(id,Map.of());}
        void cast(String id,Map<String,Object> raw) {var a=registry.get(id).orElseThrow();a.execute(new AbilityContext(caster,List.of(player,ally),new ParamValues(raw,a.params()),host,null));}
        void step(int count){for(int i=0;i<count;i++){var queue=tasks.remove(tick.incrementAndGet());if(queue!=null)queue.forEach(Runnable::run);}}
        @Override public void close(){service.close();FallProtection.shared().close();bukkit.close();}
    }
    static PersistentDataContainer pdc() {
        var p=mock(PersistentDataContainer.class);Map<NamespacedKey,Object> data=new HashMap<>();
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(2));return null;}).when(p).set(any(),any(),any());doAnswer(i->{data.remove(i.getArgument(0));return null;}).when(p).remove(any());when(p.get(any(),any())).thenAnswer(i->data.get(i.getArgument(0)));when(p.has(any(),any())).thenAnswer(i->data.containsKey(i.getArgument(0)));return p;
    }

    @ParameterizedTest @ValueSource(strings={"charge","boss_totem","decoys","interruptible_ultimate","purge","buff_steal","silence","pain_link","blink_behind"})
    void warningsPrecedeDamageAuxiliariesAndEffects(String id) {
        try(var f=new Fixture()) {
            f.cast(id);f.step(9);assertEquals(0,f.created.size());
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.player,never()).removePotionEffect(any());
            verify(f.entity,never()).teleport(any(Location.class));
            verify(f.player,atLeastOnce()).sendActionBar(any(net.kyori.adventure.text.Component.class));
            verify(f.player,atLeastOnce()).spawnParticle(any(Particle.class),any(Location.class),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
        }
    }
    @ParameterizedTest @MethodSource("releases") void releaseAlwaysRemovesOwnedEntitiesAndRestoresAI(String id,String reason) {
        try(var f=new Fixture()) {
            f.cast(id);f.step(25);
            switch(reason) {
                case "death" -> {var event=mock(EntityDeathEvent.class);when(event.getEntity()).thenReturn(f.entity);f.service.death(event);}
                case "retire" -> CombatService.cleanup(f.caster);case "reload" -> CombatService.reset();case "disable" -> f.service.close();
            }
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.auxiliaryCount());assertEquals(0,f.service.silenceCount());
            assertFalse(CombatService.paused(f.caster));
            if(id.equals("charge")||id.equals("interruptible_ultimate"))verify(f.entity).setAI(true);
            int count=f.created.size();f.step(1300);assertEquals(count,f.created.size());
            for(Entity entity:f.created)verify(entity,atLeastOnce()).remove();
        }
    }
    @Test void ultimateInterruptedByGroupFinalDamageStunsThenRestoresAI() {
        try(var f=new Fixture()) {
            f.cast("interruptible_ultimate");f.step(30);
            var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(f.ally);
            f.service.applied(new MobDamageAppliedEvent(f.entity,hit,9,91));f.step(1);assertTrue(CombatService.paused(f.caster));
            f.service.applied(new MobDamageAppliedEvent(f.entity,hit,1,90));f.step(39);assertTrue(CombatService.paused(f.caster));
            f.step(1);assertFalse(CombatService.paused(f.caster));verify(f.entity).setAI(true);
            f.step(200);verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void foreignDamageCannotInterruptAndUltimateCanBeEscaped() {
        try(var f=new Fixture()) {
            f.cast("interruptible_ultimate");var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(f.foreign);
            f.service.applied(new MobDamageAppliedEvent(f.entity,hit,100,0));
            f.positions.put(f.player,new Location(f.world,20,64,20));f.step(80);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.ally).damage(30,f.entity);verify(f.entity).setAI(true);
        }
    }
    @Test void silenceBlocksApplePotionAndPearlButNeverResurrection() {
        try(var f=new Fixture()) {
            f.cast("silence");f.step(20);assertEquals(2,f.service.silenceCount());
            for(Material type:List.of(Material.POTION,Material.SPLASH_POTION,Material.LINGERING_POTION,Material.ENDER_PEARL,Material.TOTEM_OF_UNDYING)) {
                var e=mock(PlayerInteractEvent.class);when(e.getPlayer()).thenReturn(f.player);when(e.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_AIR);var item=mock(ItemStack.class);when(item.getType()).thenReturn(type);when(e.getItem()).thenReturn(item);f.service.use(e);
                if(type==Material.TOTEM_OF_UNDYING)verify(e,never()).setCancelled(true);else verify(e).setCancelled(true);
            }
            var consume=mock(PlayerItemConsumeEvent.class);when(consume.getPlayer()).thenReturn(f.player);var apple=mock(ItemStack.class);when(apple.getType()).thenReturn(Material.GOLDEN_APPLE);when(consume.getItem()).thenReturn(apple);f.service.consume(consume);verify(consume).setCancelled(true);
            var foreign=mock(PlayerItemConsumeEvent.class);when(foreign.getPlayer()).thenReturn(f.foreign);f.service.consume(foreign);verify(foreign,never()).setCancelled(true);
            var resurrection=mock(EntityResurrectEvent.class);f.step(80);assertEquals(0,f.service.silenceCount());verify(resurrection,never()).setCancelled(anyBoolean());
        }
    }
    @Test void silenceStopsAtQuitAndLinkBreaksByDistanceAndMembership() {
        try(var f=new Fixture()) {
            f.cast("silence");f.step(20);var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(f.player);f.service.quit(quit);assertEquals(1,f.service.silenceCount());
            f.cast("pain_link");f.step(20);f.positions.put(f.player,new Location(f.world,13,64,0));
            f.service.applied(new MobDamageAppliedEvent(f.entity,mock(EntityDamageEvent.class),20,80));verify(f.player,never()).damage(anyDouble());
            f.step(1);assertEquals(1,f.service.activeCount());
        }
    }
    @Test void linkSharesFinalDamageUpToTotalCapAndNeverCountsReflections() {
        try(var f=new Fixture()) {
            f.cast("pain_link");f.step(20);
            f.service.applied(new MobDamageAppliedEvent(f.entity,mock(EntityDamageEvent.class),10,90));
            f.service.applied(new MobDamageAppliedEvent(f.entity,mock(EntityDamageEvent.class),100,0));
            f.service.applied(new MobDamageAppliedEvent(f.entity,mock(EntityDamageEvent.class),100,0));
            verify(f.player).damage(3);verify(f.player).damage(5);verify(f.player,times(2)).damage(anyDouble());
            doAnswer(i->{f.service.applied(new MobDamageAppliedEvent(f.entity,mock(EntityDamageEvent.class),100,0));return null;}).when(f.player).damage(anyDouble());
            CombatService.reset();f.cast("pain_link");f.step(20);f.service.applied(new MobDamageAppliedEvent(f.entity,mock(EntityDamageEvent.class),10,90));verify(f.player,times(3)).damage(anyDouble());
        }
    }
    @Test void purgeAndStealOnlyPositiveEffectsAndStolenDurationIsBounded() {
        for(String id:List.of("purge","buff_steal"))try(var f=new Fixture()) {
            f.effects.put(f.player,new PotionEffect(PotionEffectType.STRENGTH,-1,1));f.effects.put(f.ally,new PotionEffect(PotionEffectType.POISON,400,1));
            f.cast(id);f.step(20);verify(f.player).removePotionEffect(PotionEffectType.STRENGTH);verify(f.ally,never()).removePotionEffect(any());
            if(id.equals("buff_steal"))verify(f.entity).addPotionEffect(argThat(p->p.getType()==PotionEffectType.STRENGTH&&p.getDuration()==600));
        }
    }
    @Test void totemProtectsUntilDestroyedAndCannotBeDamagedByForeignPlayer() {
        try(var f=new Fixture()) {
            f.cast("boss_totem",Map.of("protect",true));f.step(20);assertEquals(2,f.service.auxiliaryCount());
            var hit=mock(EntityDamageEvent.class);when(hit.getEntity()).thenReturn(f.entity);when(hit.getDamage()).thenReturn(10.0);f.service.protect(hit);verify(hit).setDamage(7);
            var pre=mock(io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class);when(pre.getAttacked()).thenReturn(f.created.getLast());when(pre.getPlayer()).thenReturn(f.foreign);f.service.totemAttack(pre);assertEquals(2,f.service.auxiliaryCount());
            var damage=mock(EntityDamageByEntityEvent.class);when(damage.getEntity()).thenReturn(f.created.getLast());when(damage.getDamager()).thenReturn(f.ally);when(damage.getFinalDamage()).thenReturn(30.0);f.service.auxiliaryDamage(damage);assertEquals(0,f.service.auxiliaryCount());
        }
    }
    @Test void totemMeleeRejectsRemotePacketsButAcceptsAnAttackWithinReach() {
        try(var f=new Fixture()) {
            f.cast("boss_totem",Map.of("health",5));f.step(20);
            var attack=mock(AttributeInstance.class);when(attack.getValue()).thenReturn(30.0);
            when(f.player.getAttribute(Attribute.ATTACK_DAMAGE)).thenReturn(attack);when(f.player.getAttackCooldown()).thenReturn(1.0f);
            var pre=mock(io.papermc.paper.event.player.PrePlayerAttackEntityEvent.class);
            when(pre.getAttacked()).thenReturn(f.created.getLast());when(pre.getPlayer()).thenReturn(f.player);
            f.positions.put(f.player,new Location(f.world,30,64,0));f.service.totemAttack(pre);
            assertEquals(2,f.service.auxiliaryCount(),"A remote packet cannot destroy the totem");
            f.positions.put(f.player,new Location(f.world,3,64,0));f.service.totemAttack(pre);
            assertEquals(0,f.service.auxiliaryCount());
        }
    }
    @Test void decoysMarkedBeforeSpawnExcludedFromHostAndDropsAndExpired() {
        try(var f=new Fixture()) {
            f.cast("decoys");f.step(20);assertEquals(2,f.created.size());assertEquals(1,f.host.mobs().size());
            for(Entity entity:f.created) {
                assertTrue(CombatService.isDecoy(entity));assertFalse(MobKeys.isDungeonMob(entity));
                var death=mock(EntityDeathEvent.class);when(death.getEntity()).thenReturn((Mob)entity);var drops=new ArrayList<ItemStack>();drops.add(new ItemStack(Material.DIAMOND));when(death.getDrops()).thenReturn(drops);f.service.death(death);assertTrue(drops.isEmpty());verify(death).setDroppedExp(0);
            }
            f.step(300);assertEquals(0,f.service.auxiliaryCount());
        }
    }
    @Test void remnantsRemovedAfterCrashAndNoSafeBlinkCancelsWithoutDamage() {
        try(var f=new Fixture()) {
            f.cast("decoys");f.step(20);var load=mock(EntitiesLoadEvent.class);when(load.getEntities()).thenReturn(f.created);f.service.loaded(load);for(Entity e:f.created)verify(e).remove();
            CombatService.reset();when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);f.cast("blink_behind");f.step(20);verify(f.player,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void fullHealthNonlethalEventIsCappedAtOneHp() {
        try(var f=new Fixture()) {
            var event=mock(EntityDamageEvent.class);when(event.getEntity()).thenReturn(f.player);when(event.getDamage()).thenReturn(30.0);when(event.getFinalDamage()).thenReturn(30.0);
            doAnswer(i->{f.service.protect(event);return null;}).when(f.player).damage(anyDouble(),any(Entity.class));
            f.cast("interruptible_ultimate");f.step(80);verify(event).setDamage(19);
        }
    }
    @Test void chargeStopsAtWallStunsAndSideStepDodges() {
        try(var f=new Fixture()) {
            when(f.world.getBlockAt(0,64,4).isSolid()).thenReturn(true);f.cast("charge");f.positions.put(f.player,new Location(f.world,3,64,2));f.step(28);
            assertTrue(CombatService.paused(f.caster));verify(f.player,never()).damage(anyDouble(),any(Entity.class));f.step(50);assertFalse(CombatService.paused(f.caster));verify(f.entity).setAI(true);
        }
    }
    @Test void phaseChangeCancelsChargeAndRestoresAIImmediately() {
        try(var f=new Fixture()) {
            var phase=new PhaseDef(.5,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,0);
            var template=new MobTemplate("boss","HUSK","boss",100,10,0,0,1,Map.of(),List.of(),List.of(),List.of(),true,"PURPLE",null,List.of(phase),false);
            var boss=new ActiveMob(f.entity,template,f.host);var ability=f.registry.get("interruptible_ultimate").orElseThrow();
            ability.execute(new AbilityContext(boss,List.of(f.player),new ParamValues(Map.of(),ability.params()),f.host,null));
            assertTrue(CombatService.paused(boss));when(f.entity.getHealth()).thenReturn(50.0);
            var controller=new BossController(new MobFactory(f.platform),Map.of());controller.onDamaged(boss,1);
            assertFalse(CombatService.paused(boss));verify(f.entity).setAI(true);f.step(200);verify(f.player,never()).damage(anyDouble(),any(Entity.class));controller.cleanup(boss);
        }
    }
    @Test void originalDisabledAIIsPreservedAndUnexpectedFailuresStillReleaseIt() {
        try(var f=new Fixture()) {
            when(f.entity.hasAI()).thenReturn(false);f.cast("interruptible_ultimate");CombatService.reset();verify(f.entity,times(2)).setAI(false);verify(f.entity,never()).setAI(true);
            when(f.entity.hasAI()).thenReturn(true);f.cast("charge");when(f.entity.teleport(any(Location.class))).thenThrow(new IllegalStateException("test failure"));
            assertThrows(IllegalStateException.class,()->f.step(20));assertFalse(CombatService.paused(f.caster));verify(f.entity).setAI(true);
        }
    }
    @ParameterizedTest @ValueSource(strings={"charge","interruptible_ultimate"})
    void setupFailureRestoresAIAndClearsTheOwner(String id) {
        try(var f=new Fixture()) {
            doThrow(new IllegalStateException("warning setup failed")).when(f.entity).setVelocity(any(Vector.class));
            assertThrows(IllegalStateException.class,()->f.cast(id));
            assertFalse(CombatService.paused(f.caster));assertEquals(0,f.service.activeCount());
            verify(f.entity).setAI(true);
        }
    }
    @Test void ordinaryBuffsRemainPurgeableDuringControlAndBuffWarningCanBeEscaped() {
        try(var f=new Fixture();var controls=mockStatic(dev.dasan.customdungeons.ability.control.ControlService.class)) {
            controls.when(()->dev.dasan.customdungeons.ability.control.ControlService.hasActiveControl(f.player)).thenReturn(true);
            f.effects.put(f.player,new PotionEffect(PotionEffectType.STRENGTH,200,1));f.cast("purge");f.step(20);verify(f.player).removePotionEffect(PotionEffectType.STRENGTH);
            clearInvocations(f.player);
            controls.when(()->dev.dasan.customdungeons.ability.control.ControlService.hasActiveControl(f.player)).thenReturn(false);
            f.cast("buff_steal");f.positions.put(f.player,new Location(f.world,5,64,2));f.step(20);verify(f.player,never()).removePotionEffect(any());
        }
    }
    @Test void totemHealsPercentageOfVirtualMaximumAndBlinkStrikesWithBonus() {
        try(var f=new Fixture()) {
            when(f.entity.getHealth()).thenReturn(50.0);f.cast("boss_totem");f.step(20);verify(f.entity).setHealth(52);
            CombatService.reset();f.cast("blink_behind");f.step(10);verify(f.entity).teleport(argThat((Location at)->Math.abs(at.distance(f.positions.get(f.player))-2)<1e-8));verify(f.player).damage(15,f.entity);
        }
    }
    @Test void silentPlayersCannotLaunchPotionsOrPearlsButForeignPlayersCan() {
        try(var f=new Fixture()) {
            f.cast("silence");f.step(20);
            for(Class<? extends Projectile> type:List.of(ThrownPotion.class,EnderPearl.class)) {
                var shot=mock(type);when(shot.getShooter()).thenReturn(f.player);var event=mock(ProjectileLaunchEvent.class);when(event.getEntity()).thenReturn(shot);f.service.launch(event);verify(event).setCancelled(true);
                when(shot.getShooter()).thenReturn(f.foreign);var foreign=mock(ProjectileLaunchEvent.class);when(foreign.getEntity()).thenReturn(shot);f.service.launch(foreign);verify(foreign,never()).setCancelled(true);
            }
        }
    }
    @Test void aMissingTotemHitboxCannotLeaveIndestructibleProtection() {
        try(var f=new Fixture()) {
            f.cast("boss_totem",Map.of("protect",true));f.step(20);
            var removed=mock(com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent.class);when(removed.getEntity()).thenReturn(f.created.getLast());f.service.removed(removed);
            assertEquals(0,f.service.auxiliaryCount());assertEquals(0,f.service.activeCount());
        }
    }

    @Test void innateDecoySummonsAndFangsAreCancelled() {
        try(var f=new Fixture()) {
            f.cast("decoys");f.step(20);var copy=(Mob)f.created.getFirst();
            var vex=mock(Vex.class);doReturn(pdc()).when(vex).getPersistentDataContainer();when(vex.getOwner()).thenReturn(copy);var summoned=mock(EntitySpawnEvent.class);when(summoned.getEntity()).thenReturn(vex);f.service.nativeSpawn(summoned);verify(summoned).setCancelled(true);
            var fangs=mock(EvokerFangs.class);when(fangs.getOwner()).thenReturn(copy);when(fangs.getUniqueId()).thenReturn(UUID.randomUUID());when(fangs.isValid()).thenReturn(true);doReturn(pdc()).when(fangs).getPersistentDataContainer();
            var spell=mock(EntitySpawnEvent.class);when(spell.getEntity()).thenReturn(fangs);f.service.nativeSpawn(spell);verify(spell).setCancelled(true);
            var hit=mock(EntityDamageByEntityEvent.class);when(hit.getDamager()).thenReturn(fangs);when(hit.getEntity()).thenReturn(f.player);f.service.auxiliaryDamage(hit);verify(hit).setCancelled(true);
            CombatService.reset();verify(fangs,never()).remove();
        }
    }

    @Test void areaEdgeStopsAChargeWithoutPretendingItHitAWall() {
        try(var f=new Fixture()) {
            when(f.host.area()).thenReturn(new MobArea("test",new BoundingBox(-1,60,-1,1,100,1)));
            f.cast("charge");f.step(30);assertFalse(CombatService.paused(f.caster));verify(f.entity).setAI(true);
        }
    }

}
