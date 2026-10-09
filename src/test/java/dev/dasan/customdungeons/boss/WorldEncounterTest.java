package dev.dasan.customdungeons.boss;

import dev.dasan.customdungeons.ability.AbilityRegistry;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.config.EntityHeights;
import dev.dasan.customdungeons.mob.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Messages;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorldEncounterTest {
    @TempDir Path temp;
    MockedStatic<Bukkit> api;
    World world;Mob mob;MobsPlatform platform;WorldBossService service;WorldEncounter encounter;Player a,b;
    MobTemplate template;
    @BeforeEach void setup() {
        PaperApiTestBootstrap.initialize();api=mockStatic(Bukkit.class);
        var plugin=mock(Plugin.class,RETURNS_DEEP_STUBS);when(plugin.getDataFolder()).thenReturn(temp.toFile());
        when(plugin.isEnabled()).thenReturn(true);
        api.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));api.when(Bukkit::getWorlds).thenReturn(List.of());
        world=mock(World.class);when(world.getName()).thenReturn("world");when(world.getMinHeight()).thenReturn(-64);when(world.getMaxHeight()).thenReturn(320);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        a=player(0,GameMode.SURVIVAL);b=player(0,GameMode.ADVENTURE);when(world.getPlayers()).thenReturn(List.of(a,b));when(world.getNearbyPlayers(any(Location.class),eq(48d))).thenReturn(List.of(a,b));
        platform=mock(MobsPlatform.class);when(platform.messages()).thenReturn(new Messages());when(platform.abilityRegistry()).thenReturn(new AbilityRegistry());when(platform.limits()).thenReturn(new PluginConfig.PerformanceLimits(50,1,48));
        template=new MobTemplate("boss","ZOMBIE","Boss",5000,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),true,"PURPLE",null,List.of(),false)
                .withWorldBoss(new WorldBossDef("world",-10,10,-10,10,1,48,5,new RewardDef(List.of(),0,17,List.of())));
        when(platform.templates()).thenReturn(Map.of("boss",template));
        service=new WorldBossService(plugin,platform,new BossRegistry(),new EntityHeights(Map.of()),20,p->true,()->false);
        encounter=new WorldEncounter(service,template,new Location(world,0,64,0));service.encounters.put(encounter.id,encounter);
        mob=mock(Mob.class);when(mob.getUniqueId()).thenReturn(UUID.randomUUID());when(mob.getWorld()).thenReturn(world);when(mob.getLocation()).thenReturn(new Location(world,0,64,0));when(mob.isValid()).thenReturn(true);when(mob.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(mob.getHealth()).thenReturn(5000d);var health=mock(AttributeInstance.class);when(health.getValue()).thenReturn(5000d);when(mob.getAttribute(Attribute.MAX_HEALTH)).thenReturn(health);
        var data=mob.getPersistentDataContainer();var values=new HashMap<NamespacedKey,Object>();
        when(data.get(any(),any())).thenAnswer(c->values.get(c.getArgument(0)));
        when(data.has(any(),any())).thenAnswer(c->values.containsKey(c.getArgument(0)));
        doAnswer(c->{values.put(c.getArgument(0),c.getArgument(2));return null;}).when(data).set(any(),any(),any());
        values.put(WorldBossService.MARKER,encounter.id.toString());
        var events=plugin.getServer().getPluginManager();
        doAnswer(c->{if(c.getArgument(0) instanceof MobDamageAppliedEvent applied)service.countDamage(applied);return null;})
                .when(events).callEvent(any());
        encounter.principal=mob;encounter.damage=new DamageLedger(5000,5);encounter.active.put(mob.getUniqueId(),new ActiveMob(mob,template,encounter));encounter.track(mob);
    }
    private Player player(double x,GameMode mode) {
        var p=mock(Player.class);when(p.getUniqueId()).thenReturn(UUID.randomUUID());when(p.isOnline()).thenReturn(true);when(p.isValid()).thenReturn(true);when(p.getGameMode()).thenReturn(mode);when(p.getWorld()).thenReturn(world);when(p.getLocation()).thenAnswer(c->new Location(world,x,64,0));return p;
    }
    @AfterEach void close(){if(service!=null)service.close();api.close();}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void thiefConstructionReturnDropsOnceAndOtherwiseKeepsMainPlatformDelivery(boolean building) {
        var item=mock(org.bukkit.inventory.ItemStack.class);when(item.clone()).thenReturn(item);
        api.when(()->Bukkit.getPlayer(a.getUniqueId())).thenReturn(a);
        ThiefReturns.constructionMode(p->building);
        try {
            encounter.onItemStolen(a.getUniqueId(),item,encounter.active.get(mob.getUniqueId()));
            encounter.returnStolenTo(a);encounter.returnStolenTo(a);encounter.close();
            verify(world,building?times(1):never()).dropItemNaturally(a.getLocation(),item);
            verify(platform,building?never():times(1)).deliverReward(eq(a),argThat(r->r.items().equals(List.of(item))&&r.money()==0&&r.xp()==0&&r.commands().isEmpty()));
        } finally {ThiefReturns.constructionMode(p->false);}
    }
    @Test void audienceUsesRadiusAndCombatModesAndExcludesDungeonPlayers() {
        var creative=player(0,GameMode.CREATIVE);var spectator=player(0,GameMode.SPECTATOR);var outside=player(49,GameMode.SURVIVAL);
        when(world.getNearbyPlayers(any(Location.class),eq(48d))).thenReturn(List.of(a,b,creative,spectator,outside));assertEquals(List.of(a,b),List.copyOf(encounter.players()));
        assertEquals(List.of(a,b),List.copyOf(encounter.audience(mob.getLocation())));
        assertFalse(service.ticking());
    }
    @Test void damageRewardsOnlyQualifiedPlayerAndProjectileCredit() {
        var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(mob);when(hit.getDamager()).thenReturn(a);when(hit.getFinalDamage()).thenReturn(249d);
        applyDamage(hit);
        var projectile=mock(Projectile.class);when(projectile.getShooter()).thenReturn(b);
        var second=mock(EntityDamageByEntityEvent.class);when(second.getEntity()).thenReturn(mob);when(second.getDamager()).thenReturn(projectile);when(second.getFinalDamage()).thenReturn(250d);
        applyDamage(second);encounter.reward(true);
        verify(platform).deliverReward(b,template.worldBoss().reward());verify(platform,never()).deliverReward(eq(a),any());
    }
    @Test void virtualLedgerCountsOriginalDamageAfterMirrorWasReduced() {
        var data=mob.getPersistentDataContainer();when(data.has(MobKeys.VIRTUAL_MAX_HEALTH,PersistentDataType.DOUBLE)).thenReturn(true);when(data.get(MobKeys.VIRTUAL_HEALTH,PersistentDataType.DOUBLE)).thenReturn(5000d);
        var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(mob);when(hit.getDamager()).thenReturn(a);when(hit.getFinalDamage()).thenReturn(300d);when(mob.getHealth()).thenReturn(10d);
        applyDamage(hit);assertEquals(300,encounter.damage.damage(a.getUniqueId()));encounter.reward(true);verify(platform).deliverReward(a,template.worldBoss().reward());
    }
    void applyDamage(EntityDamageEvent event) {if(event.getCause()==null)when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.CUSTOM);new MobCombatListener(service.plugin).damaged(event);}
    @Test void petOwnerAndProjectileShooterReceiveCreditButEnvironmentalDamageDoesNot() {
        var pet=mock(Wolf.class);when(pet.getOwner()).thenReturn(a);
        var petShot=mock(Projectile.class);when(petShot.getShooter()).thenReturn(pet);
        var hit=mock(EntityDamageByEntityEvent.class);when(hit.getEntity()).thenReturn(mob);when(hit.getDamager()).thenReturn(petShot);when(hit.getFinalDamage()).thenReturn(250d);
        applyDamage(hit);assertEquals(250,encounter.damage.damage(a.getUniqueId()));
        var direct=mock(EntityDamageByEntityEvent.class);when(direct.getEntity()).thenReturn(mob);when(direct.getDamager()).thenReturn(pet);when(direct.getFinalDamage()).thenReturn(100d);
        applyDamage(direct);assertEquals(350,encounter.damage.damage(a.getUniqueId()));
        var environmental=mock(EntityDamageEvent.class);when(environmental.getEntity()).thenReturn(mob);when(environmental.getCause()).thenReturn(EntityDamageEvent.DamageCause.LAVA);when(environmental.getFinalDamage()).thenReturn(1000d);
        applyDamage(environmental);assertEquals(350,encounter.damage.damage(a.getUniqueId()));assertEquals(1,encounter.contributors.size());
    }
    @Test void killAndDespawnNeverPayAndCloseIsIdempotent() {
        encounter.contributors.put(a.getUniqueId(),a);encounter.damage.add(a.getUniqueId(),5000,5000);
        var kill=mock(EntityDamageEvent.class);when(kill.getCause()).thenReturn(EntityDamageEvent.DamageCause.KILL);assertFalse(WorldBossService.rewardDeath(kill,kill));
        encounter.reward(false);encounter.close();encounter.close();verify(platform,never()).deliverReward(any(),any());verify(mob,times(1)).remove();assertTrue(service.encounters.isEmpty());assertFalse(service.ticking());
    }
    @Test void leashReturnsToSpawnOnlyFromLoadedChunks() {
        when(mob.getLocation()).thenReturn(new Location(world,11,64,0));for(int i=0;i<20;i++)encounter.tick();verify(mob).teleport(encounter.origin);
    }
    @Test void unloadRemovesEncounterAndMarkedOrphansAreCleaned() {
        var event=mock(EntitiesUnloadEvent.class);when(event.getEntities()).thenReturn(List.of(mob));service.unloaded(event);assertTrue(encounter.closed);verify(mob).remove();
        var orphan=mock(Entity.class);when(orphan.getUniqueId()).thenReturn(UUID.randomUUID());when(orphan.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));when(orphan.getPersistentDataContainer().has(WorldBossService.MARKER)).thenReturn(true);
        var load=mock(EntitiesLoadEvent.class);when(load.getEntities()).thenReturn(List.of(orphan));service.loaded(load);verify(orphan).remove();
    }
    @Test void registryPreservesCodeBaseAndAllowsOnlyOptedInWorldOverrides() {
        var registry=new BossRegistry();registry.register(template,true);
        var override=new MobTemplate("boss","CREEPER","Bad base",1,1,0,0,1,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false).withWorldBoss(WorldBossDef.defaults("other"));
        assertEquals(template.withWorldBoss(override.worldBoss()),registry.all(Map.of("boss",override)).get("boss"));
        var locked=new BossRegistry();locked.register(template,false);assertEquals(template,locked.all(Map.of("boss",override)).get("boss"));assertThrows(IllegalArgumentException.class,()->locked.register(template,false));
    }
}
