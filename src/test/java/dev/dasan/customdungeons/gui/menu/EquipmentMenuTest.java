package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.MobTemplate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EquipmentMenuTest {
    @Test void realPlacementSlotsAreAvailableOnlyForSupportedEquipment() {
        try (var bukkit=mockStatic(Bukkit.class)) {
            var inventory=mock(Inventory.class);
            bukkit.when(() -> Bukkit.createInventory(any(),anyInt(),any(net.kyori.adventure.text.Component.class))).thenReturn(inventory);
            var services=mock(org.bukkit.plugin.ServicesManager.class);
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            when(services.load(PluginConfig.class)).thenReturn(new PluginConfig("","es",null,"world",false,null,
                    null,Set.of(EntityType.ZOMBIE),List.of(),null,null,300,Map.of()));
            var messages=mock(dev.dasan.customdungeons.text.Messages.class);
            when(messages.get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
                    .thenReturn(net.kyori.adventure.text.Component.empty());
            var plugin=mock(dev.dasan.customdungeons.CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
            when(plugin.getServer().getServicesManager()).thenReturn(services);
            when(plugin.messages()).thenReturn(messages);
            var definitions=mock(dev.dasan.customdungeons.config.DefinitionStore.class);
            when(services.load(dev.dasan.customdungeons.config.DefinitionStore.class)).thenReturn(definitions);
            var listener=new dev.dasan.customdungeons.gui.MenuListener(plugin,
                    messages,new PluginConfig.GuiSounds("","","",""),new dev.dasan.customdungeons.gui.EditLocks());
            try (var shared=mockStatic(dev.dasan.customdungeons.gui.MenuListener.class)) {
                shared.when(dev.dasan.customdungeons.gui.MenuListener::instance).thenReturn(listener);
                var draft=draft("ZOMBIE");
                var player=mock(Player.class);
                when(player.hasPermission("customdungeons.admin.edit")).thenReturn(true);
                var playerInventory=mock(org.bukkit.inventory.PlayerInventory.class);
                when(player.getInventory()).thenReturn(playerInventory);
                when(playerInventory.addItem(any(org.bukkit.inventory.ItemStack.class))).thenReturn(new java.util.HashMap<>());
                var menu=new EquipmentMenu(player,draft,draft,null);
                for(int slot=0;slot<54;slot++) assertEquals(Set.of(28,29,30,32,33,34).contains(slot),menu.allowsPlacement(slot),"slot "+slot);
                // The common listener permits ordinary clicks and drags in these empty slots.
                when(inventory.getSize()).thenReturn(54);
                when(inventory.getHolder()).thenReturn(menu);
                var view=mock(org.bukkit.inventory.InventoryView.class);
                when(view.getTopInventory()).thenReturn(inventory);
                var click=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
                when(click.getView()).thenReturn(view);
                when(click.getWhoClicked()).thenReturn(player);
                when(click.getRawSlot()).thenReturn(30);
                when(click.isLeftClick()).thenReturn(true);
                when(click.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
                listener.onClick(click);
                verify(click).setCancelled(false);
                var drag=mock(org.bukkit.event.inventory.InventoryDragEvent.class);
                when(drag.getView()).thenReturn(view);
                when(drag.getWhoClicked()).thenReturn(player);
                when(drag.getRawSlots()).thenReturn(Set.of(28,30));
                listener.onDrag(drag);
                verify(drag).setCancelled(false);

                // A reload must also block real item placement, not just editor buttons.
                when(definitions.isReloading()).thenReturn(true);
                clearInvocations(click,drag);
                listener.onClick(click); listener.onDrag(drag);
                verify(click,never()).setCancelled(false);
                verify(drag,never()).setCancelled(false);
                verify(messages,times(2)).send(player,"command.reloading");
                when(definitions.isReloading()).thenReturn(false);

                // Closing copies the untouched item (including its metadata) and returns the real input.
                var item=mock(org.bukkit.inventory.ItemStack.class);
                when(item.getType()).thenReturn(mock(org.bukkit.Material.class));
                when(item.clone()).thenReturn(item);
                when(inventory.getItem(30)).thenReturn(item);
                var previousItem=mock(org.bukkit.inventory.ItemStack.class);
                when(previousItem.clone()).thenReturn(previousItem);
                draft.equipment.put(org.bukkit.inventory.EquipmentSlot.HEAD,new dev.dasan.customdungeons.model.EquipmentDef(previousItem,.25f));
                var close=mock(org.bukkit.event.inventory.InventoryCloseEvent.class);
                when(close.getInventory()).thenReturn(inventory);
                menu.closed(close);
                assertSame(item,draft.equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD).item());
                assertEquals(.25f,draft.equipment.get(org.bukkit.inventory.EquipmentSlot.HEAD).dropChance());
                verify(inventory).setItem(30,null);
                verify(playerInventory).addItem(item);
                verify(item,never()).editMeta(any());
                when(inventory.getItem(30)).thenReturn(null);
                menu.acceptPlacedItems();
                verify(playerInventory,times(1)).addItem(item);

                // Reserved markers must neither alter the draft nor consume the real item.
                for (var key : List.of(dev.dasan.customdungeons.mob.MobKeys.TOOL,dev.dasan.customdungeons.mob.MobKeys.KEY_ITEM)) {
                    var reserved=mock(org.bukkit.inventory.ItemStack.class);
                    when(reserved.getType()).thenReturn(org.bukkit.Material.STICK);
                    when(reserved.hasItemMeta()).thenReturn(true);
                    var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);
                    var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);
                    when(reserved.getItemMeta()).thenReturn(meta);
                    when(meta.getPersistentDataContainer()).thenReturn(pdc);
                    when(pdc.has(key)).thenReturn(true);
                    var before=draft.snapshot();
                    when(inventory.getItem(30)).thenReturn(reserved);
                    menu.acceptPlacedItems();
                    assertEquals(before,draft.snapshot());
                    verify(playerInventory).addItem(reserved);
                    verify(messages).send(player,key.equals(dev.dasan.customdungeons.mob.MobKeys.TOOL)
                            ? "gui.mob.equipment-tool-rejected" : "gui.mob.equipment-key-rejected");
                    when(inventory.getItem(30)).thenReturn(null);
                }

                draft.type="WARDEN";
                when(click.getRawSlot()).thenReturn(33);
                for(int slot=0;slot<54;slot++) assertEquals(slot==30 || slot==32,menu.allowsPlacement(slot),"slot "+slot);
                clearInvocations(click,drag);
                listener.onClick(click);
                verify(click,never()).setCancelled(false);
                listener.onDrag(drag);
                verify(drag,never()).setCancelled(false);
            }
        }
    }
    private MobMenu.MobDraft draft(String type) {
        return new MobMenu.MobDraft(new MobTemplate("test",type,"",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false));
    }
}
