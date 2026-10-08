package dev.dasan.customdungeons.gui.menu;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.bukkit.event.inventory.ClickType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class T55ReviewPermissionTest {
    DungeonMenuFlowTest fixture;
    @BeforeEach void setup() {fixture=new DungeonMenuFlowTest();fixture.setup();}
    @AfterEach void teardown() throws Exception {fixture.cleanup();}
    private dev.dasan.customdungeons.boss.WorldBossService runtime() {
        var runtime=mock(dev.dasan.customdungeons.boss.WorldBossService.class);
        when(fixture.plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.boss.WorldBossService.class)).thenReturn(runtime);
        when(fixture.plugin.bossRegistry()).thenReturn(new dev.dasan.customdungeons.boss.BossRegistry());
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(true);
        return runtime;
    }
    @Test void confirmingBossDeletionMustRecheckRevokedBossPermission() {
        when(fixture.plugin.bossRegistry()).thenReturn(new dev.dasan.customdungeons.boss.BossRegistry());
        var boss=fixture.store.mobs().get("mob").withWorldBoss(dev.dasan.customdungeons.model.WorldBossDef.defaults("world"));
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(true);
        when(fixture.store.deleteMob("mob")).thenReturn(CompletableFuture.completedFuture(null));
        var menu=new MobLibraryMenu(fixture.player,fixture.list);
        menu.button(boss).onClick().handle(fixture.player,ClickType.SHIFT_RIGHT);fixture.drain();
        assertNotNull(fixture.confirm);
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(false);
        fixture.confirm.run();
        verify(fixture.store,never()).deleteMob("mob");
    }
    @Test void confirmingRetirementRechecksRevokedPermission() {
        var runtime=runtime();
        WorldBossMenu.despawn(fixture.player,"mob",fixture.list);
        assertNotNull(fixture.confirm);
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(false);
        fixture.confirm.run();
        verify(runtime,never()).despawn(any(),anyString());
    }
    @Test void savingBossBaseRechecksPermissionEvenWhenWorldSectionIsUnchanged() {
        runtime();
        var boss=fixture.store.mobs().get("mob").withWorldBoss(dev.dasan.customdungeons.model.WorldBossDef.defaults("world"));
        var menu=new MobMenu(fixture.player,boss,fixture.list);menu.data.name="changed";
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(false);
        menu.save();
        verify(fixture.store,never()).save(any(dev.dasan.customdungeons.model.MobTemplate.class));
    }
    @Test void queuedSpawnRechecksRevokedPermission() {
        var runtime=runtime();
        var boss=fixture.store.mobs().get("mob").withWorldBoss(dev.dasan.customdungeons.model.WorldBossDef.defaults("world"));
        new BossListMenu(fixture.player,fixture.list).button(boss).onClick().handle(fixture.player,ClickType.RIGHT);
        when(fixture.player.hasPermission("customdungeons.admin.boss")).thenReturn(false);
        fixture.drain();
        verify(runtime,never()).spawn(any(),anyString());
    }
    @Test void deletingTemplateWarnsAboutLivingBossesAndOrphanEntryCanBeRetired() {
        var runtime=runtime();when(runtime.alive("mob")).thenReturn(2);
        var boss=fixture.store.mobs().get("mob").withWorldBoss(dev.dasan.customdungeons.model.WorldBossDef.defaults("world"));
        new MobLibraryMenu(fixture.player,fixture.list).button(boss).onClick().handle(fixture.player,ClickType.SHIFT_RIGHT);fixture.drain();
        verify(fixture.plugin.messages()).send(eq(fixture.player),eq("gui.world-boss.delete-living-warning"),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        when(runtime.orphaned("mob")).thenReturn(true);when(runtime.listed()).thenReturn(java.util.Map.of("mob",boss));
        var menu=new BossListMenu(fixture.player,fixture.list);assertEquals(java.util.List.of(boss),menu.items());
        menu.button(boss).onClick().handle(fixture.player,ClickType.RIGHT);fixture.drain();
        verify(runtime,never()).spawn(any(),anyString());
        menu.button(boss).onClick().handle(fixture.player,ClickType.SHIFT_RIGHT);fixture.drain();fixture.confirm.run();
        verify(runtime).despawn(fixture.player,"mob");
    }
}
