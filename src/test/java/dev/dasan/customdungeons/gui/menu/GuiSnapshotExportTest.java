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
    private final Map<Inventory, Component> titles = new WeakHashMap<>();
    private final Map<ItemStack, Boolean> actions = new WeakHashMap<>();
    private final Map<ItemStack, Map<org.bukkit.enchantments.Enchantment, Integer>> enchantments = new WeakHashMap<>();
    
    private org.mockito.MockedStatic<Button> snapshotButtons;
    private org.mockito.MockedStatic<Bukkit> snapshotBukkit;
    private Player snapshotPlayer;
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
        when(plugin.getConfig()).thenReturn(YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile()));
        when(plugin.abilityRegistry()).thenReturn(registry);
        var scheduler = plugin.getServer().getScheduler();
        doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        var player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000033"));
        when(player.isOnline()).thenReturn(true);
        var world=mock(World.class);when(world.getName()).thenReturn("dungeons");
        when(player.getLocation()).thenReturn(new Location(world,0,64,0));
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        var view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        var listener = new MenuListener(plugin, messages, config.guiSounds(), new EditLocks());
        try (var bukkit = mockStatic(Bukkit.class);
             var plugins = mockStatic(JavaPlugin.class);
             var framework = mockStatic(MenuListener.class);
             var buttons = mockStatic(Button.class)) {
            snapshotButtons = buttons; snapshotBukkit = bukkit; snapshotPlayer = player;
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
            exportSpawnerPresets(player,list,store,demo,mobs);
            when(store.dungeons()).thenReturn(Map.of("demo",demo));when(store.mobs()).thenReturn(mobs);when(store.spawnerPresets()).thenReturn(Map.of());
            snapshot("main",list);
            snapshot("dungeons",new DungeonListMenu(player,true,list));
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
            var legacyFinal=new DungeonMenu(player,demo,list);
            int finalIndex=demo.rooms().size()-1;
            legacyFinal.room(finalIndex,r->new RoomDef(r.id(),r.region(),r.checkpoint(),null,UnlockMode.KEY,"missing",r.spawners()));
            snapshot("room-final-legacy-key",new RoomMenu(legacyFinal,finalIndex,legacyFinal));
            var emptySpawner=new DungeonMenu(player,demo,list);
            emptySpawner.spawner(0,0,s->new SpawnerDef(s.id(),null,s.radius(),List.of()));
            snapshot("spawner-empty",new SpawnerMenu(emptySpawner,0,0,emptySpawner));
            var manyWaves=new DungeonMenu(player,demo,list);
            var sampleWave=demo.rooms().getFirst().spawners().getFirst().waves().getFirst();
            manyWaves.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),2.5,List.of(sampleWave,sampleWave,sampleWave)));
            snapshot("spawner-three-waves",new SpawnerMenu(manyWaves,0,0,manyWaves));
            manyWaves.spawner(0,0,s->new SpawnerDef(s.id(),s.location(),2.5,List.of(sampleWave,sampleWave,sampleWave,sampleWave)));
            snapshot("spawner-four-waves",new SpawnerMenu(manyWaves,0,0,manyWaves));
            snapshot("waves-empty",new WaveListMenu(emptySpawner,0,0,emptySpawner));
            var noSpawners=new DungeonMenu(player,demo,list);
            noSpawners.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),r.unlock(),r.keyCarrierTemplateId(),List.of()));
            snapshot("spawners-empty",new RoomSpawnerList(noSpawners,0,new RoomMenu(noSpawners,0,noSpawners)));
            noSpawners.room(0,r->new RoomDef(r.id(),r.region(),r.checkpoint(),r.door(),UnlockMode.KEY,"*",r.spawners()));
            captureClick("key-carrier-empty",new RoomMenu(noSpawners,0,noSpawners),34,view);
            var emptyWave=new DungeonMenu(player,demo,list);
            emptyWave.wave(0,0,0,w->new WaveDef(List.of(),SpawnMode.STAGGERED,20,0));
            snapshot("wave-empty",new WaveMenu(emptyWave,0,0,0,emptyWave));
            var invalidRoot=new DungeonMenu(player,demo,list);
            invalidRoot.change(v->v.lobby=null);
            var errorsField=DungeonMenu.class.getDeclaredField("errors");errorsField.setAccessible(true);
            errorsField.set(invalidRoot,new Validator().validate(invalidRoot.draft.get(),mobs));
            snapshot("dungeon-error",invalidRoot);
            snapshot("templates", new TemplatePickerMenu(root, root, v -> {}));
            // The private carrier selector is reached through the real navigation button.
            captureClick("key-carrier", new RoomMenu(root, 1, root), 34, view);
            var empty = new DungeonMenu.Values(demo); empty.rooms = List.of();
            var emptyRoot = new DungeonMenu(player, empty.build(), list);
            snapshot("dungeon-empty", emptyRoot); snapshot("rooms-empty", new RoomListMenu(emptyRoot));
            var incomplete = new DungeonMenu(player, demo, list);
            incomplete.room(0, r -> new RoomDef(r.id(), null, null, null, r.unlock(), r.keyCarrierTemplateId(), r.spawners()));
            snapshot("room-no-region", new RoomMenu(incomplete, 0, incomplete));
            snapshot("rooms-incomplete",new RoomListMenu(incomplete));
            snapshot("mob-library", new MobLibraryMenu(player, list));
            var originalDungeons=store.dungeons();var originalMobs=store.mobs();
            when(store.dungeons()).thenReturn(Map.of());when(store.mobs()).thenReturn(Map.of());
            snapshot("main-empty",new DungeonListMenu(player));snapshot("dungeons-empty",new DungeonListMenu(player,true,list));
            snapshot("mob-library-empty",new MobLibraryMenu(player,list));
            when(store.dungeons()).thenReturn(originalDungeons);when(store.mobs()).thenReturn(originalMobs);
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
            var emptyEquipment=new MobMenu.MobDraft(mobs.get("demo-zombie"));emptyEquipment.equipment.clear();
            snapshot("equipment-empty",new EquipmentMenu(player,emptyEquipment,emptyEquipment,list));
            var boss = new MobMenu.MobDraft(mobs.get("demo-boss"));
            var parent = new MobMenu(player, boss, list);
            var scalePreview=new MobMenu.MobDraft(mobs.get("demo-boss"));
            scalePreview.scale=0; snapshot("stats-scale-zero",new StatsMenu(player,scalePreview,parent));
            scalePreview.scale=10; snapshot("stats-scale-ten",new StatsMenu(player,scalePreview,parent));
            snapshot("ability-picker", new AbilityPickerMenu(player, parent, a -> {}));
            boss.potions.add(new PotionDef("minecraft:strength", 0, true));
            snapshot("potions-populated", new PotionMenu(player, boss, boss, parent));
            captureClick("potion-editor", new PotionMenu(player, boss, boss, parent), 19, view);
            snapshot("dungeon-control-only", new DungeonMenu(player, demo, list, true));
            try (var live = mockStatic(dev.dasan.customdungeons.mob.LiveTestService.class)) {
                live.when(() -> dev.dasan.customdungeons.mob.LiveTestService.active(player)).thenReturn(true);
                snapshot("mob-live-test-active", new MobMenu(player, boss, list));
                live.when(() -> dev.dasan.customdungeons.mob.LiveTestService.invulnerable(player)).thenReturn(true);
                snapshot("mob-live-test-invulnerable", new MobMenu(player, boss, list));
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
            // T35b: empty and invalid drafts plus filtered/no-result selectors.
            var blank = new MobMenu.MobDraft(new MobTemplate("empty", "ZOMBIE", "&6Vacío",0,0,0,0,0,
                    Map.of(),List.of(),List.of(),List.of(),false,"PURPLE",null,List.of(),false));
            snapshot("mob-empty",new MobMenu(player,blank,list));
            snapshot("mob-stats-empty",new StatsMenu(player,blank,parent));
            blank.health=1024; snapshot("mob-stats-health-1024",new StatsMenu(player,blank,parent));
            blank.health=0;
            snapshot("mob-equipment-empty",new EquipmentMenu(player,blank,blank,parent));
            snapshot("mob-enchants-unavailable",new EnchantMenu(player,blank,blank,EquipmentSlot.HAND,parent));
            snapshot("mob-potions-empty",new PotionMenu(player,blank,blank,parent));
            snapshot("mob-abilities-empty",new AbilityListMenu(player,blank,blank,parent));
            snapshot("mob-combos-empty",ComboMenu.list(player,blank,blank,parent));
            snapshot("mob-phases-empty",new PhaseListMenu(player,blank,parent));
            blank.health = 9999;
            blank.validationErrors = new Validator().validate(blank.snapshot(),config,registry.all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()))
                    .stream().map(e -> Validator.describe(e,messages)).toList();
            snapshot("mob-error",new MobMenu(player,blank,list));
            snapshot("mob-stats-error",new StatsMenu(player,blank,parent));
            blank.abilities.add(new AbilityInstance("missing",Trigger.ON_SPAWN,0,TargetMode.NEAREST,16,20,1,0,Map.of()));
            snapshot("mob-abilities-error",new AbilityListMenu(player,blank,blank,parent));
            snapshot("ability-params-error",new ParamEditorMenu(player,blank,blank.abilities.getFirst(),parent,v -> {}));
            when(store.mobs()).thenReturn(Map.of("empty",blank.snapshot()));
            snapshot("mob-library-error",new MobLibraryMenu(player,list));
            when(store.mobs()).thenReturn(mobs);
            blank.health = 0; blank.validationErrors = List.of();
            var badPhase = new MobMenu.PhaseDraft(new PhaseDef(1.5,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,20));
            blank.phases.add(badPhase);
            snapshot("mob-phases-error",new PhaseListMenu(player,blank,parent));
            snapshot("mob-phase-empty",new PhaseMenu(player,blank,new MobMenu.PhaseDraft(new PhaseDef(.66,false,List.of(),List.of(),Map.of(),List.of(),0,List.of(),null,null,null,null,20)),parent));
            snapshot("mob-phase-error",new PhaseMenu(player,blank,badPhase,parent));
            var fullCombo = new MobMenu.MobDraft(mobs.get("demo-boss"));
            var firstAbility = registry.all().iterator().next();
            var fiveSteps = java.util.stream.IntStream.range(0,5).mapToObj(i -> new ComboStep(firstAbility.id(),MobMenuBase.defaults(firstAbility).params(),20)).toList();
            fullCombo.combos.add(new ComboDef("full",Trigger.ON_SPAWN,0,TargetMode.NEAREST,16,20,fiveSteps));
            snapshot("mob-combo-full",new ComboMenu(player,fullCombo,fullCombo,fullCombo.combos.size()-1,parent));
            var phasePreview = new MobMenu.PhaseDraft(boss.phases.getFirst().snapshot());
            var phaseMenu = new PhaseMenu(player,boss,phasePreview,parent);
            captureClick("phase-summons-empty",phaseMenu,41,view);
            phasePreview.summons.add(new WaveEntry("demo-zombie",2,20));
            captureClick("phase-summons",phaseMenu,41,view);
            captureClick("phase-summon-editor",(Menu)view.getTopInventory().getHolder(),19,view);
            captureClick("phase-abilities",phaseMenu,21,view);
            captureClick("phase-potions-empty",phaseMenu,32,view);
            var abilitySelector = new AbilityPickerMenu(player,parent,a -> {});
            abilitySelector.query("wither"); snapshot("ability-picker-filtered",abilitySelector);
            abilitySelector.query("no-such-ability"); snapshot("ability-picker-no-results",abilitySelector);
            when(plugin.abilityRegistry()).thenReturn(new AbilityRegistry());
            snapshot("ability-picker-empty",new AbilityPickerMenu(player,parent,a -> {}));
            when(plugin.abilityRegistry()).thenReturn(registry);
            when(store.mobs()).thenReturn(Map.of());
            snapshot("templates-empty",new TemplatePickerMenu(root,root,v -> {}));
            when(store.mobs()).thenReturn(mobs);
            var entitySelector = new EntityTypePickerMenu(player,boss,parent);
            entitySelector.query("WARDEN"); snapshot("selector-entity-filtered",entitySelector);
            entitySelector.query("missing"); snapshot("selector-entity-no-results",entitySelector);
            for (String key : List.of("trigger","target","particle","sound","potions","template","bar-color")) {
                snapshot("selector-"+key+"-empty",new MobChoiceMenu(player,key,List.of(),parent,v -> {}));
                var options = key.equals("sound") ? MobMenuBase.soundKeys() : switch(key) {
                    case "trigger" -> Arrays.stream(Trigger.values()).map(Enum::name).toList();
                    case "target" -> Arrays.stream(TargetMode.values()).map(Enum::name).toList();
                    case "particle" -> Arrays.stream(Particle.values()).map(Enum::name).toList();
                    case "potions" -> MobMenuBase.potionKeys();
                    case "template" -> mobs.keySet().stream().toList();
                    default -> Arrays.stream(net.kyori.adventure.bossbar.BossBar.Color.values()).map(Enum::name).toList();
                };
                var selector = new MobChoiceMenu(player,key,options,parent,v -> {});
                if (!options.isEmpty()) { selector.query(options.getFirst()); snapshot("selector-"+key+"-filtered",selector); }
                selector.query("no-such-choice"); snapshot("selector-"+key+"-no-results",selector);
            }
            Inputs.numberWithClicks(player,messages.get("gui.common.value",net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("value","10")),0,100,10,v -> {});
            snapshot("numeric-input",(Menu)view.getTopInventory().getHolder());
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
        Boolean[] glint={null};
        when(meta.getEnchantmentGlintOverride()).thenAnswer(c->glint[0]);
        doAnswer(c->{glint[0]=c.getArgument(0);return null;}).when(meta).setEnchantmentGlintOverride(any());
        when(item.getType()).thenReturn(material); when(item.getAmount()).thenReturn(amount);
        when(item.getItemMeta()).thenReturn(meta); when(item.hasItemMeta()).thenReturn(true);
        when(meta.displayName()).thenAnswer(c -> title[0]);
        doAnswer(c -> { title[0] = c.getArgument(0); return null; }).when(meta).displayName(any());
        when(meta.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(meta.lore()).thenAnswer(c -> List.copyOf(lines));
        doAnswer(c -> { lines.clear(); if(c.getArgument(0) != null) lines.addAll(c.getArgument(0)); return null; }).when(meta).lore(any());
        doAnswer(c -> { ((Consumer<ItemMeta>) c.getArgument(0)).accept(meta); return true; }).when(item).editMeta(any());
        when(item.clone()).thenAnswer(c -> {
            ItemStack original = (ItemStack)c.getMock();
            var clone = item(material, amount, title[0], lines); if(actions.containsKey(original)) actions.put(clone, actions.get(original)); enchantments.get(clone).putAll(enchants); clone.editMeta(m->m.setEnchantmentGlintOverride(glint[0])); return clone;
        });
        return item;
    }
    private Inventory inventory(InventoryHolder holder, int size, Component title) {
        var slots = new ItemStack[size];
        // A Mockito holder stub points back to its menu and keeps every completed inventory
        // in the inline mock registry. This small API proxy has ordinary GC lifetime.
        var inv = (Inventory) java.lang.reflect.Proxy.newProxyInstance(Inventory.class.getClassLoader(),new Class<?>[]{Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getHolder" -> holder;
                    case "getSize" -> size;
                    case "getItem" -> slots[(int)args[0]];
                    case "setItem" -> { slots[(int)args[0]] = (ItemStack)args[1]; yield null; }
                    case "clear" -> { if (args == null || args.length == 0) Arrays.fill(slots,null); else slots[(int)args[0]] = null; yield null; }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> Inventory.class.getSimpleName();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        titles.put(inv, title); return inv;
    }
    private void exportSpawnerPresets(Player player,DungeonListMenu list,DefinitionStore store,DungeonDef demo,Map<String,MobTemplate> mobs) throws Exception {
        var zombie=new MobMenu.MobDraft(mobs.get("demo-spider"));zombie.name="Zombi";
        var fixtureMobs=new TreeMap<>(mobs);fixtureMobs.put("demo-spider",zombie.snapshot());when(store.mobs()).thenReturn(fixtureMobs);
        var horde=new SpawnerPreset("a_horde","Horda de zombis",3,List.of(
                new WaveDef(List.of(new WaveEntry("demo-spider",4,0)),SpawnMode.SIMULTANEOUS,20,60),
                new WaveDef(List.of(new WaveEntry("demo-zombie",2,0)),SpawnMode.STAGGERED,20,100)));
        var archers=new SpawnerPreset("b_archers","Arqueros de la cripta",2,List.of(new WaveDef(List.of(new WaveEntry("demo-skeleton",3,0)),SpawnMode.SEQUENTIAL,20,0)));
        var boss=new SpawnerPreset("c_boss","Jefe final",1,List.of(new WaveDef(List.of(new WaveEntry("demo-boss",1,0)),SpawnMode.SIMULTANEOUS,20,0)));
        var presets=Map.of(horde.id(),horde,archers.id(),archers,boss.id(),boss);when(store.spawnerPresets()).thenReturn(presets);
        var values=new DungeonMenu.Values(demo);values.name="Cripta del Guardián";values.spawnerPresets=List.of(horde.id(),boss.id());
        var rooms=new ArrayList<RoomDef>();
        for(int r=0;r<3;r++) {
            var room=demo.rooms().get(r);var point=room.spawners().getFirst().location();
            rooms.add(new RoomDef("Sala "+(r+1),room.region(),room.checkpoint(),room.door(),room.unlock(),room.unlock()==UnlockMode.KEY?"*":room.keyCarrierTemplateId(),
                    List.of(new SpawnerDef("spawner_"+r,point,3,List.of(),r==2?archers.id():horde.id()))));
        }
        values.rooms=rooms;var dungeon=values.build();when(store.dungeons()).thenReturn(Map.of(dungeon.id(),dungeon));
        snapshot("t36-m1-principal",new DungeonListMenu(player));
        var towerValues=new DungeonMenu.Values(dungeon);towerValues.id="tower";towerValues.name="Torre";towerValues.rooms=List.of(rooms.getFirst());
        when(store.dungeons()).thenReturn(Map.of(dungeon.id(),dungeon,"tower",towerValues.build()));
        var root=new DungeonMenu(player,dungeon,list);
        snapshot("t36-m2-biblioteca-spawners",new SpawnerLibraryMenu(list,list,null));
        snapshot("t36-m3-plantilla-spawner",new SpawnerPresetMenu(list,horde,list,null));
        snapshot("t36-m4-editor-dungeon",root);
        snapshot("t36-m5-spawners-dungeon",new DungeonSpawnerMenu(root));
        snapshot("t36-m6-anadir-spawner",new SpawnerPickerMenu(root,1,new RoomMenu(root,1,root),new Point("cd_dungeons",515,65,507,0,0)));
        snapshot("t36-m7-spawner-con-plantilla",new SpawnerMenu(root,0,0,root));
        snapshot("t36-sala-lista-plantilla",new RoomSpawnerList(root,0,new RoomMenu(root,0,root)));
        var emptyValues=new DungeonMenu.Values(dungeon);emptyValues.spawnerPresets=List.of();
        var emptyRoot=new DungeonMenu(player,emptyValues.build(),list);
        snapshot("t36-dungeon-spawners-empty",new DungeonSpawnerMenu(emptyRoot));
        when(store.spawnerPresets()).thenReturn(Map.of());
        snapshot("t36-library-empty",new SpawnerLibraryMenu(list,list,null));
        snapshot("t36-preset-empty",new SpawnerPresetMenu(list,new SpawnerPreset("new","Nueva plantilla",3,List.of()),list,null));
        snapshot("t36-picker-empty",new SpawnerPickerMenu(emptyRoot,0,emptyRoot,new Point("cd_dungeons",505.5,65,507.5,0,0)));
        snapshot("t36-dungeon-spawners-missing",new DungeonSpawnerMenu(root));
        snapshot("t36-spawner-missing",new SpawnerMenu(root,0,0,root));
        snapshot("t36-picker-missing",new SpawnerPickerMenu(root,0,root,new Point("cd_dungeons",505.5,65,507.5,0,0)));
        snapshot("t36-dungeon-missing",root);
        // Keep more than one page of each list under coverage.
        var many=new TreeMap<String,SpawnerPreset>();
        for(int i=0;i<35;i++) many.put("p"+i,new SpawnerPreset("p"+i,"Plantilla "+i,3,horde.waves()));
        when(store.spawnerPresets()).thenReturn(many);
        var pagedValues=new DungeonMenu.Values(dungeon);pagedValues.spawnerPresets=new ArrayList<>(many.keySet());
        var pagedRoot=new DungeonMenu(player,pagedValues.build(),list);
        snapshot("t36-library-paged",new SpawnerLibraryMenu(list,list,null));
        snapshot("t36-dungeon-spawners-paged",new DungeonSpawnerMenu(pagedRoot));
        snapshot("t36-picker-paged",new SpawnerPickerMenu(pagedRoot,0,pagedRoot,new Point("cd_dungeons",505.5,65,507.5,0,0)));
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
                row.put("glint",meta!=null&&Boolean.TRUE.equals(meta.getEnchantmentGlintOverride()));
                row.put("amount", icon == null ? 0 : icon.getAmount()); slots.add(row);
            }
            if(menu instanceof DungeonEditor || menu instanceof MobMenuBase || menu instanceof MobLibraryMenu || menu instanceof MobChoiceMenu
                    || menu instanceof AbilityPickerMenu || menu instanceof EntityTypePickerMenu) {
                assertEquals("KNOWLEDGE_BOOK",slots.get(8).get("material"));
                assertEquals(false,slots.get(8).get("action"));
                for(var slot:slots) assertFalse(slot.get("name").toString().matches(".*&[0-9a-fA-F].*"),id+" raw color: "+slot);
                for(var slot:slots) if(slot.get("material").equals("GRAY_STAINED_GLASS_PANE")) {
                    assertEquals("",slot.get("name"));assertEquals(List.of(),slot.get("lore"));assertEquals(false,slot.get("action"));
                }
            }
            if(menu instanceof EquipmentMenu || menu instanceof RewardMenu) for(int slot=0;slot<inventory.getSize();slot++) {
                if(menu.allowsPlacement(slot)) {
                    String expected="AIR";
                    if(menu instanceof EquipmentMenu equipment) {
                        var piece=switch(slot) {
                            case 19 -> EquipmentSlot.HAND; case 20 -> EquipmentSlot.OFF_HAND;
                            case 21 -> EquipmentSlot.HEAD; case 23 -> EquipmentSlot.CHEST;
                            case 24 -> EquipmentSlot.LEGS; case 25 -> EquipmentSlot.FEET;
                            default -> throw new AssertionError("Unexpected equipment input "+slot);
                        };
                        var value=equipment.summaryLoadout().equipment.get(piece);
                        if(value!=null&&!value.item().getType().isAir()) expected=value.item().getType().name();
                    }
                    assertEquals(expected,slots.get(slot).get("material"),id+" input "+slot);
                    assertEquals(false,slots.get(slot).get("action"),id+" input "+slot);
                }
            }
            int[] neutralHeaders=switch(menu) {
                case SpawnerPresetMenu ignored -> new int[]{11,13,15};
                case StatsMenu ignored -> new int[]{13};
                case EquipmentMenu ignored -> new int[]{10,11,12,14,15,16};
                case PhaseMenu ignored -> new int[]{10,12,14,16};
                case ComboMenu ignored -> new int[]{13};
                case DungeonMenu ignored -> new int[]{10,12,14,16};
                case DungeonSettingsMenu ignored -> new int[]{10,12,14,16};
                case ScalingMenu ignored -> new int[]{12,14};
                case HooksMenu ignored -> new int[]{13,31};
                case SpawnerMenu ignored -> new int[]{11,13,15};
                case WaveMenu ignored -> new int[]{11,13,15};
                default -> new int[]{};
            };
            if(menu instanceof MobMenuBase || menu instanceof MobChoiceMenu || menu instanceof MobLibraryMenu || menu instanceof AbilityPickerMenu || menu instanceof EntityTypePickerMenu) {
                assertEquals(false,slots.get(4).get("action"),id + " summary must be informational");
                for (var slot : slots) {
                    assertFalse(slot.get("name").toString().matches(".*<gui\\..*"),id + " missing key " + slot);
                    if (slot.get("material").equals("WHITE_STAINED_GLASS_PANE")) {
                        assertEquals(false,slot.get("action"),id);
                        assertFalse(slot.get("lore").toString().toLowerCase(Locale.ROOT).contains("clic"),id + " header promises clicks");
                    }
                }
            }
            if (name.equals("mob-error")) assertEquals("RED_STAINED_GLASS_PANE",slots.get(12).get("material"));
            if (menu instanceof MobMenu) {
                assertEquals(54,inventory.getSize());
                assertEquals("TARGET",slots.get(38).get("material"));
                assertEquals(true,slots.get(38).get("action"));
                assertEquals("LIME_CONCRETE",slots.get(49).get("material"));
                assertEquals("POTION",slots.get(30).get("material"));
                assertEquals("IRON_CHAIN",slots.get(34).get("material"));
                assertEquals("NETHER_STAR",slots.get(43).get("material"));
                assertFalse(slots.get(42).get("name").toString().endsWith(": "));
            }
            if (name.equals("mob-empty")) {
                for(int slot:new int[]{32,40,42}) { assertEquals("GRAY_DYE",slots.get(slot).get("material")); assertEquals(false,slots.get(slot).get("action")); }
            }
            if(name.equals("mob-equipment-empty")) for(int slot:new int[]{19,20,21,23,24,25}) {
                assertEquals("AIR",slots.get(slot).get("material"),"input must not be filled");
                assertTrue(menu.allowsPlacement(slot));
                assertEquals("GRAY_DYE",slots.get(slot+9).get("material"));
                assertEquals("GRAY_DYE",slots.get(slot+18).get("material"));
            }
            if (name.startsWith("mob-live-test-")) {
                assertEquals("RED_CONCRETE",slots.get(40).get("material"));
                assertEquals(true,slots.get(40).get("action"));
                assertEquals(name.endsWith("invulnerable") ? "LIME_DYE" : "GRAY_DYE",slots.get(42).get("material"));
                assertEquals(true,slots.get(42).get("action"));
            }
            if (name.equals("mob-enchants-unavailable")) assertEquals("GRAY_DYE",slots.get(13).get("material"));
            for(int slot:neutralHeaders) {
                assertEquals("WHITE_STAINED_GLASS_PANE",slots.get(slot).get("material"),id);
                assertEquals(true,slots.get(slot).get("glint"),id);
                assertEquals(false,slots.get(slot).get("action"),id);
            }
            if(menu instanceof RoomMenu || menu instanceof MobMenu) for(int slot:new int[]{10,12,14,16}) {
                assertEquals(slots.get(slot).get("name").toString().startsWith("✔")?"LIME_STAINED_GLASS_PANE":"RED_STAINED_GLASS_PANE",slots.get(slot).get("material"),id);
                assertEquals(false,slots.get(slot).get("action"),id);
            }
            var data = new LinkedHashMap<String, Object>(); data.put("menu", menu.getClass().getName());
            data.put("title", SnapshotText.plain(title)); data.put("color", SnapshotText.color(title));
            data.put("rows", inventory.getSize() / 9); data.put("slots", slots);
            Files.writeString(output.resolve(id + ".json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(data) + "\n");
            assertTrue(exported.add(id), "Duplicate snapshot " + id);
            // Static mock invocation histories otherwise retain every menu and item clone.
            // Weak presentation caches keep only items still reachable by an active menu.
            snapshotButtons.clearInvocations(); snapshotBukkit.clearInvocations(); clearInvocations(snapshotPlayer);
            Class<?> next = menu.getClass(); java.lang.reflect.Method hasNext = null;
            while(next != null && hasNext == null) { try { hasNext = next.getDeclaredMethod("hasNextPage"); } catch(NoSuchMethodException e) { next = next.getSuperclass(); } }
            hasNext.setAccessible(true); if (!(boolean) hasNext.invoke(menu)) break;
            var navigate = next.getDeclaredMethod("nextPage"); navigate.setAccessible(true); navigate.invoke(menu); page++;
            assertTrue(page < 100, "Pagination must terminate");
        }
    }
}
