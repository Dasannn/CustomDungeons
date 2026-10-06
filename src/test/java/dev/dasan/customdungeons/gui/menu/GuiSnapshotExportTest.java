package dev.dasan.customdungeons.gui.menu;

import com.google.gson.GsonBuilder;
import dev.dasan.customdungeons.CustomDungeonsPlugin;
import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.ability.impl.PaperApiTestBootstrap;
import dev.dasan.customdungeons.config.*;
import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.gui.snapshot.SnapshotText;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.text.Messages;
import dev.dasan.customdungeons.tool.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Optional task only. Real render methods, messages, definitions and ability specifications. */
class GuiSnapshotExportTest {
    private final Map<Inventory, Component> titles = new IdentityHashMap<>();
    private final Map<ItemStack, Boolean> actions = new IdentityHashMap<>();
    private final Map<ItemStack, Map<org.bukkit.enchantments.Enchantment, Integer>> enchantments = new IdentityHashMap<>();
    
    private final Path output = Path.of(System.getProperty("guiSnapshots.output", "build/gui-snapshots"));
    private final Messages messages = new Messages();
    private final Set<String> exported = new TreeSet<>();

    @Test void exportAllMenus() throws Exception {
        PaperApiTestBootstrap.initialize();
        var access = mock(io.papermc.paper.registry.RegistryAccess.class);
        when(access.getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)).thenReturn(Registry.ENCHANTMENT);
        try (var registryApi = mockStatic(io.papermc.paper.registry.RegistryAccess.class)) {
            registryApi.when(io.papermc.paper.registry.RegistryAccess::registryAccess).thenReturn(access);
            Class.forName("org.bukkit.enchantments.Enchantment");
            exportMenus();
        }
    }

    private void exportMenus() throws Exception {
        // Seed the public API test registries used by the selector menus.
        for (var field : org.bukkit.enchantments.Enchantment.class.getFields()) {
            if (field.getType() == org.bukkit.enchantments.Enchantment.class) {
                var enchant = (org.bukkit.enchantments.Enchantment) field.get(null);
                when(enchant.getKey()).thenReturn(NamespacedKey.minecraft(field.getName().toLowerCase(Locale.ROOT)));
            }
        }
        for (String key : List.of("sharpness", "unbreaking", "protection", "power", "fire_aspect", "mending")) {
            var enchant = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
            when(enchant.getKey()).thenReturn(NamespacedKey.minecraft(key));
        }
        for (String key : List.of("speed", "strength", "slowness", "poison", "wither", "blindness", "regeneration"))
            Registry.EFFECT.get(NamespacedKey.minecraft(key));
        var registry = new AbilityRegistry();
        Abilities.registerDefaults(registry);
        messages.load(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile()), "");
        var codec = new DefinitionCodec();
        var demo = codec.decodeDungeon("demo", fixture("dungeons/demo.yml"));
        var mobs = new TreeMap<String, MobTemplate>();
        try (var files = Files.list(Path.of("docs/reference/ejemplos/demo/mobs"))) {
            for (var file : files.sorted().toList()) {
                var id = file.getFileName().toString().replace(".yml", "");
                mobs.put(id, codec.decodeMob(id, fixture("mobs/" + file.getFileName())));
            }
        }
        var store = mock(DefinitionStore.class);
        when(store.dungeons()).thenReturn(Map.of("demo", demo));
        when(store.mobs()).thenReturn(mobs);
        var config = new ConfigLoader().load(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile()));
        var services = mock(org.bukkit.plugin.ServicesManager.class);
        when(services.load(DefinitionStore.class)).thenReturn(store);
        when(services.load(PluginConfig.class)).thenReturn(config);
        when(services.load(dev.dasan.customdungeons.config.EntityHeights.class)).thenReturn(ConfigLoader.defaultEntityHeights());
        when(services.load(ToolService.class)).thenReturn(mock(ToolService.class));
        when(services.load(SpawnerMarkers.class)).thenReturn(mock(SpawnerMarkers.class));
        var plugin = mock(CustomDungeonsPlugin.class, RETURNS_DEEP_STUBS);
        when(plugin.getServer().getServicesManager()).thenReturn(services);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.abilityRegistry()).thenReturn(registry);
        var scheduler = plugin.getServer().getScheduler();
        doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        var player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000033"));
        when(player.isOnline()).thenReturn(true);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        var view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        var listener = new MenuListener(plugin, messages, config.guiSounds(), new EditLocks());
        try (var bukkit = mockStatic(Bukkit.class);
             var plugins = mockStatic(JavaPlugin.class);
             var framework = mockStatic(MenuListener.class);
             var buttons = mockStatic(Button.class)) {
            bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenAnswer(call -> inventory(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            plugins.when(() -> JavaPlugin.getPlugin(CustomDungeonsPlugin.class)).thenReturn(plugin);
            framework.when(MenuListener::instance).thenReturn(listener);
            buttons.when(() -> Button.of(any(), any(), anyList(), any())).thenAnswer(call -> {
                Material material = call.getArgument(0);
                Component name = call.getArgument(1);
                List<Component> lore = call.getArgument(2);
                ItemStack icon = item(material, 1, name, lore);
                actions.put(icon, actionAtCreation(material, lore));
                return new Button(icon, call.getArgument(3));
            });
            doAnswer(call -> { when(view.getTopInventory()).thenReturn(call.getArgument(0)); return view; })
                    .when(player).openInventory(any(Inventory.class));
            Files.createDirectories(output);
            // Clean only this task's generated artifacts so renamed menus never leave stale previews.
            try (var files = Files.list(output)) {
                for (var file : files.filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".png")).toList()) Files.delete(file);
            }
            var list = new DungeonListMenu(player);
            snapshot("dungeons", list);
            var root = new DungeonMenu(player, demo, list);
            snapshot("dungeon-demo", root);
            var oversized=new TreeMap<String,MobTemplate>();
            for(var mob:mobs.values()) {
                var draft=new MobMenu.MobDraft(mob); draft.type="WARDEN"; draft.scale=10;
                oversized.put(mob.id(),draft.snapshot());
            }
            var heightRoot=new DungeonMenu(player,demo,list);
            var warningField=DungeonMenu.class.getDeclaredField("warnings"); warningField.setAccessible(true);
            warningField.set(heightRoot,new dev.dasan.customdungeons.config.Validator().warnings(demo,oversized));
            snapshot("dungeon-height-warning",heightRoot);
            snapshot("dungeon-settings", new DungeonSettingsMenu(root));
            snapshot("scaling", new ScalingMenu(root));
            snapshot("hooks", new HooksMenu(root));
            snapshot("commands", new CommandList(root, root, () -> demo.hooks().get(HookEvent.START), v -> {}));
            snapshot("reward", new RewardMenu(root));
            snapshot("rooms", new RoomListMenu(root));
            for (int r = 0; r < demo.rooms().size(); r++) {
                var room = new RoomMenu(root, r, root);
                snapshot("room-complete-" + r, room);
                snapshot("room-spawners-" + r, new RoomSpawnerList(root, r, room));
                for (int s = 0; s < demo.rooms().get(r).spawners().size(); s++) {
                    String prefix = "room-" + r + "-spawner-" + s;
                    snapshot(prefix, new SpawnerMenu(root, r, s, room));
                    snapshot(prefix + "-waves", new WaveListMenu(root, r, s, room));
                    for (int w = 0; w < demo.rooms().get(r).spawners().get(s).waves().size(); w++) {
                        var wave = new WaveMenu(root, r, s, w, room);
                        snapshot(prefix + "-wave-" + w, wave);
                        for (int e = 0; e < demo.rooms().get(r).spawners().get(s).waves().get(w).entries().size(); e++)
                            snapshot(prefix + "-wave-" + w + "-entry-" + e, new WaveEntryMenu(root, r, s, w, e, wave));
                    }
                }
            }
            snapshot("templates", new TemplatePickerMenu(root, root, v -> {}));
            // The private carrier selector is reached through the real navigation button.
            captureClick("key-carrier", new RoomMenu(root, 1, root), 42, view);
            var empty = new DungeonMenu.Values(demo); empty.rooms = List.of();
            var emptyRoot = new DungeonMenu(player, empty.build(), list);
            snapshot("dungeon-empty", emptyRoot); snapshot("rooms-empty", new RoomListMenu(emptyRoot));
            var incomplete = new DungeonMenu(player, demo, list);
            incomplete.room(0, r -> new RoomDef(r.id(), null, null, null, r.unlock(), r.keyCarrierTemplateId(), r.spawners()));
            snapshot("room-no-region", new RoomMenu(incomplete, 0, incomplete));
            snapshot("mob-library", new MobLibraryMenu(player, list));
            for (var mob : mobs.values()) {
                var draft = new MobMenu.MobDraft(mob);
                var menu = new MobMenu(player, draft, list);
                String prefix = mob.id();
                snapshot(prefix, menu);
                snapshot(prefix + "-entity-types", new EntityTypePickerMenu(player, draft, menu));
                snapshot(prefix + "-stats", new StatsMenu(player, draft, menu));
                snapshot(prefix + "-equipment", new EquipmentMenu(player, draft, draft, menu));
                snapshot(prefix + "-enchants", new EnchantMenu(player, draft, draft, EquipmentSlot.HAND, menu));
                snapshot(prefix + "-potions", new PotionMenu(player, draft, draft, menu));
                snapshot(prefix + "-abilities", new AbilityListMenu(player, draft, draft, menu));
                snapshot(prefix + "-combos", ComboMenu.list(player, draft, draft, menu));
                for (int c = 0; c < mob.combos().size(); c++) snapshot(prefix + "-combo-" + c, new ComboMenu(player, draft, draft, c, menu));
                snapshot(prefix + "-phases", new PhaseListMenu(player, draft, menu));
                for (int p = 0; p < draft.phases.size(); p++) snapshot(prefix + "-phase-" + p, new PhaseMenu(player, draft, draft.phases.get(p), menu));
                for (var ability : mob.abilities()) snapshot(prefix + "-params-" + ability.abilityId(), new ParamEditorMenu(player, draft, ability, menu, v -> {}));
            }
            var boss = new MobMenu.MobDraft(mobs.get("demo-boss"));
            var parent = new MobMenu(player, boss, list);
            var scalePreview=new MobMenu.MobDraft(mobs.get("demo-boss"));
            scalePreview.scale=0; snapshot("stats-scale-zero",new StatsMenu(player,scalePreview,parent));
            scalePreview.scale=10; snapshot("stats-scale-ten",new StatsMenu(player,scalePreview,parent));
            snapshot("ability-picker", new AbilityPickerMenu(player, parent, a -> {}));
            boss.potions.add(new PotionDef("minecraft:strength", 0, true));
            snapshot("potions-populated", new PotionMenu(player, boss, boss, parent));
            captureClick("potion-editor", new PotionMenu(player, boss, boss, parent), 13, view);
            snapshot("dungeon-control-only", new DungeonMenu(player, demo, list, true));
            try (var live = mockStatic(dev.dasan.customdungeons.mob.LiveTestService.class)) {
                live.when(() -> dev.dasan.customdungeons.mob.LiveTestService.active(player)).thenReturn(true);
                snapshot("mob-live-test-active", new MobMenu(player, boss, list));
            }
            for (var ability : registry.all()) snapshot("ability-params-" + ability.id(),
                    new ParamEditorMenu(player, boss, MobMenuBase.defaults(ability), parent, v -> {}));
            // Generic selectors generated at runtime, also rendered through the actual Menu implementation.
            for (String key : List.of("trigger", "target", "particle", "sound", "potions", "template", "bar-color")) {
                var choices = switch (key) {
                    case "trigger" -> Arrays.stream(Trigger.values()).map(Enum::name).toList();
                    case "target" -> Arrays.stream(TargetMode.values()).map(Enum::name).toList();
                    case "particle" -> Arrays.stream(Particle.values()).map(Enum::name).toList();
                    case "sound" -> MobMenuBase.soundKeys();
                    case "potions" -> MobMenuBase.potionKeys();
                    case "template" -> mobs.keySet().stream().toList();
                    default -> Arrays.stream(net.kyori.adventure.bossbar.BossBar.Color.values()).map(Enum::name).toList();
                };
                MobMenuBase.choose(player, key, choices, parent, v -> {});
                snapshot("selector-" + key, (Menu) view.getTopInventory().getHolder());
            }
            assertTrue(exported.containsAll(List.of("dungeon-empty", "room-no-region", "ability-picker", "demo-boss-phase-0")));
            System.out.println("GUI snapshots: " + exported.size() + " JSON en " + output.toAbsolutePath());
        }
    }

    private void captureClick(String name, Menu menu, int slot, InventoryView view) throws Exception {
        menu.refresh();
        var lookup = Menu.class.getDeclaredMethod("buttonAt", int.class); lookup.setAccessible(true);
        ((Button) lookup.invoke(menu, slot)).onClick().handle(viewer(menu), org.bukkit.event.inventory.ClickType.LEFT);
        // The scheduler mock runs only this navigation callback; game actions are never invoked.
        snapshot(name, (Menu) view.getTopInventory().getHolder());
    }
    private Player viewer(Menu menu) throws Exception {
        var field = Menu.class.getDeclaredField("viewer"); field.setAccessible(true); return (Player) field.get(menu);
    }

    private YamlConfiguration fixture(String relative) throws Exception {
        var yaml = new YamlConfiguration();
        Map<String, Object> raw = new org.yaml.snakeyaml.Yaml().load(Files.readString(Path.of("docs/reference/ejemplos/demo", relative)));
        raw.forEach((k, v) -> yaml.set(k, fixtureValue(v)));
        // ItemStack decoding normally requires a running Paper item factory. Replace only vanilla
        // item YAML nodes with metadata-capable mocks; every other field uses the real codec.
        
        return yaml;
    }
    private Object fixtureValue(Object value) {
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) return fixtureValue(section.getValues(false));
        if (value instanceof List<?> list) return list.stream().map(this::fixtureValue).toList();
        if (value instanceof Map<?, ?> map) {
            if ("org.bukkit.inventory.ItemStack".equals(map.get("=="))) {
                var icon = item(Material.valueOf(map.get("type").toString()), map.get("amount") instanceof Number n ? n.intValue() : 1, null, List.of());
                if (map.get("meta") instanceof Map<?, ?> meta && meta.get("enchants") instanceof Map<?, ?> enchants) {
                    enchants.forEach((key, level) -> enchantments.get(icon).put(
                            Registry.ENCHANTMENT.get(Objects.requireNonNull(NamespacedKey.fromString(key.toString()))), ((Number) level).intValue()));
                }
                return icon;
            }
            var copy = new LinkedHashMap<String, Object>(); map.forEach((k, v) -> copy.put(k.toString(), fixtureValue(v))); return copy;
        }
        return value;
    }
    private ItemStack item(Material material, int amount, Component name, List<Component> lore) {
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        var enchants = new HashMap<org.bukkit.enchantments.Enchantment, Integer>(); enchantments.put(item, enchants);
        when(item.getEnchantmentLevel(any())).thenAnswer(c -> enchants.getOrDefault(c.getArgument(0), 0));
        when(item.getEnchantments()).thenAnswer(c -> Map.copyOf(enchants));
        Component[] title = {name};
        var lines = new ArrayList<>(lore);
        when(item.getType()).thenReturn(material); when(item.getAmount()).thenReturn(amount);
        when(item.getItemMeta()).thenReturn(meta); when(item.hasItemMeta()).thenReturn(true);
        when(meta.displayName()).thenAnswer(c -> title[0]);
        doAnswer(c -> { title[0] = c.getArgument(0); return null; }).when(meta).displayName(any());
        when(meta.lore()).thenAnswer(c -> List.copyOf(lines));
        doAnswer(c -> { lines.clear(); if(c.getArgument(0) != null) lines.addAll(c.getArgument(0)); return null; }).when(meta).lore(any());
        doAnswer(c -> { ((Consumer<ItemMeta>) c.getArgument(0)).accept(meta); return true; }).when(item).editMeta(any());
        when(item.clone()).thenAnswer(c -> {
            var clone = item(material, amount, title[0], lines); if(actions.containsKey(item)) actions.put(clone, actions.get(item)); enchantments.get(clone).putAll(enchants); return clone;
        });
        return item;
    }
    private Inventory inventory(InventoryHolder holder, int size, Component title) {
        var inv = mock(Inventory.class); var slots = new ItemStack[size];
        when(inv.getHolder()).thenReturn(holder); when(inv.getSize()).thenReturn(size);
        when(inv.getItem(anyInt())).thenAnswer(c -> slots[(int) c.getArgument(0)]);
        doAnswer(c -> { slots[(int) c.getArgument(0)] = c.getArgument(1); return null; }).when(inv).setItem(anyInt(), any());
        doAnswer(c -> { Arrays.fill(slots, null); return null; }).when(inv).clear();
        titles.put(inv, title); return inv;
    }
    private boolean actionAtCreation(Material material, List<Component> lore) throws Exception {
        if (lore.stream().anyMatch(c -> SnapshotText.plain(c).equals(SnapshotText.plain(messages.get("gui.common.unavailable"))))) return false;
        // Button has no informational flag. Inspect the creation expression for its explicit
        // empty handler (never execute callbacks just to guess whether they change game state).
        for (var frame : Thread.currentThread().getStackTrace()) {
            if (!frame.getClassName().startsWith("dev.dasan.customdungeons.gui.") || frame.getClassName().contains("GuiSnapshotExportTest") || frame.getLineNumber() < 1) continue;
            var path = Path.of("src/main/java/dev/dasan/customdungeons/gui", frame.getClassName().contains(".menu.") ? "menu" : "", frame.getFileName());
            if (!Files.exists(path)) continue;
            var lines = Files.readAllLines(path);
            var expression = String.join("\n", lines.subList(frame.getLineNumber() - 1, lines.size()));
            if (!expression.contains("Button.of(")) continue;
            return !dev.dasan.customdungeons.gui.snapshot.SnapshotButtons.noopCreation(expression);
        }
        throw new IllegalStateException("Unknown button creation site for " + material);
    }
    private void snapshot(String name, Menu menu) throws Exception {
        int page = 0;
        while (true) {
            menu.refresh();
            String id = page == 0 ? name : name + "-page-" + page;
            var inventory = menu.getInventory(); var title = titles.get(inventory);
            var slots = new ArrayList<Map<String, Object>>();
            var lookup = Menu.class.getDeclaredMethod("buttonAt", int.class); lookup.setAccessible(true);
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                var icon = inventory.getItem(slot); var row = new LinkedHashMap<String, Object>(); row.put("slot", slot);
                row.put("material", icon == null ? "AIR" : icon.getType().name());
                var meta = icon == null ? null : icon.getItemMeta();
                var label = meta == null || meta.displayName() == null ? Component.empty() : meta.displayName();
                row.put("name", SnapshotText.plain(label)); row.put("color", SnapshotText.color(label));
                row.put("lore", meta == null || meta.lore() == null ? List.of() : meta.lore().stream().map(SnapshotText::plain).toList());
                row.put("action", icon != null && lookup.invoke(menu, slot) != null && actions.getOrDefault(icon, true));
                row.put("amount", icon == null ? 0 : icon.getAmount()); slots.add(row);
            }
            var data = new LinkedHashMap<String, Object>(); data.put("menu", menu.getClass().getName());
            data.put("title", SnapshotText.plain(title)); data.put("color", SnapshotText.color(title));
            data.put("rows", inventory.getSize() / 9); data.put("slots", slots);
            Files.writeString(output.resolve(id + ".json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data) + "\n");
            assertTrue(exported.add(id), "Duplicate snapshot " + id);
            Class<?> next = menu.getClass(); java.lang.reflect.Method hasNext = null;
            while(next != null && hasNext == null) { try { hasNext = next.getDeclaredMethod("hasNextPage"); } catch(NoSuchMethodException e) { next = next.getSuperclass(); } }
            hasNext.setAccessible(true); if (!(boolean) hasNext.invoke(menu)) break;
            var navigate = next.getDeclaredMethod("nextPage"); navigate.setAccessible(true); navigate.invoke(menu); page++;
            assertTrue(page < 100, "Pagination must terminate");
        }
    }
}
