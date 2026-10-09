package dev.dasan.customdungeons.ability.zone;

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
import org.bukkit.potion.*;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.mockito.MockedStatic;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoneServiceTest {
    static {dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();}
    static final List<String> IDS=List.of("vortex","inverted_gravity","cracked_floor","sweep","falling_pillars","poison_pools","charged_beam","arrow_rain","rift");
    static java.util.stream.Stream<Arguments> releases(){return IDS.stream().flatMap(id->List.of("death","retire","reload","disable").stream().map(reason->Arguments.of(id,reason)));}
    static final class Fixture implements AutoCloseable {
        final AtomicLong tick=new AtomicLong();final Map<Long,List<Runnable>> tasks=new TreeMap<>();
        final World world=mock(World.class);final Mob entity=mock(Mob.class);final MobHost host=mock(MobHost.class);
        final List<Player> players=new ArrayList<>();final List<Arrow> created=new ArrayList<>();
        final MobsPlatform platform=mock(MobsPlatform.class);final AbilityRegistry registry=new AbilityRegistry();
        final TickScheduler scheduler=mock(TickScheduler.class);final MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class);
        final RestorableTempBlocks blocks=mock(RestorableTempBlocks.class);final Map<String,Block> terrain=new HashMap<>();
        final ZoneService service;final ActiveMob caster;final Player player,ally,foreign;final Messages messages=new Messages();
        final Map<Player,Location> positions=new HashMap<>();final Map<Player,PotionEffect> effects=new HashMap<>();
        Fixture() {
            bukkit.when(Bukkit::getCurrentTick).thenAnswer(i->(int)tick.get());
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());when(entity.isValid()).thenReturn(true);when(entity.getLocation()).thenAnswer(i->new Location(world,0,64,0));when(entity.getWorld()).thenReturn(world);doReturn(pdc()).when(entity).getPersistentDataContainer();
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
            ZoneAbilities.register(registry);service=new ZoneService(platform);
            when(world.spawn(any(Location.class),eq(BlockDisplay.class),any(Consumer.class))).thenAnswer(i->{BlockDisplay result=mock(BlockDisplay.class);when(result.getUniqueId()).thenReturn(UUID.randomUUID());doReturn(pdc()).when(result).getPersistentDataContainer();((Consumer<BlockDisplay>)i.getArgument(2)).accept(result);return result;});
            when(world.spawn(any(Location.class),eq(Arrow.class),any(Consumer.class))).thenAnswer(i->{Arrow result=mock(Arrow.class);when(result.getUniqueId()).thenReturn(UUID.randomUUID());doReturn(pdc()).when(result).getPersistentDataContainer();when(result.isValid()).thenReturn(true);((Consumer<Arrow>)i.getArgument(2)).accept(result);created.add(result);return result;});
        }
        Player player(double x,double z) {
            var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.getWorld()).thenReturn(world);positions.put(p,new Location(world,x,64,z));when(p.getLocation()).thenAnswer(i->positions.get(p).clone());when(p.getVelocity()).thenAnswer(i->new Vector());
            when(p.isOnline()).thenReturn(true);when(p.isValid()).thenReturn(true);when(p.getGameMode()).thenReturn(GameMode.SURVIVAL);when(p.getHealth()).thenReturn(20.0);when(p.getWidth()).thenReturn(.6);when(p.getHeight()).thenReturn(1.8);
            var health=mock(AttributeInstance.class);when(health.getValue()).thenReturn(20.0);when(p.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
            when(p.teleport(any(Location.class))).thenReturn(true);
            when(p.addPotionEffect(any())).thenAnswer(i->{effects.put(p,i.getArgument(0));return true;});when(p.getPotionEffect(any())).thenAnswer(i->effects.get(p));when(p.hasPotionEffect(any())).thenAnswer(i->effects.containsKey(p));doAnswer(i->{effects.remove(p);return null;}).when(p).removePotionEffect(any());return p;
        }
        void cast(String id){cast(id,Map.of());}
        void cast(String id,Map<String,Object> raw) {var a=registry.get(id).orElseThrow();a.execute(new AbilityContext(caster,List.of(player,ally),new ParamValues(raw,a.params()),host,null));}
        void step(int count){for(int i=0;i<count;i++){var queue=tasks.remove(tick.incrementAndGet());if(queue!=null)queue.forEach(Runnable::run);}}
        @Override public void close(){service.close();FallProtection.shared().close();bukkit.close();}
    }
    static PersistentDataContainer pdc() {
        var p=mock(PersistentDataContainer.class);Map<NamespacedKey,Object> data=new HashMap<>();
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(2));return null;}).when(p).set(any(),any(),any());when(p.get(any(),any())).thenAnswer(i->data.get(i.getArgument(0)));when(p.has(any(),any())).thenAnswer(i->data.containsKey(i.getArgument(0)));return p;
    }
    @ParameterizedTest @ValueSource(strings={"vortex","inverted_gravity","cracked_floor","sweep","falling_pillars","poison_pools","charged_beam","arrow_rain","rift"})
    void visibleWarningPrecedesAllEffectsAndDamage(String id) {
        try(var f=new Fixture()) {
            f.cast(id);f.step(9);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.player,never()).teleport(any(Location.class));verify(f.player,never()).addPotionEffect(any());assertEquals(0,f.created.size());verify(f.blocks,never()).place(any(),any(),anyInt());
            verify(f.player,atLeastOnce()).sendActionBar(any(net.kyori.adventure.text.Component.class));verify(f.player,atLeastOnce()).spawnParticle(any(Particle.class),any(Location.class),anyInt(),anyDouble(),anyDouble(),anyDouble(),anyDouble());
        }
    }
    @ParameterizedTest @MethodSource("releases") void cleanupCancelsPendingAndActiveZones(String id,String reason) {
        try(var f=new Fixture()) {
            f.cast(id);f.step(45);
            switch(reason) {
                case "death" -> {var event=mock(EntityDeathEvent.class);when(event.getEntity()).thenReturn(f.entity);f.service.death(event);}
                case "retire" -> ZoneService.cleanup(f.caster);case "reload" -> ZoneService.reset();case "disable" -> f.service.close();
            }
            assertEquals(0,f.service.activeCount());assertEquals(0,f.service.arrowCount());
            int count=f.created.size();f.step(700);assertEquals(count,f.created.size());
            for(Arrow arrow:f.created)verify(arrow).remove();
            if(id.equals("falling_pillars"))verify(f.blocks,atLeastOnce()).restore(any(Block.class));
        }
    }
    @Test void beamAndSweepKeepTheirWarningDirectionEvenWhenCasterAndPlayersMove() {
        for(String id:List.of("charged_beam","sweep"))try(var f=new Fixture()) {
            f.positions.put(f.ally,new Location(f.world,0,64,3));f.cast(id);when(f.entity.getLocation()).thenReturn(new Location(f.world,0,64,0,180,0));f.positions.put(f.player,new Location(f.world,0,64,-2));f.step(45);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.ally).damage(anyDouble(),eq(f.entity));
        }
    }
    @Test void solidWallStopsBeamAndDoesNotLoadChunks() {
        try(var f=new Fixture()) {
            when(f.world.getBlockAt(0,65,1).isSolid()).thenReturn(true);f.cast("charged_beam");f.step(40);
            verify(f.player,never()).damage(anyDouble(),any(Entity.class));verify(f.world,never()).getChunkAt(anyInt(),anyInt());
        }
    }
    @Test void poolsDamageOnlyValidTargetsAndCanBeEscaped() {
        try(var f=new Fixture()) {
            f.cast("poison_pools",Map.of("count",1));f.step(20);verify(f.player).damage(1, f.entity);verify(f.foreign,never()).damage(anyDouble(),any(Entity.class));
            f.positions.put(f.player,new Location(f.world,20,64,20));f.step(200);verify(f.player,times(1)).damage(1,f.entity);assertEquals(0,f.service.activeCount());
        }
    }
    @Test void zonesAndArrowsRespectLiveLimitsAndArrowsAreMarkedUncollectableAndExpired() {
        try(var f=new Fixture()) {
            f.cast("poison_pools",Map.of("count",6));f.cast("poison_pools",Map.of("count",6));assertEquals(6,f.service.activeCount());f.cast("arrow_rain");assertEquals(7,f.service.activeCount());ZoneService.reset();
            when(f.platform.limits()).thenReturn(new dev.dasan.customdungeons.config.PluginConfig.PerformanceLimits(1,1,48));
            f.cast("arrow_rain",Map.of("rate",20,"ticks",160));f.step(100);assertEquals(8,f.service.arrowCount());
            for(Arrow a:f.created){assertTrue(a.getPersistentDataContainer().has(ZoneService.ARROW,PersistentDataType.BYTE));verify(a).setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);verify(a).setShooter(f.entity);}
            f.step(240);assertEquals(0,f.service.arrowCount());assertEquals(0,f.service.activeCount());
        }
    }
    @Test void nativeArrowHitsAreFilteredAndRemovedWithoutBypassingShieldDamage() {
        try(var f=new Fixture()) {
            f.cast("arrow_rain");f.step(35);Arrow arrow=f.created.getFirst();
            var invalid=mock(EntityDamageByEntityEvent.class);when(invalid.getDamager()).thenReturn(arrow);when(invalid.getEntity()).thenReturn(f.foreign);f.service.arrowDamage(invalid);verify(invalid).setCancelled(true);
            var shield=mock(EntityDamageByEntityEvent.class);when(shield.getDamager()).thenReturn(arrow);when(shield.getEntity()).thenReturn(f.player);when(shield.getFinalDamage()).thenReturn(0.0);f.service.arrowDamage(shield);verify(shield).setDamage(3.0);verify(shield,never()).setCancelled(true);verify(f.player,never()).damage(anyDouble(),any(Entity.class));
            var hit=mock(ProjectileHitEvent.class);when(hit.getEntity()).thenReturn(arrow);when(hit.getHitEntity()).thenReturn(f.player);f.service.hit(hit);f.step(1);verify(arrow).remove();
        }
    }
    @Test void markedRainArrowsNeverIgnitePlayersEvenIfAnotherSourceSetsThemOnFire() {
        try(var f=new Fixture()) {
            f.cast("arrow_rain");f.step(35);
            var event=mock(EntityCombustByEntityEvent.class);when(event.getCombuster()).thenReturn(f.created.getFirst());when(event.getEntity()).thenReturn(f.player);
            f.service.combust(event);verify(event).setCancelled(true);
        }
    }
    @Test void gravityIsNotAnExclusiveControlAndReleasesOnExitOrQuitWithFallProtection() {
        try(var f=new Fixture()) {
            f.cast("inverted_gravity");f.step(30);assertTrue(f.player.hasPotionEffect(PotionEffectType.LEVITATION));verify(f.player,never()).setVelocity(any());verify(f.player,never()).teleport(any(Location.class));
            f.positions.put(f.player,new Location(f.world,20,64,20));f.step(1);assertFalse(f.player.hasPotionEffect(PotionEffectType.LEVITATION));assertTrue(FallProtection.shared().hasPending());
            var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(f.ally);f.service.quit(quit);assertFalse(f.ally.hasPotionEffect(PotionEffectType.LEVITATION));
        }
    }
    @Test void crackedFloorRepeatsWarningBetweenAlternatingWaves() {
        try(var f=new Fixture()) {
            f.cast("cracked_floor",Map.of("waves",3,"warning",20));f.step(20);assertEquals(1,f.service.activeCount());f.step(40);assertEquals(0,f.service.activeCount());verify(f.player,atLeastOnce()).damage(8,f.entity);verify(f.player,atLeastOnce()).setVelocity(any());
        }
    }
    @Test void riftCancelsWithoutSafeTerrainAndTeleportsOnlyInsideHostOnLoadedGround() {
        try(var f=new Fixture()) {
            when(f.world.getHighestBlockYAt(anyInt(),anyInt(),any())).thenReturn(-64);f.cast("rift");f.step(20);verify(f.player,never()).teleport(any(Location.class));verify(f.player,never()).addPotionEffect(any());
            when(f.world.getHighestBlockYAt(anyInt(),anyInt(),any())).thenReturn(63);f.cast("rift");f.step(20);verify(f.player).teleport(argThat((Location at)->f.host.area().contains(at)&&at.getY()==64));verify(f.player).addPotionEffect(argThat(effect->effect.getType()==PotionEffectType.BLINDNESS&&effect.getDuration()==20));verify(f.world,never()).getChunkAt(anyInt(),anyInt());
        }
    }
    @Test void externalLevitationReplacementSurvivesOwnerCleanup() {
        try(var f=new Fixture()) {
            f.cast("inverted_gravity");f.step(30);
            var replacement=new PotionEffect(PotionEffectType.LEVITATION,20,0);
            f.effects.put(f.player,replacement);
            var event=mock(EntityPotionEffectEvent.class);when(event.getEntity()).thenReturn(f.player);when(event.getModifiedType()).thenReturn(PotionEffectType.LEVITATION);when(event.getAction()).thenReturn(EntityPotionEffectEvent.Action.CHANGED);when(event.isOverride()).thenReturn(true);
            f.service.potion(event);ZoneService.cleanup(f.caster);assertSame(replacement,f.effects.get(f.player));
        }
    }
    @Test void pillarsCanHaveEightShadowsAndZeroObstacleNeverPlacesBlocks() {
        try(var f=new Fixture()) {
            for(int i=2;i<8;i++)f.players.add(f.player(i,2));
            var ability=f.registry.get("falling_pillars").orElseThrow();
            ability.execute(new AbilityContext(f.caster,new ArrayList<LivingEntity>(f.players),new ParamValues(Map.of("count",8,"obstacle-ticks",0),ability.params()),f.host,null));
            assertEquals(8,f.service.activeCount());f.step(40);verify(f.blocks,never()).place(any(),any(),anyInt());assertEquals(0,f.service.activeCount());
        }
    }
    @Test void arrowDamageFromFullHealthAndConcurrentHitsKeepOneHealth() {
        try(var f=new Fixture()) {
            f.cast("arrow_rain",Map.of("damage",10));f.step(35);Arrow arrow=f.created.getFirst();
            var first=mock(EntityDamageByEntityEvent.class);when(first.getDamager()).thenReturn(arrow);when(first.getEntity()).thenReturn(f.player);when(first.getDamage()).thenReturn(30.0);when(first.getFinalDamage()).thenReturn(30.0);f.service.arrowDamage(first);verify(first).setDamage(19.0);
            when(f.player.getHealth()).thenReturn(1.0);
            var next=mock(EntityDamageByEntityEvent.class);when(next.getDamager()).thenReturn(arrow);when(next.getEntity()).thenReturn(f.player);when(next.getDamage()).thenReturn(30.0);when(next.getFinalDamage()).thenReturn(30.0);f.service.arrowDamage(next);verify(next).setDamage(0.0);
        }
    }
    @Test void riftDoesNothingIfPlayerLeavesPortalOrChunksAreUnavailable() {
        try(var f=new Fixture()) {
            f.cast("rift");f.positions.put(f.player,new Location(f.world,30,64,30));f.positions.put(f.ally,new Location(f.world,30,64,30));f.step(20);verify(f.player,never()).teleport(any(Location.class));
            f.positions.put(f.player,new Location(f.world,0,64,2));f.cast("rift");when(f.world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);f.step(20);verify(f.player,never()).teleport(any(Location.class));verify(f.world,never()).getHighestBlockYAt(anyInt(),anyInt(),any(HeightMap.class));
        }
    }
    @Test void loadedCrashRemnantsAreRemoved() {
        try(var f=new Fixture()) {
            Arrow arrow=mock(Arrow.class);var data=pdc();data.set(ZoneService.ARROW,PersistentDataType.BYTE,(byte)1);when(arrow.getPersistentDataContainer()).thenReturn(data);
            var event=mock(EntitiesLoadEvent.class);when(event.getEntities()).thenReturn(List.of(arrow));f.service.load(event);verify(arrow).remove();
        }
    }
}
