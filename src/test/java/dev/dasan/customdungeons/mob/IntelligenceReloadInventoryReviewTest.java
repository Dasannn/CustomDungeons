package dev.dasan.customdungeons.mob;

import com.mojang.brigadier.CommandDispatcher;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.command.CustomDungeonCommand;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.MenuListener;
import dev.dasan.customdungeons.gui.menu.BuildMenu;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.tool.BuildModeService;
import dev.dasan.customdungeons.tool.construction.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real build/live-test/journal services; inventory effects and disk state from reviews 2 and 3. */
class IntelligenceReloadInventoryReviewTest {
    @TempDir java.nio.file.Path directory;
    @ParameterizedTest @ValueSource(strings={"reload","stop","death","quit","stop-full","stop-pending-journal","stop-encoding-failure","normal-stop","normal-overflow"})
    void thiefReturnIsRestoredByReloadOrDroppedExactlyOnceWhileBuilding(String path) throws Exception {
        var f=new LiveTestParticipantsTest();f.directory=directory;f.setup();
        var manager=LiveTestService.class.getDeclaredField("manager");manager.setAccessible(true);
        var previous=manager.get(null);manager.set(null,f.services);
        var plugin=mock(CustomDungeonsPlugin.class,RETURNS_DEEP_STUBS);
        when(plugin.isEnabled()).thenReturn(true);when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(plugin.getResource(anyString())).thenAnswer(i->new java.io.ByteArrayInputStream("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var scheduler=plugin.getServer().getScheduler();
        doAnswer(i->{((Runnable)i.getArgument(1)).run();return null;}).when(scheduler).runTask(eq(plugin),any(Runnable.class));
        var pending=new ArrayDeque<Runnable>();
        Executor writer=path.equals("stop-pending-journal")?pending::add:Runnable::run;
        var journal=new BuildJournal(directory.resolve("build"),writer);
        var constructor=BuildModeService.class.getDeclaredConstructor(CustomDungeonsPlugin.class,BuildJournal.class,Executor.class);
        constructor.setAccessible(true);var build=constructor.newInstance(plugin,journal,(Executor)Runnable::run);
        try(var framework=mockStatic(MenuListener.class);var menus=mockStatic(BuildMenu.class);var stacks=mockStatic(ItemStack.class)) {
            var inventory=mock(PlayerInventory.class);when(f.admin.getInventory()).thenReturn(inventory);
            var state=new AtomicReference<>(new ItemStack[41]);
            when(inventory.getContents()).thenAnswer(i->state.get());
            doAnswer(i->{state.set(new ItemStack[41]);return null;}).when(inventory).clear();
            doAnswer(i->{state.set(i.getArgument(0));return null;}).when(inventory).setContents(any(ItemStack[].class));
            var stolen=mock(ItemStack.class);when(stolen.clone()).thenReturn(stolen);
            when(stolen.getType()).thenReturn(org.bukkit.Material.DIAMOND);when(stolen.getAmount()).thenReturn(1);when(stolen.getMaxStackSize()).thenReturn(64);
            var returned=mock(org.bukkit.entity.Item.class);when(returned.getUniqueId()).thenReturn(UUID.randomUUID());
            when(returned.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(returned.getEntitySpawnReason()).thenReturn(org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM);
            when(f.world.dropItemNaturally(f.admin.getLocation(),stolen)).thenAnswer(i->{
                f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(returned));return returned;
            });
            when(inventory.addItem(any(ItemStack[].class))).thenAnswer(i->{
                assertFalse(build.protects(f.admin.getUniqueId()),"never return into a leased inventory");
                if(path.equals("normal-overflow"))return new HashMap<>(Map.of(0,stolen));
                state.get()[0]=stolen;return new HashMap<Integer,ItemStack>();
            });
            f.services.executing=f.live;f.services.spawned(new org.bukkit.event.entity.EntitySpawnEvent(f.principal));f.services.executing=null;
            f.live.onItemStolen(f.admin.getUniqueId(),stolen,f.live.mobs().iterator().next());
            if(path.equals("stop-full")) {
                var original=mock(ItemStack.class);when(original.clone()).thenReturn(original);when(original.getType()).thenReturn(org.bukkit.Material.STONE);
                when(original.getAmount()).thenReturn(64);when(original.getMaxStackSize()).thenReturn(64);
                Arrays.fill(state.get(),0,36,original);
            }
            var menu=mock(BuildMenu.class);
            var definition=new DungeonDef("review","review",false,null,null,1,0,30,3,false,0,0,false,
                    new ScalingDef(.25,.15),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of());
            when(menu.state()).thenReturn(new BuildState(definition));when(menu.ready()).thenReturn(true);
            framework.when(MenuListener::instance).thenReturn(mock(MenuListener.class));
            menus.when(()->BuildMenu.prepare(f.admin,"review",build)).thenReturn(menu);
            stacks.when(()->ItemStack.serializeItemsAsBytes(any(ItemStack[].class)))
                    .thenAnswer(i->new byte[]{(byte)(Arrays.asList((ItemStack[])i.getArgument(0)).contains(stolen)?2:1)});
            when(f.admin.hasPermission("customdungeons.admin.edit")).thenReturn(true);
            var server=plugin.getServer();when(f.admin.getServer()).thenReturn(server);
            when(server.getServicesManager().load(BuildModeService.class)).thenReturn(build);
            ThiefReturns.constructionMode(p->build.protects(p.getUniqueId()));
            boolean building=!path.startsWith("normal-");
            if(building) {
                build.enter(f.admin,"review");while(!pending.isEmpty())pending.remove().run();
                assertTrue(build.active(f.admin.getUniqueId(),menu));
            }
            stacks.clearInvocations();
            if(path.equals("stop-encoding-failure"))stacks.when(()->ItemStack.serializeItemsAsBytes(any(ItemStack[].class)))
                    .thenThrow(new IllegalStateException("simulated journal encoding failure"));
            switch(path) {
                case "reload" -> {
                    var definitions=mock(DefinitionStore.class);when(definitions.dungeons()).thenReturn(Map.of());
                    when(definitions.reloadAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
                    when(server.getServicesManager().load(DefinitionStore.class)).thenReturn(definitions);
                    when(server.getOnlinePlayers()).thenReturn(List.of());
                    var source=mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);
                    var sender=mock(org.bukkit.command.ConsoleCommandSender.class);when(source.getSender()).thenReturn(sender);
                    when(sender.hasPermission("customdungeons.admin.reload")).thenReturn(true);
                    try(var arguments=mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class);var migration=mockStatic(ConfigMigration.class)) {
                        arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players).thenReturn(mock(com.mojang.brigadier.arguments.ArgumentType.class));
                        var dispatcher=new CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>();
                        var commandConstructor=CustomDungeonCommand.class.getDeclaredConstructor(CustomDungeonsPlugin.class);commandConstructor.setAccessible(true);
                        var tree=CustomDungeonCommand.class.getDeclaredMethod("tree");tree.setAccessible(true);
                        @SuppressWarnings("unchecked") var root=(com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>)tree.invoke(commandConstructor.newInstance(plugin));
                        dispatcher.register(root);assertEquals(1,dispatcher.execute("customdungeon reload",source));
                    }
                }
                case "death" -> f.services.death(new org.bukkit.event.entity.EntityDeathEvent(f.principal,mock(org.bukkit.damage.DamageSource.class),new ArrayList<>()));
                case "quit" -> {var event=mock(org.bukkit.event.player.PlayerQuitEvent.class);when(event.getPlayer()).thenReturn(f.admin);f.services.quit(event);}
                default -> LiveTestService.stopAll();
            }
            // Repeated stop/close must never deliver again or remove a returned item from the ground.
            LiveTestService.stopAll();f.live.close();
            boolean inInventory=path.equals("reload")||path.equals("normal-stop");
            verify(inventory,inInventory||path.equals("normal-overflow")?times(1):never()).addItem(stolen);
            verify(f.world,inInventory?never():times(1)).dropItemNaturally(f.admin.getLocation(),stolen);
            verify(returned,never()).remove();assertTrue(pending.isEmpty(),"returning the thief's item never queues a journal write");
            stacks.verify(()->ItemStack.serializeItemsAsBytes(any(ItemStack[].class)),never());
            verify(f.services.platform,never()).deliverReward(any(),any());
            verify(f.services.platform.messages(),never()).send(f.admin,"reward.encounter-received");
            if(building)assertEquals(1,journal.latestInventory(f.admin.getUniqueId()).orElseThrow().contents()[0],"no thief return rewrites the backup");
            build.exitAll();while(!pending.isEmpty())pending.remove().run();assertEquals(inInventory,Arrays.asList(state.get()).contains(stolen));
        } finally {
            build.exitAll();while(!pending.isEmpty())pending.remove().run();build.close();ThiefReturns.constructionMode(p->false);manager.set(null,previous);f.cleanup();
        }
    }
}
