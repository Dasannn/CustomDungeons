package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.config.DefinitionStore;
import dev.dasan.customdungeons.config.PluginConfig;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import dev.dasan.customdungeons.tool.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DungeonMenuFlowTest {
    MockedStatic<Bukkit> bukkit;
    MockedStatic<JavaPlugin> javaPlugin;
    MockedStatic<MenuListener> menuServices;
    MockedStatic<Inputs> inputs;
    MockedStatic<GuiTheme> theme;
    MockedStatic<Button> buttons;
    final CustomDungeonsPlugin plugin = mock(CustomDungeonsPlugin.class);
    final DefinitionStore store = mock(DefinitionStore.class);
    final EditLocks locks = new EditLocks();
    final Map<String,DungeonDef> definitions = new HashMap<>();
    final Deque<Runnable> tasks = new ArrayDeque<>();
    final List<Listener> listeners = new ArrayList<>();
    Player player;
    org.bukkit.World world;
    InventoryView view;
    Inventory top;
    MenuListener framework;
    DungeonListMenu list;
    Runnable confirm;

    @BeforeEach void setup() {
        dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap.initialize();
        bukkit = mockStatic(Bukkit.class);
        javaPlugin = mockStatic(JavaPlugin.class);
        menuServices = mockStatic(MenuListener.class);
        inputs = mockStatic(Inputs.class);
        inputs.when(() -> Inputs.formatNumber(anyDouble(),anyInt())).thenCallRealMethod();
        theme = mockStatic(GuiTheme.class, CALLS_REAL_METHODS);
        theme.when(()->GuiTheme.frame(any())).thenAnswer(call->null);
        buttons = mockStatic(Button.class);
        buttons.when(() -> Button.of(any(), any(), anyList(), any())).thenAnswer(call ->
                new Button(item(call.getArgument(0)), call.getArgument(3)));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(call -> inventory(call.getArgument(0),call.getArgument(1)));
        javaPlugin.when(() -> JavaPlugin.getPlugin(CustomDungeonsPlugin.class)).thenReturn(plugin);
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.abilityRegistry()).thenReturn(new dev.dasan.customdungeons.ability.AbilityRegistry());
        var config = new dev.dasan.customdungeons.config.ConfigLoader(path -> {}, material -> true).load(new org.bukkit.configuration.file.YamlConfiguration());
        when(server.getServicesManager().load(PluginConfig.class)).thenReturn(config);
        var mobServices = server.getServicesManager();
        bukkit.when(Bukkit::getServicesManager).thenReturn(mobServices);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        when(server.getServicesManager().load(DefinitionStore.class)).thenReturn(store);
        when(server.getServicesManager().load(dev.dasan.customdungeons.config.EntityHeights.class)).thenReturn(dev.dasan.customdungeons.config.ConfigLoader.defaultEntityHeights());
        when(server.getServicesManager().load(ToolService.class)).thenReturn(mock(ToolService.class));
        when(server.getServicesManager().load(SpawnerMarkers.class)).thenReturn(mock(SpawnerMarkers.class));
        var pluginManager = server.getPluginManager();
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        doAnswer(call -> { listeners.add(call.getArgument(0)); return null; })
                .when(pluginManager).registerEvents(any(), eq(plugin));
        BukkitScheduler scheduler = server.getScheduler();
        doAnswer(call -> { tasks.add(call.getArgument(1)); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        Messages messages = mock(Messages.class);
        when(messages.get(anyString(), any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class))).thenReturn(Component.empty());
        when(plugin.messages()).thenReturn(messages);
        framework = new MenuListener(plugin, messages, new PluginConfig.GuiSounds("", "", "", ""), locks);
        menuServices.when(MenuListener::instance).thenReturn(framework);
        when(store.dungeons()).thenAnswer(call -> Map.copyOf(definitions));
        when(store.mobs()).thenReturn(Map.of("mob", new MobTemplate("mob","ZOMBIE","Mob",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false)));
        player = player();
        world=mock(org.bukkit.World.class);when(world.getName()).thenReturn("world");
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world,0,64,0));
        view = player.getOpenInventory();
        top = inventory(null);
        when(view.getTopInventory()).thenAnswer(call -> top);
        doAnswer(call -> { top = call.getArgument(0); return view; }).when(player).openInventory(any(Inventory.class));
        doAnswer(call -> { top = inventory(null); return null; }).when(player).closeInventory();
        inputs.when(() -> Inputs.confirm(eq(player), any(), any())).thenAnswer(call -> { confirm = call.getArgument(2); return null; });
        list = new DungeonListMenu(player,true,null);
        DungeonListMenu.register(plugin);
    }
    @AfterEach void cleanup() throws Exception {
        var wizard=WizardMenu.active(player.getUniqueId());if(wizard!=null) wizard.pause();
        var editors = DungeonListMenu.class.getDeclaredField("editors");
        editors.setAccessible(true);
        ((Map<?,?>) editors.get(null)).clear();
        DungeonListMenu.dungeonBusy(id -> false);
        buttons.close(); if(theme!=null) theme.close(); inputs.close(); menuServices.close(); javaPlugin.close(); bukkit.close();
    }
    Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        when(p.isOnline()).thenReturn(true);
        when(p.hasPermission("customdungeons.admin.edit")).thenReturn(true);
        when(p.getOpenInventory()).thenReturn(mock(InventoryView.class));
        when(p.getInventory()).thenReturn(mock(PlayerInventory.class));
        when(p.getInventory().addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        return p;
    }
    Inventory inventory(InventoryHolder holder) {return inventory(holder,54);}
    Inventory inventory(InventoryHolder holder,int size) {
        Inventory inv = mock(Inventory.class);
        Map<Integer,ItemStack> slots = new HashMap<>();
        when(inv.getHolder()).thenReturn(holder);
        when(inv.getSize()).thenReturn(size);
        when(inv.getItem(anyInt())).thenAnswer(call -> slots.get(call.getArgument(0)));
        doAnswer(call -> { slots.put(call.getArgument(0), call.getArgument(1)); return null; }).when(inv).setItem(anyInt(), any());
        doAnswer(call -> { slots.clear(); return null; }).when(inv).clear();
        return inv;
    }
    static ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.clone()).thenReturn(item);
        return item;
    }
    static DungeonDef definition(String id) {
        Point point = new Point("world", 0, 64, 0, 0, 0);
        var wave = new WaveDef(List.of(new WaveEntry("mob",1,0)), SpawnMode.SIMULTANEOUS,0,0);
        var spawner = new SpawnerDef("s",point,1,List.of(wave));
        var room = new RoomDef("r",Region.of("world",new BlockPos(0,0,0),new BlockPos(1,1,1)),point,null,UnlockMode.AUTOMATIC,null,List.of(spawner));
        return new DungeonDef(id,id,false,point,point,1,4,10,3,false,0,0,false,new ScalingDef(0,0),Map.of(),new RewardDef(List.of(),0,0,List.of()),List.of(room));
    }
    DungeonMenu remember(DungeonDef definition) throws Exception {
        var method = DungeonListMenu.class.getDeclaredMethod("remember", DungeonMenu.class);
        method.setAccessible(true);
        return (DungeonMenu) method.invoke(list, new DungeonMenu(player, definition, list));
    }
    DungeonMenu editor(String id) throws Exception {
        var method = DungeonListMenu.class.getDeclaredMethod("editor", String.class);
        method.setAccessible(true);
        return (DungeonMenu) method.invoke(list,id);
    }
    void drain() { while (!tasks.isEmpty()) tasks.remove().run(); }
    private BuildModeService buildMode() {
        var mode=mock(BuildModeService.class);
        var journal=mock(dev.dasan.customdungeons.tool.construction.BuildJournal.class);
        when(mode.journal()).thenReturn(journal);
        when(mode.active(eq(player.getUniqueId()),any(BuildMenu.class))).thenReturn(true);
        when(journal.save(eq(player.getUniqueId()),any())).thenReturn(CompletableFuture.completedFuture(null));
        when(plugin.getServer().getServicesManager().load(BuildModeService.class)).thenReturn(mode);
        return mode;
    }
    @Test void settingsToggleDisconnectModeWithoutChangingOtherRules() {
        var root=new DungeonMenu(player,definition("one"),list);var settings=new DungeonSettingsMenu(root);settings.open();
        assertEquals(DisconnectMode.DIE_AND_DROP,root.draft.get().disconnectMode());
        clickSlot(39);
        assertEquals(DisconnectMode.RETURN_TO_EXIT,root.draft.get().disconnectMode());
        clickSlot(39,org.bukkit.event.inventory.ClickType.RIGHT);
        assertEquals(DisconnectMode.DIE_AND_DROP,root.draft.get().disconnectMode());
    }
    @Test void buildEntryIsBricksInApprovedSlot47AndUsesTheExistingEditorDraft() throws Exception {
        var original=definition("build");definitions.put("build",original);var mode=buildMode();
        var root=remember(original);root.change(v->v.lives=8);root.open();
        assertEquals(Material.BRICKS,top.getItem(47).getType());
        clickSlot(47);verify(mode).enter(player,"build");
        var build=BuildMenu.prepare(player,"build",mode);assertNotNull(build);assertEquals(8,build.definition().lives());
        build.release();
    }
    @Test void buildIndependentLockSurvivesClosingTheMenuAndTheFrameworkReleasingPlayerLocks() throws Exception {
        definitions.put("build",definition("build"));var build=BuildMenu.prepare(player,"build",buildMode());
        build.open();closeRoot(build);locks.releaseAll(player.getUniqueId());
        assertEquals(Optional.of(build.lockOwner()),locks.holder("build"));assertFalse(locks.tryLock("build",UUID.randomUUID()));
        assertTrue(build.writable());build.release();assertTrue(locks.holder("build").isEmpty());
    }
    @Test void buildResumesSavedUndoAndContextAndRefusesOtherEditorsChanges() {
        var original=definition("build");definitions.put("build",original);var mode=buildMode();
        var state=new dev.dasan.customdungeons.tool.construction.BuildState(original);
        var values=new DungeonMenu.Values(original);values.lives=8;state.change(values.build());state.cyclePoint();
        when(mode.journal().draft(player.getUniqueId(),"build")).thenReturn(Optional.of(state.snapshot()));
        var build=BuildMenu.prepare(player,"build",mode);assertEquals(8,build.definition().lives());assertEquals(1,build.state().snapshot().point());
        build.undo();assertEquals(original,build.definition());verify(store,never()).save(any(DungeonDef.class));build.release();
        values.lives=9;definitions.put("build",values.build());
        assertNull(BuildMenu.prepare(player,"build",mode));
    }
    @Test void buildResumesPublicationAfterCrashBeforeBaselinePersistence() {
        var original=definition("build");var mode=buildMode();
        var state=new dev.dasan.customdungeons.tool.construction.BuildState(original);
        var values=new DungeonMenu.Values(original);values.lives=8;state.change(values.build());
        definitions.put("build",state.definition());
        when(mode.journal().draft(player.getUniqueId(),"build")).thenReturn(Optional.of(state.snapshot()));
        var build=BuildMenu.prepare(player,"build",mode);assertNotNull(build);
        assertEquals(state.definition(),build.state().snapshot().baseline());assertEquals(1,build.state().snapshot().undo().size());
        build.release();
    }
    @Test void buildPublicationSerializationReturnsToMainAfterDraftIo() throws Exception {
        definitions.put("build",definition("build"));var mode=buildMode();
        var build=BuildMenu.prepare(player,"build",mode);
        var durable=new CompletableFuture<Void>();when(mode.journal().save(eq(player.getUniqueId()),any())).thenReturn(durable);
        when(store.save(any(DungeonDef.class))).thenReturn(CompletableFuture.completedFuture(null));
        build.saveDraft();
        var worker=new Thread(()->durable.complete(null));worker.start();worker.join();
        verify(store,never()).save(any(DungeonDef.class));
        drain();verify(store).save(any(DungeonDef.class));build.release();
    }
    @Test void buildSaveValidatesAndRetainsTheWriteLockAcrossAnExit() {
        var original=definition("build");definitions.put("build",original);var mode=buildMode();
        var build=BuildMenu.prepare(player,"build",mode);build.change(v->v.lobby=null);build.saveDraft();
        verify(store,never()).save(any(DungeonDef.class));build.undo();
        var write=new CompletableFuture<Void>();when(store.save(any(DungeonDef.class))).thenReturn(write);
        build.saveDraft();assertTrue(build.saving());build.release();
        assertEquals(Optional.of(build.lockOwner()),locks.holder("build"));
        write.complete(null);drain();assertTrue(locks.holder("build").isEmpty());
    }
    private void heldBuildTool(int slot) {
        var held=mock(ItemStack.class);var meta=mock(org.bukkit.inventory.meta.ItemMeta.class);
        var pdc=mock(org.bukkit.persistence.PersistentDataContainer.class);
        when(held.hasItemMeta()).thenReturn(true);when(held.getItemMeta()).thenReturn(meta);when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(BuildTools.KEY,org.bukkit.persistence.PersistentDataType.INTEGER)).thenReturn(slot);
        when(player.getInventory().getItemInMainHand()).thenReturn(held);when(player.getInventory().getHeldItemSlot()).thenReturn(slot);
    }
    private org.bukkit.block.Block buildBlock(int x,int y,int z) {
        var block=mock(org.bukkit.block.Block.class);when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);when(block.getY()).thenReturn(y);when(block.getZ()).thenReturn(z);
        when(block.getLocation()).thenReturn(new org.bukkit.Location(world,x,y,z));
        when(world.getBlockAt(x,y,z)).thenReturn(block);return block;
    }
    private void buildClick(BuildMenu menu,org.bukkit.block.Block block,boolean left,boolean shift) {
        heldBuildTool(player.getInventory().getHeldItemSlot());when(player.isSneaking()).thenReturn(shift);
        var event=mock(org.bukkit.event.player.PlayerInteractEvent.class);
        when(event.getAction()).thenReturn(left?org.bukkit.event.block.Action.LEFT_CLICK_BLOCK:org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);menu.interact(event);
    }
    @Test void buildShiftDoorEditsEntranceWithoutRoomsAndUndoRestoresIt() {
        var v=new DungeonMenu.Values(definition("build"));v.rooms=List.of();definitions.put("build",v.build());
        var mode=buildMode();var build=BuildMenu.prepare(player,"build",mode);heldBuildTool(2);
        buildClick(build,buildBlock(1,64,1),true,true);buildClick(build,buildBlock(2,65,1),false,true);
        assertEquals(Region.of("world",new BlockPos(1,64,1),new BlockPos(2,65,1)),build.definition().entranceDoor());
        assertTrue(build.definition().rooms().isEmpty());build.undo();assertNull(build.definition().entranceDoor());build.release();
    }
    @Test void switchingShiftNeverCombinesRoomDoorAndEntranceSelections() {
        definitions.put("build",definition("build"));var build=BuildMenu.prepare(player,"build",buildMode());heldBuildTool(2);
        buildClick(build,buildBlock(1,64,1),true,false);buildClick(build,buildBlock(2,65,1),false,true);
        assertNull(build.definition().entranceDoor());assertNull(build.definition().rooms().getFirst().door());
        buildClick(build,buildBlock(3,64,1),true,true);
        assertEquals(Region.of("world",new BlockPos(2,65,1),new BlockPos(3,64,1)),build.definition().entranceDoor());build.release();
    }
    @Test void buildPlacesBothPlateTypesRemovesByRegisteredTypeAndUndoRestoresWorldAndMinimum() {
        var tools=new ToolService(framework.messages(),mock(PreviewRenderer.class));when(plugin.getServer().getServicesManager().load(ToolService.class)).thenReturn(tools);
        var v=new DungeonMenu.Values(definition("build"));v.startMode=StartMode.PLATES;definitions.put("build",v.build());
        var mode=buildMode();var build=BuildMenu.prepare(player,"build",mode);heldBuildTool(4);
        bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var support=buildBlock(1,63,1);var plate=buildBlock(1,64,1);var solid=mock(Material.class);when(solid.isSolid()).thenReturn(true);when(support.getType()).thenReturn(solid);
        when(support.getRelative(org.bukkit.block.BlockFace.UP)).thenReturn(plate);when(plate.getRelative(org.bukkit.block.BlockFace.DOWN)).thenReturn(support);
        var material=new java.util.concurrent.atomic.AtomicReference<>(Material.AIR);when(plate.getType()).thenAnswer(c->material.get());
        doAnswer(c->{material.set(c.getArgument(0));return null;}).when(plate).setType(any(),eq(false));
        buildClick(build,support,true,false);assertEquals(Material.STONE_PRESSURE_PLATE,material.get());assertEquals(1,build.definition().minPlayers());
        verify(framework.messages()).send(eq(player),eq("tool.plate-added"),any(),any());
        build.undo();assertEquals(Material.AIR,material.get());assertTrue(build.definition().plates().isEmpty());
        buildClick(build,support,true,true);assertEquals(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,material.get());assertEquals(1,build.definition().exitPlates().size());assertEquals(0,build.definition().minPlayers());
        verify(framework.messages()).send(eq(player),eq("tool.exit-plate-added"),any(),any());
        buildClick(build,plate,false,false);assertEquals(Material.AIR,material.get());assertTrue(build.definition().exitPlates().isEmpty());
        build.undo();assertEquals(Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,material.get());assertEquals(1,build.definition().exitPlates().size());
        material.set(Material.DIAMOND_BLOCK);int history=build.state().snapshot().undo().size();build.undo();
        assertEquals(Material.DIAMOND_BLOCK,material.get());assertEquals(history,build.state().snapshot().undo().size());build.release();
    }
    @Test void constructionActionbarReportsActiveRoomAndStartExitPlateCounts() {
        var messages=new Messages();messages.load(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.nio.file.Path.of("src/main/resources/messages.yml").toFile()),"");
        when(framework.messages().get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
            .thenAnswer(c->messages.get((String)c.getRawArguments()[0],(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[])c.getRawArguments()[1]));
        var v=new DungeonMenu.Values(definition("build"));v.min=3;v.rooms=List.of(v.rooms.getFirst(),v.rooms.getFirst());
        v.plates=List.of(new Point("world",1.5,64,1.5,0,0),new Point("world",2.5,64,1.5,0,0));v.exitPlates=List.of(new Point("world",3.5,64,1.5,0,0));
        definitions.put("build",v.build());var build=BuildMenu.prepare(player,"build",buildMode());build.selectRoom(1);
        var bars=org.mockito.ArgumentCaptor.forClass(Component.class);verify(player,atLeastOnce()).sendActionBar(bars.capture());
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertTrue(bars.getAllValues().stream().map(plain::serialize).anyMatch(text->text.contains("Sala 2 · placas 2/3 · salida 1")));build.release();
    }
    @Test void constructionValidationKeepsStartAndFinishAccessibleInSlot41() {
        definitions.put("build",definition("build"));var build=BuildMenu.prepare(player,"build",buildMode());build.open();
        build.change(v->v.lobby=null);build.saveDraft();assertEquals(Material.LEVER,top.getItem(41).getType());build.release();
    }
    void closeRoot(DungeonMenu root) throws Exception {
        var event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(root.getInventory());
        when(event.getPlayer()).thenReturn(player);
        when(event.getReason()).thenReturn(InventoryCloseEvent.Reason.PLAYER);
        for (Listener listener : List.copyOf(listeners)) listener.getClass().getMethod("close", InventoryCloseEvent.class).invoke(listener,event);
        player.closeInventory();
        framework.onClose(event);
        drain();
    }



    private void paperClose(Inventory previous) throws Exception {
        paperClose(previous, InventoryCloseEvent.Reason.OPEN_NEW);
    }
    private void paperClose(Inventory previous, InventoryCloseEvent.Reason reason) throws Exception {
        var event=mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(previous); when(event.getPlayer()).thenReturn(player);
        when(event.getReason()).thenReturn(reason);
        for(var registered:List.copyOf(listeners)) {
            if(registered instanceof EquipmentMenu equipment) equipment.closed(event);
            else registered.getClass().getMethod("close",InventoryCloseEvent.class).invoke(registered,event);
        }
        framework.onClose(event);
    }
    private org.mockito.MockedStatic<org.bukkit.event.HandlerList> paperInventoryLifecycle() {
        var manager=plugin.getServer().getPluginManager();
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        var handlers=mockStatic(org.bukkit.event.HandlerList.class);
        handlers.when(()->org.bukkit.event.HandlerList.unregisterAll(any(Listener.class)))
                .thenAnswer(call->{listeners.remove(call.getArgument(0));return null;});
        // Paper closes the previous view AFTER the new menu has rendered, before installing the new view.
        doAnswer(call->{
            Inventory next=call.getArgument(0);
            if(top.getHolder() instanceof Menu) paperClose(top);
            top=next;return view;
        }).when(player).openInventory(any(Inventory.class));
        return handlers;
    }
    private EquipmentMenu equipmentWithPreview() {
        var draft=new MobMenu.MobDraft(store.mobs().get("mob"));
        draft.equipment.put(EquipmentSlot.HAND,new EquipmentDef(item(Material.STONE_SWORD),0));
        return new EquipmentMenu(player,draft,draft,list);
    }
    private void assertPreviewCannotBePickedUp(EquipmentMenu menu) {
        var cursor=new java.util.concurrent.atomic.AtomicReference<ItemStack>();
        when(player.getItemOnCursor()).thenAnswer(call->cursor.get());
        doAnswer(call->{cursor.set(call.getArgument(0));return null;}).when(player).setItemOnCursor(any());
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var click=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(click.getView()).thenReturn(view);when(click.getWhoClicked()).thenReturn(player);
        when(click.getRawSlot()).thenReturn(19);when(click.isLeftClick()).thenReturn(true);
        when(click.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        when(click.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        when(click.getCursor()).thenAnswer(call->cursor.get());
        doAnswer(call->{cancelled.set(call.getArgument(0));return null;}).when(click).setCancelled(anyBoolean());
        ItemStack preview=top.getItem(19);
        framework.onClick(click);
        if(listeners.contains(menu)) menu.placed(click);
        // Simulate vanilla applying an uncancelled pickup; the actual template must never reach the cursor.
        if(!cancelled.get()) {player.setItemOnCursor(preview);top.setItem(19,null);}
        assertAll(()->assertTrue(cancelled.get(),"Template pickup must remain cancelled"),
                ()->assertNull(player.getItemOnCursor(),"No draft copy on the cursor"),
                ()->assertSame(preview,top.getItem(19)));
        verify(player.getInventory(),never()).addItem(preview);
    }
    @Test void twoEnqueuedBackClicksKeepEquipmentListenerAndBlockTemplatePickup() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            var menu=equipmentWithPreview();menu.open();clickSlot(37);
            assertInstanceOf(EnchantMenu.class,top.getHolder());
            var button=Menu.class.getDeclaredMethod("buttonAt",int.class);button.setAccessible(true);
            var back=(Button)button.invoke(top.getHolder(),top.getSize()-9);
            back.onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT);
            back.onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT);
            drain();
            assertSame(menu,top.getHolder());
            assertPreviewCannotBePickedUp(menu);
            assertTrue(listeners.contains(menu),"The active inventory still owns its listener");
        }
    }
    @Test void lateEquipmentCloseDoesNotUnregisterCurrentInventoryListener() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            var menu=equipmentWithPreview();menu.open();Inventory previous=top;
            menu.open();Inventory current=top;
            paperClose(previous);drain();
            assertAll(()->assertNotSame(previous,current,"Each reopening needs its own inventory instance"),
                    ()->assertTrue(listeners.contains(menu),"Stale close must not unregister the current binding"));
            assertPreviewCannotBePickedUp(menu);
            paperClose(current);
            assertFalse(listeners.contains(menu),"A genuine close releases only the current binding");
            assertNull(current.getItem(19),"Closed inventories retain no draft copies to recapture");
            menu.open();
            assertTrue(listeners.contains(menu));
            assertPreviewCannotBePickedUp(menu);
        }
    }
    @Test void equipmentCopiesAreProtectedEvenWithoutTemporaryListener() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            var menu=equipmentWithPreview();menu.open();
            org.bukkit.event.HandlerList.unregisterAll(menu);
            assertPreviewCannotBePickedUp(menu);
        }
    }
    @Test void eventsFromOldRewardInventoryAreCancelledAfterReopening() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            var root=remember(definition("reward-stale-events"));
            var menu=new RewardMenu(root);menu.open();Inventory previous=top;menu.open();
            var staleView=mock(InventoryView.class);when(staleView.getTopInventory()).thenReturn(previous);
            var click=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            when(click.getView()).thenReturn(staleView);when(click.getWhoClicked()).thenReturn(player);
            when(click.getRawSlot()).thenReturn(19);when(click.isLeftClick()).thenReturn(true);
            when(click.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            framework.onClick(click);verify(click).setCancelled(true);verify(click,never()).setCancelled(false);
            var drag=rewardDrag(Set.of(19));when(drag.getView()).thenReturn(staleView);
            framework.onDrag(drag);verify(drag).setCancelled(true);verify(drag,never()).setCancelled(false);
        }
    }
    @Test void obsoleteQueuedResizeCannotReopenTheCurrentInventory() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            int[] rows={3};
            var menu=new Menu(player,Component.empty(),3) {
                @Override protected int preferredRows() {return rows[0];}
                @Override protected void render() {}
            };
            menu.open();rows[0]=4;menu.refresh(); // Queue the switch from the old view to its resized replacement.
            menu.open();Inventory current=top; // A newer opening already installed its own inventory.
            drain();
            assertSame(current,top);
            verify(player,times(2)).openInventory(any(Inventory.class));
        }
    }
    @Test void closingDuringQueuedResizeDoesNotReopenMenuOrRetainEditLock() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            int[] rows={3};
            var menu=new Menu(player,Component.empty(),3) {
                @Override protected int preferredRows() {return rows[0];}
                @Override protected void render() {}
            };
            menu.open();Inventory previous=top;
            assertTrue(locks.tryLock("resize",player.getUniqueId()));
            rows[0]=4;menu.refresh();
            player.closeInventory();paperClose(previous);drain();
            assertFalse(top.getHolder() instanceof Menu,"An obsolete switch must not reopen a closed editor");
            assertTrue(locks.holder("resize").isEmpty());
            verify(player,times(1)).openInventory(any(Inventory.class));
        }
    }
    @Test void twoQueuedResizesDisplayOnlyTheLatestReplacement() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            int[] rows={3};
            var menu=new Menu(player,Component.empty(),3) {
                @Override protected int preferredRows() {return rows[0];}
                @Override protected void render() {}
            };
            menu.open();rows[0]=4;menu.refresh();rows[0]=5;menu.refresh();
            Inventory latest=menu.getInventory();drain();
            assertSame(latest,top);assertEquals(45,top.getSize());
            verify(player,times(2)).openInventory(any(Inventory.class));
        }
    }
    @Test void undeclaredEmptySlotDoesNotAllowNativePickupOrPlacement() {
        var menu=new Menu(player,Component.empty(),3) {
            @Override protected void render() {clear(13);}
        };
        menu.open();
        for(var action:List.of(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL,org.bukkit.event.inventory.InventoryAction.PLACE_ALL)) {
            var click=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            when(click.getView()).thenReturn(view);when(click.getWhoClicked()).thenReturn(player);
            when(click.getRawSlot()).thenReturn(13);when(click.isLeftClick()).thenReturn(true);when(click.getAction()).thenReturn(action);
            framework.onClick(click);verify(click).setCancelled(true);verify(click,never()).setCancelled(false);
        }
    }
    @Test void lateRewardCloseDoesNotCaptureOrReturnDepositsFromReopenedInventory() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            var root=remember(definition("reward-lifecycle"));
            var menu=new RewardMenu(root);menu.open();Inventory previous=top;
            ItemStack first=item(Material.DIAMOND);previous.setItem(18,first);
            menu.open();Inventory current=top;
            verify(player.getInventory(),times(1)).addItem(first);
            ItemStack second=item(Material.EMERALD);current.setItem(19,second);
            paperClose(previous);drain();
            assertNotSame(previous,current);
            assertSame(second,current.getItem(19));
            verify(player.getInventory(),never()).addItem(second);
            paperClose(current);paperClose(current);
            verify(player.getInventory(),times(1)).addItem(second);
            assertEquals(List.of(first,second),root.draft.get().reward().items());
        }
    }

    @Test void creatingMobPersistsOnIdConfirmationBeforeEditorOpens() throws Exception {
        var registered=plugin.getServer().getServicesManager();
        bukkit.when(Bukkit::getServicesManager).thenReturn(registered);
        when(store.mobs()).thenReturn(Map.of());
        var future=new CompletableFuture<Void>();
        when(store.save(any(MobTemplate.class))).thenReturn(future);
        var accepted=new java.util.concurrent.atomic.AtomicReference<java.util.function.Consumer<String>>();
        inputs.when(()->Inputs.text(eq(player),any(),eq(""),eq(32),any())).thenAnswer(call->{accepted.set(call.getArgument(4));return null;});
        var library=new MobLibraryMenu(player,list);library.open();clickSlot(library.getInventory().getSize()-5);
        assertNotNull(accepted.get());accepted.get().accept("created");accepted.get().accept("created");
        var template=org.mockito.ArgumentCaptor.forClass(MobTemplate.class);
        verify(store,times(1)).save(template.capture());assertEquals("created",template.getValue().id());
        assertSame(library,top.getHolder(),"Do not open an unsaved template while the write is pending");
        future.complete(null);drain();assertInstanceOf(MobMenu.class,top.getHolder());
    }

    private MobMenu.MobDraft equipmentDraft(String type) {
        var services=plugin.getServer().getServicesManager();
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        var manager=plugin.getServer().getPluginManager();
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        when(services.load(PluginConfig.class)).thenReturn(new PluginConfig("","es",null,"world",false,null,
                null,Set.of(org.bukkit.entity.EntityType.ZOMBIE),List.of(),null,null,300,Map.of()));
        return new MobMenu.MobDraft(new MobTemplate("equipment",type,"",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false));
    }
    @Test void equipmentInputsStayEmptyWithRealFrameAndAcceptClicksAndDrags() {
        theme.close();theme=null;
        var equipmentSlots=List.of(EquipmentSlot.HAND,EquipmentSlot.OFF_HAND,EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
        for(String type:List.of("ZOMBIE","WARDEN")) {
            var draft=equipmentDraft(type);var menu=new EquipmentMenu(player,draft,draft,list);menu.open();
            var slots=type.equals("ZOMBIE")?List.of(19,20,21,23,24,25):List.of(19,20);
            // Exercise the frame itself: render() clearing inputs must not hide an incorrect reservation.
            GuiTheme.frame(menu);
            assertEquals(Material.GRAY_STAINED_GLASS_PANE,top.getItem(22).getType());
            for(int index=0;index<slots.size();index++) {
                int slot=slots.get(index);
                assertNull(top.getItem(slot),type+" input "+slot);
                assertTrue(menu.allowsPlacement(slot));
                var original=item(Material.DIAMOND_SWORD);var copied=item(Material.DIAMOND_SWORD);
                when(original.clone()).thenReturn(copied);
                var click=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
                when(click.getView()).thenReturn(view);when(click.getWhoClicked()).thenReturn(player);
                when(click.getRawSlot()).thenReturn(slot);when(click.isLeftClick()).thenReturn(true);
                when(click.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
                when(click.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
                when(click.getCursor()).thenReturn(original);
                framework.onClick(click);menu.placed(click);
                verify(click,never()).setCancelled(false);verify(click,atLeastOnce()).setCancelled(true);
                assertSame(original,click.getCursor());
                assertSame(copied,draft.equipment.get(equipmentSlots.get(index)).item());
                drain();
                assertSame(copied,top.getItem(slot),"The second row is also the equipment preview");
                // Frames leave occupied inputs intact as well as empty ones.
                GuiTheme.frame(menu);assertSame(copied,top.getItem(slot));
                verify(player.getInventory(),never()).addItem(original);
                verify(player.getInventory(),never()).addItem(copied);
            }
            var dragged=new HashMap<Integer,ItemStack>();var copies=new HashMap<Integer,ItemStack>();
            for(int slot:slots) {
                var original=item(Material.IRON_SWORD);var copied=item(Material.IRON_SWORD);
                when(original.clone()).thenReturn(copied);dragged.put(slot,original);copies.put(slot,copied);
            }
            var drag=rewardDrag(new HashSet<>(slots));when(drag.getNewItems()).thenReturn(dragged);
            framework.onDrag(drag);menu.dragged(drag);
            verify(drag,never()).setCancelled(false);verify(drag,atLeastOnce()).setCancelled(true);
            drain();
            for(int index=0;index<slots.size();index++) {
                int slot=slots.get(index);
                assertSame(copies.get(slot),top.getItem(slot));
                assertSame(copies.get(slot),draft.equipment.get(equipmentSlots.get(index)).item());
                verify(player.getInventory(),never()).addItem(dragged.get(slot));
                verify(player.getInventory(),never()).addItem(copies.get(slot));
            }
            var beforeMixedDrag=draft.snapshot();
            var mixed=rewardDrag(Set.of(slots.getFirst(),22));
            var rejected=item(Material.GOLDEN_SWORD);
            when(mixed.getNewItems()).thenReturn(Map.of(slots.getFirst(),rejected));
            framework.onDrag(mixed);menu.dragged(mixed);
            verify(mixed,never()).setCancelled(false);assertEquals(beforeMixedDrag,draft.snapshot());
            assertPreviewCannotBePickedUp(menu);drain();
            // Recover real physical deposits from other inventory writers exactly once.
            var deposited=new ArrayList<ItemStack>();
            for(int slot:slots) {var item=item(Material.DIAMOND_SWORD);deposited.add(item);top.setItem(slot,item);}
            menu.acceptPlacedItems();menu.acceptPlacedItems();
            assertEquals(slots.size(),draft.equipment.size());
            for(int slot:slots) assertNull(top.getItem(slot));
            menu.refresh();menu.acceptPlacedItems();menu.acceptPlacedItems();
            for(var item:deposited) verify(player.getInventory(),times(1)).addItem(item);
            assertEquals(Material.GRAY_STAINED_GLASS_PANE,top.getItem(22).getType());
        }
    }
    @Test void capturingEmptyEquipmentWithRealFrameNeverCopiesOrReturnsDecorations() {
        theme.close();theme=null;
        for(String type:List.of("ZOMBIE","WARDEN")) {
            var draft=equipmentDraft(type);var menu=new EquipmentMenu(player,draft,draft,list);menu.open();
            menu.acceptPlacedItems();menu.acceptPlacedItems();
            assertEquals(Map.of(),draft.equipment);
            verify(player.getInventory(),never()).addItem(any(ItemStack.class));
        }
    }
    @Test void equipmentReturnsInputsFromPreviousLayoutAfterEntityTypeChanges() {
        theme.close();theme=null;
        var draft=equipmentDraft("ZOMBIE");var menu=new EquipmentMenu(player,draft,draft,list);menu.open();
        var previousInputs=List.of(21,23,24,25);var deposited=new ArrayList<ItemStack>();
        for(int slot:previousInputs) {
            var item=item(Material.DIAMOND_HELMET);deposited.add(item);top.setItem(slot,item);
        }
        draft.type="WARDEN";menu.acceptPlacedItems();menu.acceptPlacedItems();
        assertTrue(draft.equipment.isEmpty(),"Unsupported armor deposits must not become equipment");
        for(int slot:previousInputs) assertNull(top.getItem(slot));
        for(var item:deposited) verify(player.getInventory(),times(1)).addItem(item);
        menu.refresh();menu.acceptPlacedItems();
        for(int slot:previousInputs) {
            assertEquals(Material.GRAY_DYE,top.getItem(slot).getType());
            assertFalse(menu.allowsPlacement(slot));
        }
        for(int slot:List.of(19,20)) {assertNull(top.getItem(slot));assertTrue(menu.allowsPlacement(slot));}
        assertEquals(Material.GRAY_STAINED_GLASS_PANE,top.getItem(22).getType());
        for(var item:deposited) verify(player.getInventory(),times(1)).addItem(item);
    }
    @Test void equipmentReopeningAfterEntityTypeChangeReturnsPreviousDepositsAndKeepsCopiesProtected() throws Exception {
        theme.close();theme=null;
        try(var handlers=paperInventoryLifecycle()) {
            var draft=equipmentDraft("ZOMBIE");var hand=item(Material.STONE_SWORD);
            draft.equipment.put(EquipmentSlot.HAND,new EquipmentDef(hand,0));
            var menu=new EquipmentMenu(player,draft,draft,list);menu.open();
            Inventory previous=top;var deposited=item(Material.DIAMOND_HELMET);previous.setItem(21,deposited);
            draft.type="WARDEN";menu.open();
            assertNotSame(previous,top);assertNull(previous.getItem(21));
            assertEquals(Set.of(EquipmentSlot.HAND),draft.equipment.keySet());
            assertEquals(Material.GRAY_DYE,top.getItem(21).getType());
            verify(player.getInventory(),times(1)).addItem(deposited);
            verify(player.getInventory(),never()).addItem(hand);
            assertPreviewCannotBePickedUp(menu);drain();
            paperClose(previous);paperClose(top);paperClose(top);
            verify(player.getInventory(),times(1)).addItem(deposited);
            verify(player.getInventory(),never()).addItem(hand);
        }
    }
    @Test void roomListValidatesDoorsOncePerOpeningAndUpdatesRoomIconsOnReopening() throws Exception {
        var server=plugin.getServer();
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
        var block=mock(org.bukkit.block.Block.class);
        when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        var values=new DungeonMenu.Values(definition("doors"));var sample=values.rooms.getFirst();
        var door=Region.of("world",new BlockPos(0,0,0),new BlockPos(1,0,0));
        values.rooms=java.util.stream.IntStream.range(0,8).mapToObj(i->new RoomDef("r"+i,sample.region(),sample.checkpoint(),door,
                UnlockMode.AUTOMATIC,null,sample.spawners())).toList();
        var root=remember(values.build());var menu=new RoomListMenu(root);menu.open();
        verify(world,times(16)).getBlockAt(anyInt(),anyInt(),anyInt());
        for(int i=0;i<8;i++) assertEquals(Material.OAK_DOOR,top.getItem(GuiLayout.pageSlot(i,8,1)).getType());
        // Opening again must see changes in the world instead of keeping stale valid icons.
        when(block.getState()).thenReturn(mock(org.bukkit.block.TileState.class));clearInvocations(world);
        menu.open();verify(world,times(8)).getBlockAt(anyInt(),anyInt(),anyInt());
        for(int i=0;i<8;i++) assertEquals(Material.IRON_DOOR,top.getItem(GuiLayout.pageSlot(i,8,1)).getType());
    }

    @Test void rewardDepositsReachAll27CellsWithTheRealThemeAndReturnEveryItemOnce() throws Exception {
        theme.close();theme=null; // Exercise real frame and navigation button registration.
        var root=remember(definition("reward"));
        var menu=new RewardMenu(root);menu.open();
        var deposited=new ArrayList<ItemStack>();
        for(int slot:RewardMenu.itemSlots()) {
            assertNull(menu.getInventory().getItem(slot));
            assertTrue(menu.allowsPlacement(slot));
            var event=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(player);
            when(event.getRawSlot()).thenReturn(slot);when(event.isLeftClick()).thenReturn(true);
            when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
            when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            framework.onClick(event);
            verify(event).setCancelled(false);
            var item=item(mock(Material.class));
            deposited.add(item);
            menu.getInventory().setItem(slot,item); // Simulate Bukkit applying the permitted deposit.
        }
        menu.capture();menu.capture();
        assertEquals(deposited,root.draft.get().reward().items());
        for(var item:deposited) verify(player.getInventory(),times(1)).addItem(item);
        for(int slot:RewardMenu.itemSlots()) assertFalse(menu.allowsPlacement(slot));
    }
    @Test void rewardDragsReachAll27CellsWithTheRealThemeAndRejectControls() throws Exception {
        theme.close();theme=null;
        var root=remember(definition("reward"));
        var menu=new RewardMenu(root);menu.open();
        for(int slot:RewardMenu.itemSlots()) {
            var event=rewardDrag(Set.of(slot));framework.onDrag(event);
            verify(event).setCancelled(false);
        }
        var all=rewardDrag(new HashSet<>(RewardMenu.itemSlots()));framework.onDrag(all);
        verify(all).setCancelled(false);
        for(int blocked:new int[]{0,4,11,13,15,45,48,49,50,53}) {
            var mixed=rewardDrag(Set.of(18,blocked));framework.onDrag(mixed);
            verify(mixed,never()).setCancelled(false);
        }
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(false);
        var denied=rewardDrag(Set.of(18));framework.onDrag(denied);
        verify(denied,never()).setCancelled(false);
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(true);
        when(store.isReloading()).thenReturn(true);
        var reload=rewardDrag(Set.of(18));framework.onDrag(reload);
        verify(reload,never()).setCancelled(false);
    }
    private org.bukkit.event.inventory.InventoryDragEvent rewardDrag(Set<Integer> slots) {
        var event=mock(org.bukkit.event.inventory.InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlots()).thenReturn(slots);
        return event;
    }

    @Test void sectionHeadersAndControlsDoNotCollideWithListsOrDeposits() throws Exception {
        DungeonMenu root=remember(definition("new"));
        var settings=new DungeonSettingsMenu(root);settings.refresh();
        assertEquals(Material.WHITE_STAINED_GLASS_PANE,settings.getInventory().getItem(10).getType());
        assertEquals(Material.WHITE_STAINED_GLASS_PANE,settings.getInventory().getItem(12).getType());
        for(int slot:new int[]{4,8,10,12,14,16,19,21,23,25,28,30,32,34,37,41})
            assertNotNull(settings.getInventory().getItem(slot));
        var reward=new RewardMenu(root);reward.refresh();
        for(int slot:new int[]{4,11,13,15}) {
            assertNotNull(reward.getInventory().getItem(slot));
            assertFalse(reward.allowsPlacement(slot));
        }
        for(int slot=18;slot<45;slot++) {
            assertNull(reward.getInventory().getItem(slot));
            assertTrue(reward.allowsPlacement(slot));
        }
        var room=new RoomMenu(root,0,new RoomListMenu(root));room.refresh();
        assertEquals(Material.LIME_DYE,room.getInventory().getItem(32).getType());
        assertEquals(Material.OAK_DOOR,room.getInventory().getItem(4).getType());
        assertEquals(Material.GRAY_DYE,room.getInventory().getItem(34).getType());
        for(int slot:new int[]{4,8,10,12,14,16,19,21,23,25,28,30,32,34,37,39,41,43})
            assertNotNull(room.getInventory().getItem(slot));
        var spawners=new RoomSpawnerList(root,0,room);spawners.refresh();
        assertEquals(Material.SPAWNER,spawners.getInventory().getItem(13).getType());
        var wave=new WaveMenu(root,0,0,0,room);wave.refresh();
        assertEquals(Material.TNT,wave.getInventory().getItem(20).getType());
        assertEquals(Material.ZOMBIE_SPAWN_EGG,wave.getInventory().getItem(24).getType());
    }

    @Test void ambienceApprovedSlotsStoreOnlyChangesAndResetWithShift() throws Exception {
        var root=remember(definition("ambience"));
        var room=new RoomMenu(root,0,root);room.open();
        assertEquals(Material.SPAWNER,top.getItem(37).getType());
        assertEquals(Material.LIME_DYE,top.getItem(39).getType());
        assertEquals(Material.SPYGLASS,top.getItem(41).getType());
        assertEquals(Material.NOTE_BLOCK,top.getItem(43).getType());
        clickSlot(43);assertInstanceOf(AmbienceMenu.class,top.getHolder());
        for(int slot:new int[]{19,28,37,21,30,39,23,32,41,25,34,43})assertNotNull(top.getItem(slot));
        assertNull(root.draft.get().rooms().getFirst().ambience());
        clickSlot(41);assertEquals(Map.of("door-shake",true),root.draft.get().rooms().getFirst().ambience().values());
        clickSlot(43);assertNotNull(root.draft.get().rooms().getFirst().ambience());
        clickSlot(43,org.bukkit.event.inventory.ClickType.SHIFT_LEFT);assertNull(root.draft.get().rooms().getFirst().ambience());
        clickSlot(41);clickSlot(41,org.bukkit.event.inventory.ClickType.RIGHT);assertNull(root.draft.get().rooms().getFirst().ambience());
        clickSlot(39,org.bukkit.event.inventory.ClickType.RIGHT);assertEquals(Map.of("density",12),root.draft.get().rooms().getFirst().ambience().values());
        clickSlot(39,org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);assertNull(root.draft.get().rooms().getFirst().ambience());
        clickSlot(30);
        for(int slot:new int[]{37,38,39})assertEquals(Material.CLOCK,top.getItem(slot).getType());
        for(int slot:new int[]{40,41})assertEquals(Material.CAMPFIRE,top.getItem(slot).getType());
        assertEquals(Material.NOTE_BLOCK,top.getItem(42).getType());assertEquals(Material.NAME_TAG,top.getItem(43).getType());
        clickSlot(40,org.bukkit.event.inventory.ClickType.RIGHT);assertNull(root.draft.get().rooms().getFirst().ambience());
    }
    @Test void ambienceChangesArePreservedWhenEditingSpawnerAndRoomGeometry() throws Exception {
        var root=remember(definition("ambience_copy"));
        var settings=new RoomAmbience(Map.of("entry-title","Cubil"));root.room(0,r->r.withAmbience(settings));
        root.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),5,s.waves(),s.presetId()));
        assertEquals(settings,root.draft.get().rooms().getFirst().ambience());
        new RoomMenu(root,0,root).open();clickSlot(21);
        assertEquals(settings,root.draft.get().rooms().getFirst().ambience());
    }

    @Test void coloredDungeonNameIsParsedInTextDialogTitle() throws Exception {
        var messages=new Messages();
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/messages.yml"),java.nio.charset.StandardCharsets.UTF_8)) {
            var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.load(reader);messages.load(yaml,"");
        }
        framework=new MenuListener(plugin,messages,new PluginConfig.GuiSounds("","","",""),locks);
        menuServices.when(MenuListener::instance).thenReturn(framework);
        var root=remember(definition("colored"));root.change(v->v.name="&6Cueva");
        var title=new java.util.concurrent.atomic.AtomicReference<Component>();
        inputs.when(()->Inputs.text(eq(player),any(),eq("&6Cueva"),eq(128),any()))
                .thenAnswer(call->{title.set(call.getArgument(1));return null;});
        new DungeonSettingsMenu(root).open();clickSlot(37);
        assertEquals(messages.get("gui.dungeon.name",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component(
                "value",dev.dasan.customdungeons.text.Text.parse("&6Cueva"))),title.get());
    }
    @Test void incompleteRoomUsesIronDoorAndMissingHeadersAreRed() throws Exception {
        var root=remember(definition("incomplete"));
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,v.rooms.getFirst()));
        root.room(0,r->new RoomDef(r.id(),null,null,r.door(),UnlockMode.KEY,null,r.spawners()));
        var rooms=new RoomListMenu(root);rooms.refresh();assertEquals(Material.IRON_DOOR,rooms.getInventory().getItem(12).getType());
        var room=new RoomMenu(root,0,rooms);room.refresh();
        for(int slot:new int[]{10,12,14,16}) assertEquals(Material.RED_STAINED_GLASS_PANE,room.getInventory().getItem(slot).getType());
    }
    @Test void roomSectionHeadersStayReadOnlyWhileUnlockModeRemainsEditable() throws Exception {
        DungeonMenu root=remember(definition("new"));
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,v.rooms.getFirst()));
        var room=new RoomMenu(root,0,new RoomListMenu(root));room.open();
        var before=root.draft.get();
        for(int slot:new int[]{4,8,10,12,14,16}) clickSlot(slot);
        assertEquals(before,root.draft.get());
        assertSame(room,top.getHolder());
        clickSlot(25);
        assertEquals(UnlockMode.KEY,root.draft.get().rooms().getFirst().unlock());
        assertNull(root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void openingCyclesThroughThreeModesAndSpawnerEditsPreservePuzzle() throws Exception {
        var root=remember(definition("puzzle"));
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,v.rooms.getFirst()));
        var room=new RoomMenu(root,0,new RoomListMenu(root));room.open();
        for(var expected:List.of(RoomDef.OpeningMode.KEY,RoomDef.OpeningMode.EXTERNAL_KEY,RoomDef.OpeningMode.AUTOMATIC)) {
            clickSlot(25);
            assertEquals(expected,root.draft.get().rooms().getFirst().openingMode());
            assertEquals(switch(expected) { case AUTOMATIC -> Material.LIME_DYE; case KEY -> Material.TRIPWIRE_HOOK; case EXTERNAL_KEY -> Material.COMMAND_BLOCK; },top.getItem(25).getType());
            if(expected!=RoomDef.OpeningMode.KEY) assertEquals(Material.GRAY_DYE,top.getItem(34).getType());
        }
        clickSlot(25);clickSlot(25);
        var before=root.draft.get().rooms().getFirst();
        root.spawner(0,0,sp->new SpawnerDef(sp.id(),sp.location(),4,sp.waves(),sp.presetId()));
        assertEquals(RoomDef.OpeningMode.EXTERNAL_KEY,root.draft.get().rooms().getFirst().openingMode());
        assertEquals(before.keyCarrierTemplateId(),root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void finalPuzzleRoomHasAutomaticDisplayAndReadOnlyControls() throws Exception {
        var root=remember(definition("final-puzzle"));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.KEY,null,r.spawners(),RoomDef.OpeningMode.EXTERNAL_KEY));
        var menu=new RoomMenu(root,0,root);menu.open();var before=root.draft.get();
        assertEquals(Material.GRAY_DYE,top.getItem(25).getType());assertEquals(Material.GRAY_DYE,top.getItem(34).getType());
        clickSlot(25);clickSlot(34);assertEquals(before,root.draft.get());
    }
    @Test void finalRoomUnlockAndCarrierAreReadOnlyEvenForLegacyKeyDraft() throws Exception {
        var root=remember(definition("final"));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.KEY,"missing",r.spawners()));
        var menu=new RoomMenu(root,0,root);menu.open();var before=root.draft.get();
        assertEquals(Material.GRAY_DYE,top.getItem(25).getType());
        assertEquals(Material.GRAY_DYE,top.getItem(34).getType());
        for(int slot:new int[]{14,16}) assertEquals(Material.LIME_STAINED_GLASS_PANE,top.getItem(slot).getType());
        clickSlot(25);clickSlot(34);assertEquals(before,root.draft.get());assertSame(menu,top.getHolder());
    }
    @Test void savingLegacyFinalKeyDraftKeepsEditorInSyncWithNormalizedDefinition() throws Exception {
        var original=definition("final-save");definitions.put(original.id(),original);
        var root=remember(original);
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.KEY,"missing",r.spawners()));
        root.open();
        when(store.save(any(DungeonDef.class))).thenAnswer(call->{
            var raw=(DungeonDef)call.getArgument(0);var r=raw.rooms().getLast();
            var saved=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(raw,List.of(
                    new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),UnlockMode.AUTOMATIC,r.keyCarrierTemplateId(),r.spawners())));
            definitions.put(raw.id(),saved);return CompletableFuture.completedFuture(null);
        });
        root.saveDraft();drain();assertFalse(root.dirty());assertFalse(root.outdated());
        assertEquals(UnlockMode.AUTOMATIC,root.draft.get().rooms().getLast().unlock());
        assertEquals(definitions.get(original.id()),root.draft.get());
        root.change(v->v.name="Edited again");assertEquals("Edited again",root.draft.get().displayName());
    }
    @Test void finalKeyRoomListShowsEffectiveAutomaticUnlockAndOptionalDoor() throws Exception {
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/messages.yml"),java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        var messages=new Messages();messages.load(yaml,"");
        when(plugin.messages().get(anyString(),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class)))
                .thenAnswer(call->messages.get(call.getArgument(0),(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[])call.getRawArguments()[1]));
        var loreByItem=new IdentityHashMap<ItemStack,List<Component>>();
        buttons.when(()->Button.of(any(),any(),anyList(),any())).thenAnswer(call->{
            var item=item(call.getArgument(0));loreByItem.put(item,call.getArgument(2));
            return new Button(item,call.getArgument(3));
        });
        var root=remember(definition("final-list"));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.KEY,null,r.spawners()));
        new RoomListMenu(root).open();
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        var lore=loreByItem.get(top.getItem(13)).stream().map(plain::serialize).toList();
        assertTrue(lore.contains("✔ Puerta"),lore.toString());
        assertTrue(lore.contains("✔ Desbloqueo (Automático)"),lore.toString());
    }
    @Test void addingReorderingAndDeletingRoomsRecomputesFinalUnlockAvailability() throws Exception {
        var root=remember(definition("room-order"));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.KEY,"*",r.spawners()));
        var rooms=new RoomListMenu(root);
        new RoomMenu(root,0,rooms).open();assertEquals(Material.GRAY_DYE,top.getItem(25).getType());
        rooms.open();clickSlot(top.getSize()-7); // Add a new final room.
        assertEquals(2,root.draft.get().rooms().size());
        new RoomMenu(root,0,rooms).open();assertEquals(Material.TRIPWIRE_HOOK,top.getItem(25).getType());
        assertEquals(UnlockMode.KEY,root.draft.get().rooms().getFirst().unlock());
        rooms.open();clickSlot(GuiLayout.pageSlot(1,2,1),org.bukkit.event.inventory.ClickType.SHIFT_LEFT);
        assertEquals("r",root.draft.get().rooms().getLast().id());
        new RoomMenu(root,1,rooms).open();assertEquals(Material.GRAY_DYE,top.getItem(25).getType());
        var before=root.draft.get();clickSlot(25);assertEquals(before,root.draft.get());
        rooms.open();clickSlot(GuiLayout.pageSlot(1,2,1),org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);
        assertNotNull(confirm);confirm.run();drain();
        assertEquals(1,root.draft.get().rooms().size());
        new RoomMenu(root,0,rooms).open();assertEquals(Material.GRAY_DYE,top.getItem(25).getType());
    }
    @Test void healthDialogUses1024AndRejectsOversizedSubmission() throws Exception {
        var draft=new MobMenu.MobDraft(store.mobs().get("mob"));draft.health=1024;
        var accepted=new java.util.concurrent.atomic.AtomicReference<java.util.function.DoubleConsumer>();
        inputs.when(()->Inputs.ranged(eq(player),any(),eq(dev.dasan.customdungeons.config.NumericRanges.HEALTH),eq(1024d),any()))
                .thenAnswer(call->{accepted.set(call.getArgument(4));return null;});
        new StatsMenu(player,draft,list).open();clickSlot(19);
        assertNotNull(accepted.get());accepted.get().accept(2048);assertEquals(1024,draft.health);
        accepted.get().accept(500);assertEquals(500,draft.health);
    }
    @Test void roomAndSpawnerNavigationPreservesDraftAndReturnsToTheSameList() throws Exception {
        DungeonMenu root=remember(definition("new"));
        var rooms=new RoomListMenu(root);rooms.open();
        assertEquals(Material.LIME_DYE,top.getItem(20).getType());
        clickSlot(13);
        var room=assertInstanceOf(RoomMenu.class,top.getHolder());
        clickSlot(39);
        assertInstanceOf(SpawnerPickerMenu.class,top.getHolder());
        clickSlot(25);clickSlot(45);
        assertEquals(2,root.draft.get().rooms().getFirst().spawners().size());
        clickSlot(37);
        var list=assertInstanceOf(RoomSpawnerList.class,top.getHolder());
        assertSame(room,list.parent());
        clickSlot(12);
        var spawner=assertInstanceOf(SpawnerMenu.class,top.getHolder());
        assertSame(list,spawner.parent());
        assertSame(root,spawner.root);
    }
    @Test void directWavePreviewReturnsToSpawnerAndDeletionRefreshesTheVisiblePreview() throws Exception {
        var root=remember(definition("preview"));var spawner=new SpawnerMenu(root,0,0,root);spawner.open();
        clickSlot(24);var wave=assertInstanceOf(WaveMenu.class,top.getHolder());assertSame(spawner,wave.parent());
        spawner.open();clickSlot(24,org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);
        assertTrue(root.draft.get().rooms().getFirst().spawners().getFirst().waves().isEmpty());
        assertNull(top.getItem(24));
        assertEquals(Material.LIME_DYE,top.getItem(47).getType());
    }
    @Test void spawnerPaginationKeepsEveryEntryAfterMovingItOutOfRoomPanels() throws Exception {
        DungeonMenu root=remember(definition("new"));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),
                java.util.stream.IntStream.range(0,29).mapToObj(i->new SpawnerDef("s"+i,null,3,List.of())).toList()));
        var room=new RoomMenu(root,0,new RoomListMenu(root));
        var list=new RoomSpawnerList(root,0,room);list.refresh();
        for(int row=1;row<5;row++) for(int column=1;column<8;column++)
            assertEquals(Material.SPAWNER,list.getInventory().getItem(row*9+column).getType());
        assertTrue(list.hasNextPage());list.nextPage();
        assertEquals(Material.SPAWNER,list.getInventory().getItem(13).getType());
        assertFalse(list.hasNextPage());assertTrue(list.hasPreviousPage());
        assertEquals(29,list.entries().size());
    }

    @Test void rootSeparatesActionsAndShowsEachDungeonState() {
        var active=new DungeonMenu.Values(definition("a"));active.enabled=true;
        definitions.put("a",active.build());definitions.put("b",definition("b"));definitions.put("c",definition("c"));
        DungeonListMenu.dungeonBusy(id->id.equals("c"));list.refresh();
        assertEquals(Material.LIME_DYE,topItem(list,49));
        assertEquals(Material.LIME_CONCRETE,topItem(list,11));
        assertEquals(Material.GRAY_CONCRETE,topItem(list,13));
        assertEquals(Material.ORANGE_CONCRETE,topItem(list,15));
        list=new DungeonListMenu(player);list.refresh();
        assertEquals(27,list.getInventory().getSize());
        assertEquals(Material.BOOKSHELF,topItem(list,10));
        assertEquals(Material.LIME_DYE,topItem(list,12));
        assertEquals(Material.BOOK,topItem(list,14));
        assertEquals(Material.SPAWNER,topItem(list,16));
    }
    private Material topItem(Menu menu,int slot) {return menu.getInventory().getItem(slot).getType();}

    @Test void newRoomUsesLastMobAsDefaultCarrier() throws Exception {
        var root = remember(definition("keys"));
        new RoomListMenu(root).create();
        assertEquals("*",root.draft.get().rooms().getLast().keyCarrierTemplateId());
    }
    @Test void carrierCandidatesComeOnlyFromThisRoomsWaves() throws Exception {
        var original=definition("keys").rooms().getFirst();
        var wave=new WaveDef(List.of(new WaveEntry("mob",2,0),new WaveEntry("mob",1,0),new WaveEntry("missing",1,0)),SpawnMode.SIMULTANEOUS,0,0);
        var room=new RoomDef(original.id(),original.region(),original.checkpoint(),original.door(),UnlockMode.KEY,"*",
                List.of(new SpawnerDef("s",original.checkpoint(),2.5,List.of(wave))));
        var mobs=new HashMap<>(store.mobs());mobs.put("unrelated",store.mobs().get("mob"));
        assertEquals(List.of("mob"),RoomMenu.carrierTemplates(room,mobs));
    }
    @Test void carrierPickerWithoutRoomMobsShowsUnavailableAlongsideLastMob() throws Exception {
        var root=remember(definition("keys"));
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,v.rooms.getFirst()));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),UnlockMode.KEY,"*",List.of()));
        new RoomMenu(root,0,root).open();clickSlot(34);
        assertEquals(Material.TRIPWIRE_HOOK,top.getItem(12).getType());
        assertEquals(Material.GRAY_DYE,top.getItem(14).getType());
        clickSlot(14);assertEquals("*",root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void spawnerRadiusUsesDecimalInputAndPreservesTwoPointFive() throws Exception {
        var root=remember(definition("radius"));
        root.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),2.5,s.waves()));
        var accepted=new java.util.concurrent.atomic.AtomicReference<java.util.function.DoubleConsumer>();
        inputs.when(()->Inputs.ranged(eq(player),any(),eq(dev.dasan.customdungeons.config.NumericRanges.SPAWNER_RADIUS),eq(2.5),any()))
                .thenAnswer(call->{accepted.set(call.getArgument(4));return null;});
        new SpawnerMenu(root,0,0,root).open();clickSlot(22);
        assertNotNull(accepted.get());accepted.get().accept(2.5);
        assertEquals(2.5,root.draft.get().rooms().getFirst().spawners().getFirst().radius());
    }
    @Test void spawnerShowsThreeWavesOrAnOverflowLinkOutsideFooter() throws Exception {
        var root=remember(definition("waves"));var wave=root.draft.get().rooms().getFirst().spawners().getFirst().waves().getFirst();
        root.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),s.radius(),List.of(wave,wave,wave)));
        var menu=new SpawnerMenu(root,0,0,root);menu.open();
        assertEquals(54,top.getSize());
        for(int slot:new int[]{24,33,42}) assertEquals(Material.ZOMBIE_HEAD,top.getItem(slot).getType());
        clickSlot(42);assertInstanceOf(WaveMenu.class,top.getHolder());
        root.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),s.radius(),List.of(wave,wave,wave,wave)));
        menu.open();clickSlot(42);assertInstanceOf(WaveListMenu.class,top.getHolder());
        menu.open();assertEquals(Material.LIME_DYE,top.getItem(47).getType());
        assertNull(top.getItem(51));
    }
    @Test void carrierPickerOffersLastMobAndConcreteTemplateWithoutChangingOtherRoomFields() throws Exception {
        var root = remember(definition("keys"));
        var template = mock(MobTemplate.class);
        when(template.id()).thenReturn("mob"); when(template.entityType()).thenReturn("minecraft:zombie");
        when(template.displayName()).thenReturn("Zombie"); when(store.mobs()).thenReturn(Map.of("mob",template,"unrelated",template));
        root.change(v->v.rooms=DungeonMenu.append(v.rooms,v.rooms.getFirst()));
        root.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),UnlockMode.KEY,r.keyCarrierTemplateId(),r.spawners()));
        var menu = new RoomMenu(root,0,root); menu.open();
        var lookup = Menu.class.getDeclaredMethod("buttonAt",int.class); lookup.setAccessible(true);
        ((Button)lookup.invoke(menu,34)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        var picker = assertInstanceOf(Menu.class,top.getHolder());
        ((Button)lookup.invoke(picker,12)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        var original = definition("keys").rooms().getFirst(); var selected = root.draft.get().rooms().getFirst();
        assertEquals(new RoomDef(original.id(),original.region(),original.checkpoint(),original.door(),UnlockMode.KEY,"*",original.spawners()),selected);
        ((Button)lookup.invoke(menu,34)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        picker = assertInstanceOf(Menu.class,top.getHolder());
        ((Button)lookup.invoke(picker,14)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        assertEquals("mob",root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void rootHasMobLibraryButtonAndClickOpensLibraryOnNextTick() {
        when(store.mobs()).thenReturn(Map.of());
        var services = plugin.getServer().getServicesManager();
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        list=new DungeonListMenu(player);list.open();
        assertNotNull(top.getItem(14), "public root must expose the mob library even with no dungeons");
        assertEquals(Material.BOOK, top.getItem(14).getType());
        clickRootLibrary();
        assertSame(list, top.getHolder(), "inventory changes must wait until after the click event");
        drain();
        var library = assertInstanceOf(MobLibraryMenu.class, top.getHolder());
        assertSame(list, library.parent());
    }
    @Test void dungeonPagesReturnToMainWithLibraryAccessible() {
        for (int i = 0; i < 29; i++) definitions.put("d" + i, definition("d" + i));
        var main=new DungeonListMenu(player);
        list=new DungeonListMenu(player,true,main);list.open();
        list.nextPage();assertNotNull(top.getItem(13));
        clickSlot(45);assertSame(main,top.getHolder());
        assertEquals(Material.BOOK,top.getItem(14).getType());
    }
    @Test void mobLibraryLabelAndLoreExistInBothLanguages() throws Exception {
        for (String resource : List.of("messages.yml", "messages_en.yml")) {
            try (var reader = new java.io.InputStreamReader(
                    Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource)),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
                assertFalse(Objects.requireNonNull(yaml.getString("gui.common.mob-library")).isBlank());
                assertFalse(Objects.requireNonNull(yaml.getString("gui.common.mob-library-lore")).isBlank());
            }
        }
    }
    @Test void mobLibraryClickIsRejectedAfterPermissionLoss() {
        list.open();
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(false);
        clickRootLibrary();
        drain();
        assertSame(list, top.getHolder());
    }
    @Test void mobLibraryRechecksPermissionBeforeDeferredOpen() {
        list.open();
        clickRootLibrary();
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(false);
        drain();
        assertSame(list, top.getHolder());
    }
    void clickRootLibrary() {
        var event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(14);
        when(event.isLeftClick()).thenReturn(true);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        framework.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test void controlRechecksPermissionBeforeDeferredSessionAction() throws Exception {
        var manager=mock(dev.dasan.customdungeons.session.SessionManager.class);
        when(plugin.sessionManager()).thenReturn(manager);
        when(manager.session(anyString())).thenReturn(Optional.empty());
        when(manager.sessionOf(any())).thenReturn(Optional.empty());
        var v=new DungeonMenu.Values(definition("one")); v.enabled=true;
        var original=v.build(); definitions.put("one",original);
        when(player.hasPermission("customdungeons.admin.test")).thenReturn(true);
        DungeonMenu root=remember(original); root.open();
        var event=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(34);
        when(event.isLeftClick()).thenReturn(true);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        framework.onClick(event);
        assertFalse(tasks.isEmpty(), "the control click must queue its permission recheck");
        when(player.hasPermission("customdungeons.admin.test")).thenReturn(false);
        drain();
        verify(manager,never()).startTest(any(),anyString());
    }

    void clickSlot(int slot) {clickSlot(slot,org.bukkit.event.inventory.ClickType.LEFT);}
    void clickSlot(int slot,org.bukkit.event.inventory.ClickType click) {
        var event=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getView()).thenReturn(view); when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot); when(event.isLeftClick()).thenReturn(click.isLeftClick()); when(event.isRightClick()).thenReturn(click.isRightClick());
        when(event.getClick()).thenReturn(click);
        when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        framework.onClick(event); drain();
    }
    dev.dasan.customdungeons.session.SessionManager controlManager() {
        var manager=mock(dev.dasan.customdungeons.session.SessionManager.class);
        when(plugin.sessionManager()).thenReturn(manager);
        when(player.hasPermission("customdungeons.admin.control")).thenReturn(true);
        when(player.hasPermission("customdungeons.admin.test")).thenReturn(true);
        when(manager.session(anyString())).thenReturn(Optional.empty());
        when(manager.sessionOf(any())).thenReturn(Optional.empty());
        bukkit.when(()->Bukkit.getWorld("world")).thenReturn(mock(org.bukkit.World.class));
        var v=new DungeonMenu.Values(definition("one"));v.enabled=true;definitions.put("one",v.build());
        return manager;
    }
    dev.dasan.customdungeons.session.DungeonSession activeSession(boolean running) {
        var session=mock(dev.dasan.customdungeons.session.DungeonSession.class);
        var state=new dev.dasan.customdungeons.session.SessionStateMachine();state.openLobby();
        if(running) state.start();
        when(session.state()).thenReturn(state); when(session.def()).thenReturn(definitions.get("one"));
        return session;
    }
    @Test void listOpensRunningDungeonForStopWithoutDraftOrEditLock() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));
        DungeonListMenu.dungeonBusy(id->true);
        doAnswer(call->{session.state().fail();session.state().beginReset();session.state().finishReset();return null;})
                .when(manager).stop("one");
        list.open();clickSlot(13);
        var menu=assertInstanceOf(DungeonMenu.class,top.getHolder());
        assertNull(menu.draft(),"control view must not create a draft");
        assertTrue(locks.holder("one").isEmpty());
        assertNull(menu.onSave());
        for(int slot:new int[]{19,21,23,28,30,32,37,39})
            assertEquals(Material.GRAY_DYE,top.getItem(slot).getType());
        clickSlot(10);assertSame(menu,top.getHolder());
        clickSlot(43);verify(manager).stop("one");
        verify(plugin.messages()).send(player,"command.stop");
        assertTrue(locks.holder("one").isEmpty());
        verify(store,never()).save(any(DungeonDef.class));
    }
    @Test void listOpensLobbyAndStartReportsOnlyRunningResult() {
        var manager=controlManager();var session=activeSession(false);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(13);assertInstanceOf(DungeonMenu.class,top.getHolder());
        clickSlot(25);verify(manager).forceStart("one");
        verify(plugin.messages(),never()).send(player,"command.start");
        verify(plugin.messages()).send(player,"gui.dungeon.control-start-rejected");
        doAnswer(call->{session.state().start();return null;}).when(manager).forceStart("one");
        clickSlot(25);verify(plugin.messages()).send(player,"command.start");
    }
    @Test void testRejectsMissingWorldWithoutAnnouncingSuccess() throws Exception {
        var manager=controlManager();bukkit.when(()->Bukkit.getWorld("world")).thenReturn(null);
        var menu=remember(definitions.get("one"));menu.open();clickSlot(34);
        verify(manager).startTest(player,"one");
        verify(plugin.messages(),never()).send(player,"command.test-started");
        verify(plugin.messages()).send(player,"gui.dungeon.control-world-unavailable");
    }
    @Test void stopWithoutStateChangeReportsRejection() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(13);clickSlot(43);
        verify(manager).stop("one");verify(plugin.messages(),never()).send(player,"command.stop");
        verify(plugin.messages()).send(player,"gui.dungeon.control-stop-rejected");
    }
    @Test void startDuringChunkPreparationReportsPendingInsteadOfFalseRejection() {
        var manager=controlManager();var session=activeSession(false);
        when(session.players()).thenReturn(List.of(player));
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(13);clickSlot(25);
        verify(plugin.messages()).send(player,"gui.dungeon.control-preparing");
        verify(plugin.messages(),never()).send(player,"command.start");
        verify(plugin.messages(),never()).send(player,"gui.dungeon.control-start-rejected");
    }
    @Test void testCreatedInLobbyReportsPreparationInsteadOfSuccess() throws Exception {
        var manager=controlManager();var session=activeSession(false);when(session.testMode()).thenReturn(true);
        doAnswer(call->{when(manager.sessionOf(player.getUniqueId())).thenReturn(Optional.of(session));return null;})
                .when(manager).startTest(player,"one");
        var menu=remember(definitions.get("one"));menu.open();clickSlot(34);
        verify(plugin.messages()).send(player,"gui.dungeon.control-preparing");
        verify(plugin.messages(),never()).send(player,"command.test-started");
    }
    @Test void controlsRespectAnotherEditorsLockAndDoNotPersistDefinition() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        UUID other=UUID.randomUUID();assertTrue(locks.tryLock("one",other));
        list.open();clickSlot(13);var menu=assertInstanceOf(DungeonMenu.class,top.getHolder());
        assertNull(menu.draft());menu.saveDraft();assertEquals(Optional.of(other),locks.holder("one"));
        verify(store,never()).save(any(DungeonDef.class));
    }
    @Test void resetHasOwnButtonAndVerifiesCleanup() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        doAnswer(call->{session.state().fail();session.state().beginReset();session.state().finishReset();return null;})
                .when(manager).reset("one");
        list.open();clickSlot(13);assertNotNull(top.getItem(43));clickSlot(43,org.bukkit.event.inventory.ClickType.RIGHT);
        verify(manager).reset("one");verify(plugin.messages()).send(player,"command.reset");
    }
    @Test void controlExceptionReportsFailureInsteadOfSuccess() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        doThrow(new IllegalStateException("failure")).when(manager).stop("one");
        list.open();clickSlot(13);assertDoesNotThrow(()->clickSlot(43));
        verify(plugin.messages()).send(player,"gui.dungeon.control-failed");
        verify(plugin.messages(),never()).send(player,"command.stop");
    }
    @Test void testSuccessRequiresAdminInRunningTestSession() throws Exception {
        var manager=controlManager();var session=activeSession(true);when(session.testMode()).thenReturn(true);
        doAnswer(call->{when(manager.sessionOf(player.getUniqueId())).thenReturn(Optional.of(session));return null;})
                .when(manager).startTest(player,"one");
        var menu=remember(definitions.get("one"));menu.open();clickSlot(34);
        verify(plugin.messages()).send(player,"command.test-started");
    }

    @Test void heightWarningsAreYellowAndSaveStillPersists() throws Exception {
        var messages=new Messages();
        try(var reader=new java.io.InputStreamReader(getClass().getResourceAsStream("/messages.yml"),java.nio.charset.StandardCharsets.UTF_8)) {
            var yaml=new org.bukkit.configuration.file.YamlConfiguration(); yaml.load(reader); messages.load(yaml,"");
        }
        when(plugin.messages()).thenReturn(messages);
        framework=new MenuListener(plugin,messages,new PluginConfig.GuiSounds("","","",""),locks);
        menuServices.when(MenuListener::instance).thenReturn(framework);
        var name=new java.util.concurrent.atomic.AtomicReference<Component>();
        var lore=new java.util.concurrent.atomic.AtomicReference<List<Component>>();
        buttons.when(()->Button.of(any(),any(),anyList(),any())).thenAnswer(call -> {
            if(call.getArgument(0)==Material.YELLOW_DYE) { name.set(call.getArgument(1)); lore.set(call.getArgument(2)); }
            return new Button(item(call.getArgument(0)),call.getArgument(3));
        });
        var mob=new MobTemplate("mob","WARDEN","&aColoso",0,0,0,0,4,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        when(store.mobs()).thenReturn(Map.of("mob",mob));
        var original=definition("one"); definitions.put("one",original);
        var root=remember(original); root.open();
        new DungeonSettingsMenu(root).open(); // Saving from a child must also show the warning.
        var future=new CompletableFuture<Void>(); when(store.save(any(DungeonDef.class))).thenReturn(future);
        root.saveDraft(); verify(store).save(original);
        future.complete(null); drain();
        assertFalse(root.saving()); assertFalse(root.dirty());
        assertSame(root,top.getHolder());
        assertEquals(Material.YELLOW_DYE,top.getItem(40).getType());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW,name.get().color());
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String text=plain.serialize(lore.get().getFirst());
        assertTrue(text.contains("Coloso")); assertFalse(text.contains("&a"));
        assertTrue(text.contains("11.60")); assertTrue(text.contains("2.00"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW,lore.get().getFirst().color());
        root.change(v->v.name="Edited"); root.refresh();
        assertTrue(top.getItem(40)==null || top.getItem(40).getType()!=Material.YELLOW_DYE);
        root.change(v->v.lobby=null); root.saveDraft(); drain();
        verify(store,times(1)).save(any(DungeonDef.class));
        assertEquals(Material.RED_DYE,top.getItem(40).getType());
        assertEquals(Material.LEVER,top.getItem(41).getType());
    }
    @Test void savingUsesRegisteredEntityHeightOverridesInsteadOfBundledDefaults() throws Exception {
        var mob=new MobTemplate("mob","WARDEN","Coloso",0,0,0,0,4,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        when(store.mobs()).thenReturn(Map.of("mob",mob));
        when(plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.config.EntityHeights.class))
                .thenReturn(new dev.dasan.customdungeons.config.EntityHeights(Map.of(org.bukkit.entity.EntityType.WARDEN,.25)));
        var original=definition("one"); definitions.put("one",original);
        var root=remember(original); root.open();
        when(store.save(any(DungeonDef.class))).thenReturn(CompletableFuture.completedFuture(null));
        root.saveDraft(); drain(); verify(store).save(original);
        var warningField=DungeonMenu.class.getDeclaredField("warnings"); warningField.setAccessible(true);
        assertTrue(((List<?>)warningField.get(root)).isEmpty(),"Configured 1-block height fits the 2-block room");
    }
    @Test void mobLiveControlsRemainVisibleAndInvulnerabilityCanBeToggled() throws Exception {
        var template=new MobTemplate("mob","ZOMBIE","Mob",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var menu=new MobMenu(player,template,list);
        try(var live=mockStatic(dev.dasan.customdungeons.mob.LiveTestService.class)) {
            live.when(()->dev.dasan.customdungeons.mob.LiveTestService.active(player)).thenReturn(true);
            menu.open();
            assertEquals(54,top.getSize());
            clickSlot(42); drain();
            live.verify(()->dev.dasan.customdungeons.mob.LiveTestService.toggleInvulnerable(player));
            clickSlot(40); drain();
            live.verify(()->dev.dasan.customdungeons.mob.LiveTestService.stop(player));
            clickSlot(38); drain();
            live.verify(()->dev.dasan.customdungeons.mob.LiveTestService.start(eq(player),any(MobTemplate.class)));
        }
    }
    @Test void scaleEditorUsesZeroToSixteenWithFourDecimalInput() {
        var template=new MobTemplate("mob","WARDEN","",0,0,0,0,7.06,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var draft=new MobMenu.MobDraft(template);
        var menu=new StatsMenu(player,draft,list); menu.open(); clickSlot(24);
        inputs.verify(()->Inputs.ranged(eq(player),any(Component.class),eq(dev.dasan.customdungeons.config.NumericRanges.SCALE),eq(7.06),any(java.util.function.DoubleConsumer.class)));
    }

    @Test void pendingSaveKeepsLockAfterCloseUntilMainThreadCallback() throws Exception {
        DungeonDef original = definition("one"); definitions.put("one",original);
        DungeonMenu root = remember(original); root.change(v -> v.name = "edited");
        var future = new CompletableFuture<Void>(); when(store.save(any(DungeonDef.class))).thenReturn(future);
        top = root.getInventory(); root.saveDraft(); closeRoot(root);
        Player other = player(); DungeonMenu second = new DungeonMenu(other,original,new DungeonListMenu(other));
        second.saveDraft();
        verify(store,times(1)).save(any(DungeonDef.class));
        assertFalse(second.canEdit(false));
        definitions.put("one",root.draft.get()); future.complete(null);
        assertFalse(locks.tryLock("one",other.getUniqueId()),"completion must wait for main-thread callback");
        drain();
        assertTrue(locks.tryLock("one",other.getUniqueId()));
    }
    @Test void switchRequiresDiscardAndCancelKeepsUnsavedDungeonInList() throws Exception {
        DungeonMenu root = remember(definition("new")); root.change(v -> v.name = "unsaved");
        definitions.put("other",definition("other")); top = list.getInventory();
        assertNull(editor("other")); assertNotNull(confirm);
        assertEquals("unsaved",editor("new").draft.get().displayName());
        assertTrue(list.entries().stream().anyMatch(d -> d.id().equals("new")));
        confirm.run();
        assertFalse(list.entries().stream().anyMatch(d -> d.id().equals("new")));
        assertEquals("other",editor("other").draft.get().id());
    }
    @Test void deferredDirtyCloseDoesNotReopenConfirmationDuringReload() throws Exception {
        DungeonMenu root = remember(definition("new")); top = root.getInventory();
        player.closeInventory(); root.closed(); // The close queues its confirmation before reload begins.
        when(store.isReloading()).thenReturn(true);
        drain();
        assertNull(confirm);
    }
    @Test void closeDirtyRootRequiresConfirmationAndDiscardRemovesDraft() throws Exception {
        DungeonMenu root = remember(definition("new")); top = root.getInventory(); closeRoot(root);
        assertNotNull(confirm); assertSame(root, top.getHolder());
        assertTrue(list.entries().stream().anyMatch(d -> d.id().equals("new")));
        confirm.run(); drain();
        assertFalse(list.entries().stream().anyMatch(d -> d.id().equals("new")));
    }
    @Test void saveRejectsDefinitionChangedSinceOpening() throws Exception {
        DungeonDef original = definition("one"); definitions.put("one",original);
        DungeonMenu root = remember(original); root.change(v -> v.name = "mine");
        var changed = new DungeonMenu.Values(original); changed.name = "external"; definitions.put("one",changed.build());
        root.saveDraft(); verify(store,never()).save(any(DungeonDef.class));
        assertEquals("mine",root.draft.get().displayName());
    }
    @Test void failedSaveReleasesLockOnlyInCallbackAndKeepsDraft() throws Exception {
        DungeonDef original = definition("one"); definitions.put("one",original);
        DungeonMenu root = remember(original); root.change(v -> v.name = "retry");
        var future = new CompletableFuture<Void>(); when(store.save(any(DungeonDef.class))).thenReturn(future);
        top = root.getInventory(); root.saveDraft(); closeRoot(root);
        UUID other = UUID.randomUUID();
        future.completeExceptionally(new IllegalStateException("write failed"));
        assertFalse(locks.tryLock("one",other));
        drain(); assertTrue(locks.tryLock("one",other));
        assertEquals("retry",root.draft.get().displayName()); assertTrue(root.dirty());
        assertEquals(original,definitions.get("one"));
    }
    @Test void successfulSaveThenCloseDoesNotAskToDiscardSavedChanges() throws Exception {
        DungeonDef original = definition("one"); definitions.put("one",original);
        DungeonMenu root = remember(original); root.change(v -> v.name = "saved");
        var future = new CompletableFuture<Void>(); when(store.save(any(DungeonDef.class))).thenReturn(future);
        top = root.getInventory(); root.saveDraft();
        definitions.put("one",root.draft.get()); future.complete(null); drain();
        closeRoot(root); assertNull(confirm); assertFalse(root.dirty());
    }
    @Test void pendingSaveAlsoSurvivesQuitAndBlocksItsOwnAdmin() throws Exception {
        DungeonDef original = definition("one"); definitions.put("one",original);
        DungeonMenu root = remember(original);
        var future = new CompletableFuture<Void>(); when(store.save(any(DungeonDef.class))).thenReturn(future);
        var quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        root.saveDraft(); framework.onQuit(quit);
        DungeonMenu second = new DungeonMenu(player,original,list); second.saveDraft();
        verify(store,times(1)).save(any(DungeonDef.class));
        future.complete(null); drain(); assertTrue(locks.tryLock("one",player.getUniqueId()));
    }
    @Test void creatingReplacementAlsoRequiresDiscardConfirmation() throws Exception {
        DungeonMenu root = remember(definition("new"));
        assertNull(remember(definition("another"))); assertNotNull(confirm);
        assertSame(root,editor("new"));
        confirm.run(); assertEquals(List.of("another"),list.entries().stream().map(DungeonDef::id).toList());
    }
    @Test void backToListConfirmsButOpeningSubmenuKeepsDraftWithoutPrompt() throws Exception {
        DungeonMenu root = remember(definition("new"));
        top = new RewardMenu(root).getInventory(); root.closed(); drain(); assertNull(confirm);
        top = list.getInventory(); root.closed(); drain(); assertNotNull(confirm);
        assertSame(root,top.getHolder()); confirm.run(); drain(); assertSame(list,top.getHolder());
    }
    @Test void rewardsCaptureAndReturnFullStackOnlyOnce() throws Exception {
        DungeonMenu root = remember(definition("new")); RewardMenu reward = new RewardMenu(root);
        ItemStack deposited = item(Material.DIAMOND_SWORD);
        ItemStack snapshot = item(Material.DIAMOND_SWORD);
        Material type = mock(Material.class); // Avoid server registry lookup in Material.isAir.
        when(deposited.getType()).thenReturn(type); when(snapshot.getType()).thenReturn(type);
        when(deposited.getAmount()).thenReturn(3); when(snapshot.getAmount()).thenReturn(3);
        var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(deposited.getItemMeta()).thenReturn(meta); when(snapshot.getItemMeta()).thenReturn(meta);
        when(deposited.clone()).thenReturn(snapshot);
        reward.getInventory().setItem(18,deposited); reward.capture(); reward.capture();
        verify(player.getInventory(),times(1)).addItem(deposited);
        assertNull(reward.getInventory().getItem(18));
        assertSame(snapshot,root.draft.get().reward().items().getFirst());
        assertSame(meta,root.draft.get().reward().items().getFirst().getItemMeta());
        assertEquals(3,root.draft.get().reward().items().getFirst().getAmount());
        assertSame(type,root.draft.get().reward().items().getFirst().getType());
        assertFalse(reward.allowsPlacement(18));
    }
    @Test void rewardsAreReturnedWithoutUpdatingDraftAfterPermissionLoss() throws Exception {
        DungeonMenu root = remember(definition("new")); RewardMenu reward = new RewardMenu(root);
        ItemStack deposited = item(mock(Material.class)); reward.getInventory().setItem(18,deposited);
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(false);
        reward.capture(); reward.capture();
        verify(player.getInventory(),times(1)).addItem(deposited);
        assertNull(reward.getInventory().getItem(18)); assertTrue(root.draft.get().reward().items().isEmpty());
    }
    @Test void fullPlayerInventoryDropsOnlyReturnedOverflowWithMetadata() throws Exception {
        DungeonMenu root = remember(definition("new")); RewardMenu reward = new RewardMenu(root);
        ItemStack deposited = item(mock(Material.class)); ItemStack overflow = item(mock(Material.class));
        when(player.getInventory().addItem(deposited)).thenReturn(new HashMap<>(Map.of(0,overflow)));
        var world = mock(org.bukkit.World.class); var location = new org.bukkit.Location(world,1,2,3);
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(location);
        reward.getInventory().setItem(18,deposited); reward.capture(); reward.capture();
        verify(world,times(1)).dropItem(location,overflow);
        assertSame(deposited,root.draft.get().reward().items().getFirst());
    }
    @Test void linkedWavesAreReadOnlyAndMakeLocalRequiresConfirmation() throws Exception {
        var root=remember(definition("linked"));var wave=root.draft.get().rooms().getFirst().spawners().getFirst().waves().getFirst();
        var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));when(store.spawnerPresets()).thenReturn(Map.of("horde",preset));
        root.spawner(0,0,v->new SpawnerDef(v.id(),v.location(),2.5,List.of(),"horde"));
        var menu=new SpawnerMenu(root,0,0,root);menu.open();clickSlot(24);
        assertSame(menu,top.getHolder());assertEquals("horde",root.draft.get().rooms().getFirst().spawners().getFirst().presetId());
        clickSlot(43);assertNotNull(confirm);assertTrue(root.draft.get().rooms().getFirst().spawners().getFirst().waves().isEmpty());
        confirm.run();var local=root.draft.get().rooms().getFirst().spawners().getFirst();assertNull(local.presetId());assertEquals(List.of(wave),local.waves());assertEquals(2.5,local.radius());
    }
    @Test void selectorPrioritizesDungeonTemplatesAndAddsLibraryPlacement() throws Exception {
        var root=remember(definition("picker"));var wave=root.draft.get().rooms().getFirst().spawners().getFirst().waves().getFirst();
        var horde=new SpawnerPreset("horde","Horda",3.4,List.of(wave));var boss=new SpawnerPreset("boss","Jefe",1.2,List.of(wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",horde,"boss",boss));root.change(v->v.spawnerPresets=List.of("horde"));
        var point=new Point("world",5.5,64,7.5,0,0);new SpawnerPickerMenu(root,0,root,point).open();
        assertEquals(Material.SPAWNER,top.getItem(11).getType());assertEquals(Material.SPAWNER,top.getItem(20).getType());
        clickSlot(20);var placed=root.draft.get().rooms().getFirst().spawners().getLast();
        assertEquals("boss",placed.presetId());assertEquals(1.2,placed.radius());assertEquals(point,placed.location());assertTrue(root.draft.get().spawnerPresets().contains("boss"));
    }
    @Test void missingPresetSaveShowsValidationAndNeverWrites() throws Exception {
        var root=remember(definition("missing"));root.spawner(0,0,v->new SpawnerDef(v.id(),v.location(),v.radius(),List.of(),"missing"));
        assertDoesNotThrow(root::saveDraft);verify(store,never()).save(any(DungeonDef.class));
        new SpawnerMenu(root,0,0,root).open();assertEquals(Material.RED_DYE,top.getItem(24).getType());
    }
    @Test void presetSaveHoldsLockAndUsesExpectedDefinition() {
        var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",preset));
        var pending=new CompletableFuture<Void>();when(store.save(any(SpawnerPreset.class),any(SpawnerPreset.class))).thenReturn(pending);
        var menu=new SpawnerPresetMenu(list,preset,list,null);menu.change(v->v.name="Nueva horda");menu.open();menu.saveDraft();
        assertFalse(locks.tryLock("spawner:horde",UUID.randomUUID()));verify(store).save(eq(new SpawnerPreset("horde","Nueva horda",3,List.of(wave))),eq(preset));
        pending.complete(null);assertFalse(locks.tryLock("spawner:horde",UUID.randomUUID()));drain();
        assertFalse(menu.dirty());assertTrue(locks.tryLock("spawner:horde",UUID.randomUUID()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void presetDialogsReturnToTheIntactDraftWithoutDiscarding(boolean newPreset) throws Exception {
        var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();
        var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));
        when(store.spawnerPresets()).thenReturn(newPreset ? Map.of() : Map.of("horde",preset));
        var menu=new SpawnerPresetMenu(list,preset,list,null);
        menu.change(v->v.name="Borrador");menu.open();
        inputs.close();inputs=mockStatic(Inputs.class,CALLS_REAL_METHODS);
        inputs.when(()->Inputs.confirm(eq(player),any(),any())).thenAnswer(call->{confirm=call.getArgument(2);return null;});
        var scheduler=plugin.getServer().getScheduler();
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
        doAnswer(call->{
            var closing=top;top=inventory(null);
            var event=mock(InventoryCloseEvent.class);
            when(event.getInventory()).thenReturn(closing);when(event.getPlayer()).thenReturn(player);
            when(event.getReason()).thenReturn(InventoryCloseEvent.Reason.PLUGIN);
            // Both listener orders must preserve the shared input lifecycle.
            if(newPreset) framework.onClose(event);
            for(var listener:List.copyOf(listeners)) listener.getClass().getMethod("close",InventoryCloseEvent.class).invoke(listener,event);
            if(!newPreset) framework.onClose(event);
            return null;
        }).when(player).closeInventory();
        var callbacks=new ArrayList<io.papermc.paper.registry.data.dialog.action.DialogActionCallback>();
        try(var dialog=mockStatic(io.papermc.paper.dialog.Dialog.class);
            var actions=mockStatic(io.papermc.paper.registry.data.dialog.action.DialogAction.class);
            var actionButtons=mockStatic(io.papermc.paper.registry.data.dialog.ActionButton.class);
            var dialogInputs=mockStatic(io.papermc.paper.registry.data.dialog.input.DialogInput.class)) {
            dialog.when(()->io.papermc.paper.dialog.Dialog.create(any())).thenReturn(mock(io.papermc.paper.dialog.Dialog.class));
            actions.when(()->io.papermc.paper.registry.data.dialog.action.DialogAction.customClick(
                    any(io.papermc.paper.registry.data.dialog.action.DialogActionCallback.class),any())).thenAnswer(call->{
                callbacks.add(call.getArgument(0));return mock(io.papermc.paper.registry.data.dialog.action.DialogAction.CustomClickAction.class);
            });
            actionButtons.when(()->io.papermc.paper.registry.data.dialog.ActionButton.create(any(),isNull(),anyInt(),any()))
                    .thenReturn(mock(io.papermc.paper.registry.data.dialog.ActionButton.class));
            var builder=mock(io.papermc.paper.registry.data.dialog.input.TextDialogInput.Builder.class,RETURNS_SELF);
            when(builder.build()).thenReturn(mock(io.papermc.paper.registry.data.dialog.input.TextDialogInput.class));
            dialogInputs.when(()->io.papermc.paper.registry.data.dialog.input.DialogInput.text(anyString(),any())).thenReturn(builder);
            try {
                clickSlot(20);
                assertNull(confirm,"Opening a text dialog must not prompt to discard the draft");
                assertNull(top.getHolder());assertEquals("Borrador",menu.value().name());
                assertFalse(locks.tryLock("spawner:horde",UUID.randomUUID()));
                var response=mock(io.papermc.paper.dialog.DialogResponseView.class);
                when(response.getText("value")).thenReturn("Nombre nuevo");callbacks.getFirst().accept(response,player);drain();
                assertSame(menu,top.getHolder());assertEquals("Nombre nuevo",menu.value().name());
                assertEquals(List.of(wave),menu.value().waves());assertTrue(menu.dirty());
                clickSlot(29);assertNull(confirm);
                when(response.getText("value")).thenReturn("4.5");callbacks.get(2).accept(response,player);drain();
                assertSame(menu,top.getHolder());assertEquals(new SpawnerPreset("horde","Nombre nuevo",4.5,List.of(wave)),menu.value());
                // Cancel also returns to the same draft; a later real close still asks to discard.
                clickSlot(20);callbacks.get(5).accept(response,player);drain();
                assertSame(menu,top.getHolder());assertEquals(4.5,menu.value().radius());
                closeRoot(menu);assertNotNull(confirm);
            } finally {Inputs.cancel(player);}
        }
    }

    @Test void presetEditorLoadsLatestVersionFromAnOldLibraryButton() {
        var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();
        var stale=new SpawnerPreset("horde","Antigua",3,List.of(wave));
        var current=new SpawnerPreset("horde","Actual",4.5,List.of(wave,wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",stale));
        new SpawnerLibraryMenu(list,list,null).open();
        when(store.spawnerPresets()).thenReturn(Map.of("horde",current));
        clickSlot(13);
        var menu=assertInstanceOf(SpawnerPresetMenu.class,top.getHolder());
        assertEquals(current,menu.value());assertFalse(menu.dirty());assertFalse(menu.outdated());
        menu.change(v->v.name="Editada");
        when(store.save(any(SpawnerPreset.class),any(SpawnerPreset.class))).thenReturn(new CompletableFuture<>());
        menu.saveDraft();
        verify(store).save(eq(new SpawnerPreset("horde","Editada",4.5,current.waves())),eq(current));
    }

    @Test void presetEditorDetectsAChangeAfterOpening() {
        var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();
        var stale=new SpawnerPreset("horde","Antigua",3,List.of(wave));
        var current=new SpawnerPreset("horde","Actual",4.5,List.of(wave,wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",current));
        var menu=new SpawnerPresetMenu(list,stale,list,null);
        assertEquals(current,menu.value());menu.change(v->v.name="Borrador");
        when(store.spawnerPresets()).thenReturn(Map.of("horde",new SpawnerPreset("horde","Cambio posterior",5,List.of(wave))));
        assertTrue(menu.outdated());menu.saveDraft();
        verify(store,never()).save(any(SpawnerPreset.class),any(SpawnerPreset.class));
        assertEquals("Borrador",menu.value().name());
    }

    @Test void deletionInUseWaitsForConfirmationAndRechecksActiveSessions() {
        var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",preset));
        var d=definition("used");var room=d.rooms().getFirst();var linked=dev.dasan.customdungeons.config.SpawnerPresets.withRooms(d,List.of(new RoomDef(room.id(),room.region(),room.checkpoint(),null,UnlockMode.AUTOMATIC,null,List.of(new SpawnerDef("s",room.checkpoint(),3,List.of(),"horde")))));
        definitions.put("used",linked);new SpawnerLibraryMenu(list,list,null).open();clickSlot(13,org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);
        assertNotNull(confirm);verify(store,never()).deleteSpawnerPreset(anyString());
        DungeonListMenu.dungeonBusy(id->id.equals("used"));confirm.run();verify(store,never()).deleteSpawnerPreset(anyString());
        DungeonListMenu.dungeonBusy(id->false);when(store.deleteSpawnerPreset("horde")).thenReturn(CompletableFuture.completedFuture(null));confirm.run();drain();verify(store).deleteSpawnerPreset("horde");
    }
    @Test void leavingSavedPresetReleasesItsLockForLibraryDeletion() {
        var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",preset));var menu=new SpawnerPresetMenu(list,preset,list,null);menu.open();assertTrue(menu.writable());
        list.open();menu.closed();drain();assertTrue(locks.tryLock("spawner:horde",UUID.randomUUID()));
    }
    @Test void spawnerToolOpensTheSamePickerAtTheSelectedRoomPoint() throws Exception {
        var root=remember(definition("tool"));root.room(0,r->new RoomDef(r.id(),Region.of("world",new BlockPos(0,60,0),new BlockPos(10,70,10)),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),r.spawners()));
        var tools=plugin.getServer().getServicesManager().load(ToolService.class);
        when(tools.lastPoint(player.getUniqueId())).thenReturn(Optional.of(new org.bukkit.Location(world,5.5,64,7.5)));
        when(player.hasPermission("customdungeons.admin.tools")).thenReturn(true);
        var tool=mock(ItemStack.class,RETURNS_DEEP_STUBS);when(tool.hasItemMeta()).thenReturn(true);
        when(tool.getItemMeta().getPersistentDataContainer().get(dev.dasan.customdungeons.mob.MobKeys.TOOL,org.bukkit.persistence.PersistentDataType.STRING)).thenReturn("SPAWNER:tool");
        var event=mock(org.bukkit.event.player.PlayerInteractEvent.class);when(event.getPlayer()).thenReturn(player);when(event.getItem()).thenReturn(tool);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);when(event.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);when(event.getClickedBlock()).thenReturn(mock(org.bukkit.block.Block.class));
        var handler=listeners.stream().flatMap(l->Arrays.stream(l.getClass().getDeclaredMethods()).map(m->Map.entry(l,m))).filter(e->Arrays.equals(e.getValue().getParameterTypes(),new Class<?>[]{org.bukkit.event.player.PlayerInteractEvent.class})).findFirst().orElseThrow();
        handler.getValue().setAccessible(true);handler.getValue().invoke(handler.getKey(),event);drain();assertInstanceOf(SpawnerPickerMenu.class,top.getHolder());clickSlot(25);
        assertEquals(new Point("world",5.5,64,7.5,0,0),root.draft.get().rooms().getFirst().spawners().getLast().location());
    }
    private EquipmentMenu equipmentForLifecycleTest() {
        var manager = plugin.getServer().getPluginManager();
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        return equipmentWithPreview();
    }
    @Test void presetAndWaveListenersFollowInventoryReopeningsAndRealAbandonment() throws Exception {
        try(var handlers=paperInventoryLifecycle()) {
            int baseline=listeners.size();
            var wave=definition("preset").rooms().getFirst().spawners().getFirst().waves().getFirst();
            var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));
            when(store.spawnerPresets()).thenReturn(Map.of("horde",preset));
            var menu=new SpawnerPresetMenu(list,preset,list,null);menu.change(v->v.name="Borrador");menu.open();
            assertEquals(baseline+1,listeners.size(),"The preset's listener must be bound to its inventory");
            Inventory obsolete=menu.getInventory();menu.open();drain();
            assertNotSame(obsolete,menu.getInventory());assertEquals(baseline+1,listeners.size());assertNull(confirm);
            paperClose(obsolete);drain();
            assertEquals(baseline+1,listeners.size());assertNull(confirm);
            assertFalse(locks.tryLock("spawner:horde",UUID.randomUUID()));
            var waves=new WaveMenu(menu,0,0,0,menu);waves.open();drain();
            assertEquals(baseline+1,listeners.size());assertNull(confirm);
            assertEquals("Borrador",menu.value().name());
            paperClose(waves.getInventory(),InventoryCloseEvent.Reason.PLAYER);top=inventory(null);drain();
            assertNotNull(confirm,"Abandoning a wave editor must still protect its preset draft");
            assertEquals("Borrador",menu.value().name());assertEquals(List.of(wave),menu.value().waves());
        }
    }

    @Test void allSpawnerMenusReplaceViewsAndRejectNativeItemPlacement() throws Exception {
        var root=remember(definition("spawner-life"));
        var wave=root.draft.get().rooms().getFirst().spawners().getFirst().waves().getFirst();
        var preset=new SpawnerPreset("horde","Horda",3,List.of(wave));
        when(store.spawnerPresets()).thenReturn(Map.of("horde",preset));
        var menus=List.of(new SpawnerLibraryMenu(list,list,null),new SpawnerPresetMenu(list,preset,list,null),
                new DungeonSpawnerMenu(root),new SpawnerPickerMenu(root,0,root,root.draft.get().lobby()));
        for(var menu:menus) {
            menu.open();Inventory obsolete=menu.getInventory();menu.open();assertNotSame(obsolete,menu.getInventory());
            var click=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            when(click.getView()).thenReturn(view);when(click.getWhoClicked()).thenReturn(player);
            when(click.getRawSlot()).thenReturn(17);when(click.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
            framework.onClick(click);verify(click).setCancelled(true);
            for(int slot=0;slot<menu.getInventory().getSize();slot++) assertFalse(menu.allowsPlacement(slot));
            when(view.getTopInventory()).thenReturn(obsolete);framework.onClick(click);
            verify(click,times(2)).setCancelled(true);
            when(view.getTopInventory()).thenAnswer(call->top);
        }
    }
    @Test void cancelledEquipmentReopeningMustNotRetainListener() throws Exception {
        try (var handlers = mockStatic(org.bukkit.event.HandlerList.class)) {
            handlers.when(() -> org.bukkit.event.HandlerList.unregisterAll(any(Listener.class)))
                    .thenAnswer(call -> { listeners.remove(call.getArgument(0)); return null; });
            var menu = equipmentForLifecycleTest();
            menu.open();
            assertTrue(listeners.contains(menu));
            doAnswer(call -> {
                // CraftEventFactory: close old view, then fire/cancel org.bukkit.event.inventory.InventoryOpenEvent.
                paperClose(top, InventoryCloseEvent.Reason.OPEN_NEW);
                top = inventory(null);
                var event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
                when(event.getInventory()).thenReturn(call.getArgument(0));
                when(event.getPlayer()).thenReturn(player);
                when(event.isCancelled()).thenReturn(true);
                framework.onOpen(event);
                return null;
            }).when(player).openInventory(any(Inventory.class));
            menu.open();
            assertFalse(listeners.contains(menu), "Cancelled opening must release its listener immediately");
            drain();
            assertFalse(top.getHolder() instanceof Menu);
        }
    }

    @Test void rewardDeathMustPreservePhysicalDeposit() throws Exception {
        var root = remember(definition("death-reward"));
        var menu = new RewardMenu(root);
        menu.open();
        var physicalInventory = new ArrayList<ItemStack>();
        var worldDrops = new ArrayList<ItemStack>();
        when(player.getInventory().addItem(any(ItemStack.class))).thenAnswer(call -> {
            physicalInventory.add(call.getArgument(0));
            return new HashMap<Integer, ItemStack>();
        });
        when(player.getWorld()).thenReturn(world);
        when(world.dropItem(any(), any())).thenAnswer(call -> { worldDrops.add(call.getArgument(1)); return null; });
        var deposited = item(Material.DIAMOND);
        menu.getInventory().setItem(18, deposited);
        // Paper build 157 ServerPlayer.die: collect inventory drops, fire death,
        // closeContainer(DEATH), then clear inventory when keepInventory=false.
        worldDrops.addAll(physicalInventory);
        when(player.isDead()).thenReturn(true);
        paperClose(top, InventoryCloseEvent.Reason.DEATH);
        top = inventory(null);
        physicalInventory.clear();
        assertNull(menu.getInventory().getItem(18));
        assertTrue(worldDrops.contains(deposited) || physicalInventory.contains(deposited),
                "The deposit was absent from initial death drops and must not be returned into the inventory being cleared");
    }

    @Test void restrictedClickMatrixCannotExtractTemplates() throws Exception {
        var equipment = equipmentForLifecycleTest();
        var reward = new RewardMenu(remember(definition("click-reward")));
        reward.getInventory().setItem(18, item(Material.DIAMOND));
        reward.capture();
        for (Menu menu : List.of(equipment, reward)) {
            menu.open();
            for (int slot : List.of(menu instanceof EquipmentMenu ? 19 : 18, 54)) {
                for (var type : List.of(org.bukkit.event.inventory.ClickType.SHIFT_LEFT, org.bukkit.event.inventory.ClickType.SHIFT_RIGHT, org.bukkit.event.inventory.ClickType.NUMBER_KEY,
                        org.bukkit.event.inventory.ClickType.DOUBLE_CLICK, org.bukkit.event.inventory.ClickType.SWAP_OFFHAND, org.bukkit.event.inventory.ClickType.MIDDLE, org.bukkit.event.inventory.ClickType.DROP, org.bukkit.event.inventory.ClickType.CONTROL_DROP)) {
                    var event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
                    var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
                    when(event.getView()).thenReturn(view);
                    when(event.getWhoClicked()).thenReturn(player);
                    when(event.getRawSlot()).thenReturn(slot);
                    when(event.getClick()).thenReturn(type);
                    when(event.isLeftClick()).thenReturn(type.isLeftClick());
                    when(event.isRightClick()).thenReturn(type.isRightClick());
                    when(event.isShiftClick()).thenReturn(type.isShiftClick());
                    when(event.getAction()).thenReturn(switch (type) {
                        case SHIFT_LEFT, SHIFT_RIGHT -> org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;
                        case NUMBER_KEY, SWAP_OFFHAND -> org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP;
                        case DOUBLE_CLICK -> org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR;
                        case MIDDLE -> org.bukkit.event.inventory.InventoryAction.CLONE_STACK;
                        case DROP -> org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT;
                        default -> org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT;
                    });
                    doAnswer(call -> { cancelled.set(call.getArgument(0)); return null; }).when(event).setCancelled(anyBoolean());
                    framework.onClick(event);
                    if (menu instanceof EquipmentMenu eq) eq.placed(event);
                    assertTrue(cancelled.get(), menu.getClass().getSimpleName() + " " + type + " slot " + slot);
                }
            }
        }
    }

    @Test void rewardOtherCloseReasonsReturnExactlyOnce() throws Exception {
        for (var reason : List.of(InventoryCloseEvent.Reason.DISCONNECT, InventoryCloseEvent.Reason.TELEPORT,
                InventoryCloseEvent.Reason.PLUGIN, InventoryCloseEvent.Reason.OPEN_NEW, InventoryCloseEvent.Reason.PLAYER)) {
            var menu = new RewardMenu(new DungeonMenu(player, definition("close-" + reason.name().toLowerCase(Locale.ROOT)), list));
            menu.open();
            var deposited = item(Material.DIAMOND);
            menu.getInventory().setItem(18, deposited);
            paperClose(top, reason);
            paperClose(top, reason);
            verify(player.getInventory(), times(1)).addItem(deposited);
            assertNull(top.getItem(18));
        }
    }

    @Test void numericFallbackStillHandlesClicksAndReturnsToEquipment() throws Exception {
        inputs.close();
        inputs = mockStatic(dev.dasan.customdungeons.gui.Inputs.class, CALLS_REAL_METHODS);
        try (var handlers = mockStatic(org.bukkit.event.HandlerList.class)) {
            handlers.when(() -> org.bukkit.event.HandlerList.unregisterAll(any(Listener.class)))
                    .thenAnswer(call -> { listeners.remove(call.getArgument(0)); return null; });
            doAnswer(call -> {
                if (top.getHolder() instanceof Menu) paperClose(top, InventoryCloseEvent.Reason.OPEN_NEW);
                top = call.getArgument(0);
                var event = mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
                when(event.getInventory()).thenReturn(top);
                when(event.getPlayer()).thenReturn(player);
                framework.onOpen(event);
                return view;
            }).when(player).openInventory(any(Inventory.class));
            var equipment = equipmentForLifecycleTest();
            equipment.open();
            var result = new java.util.concurrent.atomic.AtomicReference<Double>();
            dev.dasan.customdungeons.gui.Inputs.numberWithClicks(player,
                    net.kyori.adventure.text.Component.empty(), 0, 100, 10, result::set, 0);
            lifecycleClick(12, org.bukkit.event.inventory.ClickType.LEFT);        // -1
            lifecycleClick(14, org.bukkit.event.inventory.ClickType.SHIFT_RIGHT); // +10
            lifecycleClick(22, org.bukkit.event.inventory.ClickType.LEFT);        // save
            drain();
            assertEquals(19d, result.get());
            assertSame(equipment, top.getHolder());
            assertTrue(listeners.contains(equipment));
        }
    }

    private void lifecycleClick(int slot, org.bukkit.event.inventory.ClickType type) {
        var event = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(type);
        when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        when(event.isLeftClick()).thenReturn(type.isLeftClick());
        when(event.isRightClick()).thenReturn(type.isRightClick());
        when(event.isShiftClick()).thenReturn(type.isShiftClick());
        framework.onClick(event);
        verify(event).setCancelled(true);
    }

    @Test void disableReturnsRewardAndReleasesEquipmentListener() throws Exception {
        try (var handlers = mockStatic(org.bukkit.event.HandlerList.class)) {
            handlers.when(() -> org.bukkit.event.HandlerList.unregisterAll(any(Listener.class)))
                    .thenAnswer(call -> { listeners.remove(call.getArgument(0)); return null; });
            var menu = new RewardMenu(remember(definition("disable-reward")));
            menu.open();
            var deposit = item(Material.DIAMOND);
            top.setItem(18, deposit);
            doAnswer(call -> {
                paperClose(top, InventoryCloseEvent.Reason.PLUGIN);
                top = inventory(null);
                return null;
            }).when(player).closeInventory();
            var online = plugin.getServer();
            doReturn(List.of(player)).when(online).getOnlinePlayers();
            when(plugin.isEnabled()).thenReturn(false);
            var disable = new org.bukkit.event.server.PluginDisableEvent(plugin);
            framework.onDisable(disable);
            verify(player.getInventory(), times(1)).addItem(deposit);
            assertFalse(top.getHolder() instanceof Menu);
            when(plugin.isEnabled()).thenReturn(true);
            var equipment = equipmentForLifecycleTest();
            equipment.open();
            var preview = top.getItem(19);
            when(plugin.isEnabled()).thenReturn(false);
            framework.onDisable(disable);
            assertFalse(listeners.contains(equipment));
            assertNull(equipment.getInventory().getItem(19));
            verify(player.getInventory(), never()).addItem(preview);
        }
    }

    @Test void deathCloseReturnsOnlyRealEquipmentDepositsToTheGroundExactlyOnce() throws Exception {
        try (var handlers = paperInventoryLifecycle()) {
            var menu = equipmentWithPreview();
            menu.open();
            var preview = top.getItem(19);
            var deposited = item(Material.DIAMOND_HELMET);
            top.setItem(21, deposited);
            when(player.getWorld()).thenReturn(world);
            // The close reason must work even before Bukkit reports isDead().
            when(player.isDead()).thenReturn(false);
            paperClose(top, InventoryCloseEvent.Reason.DEATH);
            paperClose(top, InventoryCloseEvent.Reason.DEATH);
            assertNull(top.getItem(21));
            assertNull(top.getItem(19));
            verify(world, times(1)).dropItem(player.getLocation(), deposited);
            verify(world, never()).dropItem(any(), eq(preview));
            verify(player.getInventory(), never()).addItem(any(ItemStack.class));
            assertFalse(listeners.contains(menu));
        }
    }

    @Test void deadRewardCaptureReturnsTheDepositToTheGroundWithoutADeathClose() throws Exception {
        var menu = new RewardMenu(remember(definition("dead-reward-capture")));
        menu.open();
        var deposited = item(Material.DIAMOND);
        top.setItem(18, deposited);
        when(player.getWorld()).thenReturn(world);
        when(player.isDead()).thenReturn(true);
        menu.capture();
        menu.capture();
        assertNull(top.getItem(18));
        verify(world, times(1)).dropItem(player.getLocation(), deposited);
        verify(player.getInventory(), never()).addItem(deposited);
    }

    @Test void cancelledQueuedResizeMustReleaseTheReplacementListener() throws Exception {
        try (var handlers = paperInventoryLifecycle()) {
            var resized = new java.util.concurrent.atomic.AtomicBoolean();
            var menu = new Menu(player, Component.empty(), 3) {
                @Override protected int preferredRows() { return resized.get() ? 6 : 3; }
                @Override protected void render() { bindInventoryListener(newListener, plugin); }
                private final Listener newListener = new Listener() {};
            };
            menu.open();
            int baseline = listeners.size();
            resized.set(true);
            menu.refresh();
            doAnswer(call -> {
                var close = mock(InventoryCloseEvent.class);
                when(close.getInventory()).thenReturn(top);
                when(close.getPlayer()).thenReturn(player);
                framework.onClose(close);
                top = inventory(null);
                return null;
            }).when(player).openInventory(any(Inventory.class));
            tasks.remove().run();
            assertFalse(top.getHolder() instanceof Menu);
            assertEquals(baseline - 1, listeners.size(), "A cancelled resize must release its new binding immediately");
            drain();
        }
    }

    private dev.dasan.customdungeons.gui.wizard.WizardDraftStore wizardDrafts(int step) {
        var drafts=mock(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class);
        var values=new DungeonMenu.Values(definition("wizard"));
        values.area=Region.of("world",new BlockPos(-100,-64,-100),new BlockPos(100,100,100));
        values.exit=new Point("world",101,64,0,0,0);
        var saved=new java.util.concurrent.atomic.AtomicReference<>(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(values.build(),step,step));
        when(drafts.get("wizard")).thenAnswer(call->Optional.ofNullable(saved.get()));
        when(drafts.save(any())).thenAnswer(call->{saved.set(call.getArgument(0));return CompletableFuture.completedFuture(null);});
        when(drafts.delete(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        when(plugin.getServer().getServicesManager().load(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.class)).thenReturn(drafts);
        return drafts;
    }
    @Test void wizardReusesSettingsAndReturnsToItsCurrentStep() {
        var drafts=wizardDrafts(4);list.openWizard("wizard");
        var wizard=(WizardMenu)top.getHolder();assertSame(wizard,WizardMenu.active(player.getUniqueId()));
        clickSlot(29);assertInstanceOf(DungeonSettingsMenu.class,top.getHolder());
        clickSlot(45);assertSame(wizard,top.getHolder());
        clickSlot(49);assertNull(WizardMenu.active(player.getUniqueId()));
        assertTrue(locks.holder("wizard").isEmpty());verify(drafts,atLeastOnce()).save(any());
    }
    @Test void closingWizardForToolsKeepsItsIndependentLockUntilExitOrDisconnect() throws Exception {
        wizardDrafts(0);list.openWizard("wizard");var wizard=(WizardMenu)top.getHolder();
        closeRoot(wizard);assertSame(wizard,WizardMenu.active(player.getUniqueId()));
        assertTrue(locks.holder("wizard").isPresent());assertFalse(locks.tryLock("wizard",UUID.randomUUID()));
        wizard.pause();assertTrue(locks.holder("wizard").isEmpty());
    }
    @Test void wizardCannotFinishInvalidAndPublishesOnlyAfterValidFinish() {
        var drafts=wizardDrafts(0);list.openWizard("wizard");
        var wizard=(WizardMenu)top.getHolder();clickSlot(53);verify(store,never()).save(any(DungeonDef.class));
        wizard.pause();drafts=wizardDrafts(6);
        when(store.save(any(DungeonDef.class))).thenAnswer(call->{var definition=(DungeonDef)call.getArgument(0);definitions.put(definition.id(),definition);return CompletableFuture.completedFuture(null);});
        list.openWizard("wizard");clickSlot(53);
        verify(store).save(any(DungeonDef.class));verify(drafts).delete("wizard");assertNull(WizardMenu.active(player.getUniqueId()));
        assertTrue(locks.holder("wizard").isEmpty());assertFalse(definitions.get("wizard").enabled());
    }
    @Test void wizardBlocksOtherEditorsAndAlwaysAllowsExitWhenDungeonBecomesBusy() {
        wizardDrafts(4);list.openWizard("wizard");
        DungeonListMenu.dungeonBusy(id->true);clickSlot(49);
        assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
    }
    @Test void wizardAdvancedEditorSharesItsDraftAndRemovesRuntimeIndicators() {
        var drafts=wizardDrafts(6);list.openWizard("wizard");clickSlot(34);
        assertInstanceOf(DungeonMenu.class,top.getHolder());assertFalse(top.getHolder() instanceof WizardMenu);
        assertNull(WizardMenu.active(player.getUniqueId()));
        var editor=(DungeonMenu)top.getHolder();editor.change(v->v.lives=8);
        var saves=org.mockito.ArgumentCaptor.forClass(dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved.class);
        verify(drafts,atLeastOnce()).save(saves.capture());assertEquals(8,saves.getValue().definition().lives());
    }

    @Test void wizardQuitEventRemovesProgressParticlesAndReleasesLock(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        wizardDrafts(3);
        var previews=mock(PreviewRenderer.class);
        when(plugin.getServer().getServicesManager().load(PreviewRenderer.class)).thenReturn(previews);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());WizardMenu.register(plugin);
        list.openWizard("wizard");assertNotNull(WizardMenu.active(player.getUniqueId()));
        var quit=new org.bukkit.event.player.PlayerQuitEvent(player,Component.empty());
        for(var listener:List.copyOf(listeners)) {
            try {listener.getClass().getMethod("quit",org.bukkit.event.player.PlayerQuitEvent.class).invoke(listener,quit);}
            catch(NoSuchMethodException ignored) {}
        }
        assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
        verify(previews).stopWizard(player.getUniqueId());
    }
    @Test void wizardDisableEventCleansUpEvenAfterTheFrameworkHasShutDown(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        wizardDrafts(3);when(plugin.getDataFolder()).thenReturn(directory.toFile());WizardMenu.register(plugin);
        var previews=mock(PreviewRenderer.class);when(plugin.getServer().getServicesManager().load(PreviewRenderer.class)).thenReturn(previews);
        list.openWizard("wizard");when(plugin.isEnabled()).thenReturn(false);
        var disable=new org.bukkit.event.server.PluginDisableEvent(plugin);
        framework.onDisable(disable);
        for(var listener:List.copyOf(listeners)) {
            try {listener.getClass().getMethod("disable",org.bukkit.event.server.PluginDisableEvent.class).invoke(listener,disable);}
            catch(NoSuchMethodException ignored) {}
        }
        assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
        verify(previews,times(1)).wizard(eq(player),any());verify(previews).stopWizard(player.getUniqueId());
    }
    @Test void wizardSaveFailureCanBeRetriedAndQuitKeepsTheLockUntilSaveCompletes() throws Exception {
        wizardDrafts(6);var pending=new CompletableFuture<Void>();
        when(store.save(any(DungeonDef.class))).thenReturn(pending);
        list.openWizard("wizard");var wizard=(WizardMenu)top.getHolder();clickSlot(53);
        assertTrue(wizard.saving());wizard.pause();assertTrue(locks.holder("wizard").isPresent());
        pending.completeExceptionally(new IllegalStateException("simulated"));drain();
        assertTrue(locks.holder("wizard").isEmpty());assertNull(WizardMenu.active(player.getUniqueId()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void replacingACleanWizardClosesItsSessionBeforeAcquiringTheReplacementLock(boolean sameDungeon) {
        var drafts=wizardDrafts(6);
        var previews=mock(PreviewRenderer.class);
        when(plugin.getServer().getServicesManager().load(PreviewRenderer.class)).thenReturn(previews);
        bukkit.when(Bukkit::getScoreboardManager).thenReturn(mock(org.bukkit.scoreboard.ScoreboardManager.class));
        try(var hud=mockConstruction(dev.dasan.customdungeons.gui.wizard.WizardProgress.class)) {
            // A published test snapshot makes the resumed wizard clean relative to publication.
            definitions.put("wizard",drafts.get("wizard").orElseThrow().definition());
            list.openWizard("wizard");var old=WizardMenu.active(player.getUniqueId());
            assertFalse(old.dirty());
            var replacement=new WizardMenu(player,sameDungeon?old.draft.get():definition("second"),list,
                    new dev.dasan.customdungeons.gui.wizard.WizardState(0,0));
            assertSame(replacement,list.remember(replacement));replacement.open();
            assertSame(replacement,WizardMenu.active(player.getUniqueId()));
            verify(hud.constructed().getFirst()).close();
            verify(previews).stopWizard(player.getUniqueId());
            assertFalse(old.canEdit(false));
            clearInvocations(previews);old.pause();verifyNoInteractions(previews);
            assertTrue(locks.holder(replacement.draft.get().id()).isPresent());
            if(!sameDungeon) assertTrue(locks.holder("wizard").isEmpty());
            replacement.pause();assertTrue(locks.holder(replacement.draft.get().id()).isEmpty());
            assertNull(WizardMenu.active(player.getUniqueId()));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void advancedEditorRejectsAnotherAdminsNewerWizardDraftEvenWhenPublicationIsUnchanged(boolean published) {
        var drafts=wizardDrafts(6);var initial=drafts.get("wizard").orElseThrow().definition();
        if(published) definitions.put("wizard",initial);
        var stale=new DungeonMenu(player,initial,list);assertSame(stale,list.remember(stale));
        // A closes the inventory and releases the GUI lock; its editor object survives.
        locks.unlock("wizard",player.getUniqueId());
        var second=player();var otherList=new DungeonListMenu(second,true,null);
        var other=new DungeonMenu(second,initial,otherList);assertSame(other,otherList.remember(other));
        other.change(v->v.lives=9);
        assertEquals(9,drafts.get("wizard").orElseThrow().definition().lives());
        other.change(v->v.max=12); // Own persisted changes must not conflict with themselves.
        assertEquals(12,drafts.get("wizard").orElseThrow().definition().maxPlayers());
        locks.unlock("wizard",second.getUniqueId());
        assertTrue(stale.outdated());stale.saveDraft();stale.change(v->v.lives=4);
        verify(store,never()).save(any(DungeonDef.class));
        verify(plugin.messages(),atLeastOnce()).send(eq(player),eq("gui.dungeon.conflict"),any(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[].class));
        assertEquals(9,drafts.get("wizard").orElseThrow().definition().lives());
        assertTrue(locks.holder("wizard").isEmpty());
        assertNull(list.editor("wizard"));assertNotNull(confirm);confirm.run();drain();
        var latest=(DungeonMenu)top.getHolder();assertEquals(9,latest.draft.get().lives());assertFalse(latest.outdated());
    }
    @Test void newerWizardDraftSurvivesClosingAnOutdatedWizard() {
        var drafts=wizardDrafts(4);list.openWizard("wizard");var old=WizardMenu.active(player.getUniqueId());
        var saved=drafts.get("wizard").orElseThrow();var values=new DungeonMenu.Values(saved.definition());values.lives=9;
        drafts.save(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(values.build(),saved.step(),saved.completed()));
        assertTrue(old.outdated());old.pause();
        assertEquals(9,drafts.get("wizard").orElseThrow().definition().lives());
        assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
        list.openWizard("wizard");assertEquals(9,WizardMenu.active(player.getUniqueId()).draft.get().lives());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2})
    void worldChangeCleansWizardSessionWithMenuClosedForToolsOrSubmenu(int inventoryMode,@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        wizardDrafts(3);when(plugin.getDataFolder()).thenReturn(directory.toFile());WizardMenu.register(plugin);
        var previews=mock(PreviewRenderer.class);when(plugin.getServer().getServicesManager().load(PreviewRenderer.class)).thenReturn(previews);
        bukkit.when(Bukkit::getScoreboardManager).thenReturn(mock(org.bukkit.scoreboard.ScoreboardManager.class));
        try(var hud=mockConstruction(dev.dasan.customdungeons.gui.wizard.WizardProgress.class)) {
            list.openWizard("wizard");var old=WizardMenu.active(player.getUniqueId());
            if(inventoryMode==0) player.closeInventory();else if(inventoryMode==2) new RoomMenu(old,0,old).open();
            var event=new org.bukkit.event.player.PlayerChangedWorldEvent(player,world);
            for(var listener:List.copyOf(listeners)) for(var method:listener.getClass().getMethods())
                if(Arrays.equals(method.getParameterTypes(),new Class<?>[]{org.bukkit.event.player.PlayerChangedWorldEvent.class})) method.invoke(listener,event);
            assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
            assertFalse(old.canEdit(false));verify(hud.constructed().getFirst()).close();verify(previews).stopWizard(player.getUniqueId());
            assertFalse(top.getHolder() instanceof Menu);
            inputs.verify(()->Inputs.cancel(player)); // Deferred dialogs cannot reopen the stopped wizard.
        }
    }
    @Test void wizardPauseStillCleansSessionIfDraftSerializationFails() {
        var drafts=wizardDrafts(4);var previews=mock(PreviewRenderer.class);
        when(plugin.getServer().getServicesManager().load(PreviewRenderer.class)).thenReturn(previews);
        var logger=mock(java.util.logging.Logger.class);when(plugin.getLogger()).thenReturn(logger);
        bukkit.when(Bukkit::getScoreboardManager).thenReturn(mock(org.bukkit.scoreboard.ScoreboardManager.class));
        try(var hud=mockConstruction(dev.dasan.customdungeons.gui.wizard.WizardProgress.class)) {
            list.openWizard("wizard");var old=WizardMenu.active(player.getUniqueId());
            doThrow(new IllegalStateException("simulated serialization failure")).when(drafts).save(any());
            old.pause();
            assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
            verify(hud.constructed().getFirst()).close();verify(previews).stopWizard(player.getUniqueId());
            verify(logger).warning(anyString());
        }
    }
    @Test void reloadCommandCleansWizardBeforeReloadingDefinitionsEvenWithMenuClosed() throws Exception {
        wizardDrafts(3);
        var previews=mock(PreviewRenderer.class);when(plugin.getServer().getServicesManager().load(PreviewRenderer.class)).thenReturn(previews);
        bukkit.when(Bukkit::getScoreboardManager).thenReturn(mock(org.bukkit.scoreboard.ScoreboardManager.class));
        when(player.hasPermission("customdungeons.admin.reload")).thenReturn(true);
        var reloadServer=plugin.getServer();doReturn(List.of(player)).when(reloadServer).getOnlinePlayers();
        when(plugin.sessionManager()).thenReturn(mock(dev.dasan.customdungeons.session.SessionManager.class));
        when(plugin.getResource(anyString())).thenAnswer(call->getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        when(store.reloadAsync(any())).thenReturn(CompletableFuture.completedFuture(null));
        try(var hud=mockConstruction(dev.dasan.customdungeons.gui.wizard.WizardProgress.class);
            var migration=mockStatic(dev.dasan.customdungeons.config.ConfigMigration.class)) {
            list.openWizard("wizard");var old=WizardMenu.active(player.getUniqueId());player.closeInventory();
            var dispatcher=dungeonCommands();
            var command=dispatcher.getRoot().getChild("customdungeon");
            assertNotNull(command.getChild("reload").getCommand());
            assertNotNull(command.getChild("create").getChild("id").getCommand());
            assertNotNull(command.getChild("key").getChild("give").getChild("players").getChild("dungeon").getCommand());
            var source=mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);when(source.getSender()).thenReturn(player);
            when(plugin.getDataFolder()).thenReturn(new java.io.File("build/nonexistent-reload-test"));
            dispatcher.execute("customdungeon reload",source);
            verify(store).reloadAsync(any());
            assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
            assertFalse(old.canEdit(false));verify(hud.constructed().getFirst()).close();verify(previews).stopWizard(player.getUniqueId());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={3,6})
    void openingRoomsOrReviewInspectsEachDoorBlockOnceAndRefreshUsesANewValidationSnapshot(int step) {
        var drafts=wizardDrafts(step);var saved=drafts.get("wizard").orElseThrow();
        var values=new DungeonMenu.Values(saved.definition());var room=values.rooms.getFirst();
        var door=Region.of("world",new BlockPos(0,0,0),new BlockPos(1,0,0));
        values.rooms=List.of(new RoomDef(room.id(),room.region(),room.checkpoint(),door,room.unlock(),room.keyCarrierTemplateId(),room.spawners()));
        drafts.save(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(values.build(),step,step));
        var server=plugin.getServer();bukkit.when(Bukkit::getServer).thenReturn(server);bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        bukkit.when(()->Bukkit.getWorld("world")).thenReturn(world);
        var block=mock(org.bukkit.block.Block.class);when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));
        list.openWizard("wizard");var wizard=WizardMenu.active(player.getUniqueId());
        verify(world,times(2)).getBlockAt(anyInt(),anyInt(),anyInt());
        clearInvocations(world);
        when(block.getState()).thenReturn(mock(org.bukkit.block.TileState.class));wizard.open();
        verify(world,times(1)).getBlockAt(anyInt(),anyInt(),anyInt()); // Stops at the first invalid block.
        assertEquals(Material.GRAY_DYE,top.getItem(53).getType());
        clearInvocations(world);when(block.getState()).thenReturn(mock(org.bukkit.block.BlockState.class));
        wizard.change(v->v.lives=0); // Pure step reconciliation must not inspect the world.
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());wizard.open();
        verify(world,times(2)).getBlockAt(anyInt(),anyInt(),anyInt());
        assertEquals(Material.LIME_CONCRETE,top.getItem(53).getType());clickSlot(53); // Rooms recover; invalid lives block Rules.
        assertEquals(Material.GRAY_DYE,top.getItem(53).getType());
    }

    @Test void pausedWizardDoesNotStartATestOrCloseAnotherMenuWhenPendingPublicationCompletes() {
        wizardDrafts(6);var pending=new CompletableFuture<Void>();
        when(store.save(any(DungeonDef.class))).thenReturn(pending);
        var sessions=mock(dev.dasan.customdungeons.session.SessionManager.class);when(plugin.sessionManager()).thenReturn(sessions);
        when(player.hasPermission("customdungeons.admin.test")).thenReturn(true);
        list.openWizard("wizard");var old=WizardMenu.active(player.getUniqueId());clickSlot(31);
        assertTrue(old.saving());old.pause();list.open();var newInventory=top;
        pending.complete(null);drain();
        verify(sessions,never()).startTest(any(),anyString());assertSame(newInventory,top);
        assertNull(WizardMenu.active(player.getUniqueId()));assertTrue(locks.holder("wizard").isEmpty());
    }
    private com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack> dungeonCommands() throws Exception {
        var type=dev.dasan.customdungeons.command.CustomDungeonCommand.class;
        var constructor=type.getDeclaredConstructor(CustomDungeonsPlugin.class);constructor.setAccessible(true);
        var tree=type.getDeclaredMethod("tree");tree.setAccessible(true);
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<io.papermc.paper.command.brigadier.CommandSourceStack>();
        // T39's selector factory needs Paper's runtime provider, unavailable in this offline fixture.
        try(var arguments=mockStatic(io.papermc.paper.command.brigadier.argument.ArgumentTypes.class)) {
            com.mojang.brigadier.arguments.ArgumentType<io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver> players=
                    reader->{throw new AssertionError("Unrelated player selector parsed");};
            arguments.when(io.papermc.paper.command.brigadier.argument.ArgumentTypes::players).thenReturn(players);
            @SuppressWarnings("unchecked") var builder=(com.mojang.brigadier.builder.LiteralArgumentBuilder<io.papermc.paper.command.brigadier.CommandSourceStack>)tree.invoke(constructor.newInstance(plugin));
            dispatcher.register(builder);
        }
        return dispatcher;
    }
    @Test void createCommandResumesWizardWithAreaAndExternalKeyRoomAfterEditingSpawner() throws Exception {
        var drafts=wizardDrafts(4);var initial=drafts.get("wizard").orElseThrow();
        var values=new DungeonMenu.Values(initial.definition());var room=values.rooms.getFirst();
        var door=Region.of("world",new BlockPos(0,0,0),new BlockPos(0,0,0));
        values.rooms=List.of(new RoomDef(room.id(),room.region(),room.checkpoint(),door,UnlockMode.KEY,null,room.spawners(),RoomDef.OpeningMode.EXTERNAL_KEY),
                new RoomDef("last",room.region(),room.checkpoint(),null,UnlockMode.AUTOMATIC,null,room.spawners()));
        drafts.save(new dev.dasan.customdungeons.gui.wizard.WizardDraftStore.Saved(values.build(),4,4));
        when(plugin.sessionManager()).thenReturn(mock(dev.dasan.customdungeons.session.SessionManager.class));
        var dispatcher=dungeonCommands();
        var source=mock(io.papermc.paper.command.brigadier.CommandSourceStack.class);when(source.getSender()).thenReturn(player);
        assertEquals(1,dispatcher.execute("customdungeon create wizard",source));
        var first=WizardMenu.active(player.getUniqueId());assertNotNull(first);
        first.change(v->v.lives=9);
        first.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),2,s.waves(),s.presetId()));
        first.pause();assertNull(WizardMenu.active(player.getUniqueId()));
        assertEquals(1,dispatcher.execute("customdungeon create wizard",source));
        var resumed=WizardMenu.active(player.getUniqueId());assertNotNull(resumed);assertNotSame(first,resumed);
        assertEquals(values.area,resumed.draft.get().area());assertEquals(9,resumed.draft.get().lives());
        assertEquals(RoomDef.OpeningMode.EXTERNAL_KEY,resumed.draft.get().rooms().getFirst().openingMode());
        assertEquals(2,resumed.draft.get().rooms().getFirst().spawners().getFirst().radius());
        assertEquals(4,drafts.get("wizard").orElseThrow().step());
        assertSame(resumed,top.getHolder());
    }

    @Test void newDungeonStartsWithoutTeleportAndAdvancedCopiesRetainStartFields() throws Exception {
        var d=list.newDefinition("one");assertFalse(d.teleportOnStart());assertTrue(d.teleportOnFinish());
        var configured=definition("one").withStart(StartMode.PLATES,List.of(new Point("world",0,64,0,0,0)),4,
                Region.of("world",new BlockPos(0,64,0),new BlockPos(0,66,0)),false,false,true,17);
        definitions.put("one",configured);var root=remember(configured);
        root.change(v->v.name="renamed");
        assertEquals(1,root.draft.get().minPlayers());assertEquals(configured.plates(),root.draft.get().plates());
        assertEquals(configured.entranceDoor(),root.draft.get().entranceDoor());assertFalse(root.draft.get().teleportOnFinish());
        assertTrue(root.draft.get().introCinematic());assertEquals(17,root.draft.get().introSeconds());
    }
    @Test void approvedStartMenuSlotsNavigateToggleAndLockMinimum() throws Exception {
        definitions.put("one",definition("one"));var root=remember(definition("one"));root.open();
        assertEquals(Material.LEVER,top.getItem(41).getType());clickSlot(41);
        assertInstanceOf(StartSettingsMenu.class,top.getHolder());assertEquals(54,top.getSize());
        for(int slot:new int[]{19,21,23,25,28,30,32,37,39,41})assertNotNull(top.getItem(slot));
        clickSlot(19);assertEquals(StartMode.PLATES,root.draft.get().startMode());
        clickSlot(32);assertTrue(root.draft.get().introCinematic());
        new DungeonSettingsMenu(root).open();assertEquals(Material.GRAY_DYE,top.getItem(19).getType());
        int min=root.draft.get().minPlayers();clickSlot(19);assertEquals(min,root.draft.get().minPlayers());
    }

    @Test void finishColumnCyclesModeAndDestinationAndGivesBothTools() throws Exception {
        var d=definition("one");definitions.put("one",d);var root=remember(d);new StartSettingsMenu(root).open();
        assertEquals(Material.ENDER_PEARL,top.getItem(25).getType());assertEquals(Material.GRAY_DYE,top.getItem(34).getType());
        clickSlot(25);assertEquals(FinishMode.DELAYED,root.draft.get().finishMode());assertEquals(Material.CLOCK,top.getItem(34).getType());
        clickSlot(43);assertEquals(FinishDestination.PREVIOUS,root.draft.get().finishDestination());assertEquals(Material.RECOVERY_COMPASS,top.getItem(43).getType());
        clickSlot(25);assertEquals(FinishMode.NONE,root.draft.get().finishMode());assertEquals(Material.BARRIER,top.getItem(25).getType());
        clickSlot(25);assertEquals(FinishMode.IMMEDIATE,root.draft.get().finishMode());
        clickSlot(28);var tools=plugin.getServer().getServicesManager().load(ToolService.class);
        verify(tools).give(player,ToolType.PLATE,"one");verify(tools).give(player,ToolType.EXIT_PLATE,"one");
    }

    @Test void plateEditsUseOnlyCurrentDungeonAndRespectBusyAndPermissionChecks() throws Exception {
        var d=definition("one").withStart(StartMode.PLATES,List.of(),3,null,false,true,false,10);
        definitions.put("one",d);var root=remember(d);
        var capture=org.mockito.ArgumentCaptor.forClass(java.util.function.BiFunction.class);
        var tools=plugin.getServer().getServicesManager().load(ToolService.class);
        verify(tools).onPlateEdit(capture.capture(),any());
        var callback=capture.getValue();
        assertNull(callback.apply(player,"other"));
        var editor=(ToolService.PlateEditor)callback.apply(player,"one");assertNotNull(editor);
        assertTrue(editor.update(List.of(new Point("world",0,64,0,0,0))));assertEquals(1,root.draft.get().minPlayers());
        assertNotNull(callback.apply(player,""));
        DungeonListMenu.dungeonBusy(id->true);assertNull(callback.apply(player,"one"));
        assertFalse(editor.update(List.of()));DungeonListMenu.dungeonBusy(id->false);
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(false);assertNull(callback.apply(player,"one"));
    }

}
