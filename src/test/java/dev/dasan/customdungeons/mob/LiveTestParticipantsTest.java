package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.session.*;
import java.nio.file.Path;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveTestParticipantsTest {
    static { PaperApiTestBootstrap.initialize(); }
    @TempDir Path directory;
    World world;
    Player admin;
    Mob principal;
    SessionManager sessions;
    LiveTestService.Manager services;
    LiveTestService live;
    ActiveMob mob;
    @BeforeEach void setup() {
        world=mock(World.class);
        admin=player(GameMode.SURVIVAL,0);
        when(admin.hasPermission("customdungeons.admin.test")).thenReturn(true);
        var plugin=mock(CustomDungeonsPlugin.class);
        when(plugin.abilityRegistry()).thenReturn(new AbilityRegistry());
        when(plugin.messages()).thenReturn(mock(dev.dasan.customdungeons.text.Messages.class));
        sessions=mock(SessionManager.class);
        when(plugin.sessionManager()).thenReturn(sessions);
        var config=new PluginConfig("","es",null,"world",false,null,
                new PluginConfig.PerformanceLimits(50,1,48),Set.of(),List.of(),null,null,300,Map.of());
        when(plugin.templates()).thenReturn(Map.of());
        when(plugin.limits()).thenReturn(config.limits());
        services=new LiveTestService.Manager(plugin,plugin,config.liveTestMaxSeconds(),dev.dasan.customdungeons.session.LiveTestIntegration.validation(plugin,config),candidate -> plugin.sessionManager()==null || plugin.sessionManager().sessionOf(candidate.getUniqueId()).isEmpty(),directory.resolve("blocks"));
        live=new LiveTestService(services,admin);
        services.tests.put(admin.getUniqueId(),live);
        principal=mock(Mob.class);
        when(principal.getUniqueId()).thenReturn(UUID.randomUUID());
        when(principal.isValid()).thenReturn(true);
        when(principal.getWorld()).thenReturn(world);
        when(principal.getType()).thenReturn(EntityType.HUSK);
        when(principal.getLocation()).thenReturn(new Location(world,0,64,0));
        var pdc=mock(PersistentDataContainer.class);
        when(principal.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(live.id().toString());
        live.principal=principal;
        mob=new ActiveMob(principal,new MobTemplate("test","HUSK","",0,0,0,0,0,
                Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false),live);
    }
    @AfterEach void cleanup() { live.close(); services.journal.close(); }
    Player player(GameMode mode,double x) {
        var p=mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isOnline()).thenReturn(true); when(p.isValid()).thenReturn(true);
        when(p.getGameMode()).thenReturn(mode); when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world,x,64,0));
        when(p.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        return p;
    }
    void populate(Player... players) { when(world.getPlayers()).thenReturn(List.of(players)); live.tick(); }
    @Test void includesNearbySurvivalAndAdventureButExcludesOtherModesSessionsAndWorlds() {
        var survival=player(GameMode.SURVIVAL,48);
        var adventure=player(GameMode.ADVENTURE,2);
        var creative=player(GameMode.CREATIVE,1);
        var spectator=player(GameMode.SPECTATOR,1);
        var inDungeon=player(GameMode.SURVIVAL,1);
        when(sessions.sessionOf(inDungeon.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        var far=player(GameMode.SURVIVAL,48.01);
        var otherWorld=player(GameMode.SURVIVAL,1);
        when(otherWorld.getLocation()).thenReturn(new Location(mock(World.class),1,64,0));
        var dead=player(GameMode.SURVIVAL,1); when(dead.isDead()).thenReturn(true);
        var offline=player(GameMode.SURVIVAL,1); when(offline.isOnline()).thenReturn(false);
        populate(admin,survival,adventure,creative,spectator,inDungeon,far,otherWorld,dead,offline);
        assertEquals(List.of(admin,survival,adventure),live.players());
        assertThrows(UnsupportedOperationException.class,()->live.players().clear());
        assertEquals(List.of(admin,survival,adventure),TargetSelector.select(mob,TargetMode.ALL_IN_RADIUS,48));
        when(principal.getTarget()).thenReturn(survival);
        assertEquals(List.of(survival),TargetSelector.select(mob,TargetMode.CURRENT_TARGET,48));
        Effects.damage(survival,6,mob); verify(survival).damage(6,principal);
        for(var excluded:List.of(creative,spectator,inDungeon,far,otherWorld,dead,offline)) {
            when(principal.getTarget()).thenReturn(excluded);
            assertTrue(TargetSelector.select(mob,TargetMode.CURRENT_TARGET,48).isEmpty());
            Effects.damage(excluded,6,mob); verify(excluded,never()).damage(anyDouble(),any(Entity.class));
        }
    }
    @Test void snapshotIsReadOnlyAndCalculatedOncePerTickAroundMovingMob() {
        var near=player(GameMode.SURVIVAL,2); populate(admin,near);
        var snapshot=live.players();
        when(near.getLocation()).thenReturn(new Location(world,100,64,0));
        for(int i=0;i<20;i++)assertEquals(snapshot,live.players());
        verify(world,times(1)).getPlayers();
        verify(sessions,times(21)).sessionOf(near.getUniqueId());
        live.tick(); assertEquals(List.of(admin),live.players());
        when(principal.getLocation()).thenReturn(new Location(world,100,64,0));
        live.tick(); assertEquals(List.of(admin,near),live.players());
        verify(world,times(3)).getPlayers();
        live.close(); assertTrue(live.players().isEmpty());
    }
    @Test void nativeTargetAndDamageUseSameParticipantsAndRejectCreativeAdminToo() {
        var near=player(GameMode.SURVIVAL,1);
        var creative=player(GameMode.CREATIVE,1);
        var spectator=player(GameMode.SPECTATOR,1);
        var inDungeon=player(GameMode.ADVENTURE,1);
        when(sessions.sessionOf(inDungeon.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        populate(admin,near,creative,spectator,inDungeon);
        assertCombat(near,false); assertCombat(admin,false);
        for(var excluded:List.of(creative,spectator,inDungeon))assertCombat(excluded,true);
        // Mode changes are protected even before the next cached snapshot.
        when(near.getGameMode()).thenReturn(GameMode.CREATIVE); assertCombat(near,true);
        when(admin.getGameMode()).thenReturn(GameMode.CREATIVE); assertCombat(admin,true);
        var clear=mock(EntityTargetLivingEntityEvent.class);
        when(clear.getEntity()).thenReturn(principal); services.target(clear);
        verify(clear,never()).setCancelled(true);
    }
    void assertCombat(Player player,boolean cancelled) {
        var target=mock(EntityTargetLivingEntityEvent.class);
        when(target.getEntity()).thenReturn(principal); when(target.getTarget()).thenReturn(player);
        services.target(target);
        verify(target,cancelled ? times(1):never()).setCancelled(true);
        var damage=mock(EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(principal); when(damage.getEntity()).thenReturn(player);
        services.damage(damage);
        verify(damage,cancelled ? times(1):never()).setCancelled(true);
    }
    @Test void potionsAndCloudsAffectParticipantsAndExcludeCreativeAndDungeonPlayers() {
        var near=player(GameMode.ADVENTURE,1); var creative=player(GameMode.CREATIVE,1);
        var inDungeon=player(GameMode.SURVIVAL,1);
        when(sessions.sessionOf(inDungeon.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        populate(admin,near,creative,inDungeon);
        var potion=mock(ThrownPotion.class);
        when(potion.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
        when(potion.getShooter()).thenReturn(principal);
        var splash=mock(PotionSplashEvent.class);
        when(splash.getPotion()).thenReturn(potion);
        when(splash.getAffectedEntities()).thenReturn(List.of(admin,near,creative,inDungeon));
        services.splash(splash);
        verify(splash,never()).setIntensity(eq(near),anyDouble());
        verify(splash).setIntensity(creative,0); verify(splash).setIntensity(inDungeon,0);
        var cloud=mock(AreaEffectCloud.class);
        var cloudData=mock(PersistentDataContainer.class);
        when(cloud.getPersistentDataContainer()).thenReturn(cloudData);
        when(cloudData.get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(live.id().toString());
        var event=mock(AreaEffectCloudApplyEvent.class);
        when(event.getEntity()).thenReturn(cloud);
        var affected=new ArrayList<LivingEntity>(List.of(admin,near,creative,inDungeon));
        when(event.getAffectedEntities()).thenReturn(affected);
        services.cloud(event); assertEquals(List.of(admin,near),affected);
    }
    @Test void nearbyThiefVictimsReceiveTheirOwnItemsOnCloseEvenAfterLeavingRadius() {
        var near=player(GameMode.SURVIVAL,1); populate(admin,near);
        var adminInventory=mock(PlayerInventory.class); when(admin.getInventory()).thenReturn(adminInventory);
        var nearInventory=mock(PlayerInventory.class); when(near.getInventory()).thenReturn(nearInventory);
        var diamond=mock(ItemStack.class); when(diamond.clone()).thenReturn(diamond);
        var gold=mock(ItemStack.class); when(gold.clone()).thenReturn(gold);
        services.executing=live; services.spawned(new EntitySpawnEvent(principal)); services.executing=null;
        var owned=live.mobs().iterator().next();
        live.onItemStolen(near.getUniqueId(),diamond,owned);
        live.onItemStolen(admin.getUniqueId(),gold,owned);
        when(near.getLocation()).thenReturn(new Location(world,100,64,0)); live.tick();
        live.close(); verify(nearInventory).addItem(diamond); verify(adminInventory).addItem(gold);
        verify(adminInventory,never()).addItem(diamond);
    }
    @Test void heldTargetIsClearedWhenParticipantJoinsDungeonOrChangesMode() {
        var near=player(GameMode.SURVIVAL,1); populate(admin,near);
        services.executing=live; services.spawned(new EntitySpawnEvent(principal)); services.executing=null;
        when(principal.getTarget()).thenReturn(near);
        when(sessions.sessionOf(near.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        live.tick(); verify(principal).setTarget(null);
        assertFalse(live.players().contains(near)); assertCombat(near,true);
        when(sessions.sessionOf(near.getUniqueId())).thenReturn(Optional.empty());
        when(near.getGameMode()).thenReturn(GameMode.CREATIVE);
        live.tick(); verify(principal,times(2)).setTarget(null); assertCombat(near,true);
    }
    @Test void quittingBystanderReceivesStolenItemOnceWithoutEndingAdminsTest() {
        var near=player(GameMode.SURVIVAL,1); populate(admin,near);
        var inventory=mock(PlayerInventory.class); when(near.getInventory()).thenReturn(inventory);
        var item=mock(ItemStack.class); when(item.clone()).thenReturn(item);
        services.executing=live; services.spawned(new EntitySpawnEvent(principal)); services.executing=null;
        live.onItemStolen(near.getUniqueId(),item,live.mobs().iterator().next());
        var quit=mock(org.bukkit.event.player.PlayerQuitEvent.class); when(quit.getPlayer()).thenReturn(near);
        services.quit(quit); verify(inventory).addItem(item);
        assertEquals(live,services.tests.get(admin.getUniqueId()));
        live.close(); verify(inventory,times(1)).addItem(item);
    }

    @Test void bossBarUsesCombatSnapshotAndRemovesPlayersWhoLeaveIt() {
        var near=player(GameMode.ADVENTURE,1); var creative=player(GameMode.CREATIVE,1);
        var inDungeon=player(GameMode.SURVIVAL,1);
        when(sessions.sessionOf(inDungeon.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        populate(admin,near,creative,inDungeon);
        var controller=new BossController(services.factory,Map.of());
        var bar=controller.barFor(mob);
        verify(admin).showBossBar(bar); verify(near).showBossBar(bar);
        verify(creative,never()).showBossBar(any()); verify(inDungeon,never()).showBossBar(any());
        when(near.getLocation()).thenReturn(new Location(world,49,64,0));
        live.tick(); controller.tickMusic(2); verify(near).hideBossBar(bar);
        controller.cleanup(mob); verify(admin).hideBossBar(bar);
    }

    @Test void joiningDungeonAfterSnapshotMustRejectNativeCombatImmediately() {
        var near=player(GameMode.SURVIVAL,1); populate(admin,near);
        when(sessions.sessionOf(near.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        assertCombat(near,true);
        assertFalse(live.players().contains(near));
        verify(world,times(1)).getPlayers();
    }
    @Test void joiningDungeonAfterSnapshotMustRejectAbilityDamageImmediately() {
        var near=player(GameMode.SURVIVAL,1); populate(admin,near);
        when(principal.getTarget()).thenReturn(near);
        when(sessions.sessionOf(near.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        assertTrue(TargetSelector.select(mob,TargetMode.CURRENT_TARGET,48).isEmpty());
        Effects.damage(near,6,mob); verify(near,never()).damage(anyDouble(),any(Entity.class));
        verify(world,times(1)).getPlayers();
    }
    @Test void disconnectingOrDyingAfterSnapshotIsProtectedImmediately() {
        var near=player(GameMode.SURVIVAL,1); populate(admin,near);
        when(principal.getTarget()).thenReturn(near);
        when(near.isOnline()).thenReturn(false);
        assertCombat(near,true); assertFalse(live.players().contains(near));
        assertTrue(TargetSelector.select(mob,TargetMode.CURRENT_TARGET,48).isEmpty());
        when(near.isOnline()).thenReturn(true); when(near.isDead()).thenReturn(true);
        assertCombat(near,true); assertFalse(live.players().contains(near));
        assertTrue(TargetSelector.select(mob,TargetMode.CURRENT_TARGET,48).isEmpty());
        verify(world,times(1)).getPlayers();
    }
    @Test void cachedProximityRevalidatesModesAndDungeonMembershipInBothDirectionsWithoutAnotherTick() {
        var near=player(GameMode.CREATIVE,1);
        when(sessions.sessionOf(near.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        populate(admin,near); assertFalse(live.players().contains(near));
        when(sessions.sessionOf(near.getUniqueId())).thenReturn(Optional.empty());
        when(near.getGameMode()).thenReturn(GameMode.ADVENTURE);
        assertTrue(live.players().contains(near)); assertCombat(near,false);
        for(var mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR)) {
            when(near.getGameMode()).thenReturn(mode);
            assertFalse(live.players().contains(near)); assertCombat(near,true);
            Effects.damage(near,6,mob); verify(near,never()).damage(anyDouble(),any(Entity.class));
        }
        when(near.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertTrue(live.players().contains(near));
        when(near.isValid()).thenReturn(false); assertFalse(live.players().contains(near)); assertCombat(near,true);
        when(admin.getGameMode()).thenReturn(GameMode.CREATIVE); assertFalse(live.players().contains(admin));
        when(admin.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(sessions.sessionOf(admin.getUniqueId())).thenReturn(Optional.of(mock(DungeonSession.class)));
        assertFalse(live.players().contains(admin)); assertCombat(admin,true);
        verify(world,times(1)).getPlayers();
    }

    @Test void decoySpawnDoesNotBecomeALiveTestMinion() {
        var decoy=mock(Mob.class);when(decoy.getUniqueId()).thenReturn(UUID.randomUUID());when(decoy.isValid()).thenReturn(true);
        var data=mock(PersistentDataContainer.class);when(decoy.getPersistentDataContainer()).thenReturn(data);
        when(data.get(MobKeys.SESSION,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn(live.id().toString());
        when(data.has(dev.dasan.customdungeons.ability.combat.CombatService.DECOY,org.bukkit.persistence.PersistentDataType.BYTE)).thenReturn(true);
        var event=mock(org.bukkit.event.entity.EntitySpawnEvent.class);when(event.getEntity()).thenReturn(decoy);services.spawned(event);
        verify(data).set(MobsPlatform.key("live_test"),org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);assertFalse(live.mobs().stream().anyMatch(m->m.entity().equals(decoy)));
    }

}
