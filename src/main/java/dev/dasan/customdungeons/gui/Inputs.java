package dev.dasan.customdungeons.gui;

import dev.dasan.customdungeons.config.NumericRange;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class Inputs {
    private static final PendingInputs pending = new PendingInputs(
            player -> MenuListener.instance().editLocks().releaseAll(player));
    private Inputs() {}

    public static int parseInteger(String text, int min, int max) {
        if (text == null || !text.trim().matches("[+-]?[0-9]+")) throw new IllegalArgumentException("Invalid integer");
        int value = Integer.parseInt(text.trim());
        if (value < min || value > max) throw new IllegalArgumentException("Out of range");
        return value;
    }
    public static double parseDecimal(String text, double min, double max, int decimals) {
        if (text == null) throw new IllegalArgumentException("Missing value");
        String normalized = text.trim().replace(',', '.');
        if (normalized.isEmpty() && min == 0) return 0;
        if (normalized.startsWith(".")) normalized="0"+normalized;
        else if (normalized.startsWith("-.")) normalized="-0"+normalized.substring(1);
        else if (normalized.startsWith("+.")) normalized="+0"+normalized.substring(1);
        if (!normalized.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("Invalid decimal");
        BigDecimal value = new BigDecimal(normalized);
        if (value.scale() > decimals || value.compareTo(BigDecimal.valueOf(min)) < 0
                || value.compareTo(BigDecimal.valueOf(max)) > 0) throw new IllegalArgumentException("Out of range or precision");
        return value.doubleValue();
    }
    public static String formatNumber(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }
    public static void integer(Player player, Component title, int min, int max, int current, IntConsumer onSubmit) {
        exact(player, title, min, max, current, 0, v -> onSubmit.accept((int)v));
    }
    public static void decimal(Player player, Component title, double min, double max, double current,
                               int decimals, DoubleConsumer onSubmit) {
        if (decimals < 1 || decimals > 8) throw new IllegalArgumentException("Invalid precision");
        exact(player, title, min, max, current, decimals, onSubmit);
    }
    public static void ranged(Player player,Component title,NumericRange range,double current,DoubleConsumer onSubmit) {
        exact(player,title,range.inputMin(),range.max(),current,range.decimals(),onSubmit,range);
    }
    public static void rangedClicks(Player player,Component title,NumericRange range,double current,DoubleConsumer onSubmit) {
        numberWithClicks(player,title,range.inputMin(),range.max(),current,onSubmit,range.decimals(),range);
    }
    private static void exact(Player player, Component title, double min, double max, double current,
                              int decimals, DoubleConsumer onSubmit) {
        exact(player,title,min,max,current,decimals,onSubmit,new NumericRange(min,max,decimals,NumericRange.Origin.PLUGIN));
    }
    private static void exact(Player player, Component title, double min, double max, double current,
                              int decimals, DoubleConsumer onSubmit,NumericRange range) {
        checkNumber(min, max, current);
        double initial = Math.clamp(current, min, max);
        var messages = MenuListener.instance().messages();
        var inputs = new java.util.ArrayList<DialogInput>();
        inputs.add(DialogInput.text("value", title).initial(formatNumber(initial, decimals)).maxLength(32).build());
        if (decimals == 0 && min < max) inputs.add(DialogInput.numberRange("slider",
                messages.get("gui.common.integer-slider"), (float)min, (float)max)
                .initial((float)initial).step(1f).labelFormat("%s: %s").build());
        try {
            show(player, title, inputs, response -> {
                try {
                    String text = response.getText("value");
                    double value;
                    if (decimals == 0 && text != null && text.isBlank()) {
                        Float slider = response.getFloat("slider");
                        if (slider == null || !Float.isFinite(slider)) throw new IllegalArgumentException("Missing slider");
                        value = parseInteger(formatNumber(slider, 0), (int)min, (int)max);
                    } else value = decimals == 0 ? parseInteger(text, (int)min, (int)max)
                            : parseDecimal(text, min, max, decimals);
                    if(!range.contains(value)) throw new IllegalArgumentException("Out of range");
                    onSubmit.accept(value);
                } catch (IllegalArgumentException invalid) {
                    messages.send(player, "gui.common.invalid-number", Placeholder.unparsed("min", formatNumber(min, decimals)),
                            Placeholder.unparsed("max", formatNumber(max, decimals)), Placeholder.unparsed("decimals", Integer.toString(decimals)));
                }
            }, "submit",NumericInputs.lore(range));
        } catch (UnsupportedOperationException unsupported) {
            numberWithClicks(player, title, min, max, initial, onSubmit, decimals,range);
        }
    }
    public static void number(Player player, Component title, double min, double max,
                              double current, DoubleConsumer onSubmit) {
        checkNumber(min, max, current);
        double initial = Math.clamp(current, min, max);
        // Dialog sliders use floats; the click alternative retains double precision.
        if (min == max || !Float.isFinite((float) min) || !Float.isFinite((float) max)
                || (float) min >= (float) max) {
            numberWithClicks(player, title, min, max, initial, onSubmit);
            return;
        }
        var input = DialogInput.numberRange("value", title, (float) min, (float) max)
                .initial((float) initial).build();
        try {
            show(player, title, List.of(input), response -> {
                Float value = response.getFloat("value");
                if (value != null && Float.isFinite(value)) { onSubmit.accept(Math.clamp(value.doubleValue(), min, max)); }
            }, "submit",NumericInputs.lore(new NumericRange(min,max,2,NumericRange.Origin.PLUGIN)));
        } catch (UnsupportedOperationException exception) {
            numberWithClicks(player, title, min, max, initial, onSubmit);
        }
    }
    public static void text(Player player, Component title, String current, int maxLength,
                            Consumer<String> onSubmit) {
        if (maxLength < 1) { throw new IllegalArgumentException("maxLength must be positive"); }
        String initial = current == null ? "" : current.substring(0, Math.min(current.length(), maxLength));
        show(player, title, List.of(DialogInput.text("value", title).initial(initial).maxLength(maxLength).build()),
                response -> {
                    String value = response.getText("value");
                    if (value != null && value.length() <= maxLength) { onSubmit.accept(value); }
                }, "submit");
    }
    public static void confirm(Player player, Component question, Runnable onYes) {
        show(player, question, List.of(), response -> onYes.run(), "yes");
    }
    private static void show(Player player, Component title, List<DialogInput> inputs,
                             Consumer<DialogResponseView> submit, String submitKey) {
        show(player,title,inputs,submit,submitKey,List.of());
    }
    private static void show(Player player,Component title,List<DialogInput> inputs,
                             Consumer<DialogResponseView> submit,String submitKey,List<Component> body) {
        MenuListener services = MenuListener.instance();
        if (!player.hasPermission("customdungeons.admin.edit")) {
            services.messages().send(player, "gui.common.no-permission");
            return;
        }
        if (services.rejectReload(player)) return;
        Menu origin = player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu ? menu : null;
        UUID token = UUID.randomUUID();
        var options = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton yes = ActionButton.create(services.messages().get("gui.common." + submitKey), null, 150,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player actor && actor.getUniqueId().equals(player.getUniqueId())) {
                        services.later(() -> finish(player, token, origin, () -> submit.accept(response)));
                    }
                }, options));
        ActionButton cancel = ActionButton.create(services.messages().get("gui.common.cancel"), null, 150,
                DialogAction.customClick((response, audience) -> {
                    if (audience instanceof Player actor && actor.getUniqueId().equals(player.getUniqueId())) {
                        services.later(() -> finish(player, token, origin, () -> {}));
                    }
                }, options));
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).canCloseWithEscape(false).inputs(inputs)
                        .body(body.stream().map(DialogBody::plainMessage).toList())
                        .afterAction(DialogBase.DialogAfterAction.CLOSE).build())
                .type(DialogType.confirmation(yes, cancel)));
        pending.begin(player.getUniqueId(), token);
        try {
            // Attach before opening so errors and synchronous abandonment also cancel the timer.
            pending.timeout(player.getUniqueId(), token, org.bukkit.Bukkit.getScheduler().runTaskLater(
                    org.bukkit.plugin.java.JavaPlugin.getPlugin(dev.dasan.customdungeons.CustomDungeonsPlugin.class),
                    () -> {
                        if (pending.matches(player.getUniqueId(), token)) {
                            player.closeDialog();
                            finish(player, token, origin, () -> {});
                        }
                    }, 12_000L));
            player.closeInventory();
            player.showDialog(dialog);
        } catch (RuntimeException exception) {
            finish(player, token, origin, () -> {});
            throw exception;
        } finally {
            pending.shown(player.getUniqueId(), token);
        }
    }
    private static void finish(Player player, UUID token, Menu origin, Runnable submit) {
        if (!pending.finish(player.getUniqueId(), token)) { return; }
        if (!player.isOnline() || !player.hasPermission("customdungeons.admin.edit")) {
            MenuListener.instance().editLocks().releaseAll(player.getUniqueId());
            return;
        }
        if (MenuListener.instance().rejectReload(player)) return;
        try { submit.run(); }
        finally {
            if (origin != null && !(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)) {
                origin.open();
            } else if (origin == null) { MenuListener.instance().editLocks().releaseAll(player.getUniqueId()); }
        }
    }
    /** A dialog keeps its origin draft and edit lock until submission or cancellation. */
    public static boolean pending(UUID player) { return pending.active(player); }
    static boolean inventoryClosed(UUID player) { return pending.inventoryClosed(player); }
    static void abandon(UUID player) { pending.abandon(player); }
    static void release(UUID player) { pending.release(player); }
    /** Cancel only our own dialog and invalidate its delayed submission. */
    public static void cancel(Player player) {
        if (!pending.active(player.getUniqueId())) return;
        pending.abandon(player.getUniqueId());
        player.closeDialog();
    }

    /** Explicit alternative for numerical input: +/-1 or +/-10 with shift, then Save. */
    public static void numberWithClicks(Player player, Component title, double min, double max,
                                        double current, DoubleConsumer onSubmit) {
        numberWithClicks(player, title, min, max, current, onSubmit, 2);
    }
    public static void numberWithClicks(Player player, Component title, double min, double max,
                                         double current, DoubleConsumer onSubmit, int decimals) {
        numberWithClicks(player,title,min,max,current,onSubmit,decimals,new NumericRange(min,max,decimals,NumericRange.Origin.PLUGIN));
    }
    private static void numberWithClicks(Player player,Component title,double min,double max,
                                         double current,DoubleConsumer onSubmit,int decimals,NumericRange range) {
        checkNumber(min, max, current);
        Menu origin = player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu ? menu : null;
        new Menu(player, title, 3) {
            @Override protected Material borderMaterial() { return Material.LIGHT_GRAY_STAINED_GLASS_PANE; }
            private double value = Math.clamp(current, min, max);
            @Override protected void render() {
                set(12, adjust(Material.RED_DYE, "decrease", -1));
                set(4, GuiTheme.information(Material.COMPARATOR, MenuListener.instance().messages().get("gui.common.value",
                        Placeholder.unparsed("value", formatNumber(value, decimals))),
                        java.util.stream.Stream.concat(java.util.stream.Stream.of(MenuListener.instance().messages().get("gui.common.number-summary")),NumericInputs.lore(range).stream()).toList()));
                set(14, adjust(Material.LIME_DYE, "increase", 1));
            }
            private Button adjust(Material material, String key, int direction) {
                return NumericInputs.decorate(Button.of(material, MenuListener.instance().messages().get("gui.common." + key),
                        List.of(MenuListener.instance().messages().get("gui.common.number-lore")), (p, click) -> {
                            value = Math.clamp(value + direction * (click.isShiftClick() ? 10 : 1), min, max);
                            refresh();
                        }),range);
            }
            @Override protected Menu parent() { return origin; }
            @Override protected Runnable onSave() {
                return () -> {
                    if(!range.contains(value)) {
                        MenuListener.instance().messages().send(player,"gui.common.invalid-number",Placeholder.unparsed("min",range.format(range.min())),
                                Placeholder.unparsed("max",range.format(range.max())),Placeholder.unparsed("decimals",Integer.toString(range.decimals())));
                        return;
                    }
                    onSubmit.accept(value);
                    MenuListener.instance().later(() -> {
                        if (player.getOpenInventory().getTopInventory() == getInventory()) {
                            if (origin != null) { origin.open(); } else { player.closeInventory(); }
                        }
                    });
                };
            }
        }.open();
    }
    private static void checkNumber(double min, double max, double current) {
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(current) || min > max) {
            throw new IllegalArgumentException("Invalid numeric input bounds");
        }
    }
}
