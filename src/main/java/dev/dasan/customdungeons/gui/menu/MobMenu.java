package dev.dasan.customdungeons.gui.menu;

import dev.dasan.customdungeons.gui.*;
import dev.dasan.customdungeons.model.*;
import dev.dasan.customdungeons.ability.*;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Root editor. A single detached draft is shared by every child menu. */
public final class MobMenu extends MobMenuBase {
    public MobMenu(Player player, MobTemplate template, Menu parent) {
        this(player, new MobDraft(template), parent);
    }
    public MobMenu(Player player, MobDraft draft, Menu parent) { super(player, "editor", draft, parent); }
    @Override protected void render() {
        var invalid = validation(data);
        statusSection(10,"identity",invalid);
        statusSection(12,"stats",invalid);
        statusSection(14,"equipment",invalid);
        statusSection(16,"combat",invalid);
        action(19, egg(data.type), "entity", data.type, () -> new EntityTypePickerMenu(viewer, data, this).open());
        text(28,"name",data.name,v -> data.name=v);
        action(21,"stats", "", () -> new StatsMenu(viewer,data,this).open());
        action(23,"equipment",data.equipment.size(),() -> new EquipmentMenu(viewer,data,data,this).open());
        if (data.equipment.values().stream().anyMatch(e -> !e.item().getType().isAir()))
            action(32,"enchants","",() -> new EquipmentMenu(viewer,data,data,this).open());
        else set(32,GuiTheme.unavailable(message("enchants"),message("no-equipment")));
        action(30,"potions",data.potions.size(),() -> new PotionMenu(viewer,data,data,this).open());
        action(25,"abilities",data.abilities.size(),() -> new AbilityListMenu(viewer,data,data,this).open());
        action(34,"combos",data.combos.size(),() -> ComboMenu.list(viewer,data,data,this).open());
        action(43,"phases",data.phases.size(),() -> new PhaseListMenu(viewer,data,this).open());
    }
    @Override protected void renderFooter() {
        action(38,"test","",() -> dev.dasan.customdungeons.mob.LiveTestService.start(viewer,data.snapshot()));
        if (dev.dasan.customdungeons.mob.LiveTestService.active(viewer)) {
            action(40,"stop-test","",() -> dev.dasan.customdungeons.mob.LiveTestService.stop(viewer));
            action(42,GuiTheme.toggleIcon(dev.dasan.customdungeons.mob.LiveTestService.invulnerable(viewer)),"invulnerable",
                    dev.dasan.customdungeons.mob.LiveTestService.invulnerable(viewer),
                    () -> dev.dasan.customdungeons.mob.LiveTestService.toggleInvulnerable(viewer));
        } else {
            set(40,GuiTheme.unavailable(message("no-live-test-name"),message("no-live-test")));
            set(42,GuiTheme.unavailable(label("invulnerable",false),message("live-required")));
        }
    }

    public static class Loadout {
        public final Map<EquipmentSlot, EquipmentDef> equipment = new EnumMap<>(EquipmentSlot.class);
        public final List<PotionDef> potions = new ArrayList<>();
        public final List<AbilityInstance> abilities = new ArrayList<>();
        public final List<ComboDef> combos = new ArrayList<>();
        Loadout(Map<EquipmentSlot, EquipmentDef> e, List<PotionDef> p, List<AbilityInstance> a, List<ComboDef> c) {
            equipment.putAll(e); potions.addAll(p); abilities.addAll(a); combos.addAll(c);
        }
    }
    public static final class MobDraft extends Loadout {
        public final String id;
        public String type, name, color, music;
        public double health, damage, speed, resistance, scale;
        public boolean boss, drops;
        MobTemplate savedSnapshot;
        public final List<PhaseDraft> phases = new ArrayList<>();
        public final Draft<MobTemplate> draft;
        List<Component> validationErrors = List.of();
        boolean saving;
        public MobDraft(MobTemplate m) {
            super(m.equipment(), m.potions(), m.abilities(), m.combos());
            draft = new Draft<>(m); savedSnapshot = m; id = m.id(); type = m.entityType(); name = m.displayName();
            health = m.maxHealth(); damage = m.damage(); speed = m.speed(); resistance = m.knockbackResistance();
            scale = m.scale(); boss = m.boss(); color = m.bossBarColor(); music = m.musicKey(); drops = m.vanillaDrops();
            m.phases().forEach(p -> phases.add(new PhaseDraft(p)));
        }
        public MobTemplate snapshot() {
            var m = new MobTemplate(id, type, name, health, damage, speed, resistance, scale, equipment,
                    potions, abilities, combos, boss, color, music, phases.stream().map(PhaseDraft::snapshot).toList(), drops);
            draft.set(m); return m;
        }
    }
    public static final class PhaseDraft extends Loadout {
        double threshold, heal;
        boolean replace;
        String title, subtitle, sound, music;
        int invulnerable;
        final List<WaveEntry> summons = new ArrayList<>();
        public PhaseDraft(PhaseDef p) {
            super(p.equipment(), p.potions(), p.abilities(), p.combos());
            threshold = p.healthThreshold(); heal = p.healPercent(); replace = p.replaceAbilities();
            title = p.title(); subtitle = p.subtitle(); sound = p.soundKey(); music = p.musicKey();
            invulnerable = p.invulnerableTicks(); summons.addAll(p.summons());
        }
        public PhaseDef snapshot() { return new PhaseDef(threshold, replace, abilities, combos, equipment,
                potions, heal, summons, title, subtitle, sound, music, invulnerable); }
    }
}

/** Package-local mechanics shared only by the T17 editors. */
abstract class MobMenuBase extends Menu {
    protected final MobMenu.MobDraft data;
    private final Menu previous;
    private int page, pages = 1;
    protected final String titleKey;
    protected MobMenuBase(Player p, String title, MobMenu.MobDraft draft, Menu parent) {
        super(p, menuTitle(title), 6); data = draft; previous = parent; titleKey = title;
    }
    static dev.dasan.customdungeons.CustomDungeonsPlugin plugin() {
        return org.bukkit.plugin.java.JavaPlugin.getPlugin(dev.dasan.customdungeons.CustomDungeonsPlugin.class);
    }
    static dev.dasan.customdungeons.config.DefinitionStore store() {
        return Objects.requireNonNull(Bukkit.getServicesManager().load(dev.dasan.customdungeons.config.DefinitionStore.class));
    }
    static dev.dasan.customdungeons.config.PluginConfig config() {
        return Objects.requireNonNull(Bukkit.getServicesManager().load(dev.dasan.customdungeons.config.PluginConfig.class));
    }
    static AbilityRegistry registry() { return plugin().abilityRegistry(); }
    static Component menuTitle(String key) { return MenuListener.instance().messages().get("gui.mob.menu-titles."+key); }
    static Component message(String key) { return MenuListener.instance().messages().get("gui.mob." + key, Placeholder.unparsed("value", "")); }
    static Component label(String key, Object value) {
        Component translated=displayValue(key,value);
        return MenuListener.instance().messages().get("gui.mob." + key, Placeholder.component("value",translated));
    }
    static String formatValue(Number value) {
        double number = value.doubleValue();
        return Double.isFinite(number) ? java.math.BigDecimal.valueOf(number).stripTrailingZeros().toPlainString() : value.toString();
    }
    static Component filterLabel(String query) { return query.isBlank() ? message("filter-none") : Component.text(query); }
    static Component displayValue(String key,Object value) {
        if (value instanceof Component component) return component;
        if (value instanceof String text && List.of("name", "title", "subtitle", "entry", "choice", "combo-id", "parameter").contains(key))
            return dev.dasan.customdungeons.text.Text.parse(text);
        var messages=MenuListener.instance().messages();
        if(value instanceof Boolean flag) return messages.get("gui.mob."+(flag ? "enabled" : "disabled"));
        if(key.equals("trigger") && value!=null) return messages.get("gui.mob.trigger-values."+value);
        if(key.equals("target") && value!=null) return messages.get("gui.mob.target-values."+value);
        if(key.equals("bar-color") && value!=null) return messages.get("gui.mob.bar-color-values."+value);
        if(key.equals("equipment-slot") && value!=null) return messages.get("gui.mob.slot-values."+value);
        return Component.text(value instanceof Number number ? formatValue(number) : Objects.toString(value,""));
    }
    static Material choiceIcon(String key,String value) {
        if(key.equals("trigger")) return switch(Trigger.valueOf(value)) {
            case EVERY_X_SECONDS -> Material.CLOCK;
            case ON_HIT -> Material.DIAMOND_SWORD;
            case ON_DAMAGED -> Material.SHIELD;
            case HEALTH_BELOW -> Material.REDSTONE;
            case ON_SPAWN -> Material.SPAWNER;
            case ON_DEATH -> Material.WITHER_SKELETON_SKULL;
            case PLAYER_IN_RANGE -> Material.SCULK_SENSOR;
        };
        if(key.equals("target")) return switch(TargetMode.valueOf(value)) {
            case CURRENT_TARGET -> Material.TARGET;
            case NEAREST -> Material.COMPASS;
            case RANDOM -> Material.ENDER_PEARL;
            case ALL_IN_RADIUS -> Material.FIREWORK_STAR;
        };
        if (key.equals("bar-color")) return Material.valueOf(value + "_CONCRETE");
        return icon(key);
    }
    static Material egg(String type) {
        Material material = Material.matchMaterial(type.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "") + "_SPAWN_EGG");
        return material == null ? Material.SPAWNER : material;
    }
    protected void action(int slot, String key, Object value, Runnable run) { action(slot, icon(key), key, value, run); }
    static Material icon(String key) {
        return switch(key) {
            case "entity", "template", "summons", "library" -> Material.SPAWNER;
            case "equipment" -> Material.DIAMOND_CHESTPLATE;
            case "enchants" -> Material.ENCHANTED_BOOK;
            case "potions", "potion-type", "potion-level" -> Material.POTION;
            case "abilities", "parameters" -> Material.BLAZE_POWDER;
            case "combos", "combo-id" -> Material.IRON_CHAIN;
            case "phases", "phase", "boss", "threshold" -> Material.NETHER_STAR;
            case "music", "sound" -> Material.MUSIC_DISC_CAT;
            case "test" -> Material.TARGET;
            case "stop-test" -> Material.RED_CONCRETE;
            case "invulnerable", "invulnerable-ticks" -> Material.SHIELD;
            case "trigger", "trigger-value" -> Material.OBSERVER;
            case "target", "range" -> Material.TARGET;
            case "cooldown", "delay", "step-delay" -> Material.CLOCK;
            case "chance", "vanilla-drops", "drop-chance" -> Material.GOLD_NUGGET;
            case "particle", "particles-visible", "telegraph" -> Material.FIREWORK_ROCKET;
            case "heal", "health" -> Material.GLISTERING_MELON_SLICE;
            case "stats" -> Material.APPLE;
            case "add", "add-step" -> Material.LIME_DYE;
            case "count" -> Material.ZOMBIE_HEAD;
            default -> Material.NAME_TAG;
        };
    }
    static Material abilityIcon(Ability ability) {
        Material icon=ability.icon();
        return icon==Material.PAPER || icon==Material.INK_SAC || icon==Material.FEATHER ? Material.BLAZE_POWDER : icon;
    }
    static Component abilityName(String id) {
        return registry().get(id).isPresent() ? MenuListener.instance().messages().get("ability."+id+".name") : label("ability-missing",id);
    }
    protected void action(int slot, Material icon, String key, Object value, Runnable run) {
        String kind = switch(key) {
            case "stats", "equipment", "enchants", "potions", "abilities", "combos", "phases", "summons", "parameters" -> "open";
            case "entity", "bar-color", "template", "trigger", "target", "potion-type", "particle" -> "choose";
            case "boss", "vanilla-drops", "replace", "particles-visible", "invulnerable" -> "toggle";
            case "add", "add-step", "add-ability", "add-combo", "add-potion", "add-phase", "add-summon" -> "add";
            case "test", "stop-test", "clamp-stats", "remove-armor" -> key;
            default -> "write";
        };
        set(slot, actionButton(icon,key,value,kind,run));
    }
    protected Button actionButton(Material icon, String key, Object value, String kind, Runnable run) {
        var lore = new ArrayList<Component>();
        lore.add(message(key+"-lore"));
        if (key.equals("stats")) lore.add(MenuListener.instance().messages().get("gui.mob.stats-preview",
                Placeholder.unparsed("health",formatValue(data.health)), Placeholder.unparsed("damage",formatValue(data.damage)),
                Placeholder.unparsed("scale",formatValue(data.scale))));
        lore.add(Component.empty());
        lore.add(message("action-"+kind));
        return Button.of(icon,label(key,value),lore,
                (p,c) -> MenuListener.instance().later(() -> { run.run(); if (p.getOpenInventory().getTopInventory() == getInventory()) refresh(); }));
    }
    protected void section(int slot,String key,Material icon) {
        set(slot, icon == Material.GRAY_DYE ? GuiTheme.unavailable(message(key), message(key+"-lore"))
                : GuiTheme.section(message(key), List.of(message(key+"-lore"))));
    }
    protected void text(int slot, String key, String value, Consumer<String> set) {
        action(slot, key, value, () -> Inputs.text(viewer, message(key), value, 256, set));
    }
    protected void number(int slot, String key, double value, double min, double max, DoubleConsumer set) {
        action(slot, key, value, () -> Inputs.number(viewer, message(key), min, max, value, set));
    }
    protected void bool(int slot, String key, boolean value, Consumer<Boolean> set) {
        action(slot, value ? Material.LIME_DYE : Material.GRAY_DYE, key, value, () -> set.accept(!value));
    }
    protected void select(int slot, String key, String value, List<String> choices, Consumer<String> set) {
        action(slot, key, value, () -> choose(viewer, key, choices, this, set));
    }
    protected void sound(int slot, String key, String value, Consumer<String> set) {
        set(slot, Button.of(icon(key), label(key, value), List.of(message("sound-lore")), (p,c) ->
            MenuListener.instance().later(() -> {
                if (c.isShiftClick()) Inputs.text(p, message(key), value, 256, v -> set.accept(v.isBlank() ? null : v));
                else choose(p, key, soundKeys(), this, set);
            })));
    }
    static List<String> filterChoices(List<String> choices, String query) {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains(normalized)).toList();
    }
    static String soundCategory(String key) {
        int colon = key.indexOf(':');
        if (colon < 0) return "other";
        String namespace = key.substring(0, colon);
        if (!namespace.equals("minecraft")) return namespace;
        String path = key.substring(colon + 1);
        int dot = path.indexOf('.');
        return dot < 0 ? "other" : path.substring(0, dot);
    }
    static List<String> soundKeys() { return Registry.SOUNDS.stream().map(s -> Registry.SOUNDS.getKey(s).toString()).sorted().toList(); }
    static List<String> potionKeys() { return Registry.POTION_EFFECT_TYPE.stream().map(s -> s.getKey().toString()).sorted().toList(); }
    static void choose(Player p, String key, List<String> choices, Menu parent, Consumer<String> accept) {
        new MobChoiceMenu(p, key, choices, parent, accept).open();
    }
    protected int contentCount() {
        return switch (titleKey) {
            case "potions" -> data.potions.size();
            case "abilities" -> data.abilities.size();
            case "combos" -> data.combos.size();
            case "phases" -> data.phases.size();
            default -> 0;
        };
    }
    @Override protected int preferredRows() {
        return switch (titleKey) {
            case "equipment", "phase" -> 6;
            case "editor" -> 6;
            case "potion-editor", "summon-editor" -> 4;
            case "combos" -> this instanceof ComboMenu ? 6 : GuiLayout.rowsFor(contentCount(), 7, 1);
            default -> GuiLayout.rowsFor(contentCount(), 7, 1);
        };
    }
    @Override protected Material borderMaterial() {
        return List.of("abilities", "parameters", "combos", "phases", "phase", "summons", "summon-editor").contains(titleKey)
                ? Material.MAGENTA_STAINED_GLASS_PANE : Material.PURPLE_STAINED_GLASS_PANE;
    }
    static boolean sectionReady(String section, List<dev.dasan.customdungeons.config.ValidationError> invalid) {
        return invalid.stream().noneMatch(e -> switch (section) {
            case "identity" -> Set.of("id","entity-type").contains(e.path());
            case "stats" -> Set.of("max-health","damage","speed","knockback-resistance","scale").contains(e.path());
            case "equipment" -> e.path().startsWith("equipment.");
            case "combat" -> e.path().startsWith("abilities[") || e.path().startsWith("combos[") || e.path().startsWith("phases[");
            default -> throw new IllegalArgumentException("Unknown mob section");
        });
    }
    static List<dev.dasan.customdungeons.config.ValidationError> validation(MobMenu.MobDraft data) {
        return new dev.dasan.customdungeons.config.Validator().validate(data.snapshot(), config(),
                registry().all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()));
    }
    static Component statusLine(Component line, boolean ready) {
        return MenuListener.instance().messages().get(ready ? "gui.common.ready" : "gui.common.missing", Placeholder.component("part",line));
    }
    protected void statusSection(int slot, String section, List<dev.dasan.customdungeons.config.ValidationError> invalid) {
        boolean ready = sectionReady(section,invalid);
        set(slot,GuiTheme.section(ready,statusLine(message("section-"+section),ready),List.of(message("section-"+section+"-lore"))));
    }
    protected List<Component> summaryLore(MobMenu.Loadout loadout) { return summaryLore(data,loadout); }
    static List<Component> summaryLore(MobMenu.MobDraft data, MobMenu.Loadout loadout) {
        var messages = MenuListener.instance().messages();
        var lore = new ArrayList<Component>();
        var invalid = validation(data);
        lore.add(messages.get("gui.mob.summary-type", Placeholder.unparsed("type", data.type), Placeholder.unparsed("id", data.id)));
        lore.add(messages.get("gui.mob.summary-stats", Placeholder.unparsed("health", formatValue(data.health)),
                Placeholder.unparsed("damage", formatValue(data.damage)), Placeholder.unparsed("scale", formatValue(data.scale))));
        lore.add(messages.get("gui.mob.summary-loadout", Placeholder.unparsed("equipment", Integer.toString(loadout.equipment.size())),
                Placeholder.unparsed("potions", Integer.toString(loadout.potions.size()))));
        lore.add(messages.get("gui.mob.summary-combat", Placeholder.unparsed("abilities", Integer.toString(loadout.abilities.size())),
                Placeholder.unparsed("combos", Integer.toString(loadout.combos.size())), Placeholder.unparsed("phases", Integer.toString(data.phases.size()))));
        lore.add(messages.get("gui.mob.summary-boss", Placeholder.component("boss", displayValue("boss", data.boss))));
        for (int i=0;i<4;i++) {
            String section = List.of("identity","stats","equipment","combat").get(i);
            lore.set(i,statusLine(lore.get(i),sectionReady(section,invalid)));
        }
        if (invalid.isEmpty()) lore.add(message("valid"));
        else lore.add(messages.get("gui.mob.invalid-summary", Placeholder.component("error", dev.dasan.customdungeons.config.Validator.describe(invalid.getFirst(), messages))));
        return lore;
    }
    protected MobMenu.Loadout summaryLoadout() { return data; }
    @Override protected void renderHeader() {
        if (data != null) set(4, GuiTheme.information(egg(data.type), MenuListener.instance().messages().get("gui.mob.summary",
                Placeholder.component("name", dev.dasan.customdungeons.text.Text.parse(data.name)),
                Placeholder.component("menu", menuTitle(titleKey))), summaryLore(summaryLoadout())));
        GuiTheme.help(this, java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(i -> message("help-editor-" + i)).toList());
    }
    @Override protected boolean hasUnsavedChanges() { return data != null && !Objects.equals(data.snapshot(), data.savedSnapshot); }
    protected int entryFirstRow() { return 2; }
    protected boolean showEntryHeading() { return true; }
    protected Component entryHeading() { return menuTitle(titleKey); }
    protected void entries(List<Button> buttons) {
        int capacity = (getInventory().getSize() / 9 - entryFirstRow() - 1) * 7;
        if (showEntryHeading()) set((entryFirstRow()-1)*9+4, GuiTheme.section(entryHeading(), List.of(message("list-heading-lore"))));
        if (buttons.isEmpty()) { pages = 1; page = 0; set((entryFirstRow()-1)*9+4, GuiTheme.section(entryHeading(), List.of(message("empty")))); return; }
        pages = PagedMenu.pageCount(buttons.size(), capacity); page = Math.clamp(page, 0, pages - 1);
        int start = PagedMenu.startIndex(buttons.size(), capacity, page);
        int end = PagedMenu.endIndex(buttons.size(), capacity, page);
        for (int i = start; i < end; i++) { int offset = i - start; set((entryFirstRow()+offset/7)*9+1+offset%7, buttons.get(i)); }
    }
    protected Button entry(Material material, Object value, Runnable edit, Runnable delete) {
        return entry(material,label("entry",value),edit,delete);
    }
    protected Button entry(Material material,Component name,Runnable edit,Runnable delete) {
        return Button.of(material, name, List.of(message(titleKey.equals("enchants") ? "action-write" : "action-open"),message("action-remove")), (p,c) ->
            MenuListener.instance().later(() -> { if (c.isShiftClick() && c.isRightClick()) { delete.run(); refresh(); } else edit.run(); }));
    }
    @Override protected Menu parent() { return previous; }
    @Override protected boolean hasPreviousPage() { return page > 0; }
    @Override protected boolean hasNextPage() { return page + 1 < pages; }
    @Override protected void previousPage() { if (page > 0) { page--; refresh(); } }
    @Override protected void nextPage() { if (page + 1 < pages) { page++; refresh(); } }
    protected void showErrors(int slot) {
        if (data != null && !data.validationErrors.isEmpty()) set(slot, Button.of(Material.RED_DYE, message("invalid"), data.validationErrors, (p,c) -> {}));
    }
    @Override protected Runnable onSave() {
        showErrors(getInventory().getSize() - 2);
        return data == null ? null : this::save;
    }
    private void save() {
        if (data.saving) return;
        var snapshot = data.snapshot();
        var invalid = new dev.dasan.customdungeons.config.Validator().validate(snapshot, config(),
                registry().all().stream().map(Ability::id).collect(java.util.stream.Collectors.toSet()));
        data.validationErrors = invalid.stream().map(e -> dev.dasan.customdungeons.config.Validator.describe(e,MenuListener.instance().messages())).toList();
        if (!data.validationErrors.isEmpty()) { MenuListener.instance().messages().send(viewer, "gui.mob.invalid"); refresh(); return; }
        data.saving = true;
        var ownerPlugin=plugin();
        store().save(snapshot).whenComplete((v,e) -> {
            if (!ownerPlugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(ownerPlugin, () -> {
                data.saving = false;
                if (e == null) data.savedSnapshot = snapshot;
                if (viewer.getOpenInventory().getTopInventory() == getInventory()) refresh();
                if (viewer.isOnline()) MenuListener.instance().messages().send(viewer, e == null ? "gui.mob.saved" : "gui.mob.save-failed");
            });
        });
    }
    static AbilityInstance defaults(Ability a) {
        var params = new LinkedHashMap<String,Object>(); a.params().forEach(p -> params.put(p.key(), p.defaultValue()));
        return new AbilityInstance(a.id(), Trigger.EVERY_X_SECONDS, 5, TargetMode.NEAREST, 16, 100, 1, 20, params);
    }
}

/** Registry selectors share filtering and keep slot 4 informational. */
final class MobChoiceMenu extends PagedMenu<String> {
    private final String key;
    private final List<String> choices;
    private final Menu previous;
    private final Consumer<String> accept;
    private String query = "";
    MobChoiceMenu(Player player, String key, List<String> choices, Menu parent, Consumer<String> accept) {
        super(player, MobMenuBase.menuTitle(key), 6);
        this.key = key; this.choices = List.copyOf(choices); previous = parent; this.accept = accept;
    }
    void query(String value) { query = value; }
    @Override protected Material borderMaterial() { return Material.LIGHT_BLUE_STAINED_GLASS_PANE; }
    @Override protected int preferredRows() { return GuiLayout.rowsFor(items().size(), 7, 0); }
    @Override protected List<String> items() { return MobMenuBase.filterChoices(choices, query); }
    @Override protected Menu parent() { return previous; }
    @Override protected void renderHeader() {
        var messages = MenuListener.instance().messages();
        set(4, GuiTheme.information(Material.BOOK, messages.get("gui.mob.selector-summary",
                Placeholder.component("value", MobMenuBase.menuTitle(key))), List.of(messages.get("gui.mob.selector-count",
                Placeholder.unparsed("count", Integer.toString(items().size())), Placeholder.component("query", MobMenuBase.filterLabel(query))))));
        if (items().isEmpty()) set(13, GuiTheme.information(Material.GRAY_DYE, MobMenuBase.message("no-results"), List.of()));
        GuiTheme.help(this, java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(i -> MobMenuBase.message("help-selector-" + i)).toList());
    }
    @Override protected void renderFooter() {
        set(getInventory().getSize() - 8, Button.of(Material.NAME_TAG, MobMenuBase.message("search"), List.of(MobMenuBase.message("search-lore")),
                (p, c) -> MenuListener.instance().later(() -> Inputs.text(p, MobMenuBase.message("search"), query, 100, this::query))));
    }
    @Override protected Button button(String item) {
        var lore = new ArrayList<Component>();
        if (List.of("sound", "music").contains(key)) lore.add(MenuListener.instance().messages().get("gui.mob.category",
                Placeholder.unparsed("value", MobMenuBase.soundCategory(item))));
        if (key.equals("particle")) lore.add(MenuListener.instance().messages().get("gui.mob.category",
                Placeholder.unparsed("value", Particle.valueOf(item).getDataType().getSimpleName())));
        Component value = MobMenuBase.displayValue(key, item);
        if (List.of("sound","music").contains(key)) {
            value = Component.text(item.startsWith("minecraft:") ? item.substring(10) : item);
            lore.add(MenuListener.instance().messages().get("gui.mob.registry-key",Placeholder.unparsed("value",item)));
        }
        Material icon = MobMenuBase.choiceIcon(key, item);
        if (key.equals("template")) {
            var mob = MobMenuBase.store().mobs().get(item);
            if (mob != null) { value = dev.dasan.customdungeons.text.Text.parse(mob.displayName()); icon = MobMenuBase.egg(mob.entityType()); }
        }
        lore.add(Component.empty()); lore.add(MobMenuBase.message("action-choose"));
        return Button.of(icon, MenuListener.instance().messages().get("gui.mob.choice", Placeholder.component("value", value)), lore,
                (p, c) -> MenuListener.instance().later(() -> { accept.accept(item); previous.open(); }));
    }
}
