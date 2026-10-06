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
    InventoryView view;
    Inventory top;
    MenuListener framework;
    DungeonListMenu list;
    Runnable confirm;

    @BeforeEach void setup() {
        bukkit = mockStatic(Bukkit.class);
        javaPlugin = mockStatic(JavaPlugin.class);
        menuServices = mockStatic(MenuListener.class);
        inputs = mockStatic(Inputs.class);
        inputs.when(() -> Inputs.formatNumber(anyDouble(),anyInt())).thenCallRealMethod();
        theme = mockStatic(GuiTheme.class);
        buttons = mockStatic(Button.class);
        buttons.when(() -> Button.of(any(), any(), anyList(), any())).thenAnswer(call ->
                new Button(item(call.getArgument(0)), call.getArgument(3)));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                .thenAnswer(call -> inventory(call.getArgument(0)));
        javaPlugin.when(() -> JavaPlugin.getPlugin(CustomDungeonsPlugin.class)).thenReturn(plugin);
        Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(server.getServicesManager().load(DefinitionStore.class)).thenReturn(store);
        when(server.getServicesManager().load(dev.dasan.customdungeons.config.EntityHeights.class)).thenReturn(dev.dasan.customdungeons.config.ConfigLoader.defaultEntityHeights());
        when(server.getServicesManager().load(ToolService.class)).thenReturn(mock(ToolService.class));
        when(server.getServicesManager().load(SpawnerMarkers.class)).thenReturn(mock(SpawnerMarkers.class));
        var pluginManager = server.getPluginManager();
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
        when(store.mobs()).thenReturn(Map.of("mob", mock(MobTemplate.class)));
        player = player();
        view = player.getOpenInventory();
        top = inventory(null);
        when(view.getTopInventory()).thenAnswer(call -> top);
        doAnswer(call -> { top = call.getArgument(0); return view; }).when(player).openInventory(any(Inventory.class));
        doAnswer(call -> { top = inventory(null); return null; }).when(player).closeInventory();
        inputs.when(() -> Inputs.confirm(eq(player), any(), any())).thenAnswer(call -> { confirm = call.getArgument(2); return null; });
        list = new DungeonListMenu(player);
        DungeonListMenu.register(plugin);
    }
    @AfterEach void cleanup() throws Exception {
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
    Inventory inventory(InventoryHolder holder) {
        Inventory inv = mock(Inventory.class);
        Map<Integer,ItemStack> slots = new HashMap<>();
        when(inv.getHolder()).thenReturn(holder);
        when(inv.getSize()).thenReturn(54);
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
    void closeRoot(DungeonMenu root) throws Exception {
        var event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(root.getInventory());
        when(event.getPlayer()).thenReturn(player);
        when(event.getReason()).thenReturn(InventoryCloseEvent.Reason.PLAYER);
        for (Listener listener : listeners) listener.getClass().getMethod("close", InventoryCloseEvent.class).invoke(listener,event);
        player.closeInventory();
        framework.onClose(event);
        drain();
    }



    @Test void rewardDepositsReachAll27CellsWithTheRealThemeAndReturnEveryItemOnce() throws Exception {
        theme.close();theme=null; // Exercise real frame and navigation button registration.
        var root=remember(definition("reward"));
        var menu=new RewardMenu(root);menu.open();
        var deposited=new ArrayList<ItemStack>();
        for(int slot:RewardMenu.itemSlots()) {
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
        assertEquals(Material.BELL,settings.getInventory().getItem(11).getType());
        assertEquals(Material.GOLDEN_APPLE,settings.getInventory().getItem(15).getType());
        for(int slot:new int[]{4,19,21,23,25,29,33,37,38,39,41,43})
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
        assertEquals(Material.BARRIER,room.getInventory().getItem(39).getType());
        assertEquals(Material.SPAWNER,room.getInventory().getItem(4).getType());
        assertEquals(Material.TRIPWIRE_HOOK,room.getInventory().getItem(42).getType());
        for(int slot:new int[]{2,6,11,15,19,21,23,25,29,33,37,38,39,41,42,43})
            assertNotNull(room.getInventory().getItem(slot));
        var spawners=new RoomSpawnerList(root,0,room);spawners.refresh();
        assertEquals(Material.SPAWNER,spawners.getInventory().getItem(13).getType());
        var wave=new WaveMenu(root,0,0,0,room);wave.refresh();
        assertEquals(Material.COMPARATOR,wave.getInventory().getItem(20).getType());
        assertEquals(Material.ZOMBIE_SPAWN_EGG,wave.getInventory().getItem(31).getType());
    }

    @Test void roomSectionHeadersStayReadOnlyWhileUnlockModeRemainsEditable() throws Exception {
        DungeonMenu root=remember(definition("new"));
        var room=new RoomMenu(root,0,new RoomListMenu(root));room.open();
        var before=root.draft.get();
        for(int slot:new int[]{4,11,15,29,33,38}) clickSlot(slot);
        assertEquals(before,root.draft.get());
        assertSame(room,top.getHolder());
        clickSlot(41);
        assertEquals(UnlockMode.KEY,root.draft.get().rooms().getFirst().unlock());
        assertNull(root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void roomAndSpawnerNavigationPreservesDraftAndReturnsToTheSameList() throws Exception {
        DungeonMenu root=remember(definition("new"));
        var rooms=new RoomListMenu(root);rooms.open();
        assertEquals(Material.EMERALD,top.getItem(13).getType());
        clickSlot(22);
        var room=assertInstanceOf(RoomMenu.class,top.getHolder());
        clickSlot(6);
        assertEquals(2,root.draft.get().rooms().getFirst().spawners().size());
        clickSlot(2);
        var list=assertInstanceOf(RoomSpawnerList.class,top.getHolder());
        assertSame(room,list.parent());
        clickSlot(12);
        var spawner=assertInstanceOf(SpawnerMenu.class,top.getHolder());
        assertSame(list,spawner.parent());
        assertSame(root,spawner.root);
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
        assertEquals(Material.EMERALD,topItem(list,11));
        assertEquals(Material.BOOK,topItem(list,13));
        assertEquals(Material.GRAY_DYE,topItem(list,15));
        assertEquals(Material.LIME_CONCRETE,topItem(list,20));
        assertEquals(Material.GRAY_CONCRETE,topItem(list,22));
        assertEquals(Material.CLOCK,topItem(list,24));
    }
    private Material topItem(Menu menu,int slot) {return menu.getInventory().getItem(slot).getType();}

    @Test void newRoomUsesLastMobAsDefaultCarrier() throws Exception {
        var root = remember(definition("keys"));
        new RoomListMenu(root).create();
        assertEquals("*",root.draft.get().rooms().getLast().keyCarrierTemplateId());
    }
    @Test void carrierPickerOffersLastMobAndConcreteTemplateWithoutChangingOtherRoomFields() throws Exception {
        var root = remember(definition("keys"));
        var template = mock(MobTemplate.class);
        when(template.id()).thenReturn("mob"); when(template.entityType()).thenReturn("minecraft:zombie");
        when(template.displayName()).thenReturn("Zombie"); when(store.mobs()).thenReturn(Map.of("mob",template));
        var menu = new RoomMenu(root,0,root); menu.open();
        var lookup = Menu.class.getDeclaredMethod("buttonAt",int.class); lookup.setAccessible(true);
        ((Button)lookup.invoke(menu,42)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        var picker = assertInstanceOf(Menu.class,top.getHolder());
        ((Button)lookup.invoke(picker,12)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        var original = definition("keys").rooms().getFirst(); var selected = root.draft.get().rooms().getFirst();
        assertEquals(new RoomDef(original.id(),original.region(),original.checkpoint(),original.door(),original.unlock(),"*",original.spawners()),selected);
        ((Button)lookup.invoke(menu,42)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        picker = assertInstanceOf(Menu.class,top.getHolder());
        ((Button)lookup.invoke(picker,14)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        assertEquals("mob",root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void rootHasMobLibraryButtonAndClickOpensLibraryOnNextTick() {
        when(store.mobs()).thenReturn(Map.of());
        var services = plugin.getServer().getServicesManager();
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        list.open();
        assertNotNull(top.getItem(13), "public root must expose the mob library even with no dungeons");
        assertEquals(Material.BOOK, top.getItem(13).getType());
        clickRootLibrary();
        assertSame(list, top.getHolder(), "inventory changes must wait until after the click event");
        drain();
        var library = assertInstanceOf(MobLibraryMenu.class, top.getHolder());
        assertSame(list, library.parent());
    }
    @Test void mobLibraryButtonRemainsVisibleAcrossDungeonPages() {
        for (int i = 0; i < 29; i++) definitions.put("d" + i, definition("d" + i));
        list.open();
        assertEquals(Material.BOOK, top.getItem(13).getType());
        list.nextPage();
        assertEquals(Material.BOOK, top.getItem(13).getType());
        assertNotNull(top.getItem(22), "second page must still render its dungeon");
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
        when(event.getRawSlot()).thenReturn(13);
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
        when(event.getRawSlot()).thenReturn(39);
        when(event.isLeftClick()).thenReturn(true);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
        when(event.getAction()).thenReturn(org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        framework.onClick(event);
        assertFalse(tasks.isEmpty(), "the control click must queue its permission recheck");
        when(player.hasPermission("customdungeons.admin.test")).thenReturn(false);
        drain();
        verify(manager,never()).startTest(any(),anyString());
    }

    void clickSlot(int slot) {
        var event=mock(org.bukkit.event.inventory.InventoryClickEvent.class);
        when(event.getView()).thenReturn(view); when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot); when(event.isLeftClick()).thenReturn(true);
        when(event.getClick()).thenReturn(org.bukkit.event.inventory.ClickType.LEFT);
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
        list.open();clickSlot(22);
        var menu=assertInstanceOf(DungeonMenu.class,top.getHolder());
        assertNull(menu.draft(),"control view must not create a draft");
        assertTrue(locks.holder("one").isEmpty());
        assertNull(menu.onSave());
        for(int slot:new int[]{10,12,14,16,21,23,28,29,33,34})
            assertEquals(Material.GRAY_CONCRETE,top.getItem(slot).getType());
        clickSlot(10);assertSame(menu,top.getHolder());
        clickSlot(41);verify(manager).stop("one");
        verify(plugin.messages()).send(player,"command.stop");
        assertTrue(locks.holder("one").isEmpty());
        verify(store,never()).save(any(DungeonDef.class));
    }
    @Test void listOpensLobbyAndStartReportsOnlyRunningResult() {
        var manager=controlManager();var session=activeSession(false);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(22);assertInstanceOf(DungeonMenu.class,top.getHolder());
        clickSlot(37);verify(manager).forceStart("one");
        verify(plugin.messages(),never()).send(player,"command.start");
        verify(plugin.messages()).send(player,"gui.dungeon.control-start-rejected");
        doAnswer(call->{session.state().start();return null;}).when(manager).forceStart("one");
        clickSlot(37);verify(plugin.messages()).send(player,"command.start");
    }
    @Test void testRejectsMissingWorldWithoutAnnouncingSuccess() throws Exception {
        var manager=controlManager();bukkit.when(()->Bukkit.getWorld("world")).thenReturn(null);
        var menu=remember(definitions.get("one"));menu.open();clickSlot(39);
        verify(manager).startTest(player,"one");
        verify(plugin.messages(),never()).send(player,"command.test-started");
        verify(plugin.messages()).send(player,"gui.dungeon.control-world-unavailable");
    }
    @Test void stopWithoutStateChangeReportsRejection() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(22);clickSlot(41);
        verify(manager).stop("one");verify(plugin.messages(),never()).send(player,"command.stop");
        verify(plugin.messages()).send(player,"gui.dungeon.control-stop-rejected");
    }
    @Test void startDuringChunkPreparationReportsPendingInsteadOfFalseRejection() {
        var manager=controlManager();var session=activeSession(false);
        when(session.players()).thenReturn(List.of(player));
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(22);clickSlot(37);
        verify(plugin.messages()).send(player,"gui.dungeon.control-preparing");
        verify(plugin.messages(),never()).send(player,"command.start");
        verify(plugin.messages(),never()).send(player,"gui.dungeon.control-start-rejected");
    }
    @Test void testCreatedInLobbyReportsPreparationInsteadOfSuccess() throws Exception {
        var manager=controlManager();var session=activeSession(false);when(session.testMode()).thenReturn(true);
        doAnswer(call->{when(manager.sessionOf(player.getUniqueId())).thenReturn(Optional.of(session));return null;})
                .when(manager).startTest(player,"one");
        var menu=remember(definitions.get("one"));menu.open();clickSlot(39);
        verify(plugin.messages()).send(player,"gui.dungeon.control-preparing");
        verify(plugin.messages(),never()).send(player,"command.test-started");
    }
    @Test void controlsRespectAnotherEditorsLockAndDoNotPersistDefinition() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        UUID other=UUID.randomUUID();assertTrue(locks.tryLock("one",other));
        list.open();clickSlot(22);var menu=assertInstanceOf(DungeonMenu.class,top.getHolder());
        assertNull(menu.draft());menu.saveDraft();assertEquals(Optional.of(other),locks.holder("one"));
        verify(store,never()).save(any(DungeonDef.class));
    }
    @Test void resetHasOwnButtonAndVerifiesCleanup() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        doAnswer(call->{session.state().fail();session.state().beginReset();session.state().finishReset();return null;})
                .when(manager).reset("one");
        list.open();clickSlot(22);assertNotNull(top.getItem(43));clickSlot(43);
        verify(manager).reset("one");verify(plugin.messages()).send(player,"command.reset");
    }
    @Test void controlExceptionReportsFailureInsteadOfSuccess() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        doThrow(new IllegalStateException("failure")).when(manager).stop("one");
        list.open();clickSlot(22);assertDoesNotThrow(()->clickSlot(41));
        verify(plugin.messages()).send(player,"gui.dungeon.control-failed");
        verify(plugin.messages(),never()).send(player,"command.stop");
    }
    @Test void testSuccessRequiresAdminInRunningTestSession() throws Exception {
        var manager=controlManager();var session=activeSession(true);when(session.testMode()).thenReturn(true);
        doAnswer(call->{when(manager.sessionOf(player.getUniqueId())).thenReturn(Optional.of(session));return null;})
                .when(manager).startTest(player,"one");
        var menu=remember(definitions.get("one"));menu.open();clickSlot(39);
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
        assertEquals(Material.YELLOW_DYE,top.getItem(31).getType());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW,name.get().color());
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        String text=plain.serialize(lore.get().getFirst());
        assertTrue(text.contains("Coloso")); assertFalse(text.contains("&a"));
        assertTrue(text.contains("11.60")); assertTrue(text.contains("2.00"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW,lore.get().getFirst().color());
        root.change(v->v.name="Edited"); root.refresh();
        assertTrue(top.getItem(31)==null || top.getItem(31).getType()!=Material.YELLOW_DYE);
        root.change(v->v.lobby=null); root.saveDraft(); drain();
        verify(store,times(1)).save(any(DungeonDef.class));
        assertEquals(Material.RED_DYE,top.getItem(31).getType());
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
    @Test void scaleEditorUsesZeroToTenWithTwoDecimalInput() {
        var template=new MobTemplate("mob","WARDEN","",0,0,0,0,7.06,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
        var draft=new MobMenu.MobDraft(template);
        var menu=new StatsMenu(player,draft,list); menu.open(); clickSlot(15);
        inputs.verify(()->Inputs.decimal(eq(player),any(Component.class),eq(0d),eq(10d),eq(7.06),eq(2),any(java.util.function.DoubleConsumer.class)));
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
}
