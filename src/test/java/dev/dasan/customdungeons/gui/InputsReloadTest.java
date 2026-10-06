package dev.dasan.customdungeons.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InputsReloadTest {
    @Test void deferredConfirmationIsDiscardedBeforeCreatingDialogDuringReload() {
        var services = mock(MenuListener.class);
        var player = mock(Player.class);
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(true);
        when(services.rejectReload(player)).thenReturn(true);
        try (var shared = mockStatic(MenuListener.class)) {
            shared.when(MenuListener::instance).thenReturn(services);
            assertDoesNotThrow(() -> Inputs.confirm(player, Component.empty(), () -> fail("Discarded callback ran")));
            verify(services).rejectReload(player);
            verify(player,never()).getOpenInventory();
            verify(player,never()).showDialog(any(io.papermc.paper.dialog.Dialog.class));
            assertFalse(Inputs.pending(player.getUniqueId()));
        }
    }
}
