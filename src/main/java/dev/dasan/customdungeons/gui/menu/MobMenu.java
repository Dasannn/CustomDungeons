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
        section(4,"section-mob",Material.SPAWNER);
        action(10, "entity", data.type, () -> new EntityTypePickerMenu(viewer, data, this).open());
        text(11, "name", data.name, v -> data.name = v);
        action(12, "stats", "", () -> new StatsMenu(viewer, data, this).open());
        action(13, "equipment", "", () -> new EquipmentMenu(viewer, data, data, this).open());
        action(14, "potions", data.potions.size(), () -> new PotionMenu(viewer, data, data, this).open());
        action(15, "abilities", data.abilities.size(), () -> new AbilityListMenu(viewer, data, data, this).open());
        action(16, "combos", data.combos.size(), () -> ComboMenu.list(viewer, data, data, this).open());
        bool(20, "boss", data.boss, v -> data.boss = v);
        action(21, "phases", data.phases.size(), () -> new PhaseListMenu(viewer, data, this).open());
        select(22, "bar-color", data.color, Arrays.stream(net.kyori.adventure.bossbar.BossBar.Color.values()).map(Enum::name).toList(), v -> data.color = v);
        sound(23, "music", data.music, v -> data.music = v);
        bool(24, "vanilla-drops", data.drops, v -> data.drops = v);
        action(29, "test", "", () -> dev.dasan.customdungeons.mob.LiveTestService.start(viewer, data.snapshot()));
        if(dev.dasan.customdungeons.mob.LiveTestService.active(viewer))
            action(31, "stop-test", "", () -> dev.dasan.customdungeons.mob.LiveTestService.stop(viewer));
        else section(31,"section-live-test",Material.TARGET);
        action(33, "invulnerable", dev.dasan.customdungeons.mob.LiveTestService.invulnerable(viewer),
                () -> dev.dasan.customdungeons.mob.LiveTestService.toggleInvulnerable(viewer));
        showErrors(40);
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
        public final List<PhaseDraft> phases = new ArrayList<>();
        public final Draft<MobTemplate> draft;
        List<Component> validationErrors = List.of();
        boolean saving;
        public MobDraft(MobTemplate m) {
            super(m.equipment(), m.potions(), m.abilities(), m.combos());
            draft = new Draft<>(m); id = m.id(); type = m.entityType(); name = m.displayName();
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
    protected MobMenuBase(Player p, String title, MobMenu.MobDraft draft, Menu parent) {
        super(p, message(title), 6); data = draft; previous = parent;
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
    static Component message(String key) { return MenuListener.instance().messages().get("gui.mob." + key, Placeholder.unparsed("value", "")); }
    static Component label(String key, Object value) {
        Component translated=displayValue(key,value);
        return MenuListener.instance().messages().get("gui.mob." + key, Placeholder.component("value",translated));
    }
    static Component displayValue(String key,Object value) {
        var messages=MenuListener.instance().messages();
        if(value instanceof Boolean flag) return messages.get("gui.mob."+(flag ? "enabled" : "disabled"));
        if(key.equals("trigger") && value!=null) return messages.get("gui.mob.trigger-values."+value);
        if(key.equals("target") && value!=null) return messages.get("gui.mob.target-values."+value);
        if(key.equals("equipment-slot") && value!=null) return messages.get("gui.mob.slot-values."+value);
        return Component.text(Objects.toString(value,""));
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
            case "abilities", "parameters", "add-step" -> Material.BLAZE_POWDER;
            case "combos", "combo-id" -> Material.IRON_CHAIN;
            case "phases", "phase", "boss", "threshold" -> Material.NETHER_STAR;
            case "music", "sound" -> Material.MUSIC_DISC_CAT;
            case "test" -> Material.TARGET;
            case "stop-test" -> Material.BARRIER;
            case "invulnerable", "invulnerable-ticks" -> Material.SHIELD;
            case "trigger", "trigger-value" -> Material.OBSERVER;
            case "target", "range" -> Material.COMPASS;
            case "cooldown", "delay", "step-delay" -> Material.CLOCK;
            case "chance", "vanilla-drops", "drop-chance" -> Material.GOLD_NUGGET;
            case "particle", "particles-visible", "telegraph" -> Material.FIREWORK_ROCKET;
            case "heal", "health" -> Material.GLISTERING_MELON_SLICE;
            case "stats" -> Material.REDSTONE;
            case "add", "count" -> Material.EMERALD;
            default -> Material.NAME_TAG;
        };
    }
    static Material abilityIcon(Ability ability) {
        Material icon=ability.icon();
        return icon==Material.PAPER || icon==Material.INK_SAC || icon==Material.FEATHER ? Material.BLAZE_POWDER : icon;
    }
    static Component abilityName(String id) { return MenuListener.instance().messages().get("ability."+id+".name"); }
    protected void action(int slot, Material icon, String key, Object value, Runnable run) {
        var lore=new ArrayList<Component>();
        lore.add(message(key+"-lore"));
        if(value!=null && !String.valueOf(value).isBlank()) lore.add(MenuListener.instance().messages().get("gui.mob.current-value",Placeholder.component("value",displayValue(key,value))));
        lore.add(message("click-lore"));
        set(slot, Button.of(icon, label(key, value), lore,
                (p,c) -> MenuListener.instance().later(() -> { run.run(); if (p.getOpenInventory().getTopInventory() == getInventory()) refresh(); })));
    }
    protected void section(int slot,String key,Material icon) {
        set(slot,Button.of(icon,message(key),List.of(message(key+"-lore")),(p,c)->{}));
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
    static List<String> soundKeys() { return Registry.SOUNDS.stream().map(s -> Registry.SOUNDS.getKey(s).toString()).sorted().toList(); }
    static List<String> potionKeys() { return Registry.POTION_EFFECT_TYPE.stream().map(s -> s.getKey().toString()).sorted().toList(); }
    static void choose(Player p, String key, List<String> choices, Menu parent, Consumer<String> accept) {
        new PagedMenu<String>(p, message(key), 6) {
            String query = "";
            @Override protected List<String> items() {
                set(4, Button.of(Material.COMPASS, message("search"), List.of(message("search-lore")), (v,c) ->
                    MenuListener.instance().later(() -> Inputs.text(v, message("search"), query, 100, s -> { query = s.toLowerCase(Locale.ROOT); }))));
                return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).contains(query)).toList();
            }
            @Override protected Button button(String item) {
                return Button.of(choiceIcon(key,item), MenuListener.instance().messages().get("gui.mob.choice",Placeholder.component("value",displayValue(key,item))), List.of(message("click-lore")), (v,c) ->
                    MenuListener.instance().later(() -> { accept.accept(item); parent.open(); }));
            }
            @Override protected Menu parent() { return parent; }
        }.open();
    }
    protected void entries(List<Button> buttons) {
        pages = PagedMenu.pageCount(buttons.size(), 28); page = Math.clamp(page, 0, pages - 1);
        int start = PagedMenu.startIndex(buttons.size(), 28, page);
        int end = PagedMenu.endIndex(buttons.size(), 28, page);
        for (int i = start; i < end; i++) { int offset = i - start; set(GuiLayout.pageSlot(offset,end-start,1), buttons.get(i)); }
    }
    protected Button entry(Material material, Object value, Runnable edit, Runnable delete) {
        return entry(material,label("entry",value),edit,delete);
    }
    protected Button entry(Material material,Component name,Runnable edit,Runnable delete) {
        return Button.of(material, name, List.of(message("entry-lore")), (p,c) ->
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
        showErrors(44);
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
                if (viewer.isOnline()) MenuListener.instance().messages().send(viewer, e == null ? "gui.mob.saved" : "gui.mob.save-failed");
            });
        });
    }
    static AbilityInstance defaults(Ability a) {
        var params = new LinkedHashMap<String,Object>(); a.params().forEach(p -> params.put(p.key(), p.defaultValue()));
        return new AbilityInstance(a.id(), Trigger.EVERY_X_SECONDS, 5, TargetMode.NEAREST, 16, 100, 1, 20, params);
    }
}
