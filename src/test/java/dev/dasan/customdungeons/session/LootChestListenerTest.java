package dev.dasan.customdungeons.session;

import dev.dasan.customdungeons.model.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LootChestListenerTest {
    final LootChestServiceTest fixture=new LootChestServiceTest();
    final Player player=fixture.player();
    final Inventory inventory=fixture.blocks.inventory;
    final LootChestListener listener=new LootChestListener(fixture.loot,p->p.equals(LootChestServiceTest.POSITION));
    final InventoryView view=mock(InventoryView.class);
    LootChestListenerTest(){when(view.getTopInventory()).thenReturn(inventory);}
    void start(boolean test){fixture.start(test,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player);fixture.generate();}
    InventoryClickEvent click(ClickType type){var e=mock(InventoryClickEvent.class);when(e.getView()).thenReturn(view);when(e.getWhoClicked()).thenReturn(player);when(e.getClick()).thenReturn(type);return e;}
    Block block(boolean registered){var b=mock(Block.class);when(b.getLocation()).thenReturn(new Location(fixture.world,registered?1:100,64,1));return b;}

    @ParameterizedTest @EnumSource(ClickType.class)
    void testBlocksEveryClickIncludingCreativeCloneHotbarOffhandShiftCollectAndDrop(ClickType type) {
        start(true);var event=click(type);listener.click(event);verify(event).setCancelled(true);
    }
    @ParameterizedTest @EnumSource(ClickType.class)
    void survivalTransfersUseVanillaWithoutCopyingOrGivingItems(ClickType type) {
        start(false);var event=click(type);listener.click(event);
        if(type==ClickType.MIDDLE)verify(event).setCancelled(true);else verify(event,never()).setCancelled(true);
        verify(player,never()).getInventory();verify(inventory,never()).addItem(any(ItemStack[].class));
    }
    @ParameterizedTest @ValueSource(strings={"pending","outsider","eliminated","spectator","dead","offline","failed"})
    void rechecksAnAlreadyOpenViewForEveryRejectedActor(String reason) {
        if(reason.equals("pending"))fixture.start(false,LootChestDef.Trigger.ROOM_ENTER,FinishMode.NONE,false,player);else start(false);
        var actor=player;
        switch(reason){case "outsider"->actor=fixture.player();case "eliminated"->{fixture.session.playerDied(player.getUniqueId());fixture.session.playerDied(player.getUniqueId());}
            case "spectator"->when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);case "dead"->when(player.isDead()).thenReturn(true);
            case "offline"->when(player.isOnline()).thenReturn(false);case "failed"->fixture.session.finish(false);}
        var event=click(ClickType.SHIFT_LEFT);when(event.getWhoClicked()).thenReturn(actor);listener.click(event);verify(event).setCancelled(true);
        var open=mock(InventoryOpenEvent.class);when(open.getInventory()).thenReturn(inventory);when(open.getPlayer()).thenReturn(actor);listener.open(open);verify(open).setCancelled(true);
        var drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);when(drag.getWhoClicked()).thenReturn(actor);listener.drag(drag);verify(drag).setCancelled(true);
    }
    @Test void testAllowsOpeningAndBlocksDragEvenWhenRawSlotsAreOnlyInPlayerInventory() {
        start(true);var open=mock(InventoryOpenEvent.class);when(open.getInventory()).thenReturn(inventory);when(open.getPlayer()).thenReturn(player);
        listener.open(open);verify(open,never()).setCancelled(true);
        var drag=mock(InventoryDragEvent.class);when(drag.getView()).thenReturn(view);when(drag.getWhoClicked()).thenReturn(player);when(drag.getRawSlots()).thenReturn(Set.of(30));
        listener.drag(drag);verify(drag).setCancelled(true);
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void hopperAndOtherAutomaticTransfersCannotInsertOrExtract(boolean source) {
        start(false);var ordinary=mock(Inventory.class);var event=mock(InventoryMoveItemEvent.class);
        when(event.getSource()).thenReturn(source?inventory:ordinary);when(event.getDestination()).thenReturn(source?ordinary:inventory);
        listener.transfer(event);verify(event).setCancelled(true);
        var pickup=mock(InventoryPickupItemEvent.class);when(pickup.getInventory()).thenReturn(inventory);listener.pickup(pickup);verify(pickup).setCancelled(true);
    }
    @Test void configuredChestIsBlockedOutsideAnySessionAndNoOtherChestIsAffected() {
        var open=mock(InventoryOpenEvent.class);when(open.getInventory()).thenReturn(inventory);when(open.getPlayer()).thenReturn(player);listener.open(open);verify(open).setCancelled(true);
        var ordinary=mock(Inventory.class);var normal=mock(InventoryOpenEvent.class);when(normal.getInventory()).thenReturn(ordinary);listener.open(normal);verify(normal,never()).setCancelled(true);
    }
    @Test void breakingExplodingBurningMovingAndReplacingCannotReleaseContents() {
        var chest=block(true);var ordinary=block(false);
        var breaking=new BlockBreakEvent(chest,player);listener.breakBlock(breaking);assertTrue(breaking.isCancelled());
        var explosion=mock(EntityExplodeEvent.class);var list=new ArrayList<>(List.of(chest,ordinary));when(explosion.blockList()).thenReturn(list);listener.explode(explosion);assertEquals(List.of(ordinary),list);
        var blockExplosion=mock(BlockExplodeEvent.class);when(blockExplosion.getBlock()).thenReturn(chest);listener.explode(blockExplosion);verify(blockExplosion).setCancelled(true);
        var extend=mock(BlockPistonExtendEvent.class);when(extend.getBlocks()).thenReturn(List.of(chest));when(extend.getDirection()).thenReturn(BlockFace.EAST);listener.extend(extend);verify(extend).setCancelled(true);
        var retract=mock(BlockPistonRetractEvent.class);when(retract.getBlocks()).thenReturn(List.of(chest));when(retract.getDirection()).thenReturn(BlockFace.WEST);listener.retract(retract);verify(retract).setCancelled(true);
        var place=mock(BlockPlaceEvent.class);when(place.getBlockPlaced()).thenReturn(chest);listener.place(place);verify(place).setCancelled(true);
        var change=mock(EntityChangeBlockEvent.class);when(change.getBlock()).thenReturn(chest);listener.change(change);verify(change).setCancelled(true);
        var burn=mock(BlockBurnEvent.class);when(burn.getBlock()).thenReturn(chest);listener.burn(burn);verify(burn).setCancelled(true);
        var flow=mock(BlockFromToEvent.class);when(flow.getToBlock()).thenReturn(chest);listener.flow(flow);verify(flow).setCancelled(true);
    }
    @Test void creativeCloneActionCannotCopySharedLootDuringNormalPlay() {
        start(false);var event=click(ClickType.CREATIVE);when(event.getAction()).thenReturn(InventoryAction.CLONE_STACK);
        listener.click(event);verify(event).setCancelled(true);
        var creative=mock(InventoryCreativeEvent.class);when(creative.getView()).thenReturn(view);when(creative.getWhoClicked()).thenReturn(player);
        listener.click(creative);verify(creative).setCancelled(true);
    }
    @Test void pickBlockWithNbtAndPhysicsDestructionAreBlockedEvenOutsideASession() {
        var chest=block(true);
        var pick=mock(io.papermc.paper.event.player.PlayerPickBlockEvent.class);when(pick.getBlock()).thenReturn(chest);listener.pick(pick);verify(pick).setCancelled(true);
        var destroy=mock(com.destroystokyo.paper.event.block.BlockDestroyEvent.class);when(destroy.getBlock()).thenReturn(chest);listener.destroy(destroy);verify(destroy).setCancelled(true);
        var physics=mock(BlockPhysicsEvent.class);when(physics.getBlock()).thenReturn(chest);listener.physics(physics);verify(physics).setCancelled(true);
        var dispense=mock(BlockDispenseLootEvent.class);when(dispense.getBlock()).thenReturn(chest);listener.dispense(dispense);verify(dispense).setCancelled(true);
    }
    @Test void neighbouringPlacementCannotCreateADoubleChest() {
        var placed=block(false);when(placed.getType()).thenReturn(Material.CHEST);var chest=block(true);when(placed.getRelative(BlockFace.WEST)).thenReturn(chest);
        var event=mock(BlockPlaceEvent.class);when(event.getBlockPlaced()).thenReturn(placed);listener.place(event);verify(event).setCancelled(true);
    }
    @Test void doubleChestWrappersCannotBypassProtection() {
        var top=mock(Inventory.class);var holder=mock(DoubleChest.class);var chest=mock(Chest.class);var ordinary=mock(Chest.class);var b=block(true);
        when(top.getHolder()).thenReturn(holder);when(holder.getLeftSide()).thenReturn(ordinary);when(holder.getRightSide()).thenReturn(chest);when(chest.getBlock()).thenReturn(b);
        var event=mock(InventoryOpenEvent.class);when(event.getInventory()).thenReturn(top);when(event.getPlayer()).thenReturn(player);listener.open(event);verify(event).setCancelled(true);
    }
    @Test void testDragAndCreativeClickAreBlockedDuringSuccessfulEvacuationToo() {
        start(true);fixture.session.finish(true);var creative=click(ClickType.MIDDLE);listener.click(creative);verify(creative).setCancelled(true);
    }
    @Test void deathQuitAndSpectatorChangeImmediatelyCloseOpenViews() {
        start(false);fixture.blocks.viewers.add(player);fixture.session.finish(true);
        var death=mock(PlayerDeathEvent.class);when(death.getEntity()).thenReturn(player);listener.death(death);assertFalse(fixture.loot.allowed(LootChestServiceTest.POSITION,player,false));verify(player,atLeastOnce()).closeInventory();
        var quit=mock(PlayerQuitEvent.class);when(quit.getPlayer()).thenReturn(player);listener.quit(quit);
        var mode=mock(PlayerGameModeChangeEvent.class);when(mode.getPlayer()).thenReturn(player);when(mode.getNewGameMode()).thenReturn(GameMode.SPECTATOR);listener.mode(mode);
    }
}
