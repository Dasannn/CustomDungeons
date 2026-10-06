package dev.dasan.customdungeons.tool;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.gui.MenuListener;
import dev.dasan.customdungeons.gui.menu.BuildMenu;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import dev.dasan.customdungeons.tool.construction.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuildModeServiceTest {
    final CustomDungeonsPlugin plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
    final BuildJournal journal=mock(BuildJournal.class);
    final Player player=mock(Player.class);
    final PlayerInventory inventory=mock(PlayerInventory.class);
    final BuildMenu menu=mock(BuildMenu.class);
    final UUID admin=UUID.randomUUID();
    final Map<org.bukkit.NamespacedKey,String> data=new HashMap<>();
    final ItemStack item=mock(ItemStack.class),cursor=mock(ItemStack.class);
    final ItemStack[] original=new ItemStack[41];
    BuildModeService mode;
    MockedStatic<MenuListener> framework;MockedStatic<BuildMenu> menus;MockedStatic<ItemStack> stacks;

    @BeforeEach void setup() {
        original[2]=item;original[36]=item;original[40]=item;
        when(item.clone()).thenReturn(item);when(cursor.clone()).thenReturn(cursor);
        when(cursor.getType()).thenReturn(org.bukkit.Material.AIR);
        when(player.getUniqueId()).thenReturn(admin);when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);when(player.getInventory()).thenReturn(inventory);
        when(player.getItemOnCursor()).thenReturn(cursor);when(inventory.getContents()).thenReturn(original);when(inventory.getHeldItemSlot()).thenReturn(7);
        var pdc=mock(PersistentDataContainer.class);when(player.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(),eq(PersistentDataType.STRING))).thenAnswer(c->data.get(c.getArgument(0)));
        doAnswer(c->{data.put(c.getArgument(0),c.getArgument(2));return null;}).when(pdc).set(any(),eq(PersistentDataType.STRING),anyString());
        doAnswer(c->{data.remove(c.getArgument(0));return null;}).when(pdc).remove(any());
        when(plugin.isEnabled()).thenReturn(true);when(plugin.messages()).thenReturn(mock(Messages.class));
        when(plugin.sessionManager().sessionOf(admin)).thenReturn(Optional.empty());
        var scheduler=plugin.getServer().getScheduler();
        doAnswer(c->{((Runnable)c.getArgument(1)).run();return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
        var definition=new DungeonDef("draft","draft",false,null,null,1,0,30,3,false,0,0,false,
                new ScalingDef(.25,.15),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());
        when(menu.state()).thenReturn(new BuildState(definition));when(menu.definition()).thenReturn(definition);when(menu.ready()).thenReturn(true);
        when(journal.save(eq(admin),any())).thenReturn(CompletableFuture.completedFuture(null));
        when(journal.backup(any())).thenReturn(CompletableFuture.completedFuture(null));
        when(journal.acknowledge(eq(admin),any())).thenReturn(CompletableFuture.completedFuture(null));
        when(journal.acknowledgeAll(admin)).thenReturn(CompletableFuture.completedFuture(null));
        framework=mockStatic(MenuListener.class);framework.when(MenuListener::instance).thenReturn(mock(MenuListener.class));
        menus=mockStatic(BuildMenu.class);menus.when(()->BuildMenu.prepare(eq(player),anyString(),any())).thenReturn(menu);
        stacks=mockStatic(ItemStack.class);stacks.when(()->ItemStack.serializeItemsAsBytes(any(ItemStack[].class))).thenReturn(new byte[]{1});
        mode=new BuildModeService(plugin,journal,Runnable::run);
    }
    @AfterEach void cleanup() {if(stacks!=null)stacks.close();if(menus!=null)menus.close();if(framework!=null)framework.close();}
    @Test void inventoryIsFrozenButUntouchedUntilBothDurableWritesFinish() {
        var durable=new CompletableFuture<Void>();when(journal.backup(any())).thenReturn(durable);
        mode.enter(player,"draft");assertTrue(mode.protects(admin));assertFalse(mode.active(admin,menu));
        verify(inventory,never()).clear();verify(player,never()).closeInventory();assertTrue(data.isEmpty());
        durable.complete(null);assertTrue(mode.active(admin,menu));verify(inventory).clear();
        verify(menu).refreshTools();verify(menu).preview();assertTrue(data.get(BuildModeService.RECOVERY).startsWith("active:"));
    }
    @Test void backupFailureNeverClearsTheInventoryOrIssuesTools() {
        when(journal.backup(any())).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("disk full")));
        mode.enter(player,"draft");assertFalse(mode.protects(admin));
        verify(inventory,never()).clear();verify(menu,never()).refreshTools();verify(menu).release();assertTrue(data.isEmpty());
    }
    @Test void nonEmptyCursorIsKeptUntouchedBecauseVanillaDoesNotPersistCarriedItems() {
        when(cursor.getType()).thenReturn(org.bukkit.Material.DIAMOND);
        mode.enter(player,"draft");verify(journal,never()).backup(any());
        verify(player,never()).closeInventory();verify(player,never()).setItemOnCursor(any());verify(inventory,never()).clear();
        assertFalse(mode.protects(admin));
    }
    @Test void disconnectRestoresEverySlotCursorAndHeldSlotAndRetainsRecoveryUntilPlayerDataIsDurable() {
        mode.enter(player,"draft");mode.disconnected(player);
        assertFalse(mode.protects(admin));var contents=ArgumentCaptor.forClass(ItemStack[].class);verify(inventory).setContents(contents.capture());
        assertArrayEquals(original,contents.getValue());verify(player).setItemOnCursor(cursor);verify(inventory).setHeldItemSlot(7);
        assertTrue(data.get(BuildModeService.RECOVERY).startsWith("restored:"));
        verify(journal,never()).acknowledge(any(),any());verify(menu).release();
    }
    @Test void reloadAndShutdownRestoreAnActiveLeaseEvenWhenTheMenuIsClosed() {
        mode.enter(player,"draft");mode.exitAll();verify(inventory).setContents(any(ItemStack[].class));
        mode.enter(player,"draft");mode.close();verify(inventory,times(2)).setContents(any(ItemStack[].class));verify(journal).close();
    }
    @Test void quitDuringPreparationCancelsToolIssuanceWithoutReplacingOriginals() {
        var durable=new CompletableFuture<Void>();when(journal.backup(any())).thenReturn(durable);
        mode.enter(player,"draft");mode.disconnected(player);durable.complete(null);
        verify(inventory,never()).clear();verify(menu,never()).refreshTools();assertFalse(mode.protects(admin));
    }
    @Test void simulatedCrashReloadUsesNbtBackupBeforeAllowingAnotherBuild() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"active:"+token);
        var backup=new BuildJournal.Inventory(admin,token,new byte[]{10},new byte[]{20},8);
        when(journal.inventory(admin,token)).thenReturn(Optional.of(backup));
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{10})).thenReturn(original);
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{20})).thenReturn(new ItemStack[]{cursor});
        mode.recover(player);verify(inventory).setContents(any(ItemStack[].class));verify(player).setItemOnCursor(cursor);verify(inventory).setHeldItemSlot(8);
        assertEquals("restored:"+token,data.get(BuildModeService.RECOVERY));verify(journal,never()).acknowledge(any(),any());
        // Only a later login, with restored player-data, can retire the backup.
        mode.recover(player);verify(journal).acknowledgeAll(admin);assertFalse(data.containsKey(BuildModeService.RECOVERY));
    }
    @Test void missingBackupFailsClosedAndNeverCreatesANewInventoryLease() {
        data.put(BuildModeService.RECOVERY,"active:"+UUID.randomUUID());
        when(journal.inventory(any(),any())).thenReturn(Optional.empty());
        mode.recover(player);assertTrue(mode.protects(admin));verify(player).kick(any());verify(inventory,never()).setContents(any());
        mode.enter(player,"draft");verify(journal,never()).backup(any());
    }
    @Test void crashBeforeFirstPlayerSaveStillRecoversTheCommittedBackupWithoutAPdcMarker() {
        UUID token=UUID.randomUUID();
        var backup=new BuildJournal.Inventory(admin,token,new byte[]{10},new byte[]{20},8);
        when(journal.latestInventory(admin)).thenReturn(Optional.of(backup));
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{10})).thenReturn(original);
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{20})).thenReturn(new ItemStack[]{cursor});
        mode.recover(player);verify(inventory).setContents(any(ItemStack[].class));assertEquals("restored:"+token,data.get(BuildModeService.RECOVERY));
    }
    @Test void failedAcknowledgementRetainsTheRestoredMarkerAndRecoveryProtection() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"restored:"+token);
        when(journal.acknowledgeAll(admin)).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("disk error")));
        mode.recover(player);assertEquals("restored:"+token,data.get(BuildModeService.RECOVERY));assertTrue(mode.protects(admin));
    }
}
