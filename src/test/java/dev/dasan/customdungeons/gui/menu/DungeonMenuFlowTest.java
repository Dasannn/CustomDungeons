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
        buttons.close(); theme.close(); inputs.close(); menuServices.close(); javaPlugin.close(); bukkit.close();
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

    @Test void carrierPickerOffersLastMobAndConcreteTemplateWithoutChangingOtherRoomFields() throws Exception {
        var root = remember(definition("keys"));
        var template = mock(MobTemplate.class);
        when(template.id()).thenReturn("mob"); when(template.entityType()).thenReturn("minecraft:zombie");
        when(template.displayName()).thenReturn("Zombie"); when(store.mobs()).thenReturn(Map.of("mob",template));
        var menu = new RoomMenu(root,0,root); menu.open();
        var lookup = Menu.class.getDeclaredMethod("buttonAt",int.class); lookup.setAccessible(true);
        ((Button)lookup.invoke(menu,8)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        var picker = assertInstanceOf(Menu.class,top.getHolder());
        ((Button)lookup.invoke(picker,10)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        var original = definition("keys").rooms().getFirst(); var selected = root.draft.get().rooms().getFirst();
        assertEquals(new RoomDef(original.id(),original.region(),original.checkpoint(),original.door(),original.unlock(),"*",original.spawners()),selected);
        ((Button)lookup.invoke(menu,8)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        picker = assertInstanceOf(Menu.class,top.getHolder());
        ((Button)lookup.invoke(picker,11)).onClick().handle(player,org.bukkit.event.inventory.ClickType.LEFT); drain();
        assertEquals("mob",root.draft.get().rooms().getFirst().keyCarrierTemplateId());
    }
    @Test void rootHasMobLibraryButtonAndClickOpensLibraryOnNextTick() {
        when(store.mobs()).thenReturn(Map.of());
        var services = plugin.getServer().getServicesManager();
        bukkit.when(Bukkit::getServicesManager).thenReturn(services);
        list.open();
        assertNotNull(top.getItem(6), "public root must expose the mob library even with no dungeons");
        assertEquals(Material.BOOK, top.getItem(6).getType());
        clickRootLibrary();
        assertSame(list, top.getHolder(), "inventory changes must wait until after the click event");
        drain();
        var library = assertInstanceOf(MobLibraryMenu.class, top.getHolder());
        assertSame(list, library.parent());
    }
    @Test void mobLibraryButtonRemainsVisibleAcrossDungeonPages() {
        for (int i = 0; i < 29; i++) definitions.put("d" + i, definition("d" + i));
        list.open();
        assertEquals(Material.BOOK, top.getItem(6).getType());
        list.nextPage();
        assertEquals(Material.BOOK, top.getItem(6).getType());
        assertNotNull(top.getItem(10), "second page must still render its dungeon");
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
        when(event.getRawSlot()).thenReturn(6);
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
        list.open();clickSlot(10);
        var menu=assertInstanceOf(DungeonMenu.class,top.getHolder());
        assertNull(menu.draft(),"control view must not create a draft");
        assertTrue(locks.holder("one").isEmpty());
        assertNull(menu.onSave());
        for(int slot:new int[]{10,12,14,16,20,22,28,29,33,34})
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
        list.open();clickSlot(10);assertInstanceOf(DungeonMenu.class,top.getHolder());
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
        list.open();clickSlot(10);clickSlot(41);
        verify(manager).stop("one");verify(plugin.messages(),never()).send(player,"command.stop");
        verify(plugin.messages()).send(player,"gui.dungeon.control-stop-rejected");
    }
    @Test void startDuringChunkPreparationReportsPendingInsteadOfFalseRejection() {
        var manager=controlManager();var session=activeSession(false);
        when(session.players()).thenReturn(List.of(player));
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        list.open();clickSlot(10);clickSlot(37);
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
        list.open();clickSlot(10);var menu=assertInstanceOf(DungeonMenu.class,top.getHolder());
        assertNull(menu.draft());menu.saveDraft();assertEquals(Optional.of(other),locks.holder("one"));
        verify(store,never()).save(any(DungeonDef.class));
    }
    @Test void resetHasOwnButtonAndVerifiesCleanup() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        doAnswer(call->{session.state().fail();session.state().beginReset();session.state().finishReset();return null;})
                .when(manager).reset("one");
        list.open();clickSlot(10);assertNotNull(top.getItem(43));clickSlot(43);
        verify(manager).reset("one");verify(plugin.messages()).send(player,"command.reset");
    }
    @Test void controlExceptionReportsFailureInsteadOfSuccess() {
        var manager=controlManager();var session=activeSession(true);
        when(manager.session("one")).thenReturn(Optional.of(session));DungeonListMenu.dungeonBusy(id->true);
        doThrow(new IllegalStateException("failure")).when(manager).stop("one");
        list.open();clickSlot(10);assertDoesNotThrow(()->clickSlot(41));
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
        reward.getInventory().setItem(10,deposited); reward.capture(); reward.capture();
        verify(player.getInventory(),times(1)).addItem(deposited);
        assertNull(reward.getInventory().getItem(10));
        assertSame(snapshot,root.draft.get().reward().items().getFirst());
        assertSame(meta,root.draft.get().reward().items().getFirst().getItemMeta());
        assertEquals(3,root.draft.get().reward().items().getFirst().getAmount());
        assertSame(type,root.draft.get().reward().items().getFirst().getType());
        assertFalse(reward.allowsPlacement(10));
    }
    @Test void rewardsAreReturnedWithoutUpdatingDraftAfterPermissionLoss() throws Exception {
        DungeonMenu root = remember(definition("new")); RewardMenu reward = new RewardMenu(root);
        ItemStack deposited = item(mock(Material.class)); reward.getInventory().setItem(10,deposited);
        when(player.hasPermission("customdungeons.admin.edit")).thenReturn(false);
        reward.capture(); reward.capture();
        verify(player.getInventory(),times(1)).addItem(deposited);
        assertNull(reward.getInventory().getItem(10)); assertTrue(root.draft.get().reward().items().isEmpty());
    }
    @Test void fullPlayerInventoryDropsOnlyReturnedOverflowWithMetadata() throws Exception {
        DungeonMenu root = remember(definition("new")); RewardMenu reward = new RewardMenu(root);
        ItemStack deposited = item(mock(Material.class)); ItemStack overflow = item(mock(Material.class));
        when(player.getInventory().addItem(deposited)).thenReturn(new HashMap<>(Map.of(0,overflow)));
        var world = mock(org.bukkit.World.class); var location = new org.bukkit.Location(world,1,2,3);
        when(player.getWorld()).thenReturn(world); when(player.getLocation()).thenReturn(location);
        reward.getInventory().setItem(10,deposited); reward.capture(); reward.capture();
        verify(world,times(1)).dropItem(location,overflow);
        assertSame(deposited,root.draft.get().reward().items().getFirst());
    }
}
