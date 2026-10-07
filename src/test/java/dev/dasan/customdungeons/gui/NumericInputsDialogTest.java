package dev.dasan.customdungeons.gui;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.*;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NumericInputsDialogTest {
    @SuppressWarnings({"rawtypes","unchecked"})
    @Test void dialogsReceiveTheSameLocalizedRangeAndPrecisionAsButtons() {
        PaperApiTestBootstrap.initialize();
        var messages=new Messages();messages.load(catalog(),"");
        var services=mock(MenuListener.class);when(services.messages()).thenReturn(messages);when(services.editLocks()).thenReturn(new EditLocks());
        var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.hasPermission(anyString())).thenReturn(true);
        var view=mock(InventoryView.class);when(player.getOpenInventory()).thenReturn(view);when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        var scheduler=mock(BukkitScheduler.class);when(scheduler.runTaskLater(any(),any(Runnable.class),anyLong())).thenReturn(mock(BukkitTask.class));
        var base=mock(DialogBase.Builder.class,RETURNS_SELF);when(base.build()).thenReturn(mock(DialogBase.class));
        var entry=mock(DialogRegistryEntry.Builder.class,RETURNS_SELF);
        RegistryBuilderFactory factory=mock(RegistryBuilderFactory.class);when(factory.empty()).thenReturn(entry);
        var body=new ArrayList<Component>();
        try(var framework=mockStatic(MenuListener.class);var bukkit=mockStatic(Bukkit.class);var plugins=mockStatic(JavaPlugin.class);
            var dialogs=mockStatic(Dialog.class);var bases=mockStatic(DialogBase.class);var bodies=mockStatic(DialogBody.class);
            var inputs=mockStatic(DialogInput.class,RETURNS_DEEP_STUBS);var buttons=mockStatic(ActionButton.class,RETURNS_MOCKS);
            var actions=mockStatic(DialogAction.class,RETURNS_MOCKS);var types=mockStatic(DialogType.class,RETURNS_MOCKS)) {
            framework.when(MenuListener::instance).thenReturn(services);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            plugins.when(()->JavaPlugin.getPlugin(CustomDungeonsPlugin.class)).thenReturn(mock(CustomDungeonsPlugin.class));
            bases.when(()->DialogBase.builder(any(Component.class))).thenReturn(base);
            bodies.when(()->DialogBody.plainMessage(any(Component.class))).thenAnswer(c->{body.add(c.getArgument(0));return mock(PlainMessageDialogBody.class);});
            dialogs.when(()->Dialog.create(any())).thenAnswer(c->{Consumer configure=c.getArgument(0);configure.accept(factory);return mock(Dialog.class);});
            for(var range:List.of(NumericRanges.SCALE,NumericRanges.POTION_LEVEL,NumericRanges.SPAWNER_RADIUS,NumericRanges.HEALTH)) {
                body.clear();
                NumericInputs.edit(player,Component.empty(),range,range.max(),n->fail("No submission was requested"));
                assertEquals(NumericInputs.lore(range),body);
                assertTrue(Inputs.pending(player.getUniqueId()));
                Inputs.cancel(player);
                assertFalse(Inputs.pending(player.getUniqueId()));
            }
            verify(player,times(4)).showDialog(any(Dialog.class));
        }
    }
    private org.bukkit.configuration.file.YamlConfiguration catalog() {
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
    }
}
