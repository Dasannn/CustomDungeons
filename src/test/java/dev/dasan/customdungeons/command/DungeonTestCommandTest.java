package dev.dasan.customdungeons.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.session.SessionManager;
import dev.dasan.customdungeons.storage.Storage;
import dev.dasan.customdungeons.text.Messages;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonTestCommandTest {
    @ParameterizedTest
    @ValueSource(strings = {"messages.yml", "messages_en.yml"})
    void missingSpawnerPresetRejectsTestWithALocalizedMessageAndNoSession(String catalogue) throws Exception {
        var plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        var definitions=mock(DefinitionStore.class);
        var config=mock(PluginConfig.class);
        var storage=mock(Storage.class);
        var world=mock(World.class);
        when(config.dungeonWorld()).thenReturn("world");
        when(plugin.getServer().getServicesManager().load(DefinitionStore.class)).thenReturn(definitions);
        var point=new Point("world",1,64,1,0,0);
        var spawner=new SpawnerDef("s",point,3,List.of(),"missing");
        var room=new RoomDef("r",Region.of("world",new BlockPos(0,60,0),new BlockPos(5,70,5)),point,
                null,UnlockMode.AUTOMATIC,null,List.of(spawner));
        var dungeon=new DungeonDef("test","Prueba",false,point,point,1,2,0,3,false,0,0,false,
                new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room));
        when(definitions.dungeons()).thenReturn(Map.of("test",dungeon));
        when(definitions.spawnerPresets()).thenReturn(Map.of());
        var yaml=YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(catalogue)),StandardCharsets.UTF_8));
        assertTrue(yaml.isString("command.test-unavailable"),"The rejection must be translated in both catalogues");
        var messages=new Messages();messages.load(yaml,"");when(plugin.messages()).thenReturn(messages);
        var player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.hasPermission(anyString())).thenReturn(true);
        var source=mock(CommandSourceStack.class);when(source.getSender()).thenReturn(player);
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
            var sessions=new SessionManager(plugin,definitions,config,storage);when(plugin.sessionManager()).thenReturn(sessions);
            var dispatcher=new CommandDispatcher<CommandSourceStack>();
            try (var arguments = mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
                arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players)
                        .thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
                dispatcher.register(new CustomDungeonCommand(plugin).tree());
            }
            assertEquals(1,assertDoesNotThrow(()->dispatcher.execute("customdungeon test test",source)));
            assertTrue(sessions.session("test").isEmpty());assertTrue(sessions.sessionOf(player.getUniqueId()).isEmpty());
            var sent=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            verify(player).sendMessage(sent.capture());
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            assertEquals(plain.serialize(messages.get("command.test-unavailable")),plain.serialize(sent.getValue()));
            verify(player,never()).teleport(any(org.bukkit.Location.class));
            verify(player,never()).getInventory();verifyNoInteractions(storage);
        }
    }
}
