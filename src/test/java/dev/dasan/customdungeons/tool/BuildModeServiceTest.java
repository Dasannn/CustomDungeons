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
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
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
        when(journal.restored(eq(admin),any())).thenReturn(CompletableFuture.completedFuture(null));
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
        verify(journal).save(eq(admin),any());
        durable.complete(null);assertTrue(mode.active(admin,menu));verify(inventory).clear();
        verify(menu).refreshTools();verify(menu).preview();assertTrue(data.get(BuildModeService.RECOVERY).startsWith("active:"));
    }
    @Test void pendingDisconnectRecoveryCancelsAConstructionLeaseWhoseBackupWasStillLoading() {
        var durable=new CompletableFuture<Void>();when(journal.backup(any())).thenReturn(durable);
        mode.enter(player,"draft");assertTrue(mode.protects(admin));
        when(plugin.sessionManager().recoveryPending(admin)).thenReturn(true);durable.complete(null);
        verify(inventory,never()).clear();verify(menu,never()).refreshTools();verify(menu).release();
        assertFalse(mode.protects(admin));assertFalse(mode.active(admin,menu));
    }
    @Test void backupFailureNeverClearsTheInventoryOrIssuesTools() {
        when(journal.backup(any())).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("disk full")));
        mode.enter(player,"draft");assertFalse(mode.protects(admin));
        verify(inventory,never()).clear();verify(menu,never()).refreshTools();verify(menu).release();assertTrue(data.get(BuildModeService.RECOVERY).startsWith("restored:"));
    }
    @Test void preparationExceptionReportsFailureWithoutTouchingInventory() {
        menus.when(()->BuildMenu.prepare(player,"draft",mode)).thenThrow(new NullPointerException("baseline"));
        assertDoesNotThrow(()->mode.enter(player,"draft"));
        verify(plugin.messages()).send(player,"build.entry-failed");
        verify(inventory,never()).clear();verify(inventory,never()).setContents(any());
        verify(player,never()).setItemOnCursor(any());verify(player,never()).closeInventory();
        verify(journal,never()).backup(any());assertFalse(mode.protects(admin));
    }
    @Test void synchronousDraftWriteFailureReleasesLeaseWithoutTouchingInventory() {
        when(journal.save(eq(admin),any())).thenThrow(new IllegalStateException("encoding"));
        assertDoesNotThrow(()->mode.enter(player,"draft"));
        verify(plugin.messages()).send(player,"build.entry-failed");verify(menu).release();
        verify(inventory,never()).clear();verify(inventory,never()).setContents(any());
        verify(player,never()).setItemOnCursor(any());assertFalse(mode.protects(admin));
    }
    @Test void readyCheckExceptionInScheduledEntryReportsFailureAndKeepsOriginals() {
        when(menu.ready()).thenThrow(new IllegalStateException("ready"));
        mode.enter(player,"draft");
        verify(plugin.messages()).send(player,"build.entry-failed");verify(menu).release();
        verify(inventory,never()).clear();verify(inventory,never()).setContents(any());
        assertFalse(mode.protects(admin));
    }
    @Test void toolIssuanceExceptionRestoresInventoryCursorAndHeldSlot() {
        doThrow(new IllegalStateException("tools")).when(menu).refreshTools();
        mode.enter(player,"draft");
        verify(plugin.messages()).send(player,"build.entry-failed");verify(menu).release();
        var contents=ArgumentCaptor.forClass(ItemStack[].class);verify(inventory).setContents(contents.capture());
        assertArrayEquals(original,contents.getValue());verify(player).setItemOnCursor(cursor);
        verify(inventory).setHeldItemSlot(7);assertFalse(mode.protects(admin));
        assertTrue(data.get(BuildModeService.RECOVERY).startsWith("restored:"));
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
        mode.recover(player);verify(journal,never()).acknowledge(any(),any());assertTrue(data.containsKey(BuildModeService.RECOVERY));
    }
    @Test void exitWhileInitialDraftIsPendingNeverRestoresStaleOriginalsOnReactivation() {
        var pending=new CompletableFuture<Void>();when(journal.save(eq(admin),any())).thenReturn(pending);
        mode.enter(player,"draft");mode.disconnected(player);pending.complete(null);
        mode.recover(player);
        verify(inventory,never()).setContents(any(ItemStack[].class));
        verify(inventory,never()).clear();
        assertTrue(data.get(BuildModeService.RECOVERY).startsWith("restored:"));
    }
    @Test void activeMarkerSelectsExactGenerationEvenWhenANewerBackupExists() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"active:"+token);
        when(journal.inventory(admin,token)).thenReturn(Optional.of(new BuildJournal.Inventory(admin,token,new byte[]{10},new byte[]{20},8)));
        when(journal.latestInventory(admin)).thenReturn(Optional.of(new BuildJournal.Inventory(admin,UUID.randomUUID(),new byte[]{30},new byte[]{40},0)));
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{10})).thenReturn(original);
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{20})).thenReturn(new ItemStack[]{cursor});
        mode.joined(player);verify(inventory).setHeldItemSlot(8);assertEquals("restored:"+token,data.get(BuildModeService.RECOVERY));
    }
    @Test void restoredMarkerWhileDecodingPreventsStaleRecovery() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"active:"+token);
        when(journal.inventory(admin,token)).thenReturn(Optional.of(new BuildJournal.Inventory(admin,token,new byte[]{10},new byte[]{20},8)));
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{10})).thenReturn(original);
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{20})).thenReturn(new ItemStack[]{cursor});
        var pending=new ArrayDeque<Runnable>();mode=new BuildModeService(plugin,journal,pending::add);
        mode.recover(player);data.put(BuildModeService.RECOVERY,"restored:"+token);pending.remove().run();
        verify(inventory,never()).setContents(any());assertFalse(mode.protects(admin));
    }
    @Test void abandonedGenerationCannotReplaceTheMarkerOfANewerEntry() {
        var pending=new CompletableFuture<Void>();when(journal.save(eq(admin),any())).thenReturn(pending,CompletableFuture.completedFuture(null));
        mode.enter(player,"draft");mode.exit(player);mode.enter(player,"draft");
        String current=data.get(BuildModeService.RECOVERY);assertTrue(current.startsWith("active:"));
        pending.complete(null);assertEquals(current,data.get(BuildModeService.RECOVERY));
        verify(inventory,times(1)).clear();verify(inventory,never()).setContents(any());
    }
    @Test void failedCleanupOfAnAbandonedEntryCannotRestoreOverANewerLease() {
        var pending=new CompletableFuture<Void>();when(journal.save(eq(admin),any())).thenReturn(pending,CompletableFuture.completedFuture(null));
        mode.enter(player,"draft");mode.exit(player);mode.enter(player,"draft");
        String current=data.get(BuildModeService.RECOVERY);clearInvocations(inventory);
        when(journal.restored(eq(admin),any())).thenThrow(new IllegalStateException("cleanup"));
        pending.complete(null);
        assertEquals(current,data.get(BuildModeService.RECOVERY));assertTrue(mode.active(admin,menu));
        verify(inventory,never()).setContents(any());
    }
    @Test void onlyFreshLoginCanRetireTheMatchingRestoredGeneration() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"restored:"+token);
        mode.joined(player);verify(journal).restored(admin,token);verify(journal).acknowledge(admin,token);
        assertFalse(data.containsKey(BuildModeService.RECOVERY));verify(inventory,never()).setContents(any());
    }
    @Test void failedDiskAcknowledgementKeepsBackupMarker() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"restored:"+token);
        when(journal.acknowledge(admin,token)).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("disk")));
        mode.joined(player);assertEquals("restored:"+token,data.get(BuildModeService.RECOVERY));assertTrue(mode.protects(admin));
    }
    @Test void missingBackupFailsClosedAndNeverCreatesANewInventoryLease() {
        data.put(BuildModeService.RECOVERY,"active:"+UUID.randomUUID());
        when(journal.inventory(any(),any())).thenReturn(Optional.empty());
        mode.recover(player);assertTrue(mode.protects(admin));verify(player).kick(any());verify(inventory,never()).setContents(any());
        mode.enter(player,"draft");verify(journal,never()).backup(any());
    }
    @Test void unmarkedBackupNeverReplacesANewerPlayerInventory() {
        UUID token=UUID.randomUUID();
        var backup=new BuildJournal.Inventory(admin,token,new byte[]{10},new byte[]{20},8);
        when(journal.latestInventory(admin)).thenReturn(Optional.of(backup));
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{10})).thenReturn(original);
        stacks.when(()->ItemStack.deserializeItemsFromBytes(new byte[]{20})).thenReturn(new ItemStack[]{cursor});
        mode.recover(player);verify(inventory,never()).setContents(any(ItemStack[].class));assertTrue(data.isEmpty());
    }
    @Test void reenableCannotAcknowledgeAnUnpersistedRestoredMarker() {
        UUID token=UUID.randomUUID();data.put(BuildModeService.RECOVERY,"restored:"+token);
        when(journal.acknowledge(eq(admin),any())).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("disk error")));
        mode.recover(player);assertEquals("restored:"+token,data.get(BuildModeService.RECOVERY));assertFalse(mode.protects(admin));verify(journal,never()).acknowledge(any(),any());
    }
}
