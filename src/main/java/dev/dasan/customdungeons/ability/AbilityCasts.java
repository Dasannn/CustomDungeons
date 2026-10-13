package dev.dasan.customdungeons.ability;

import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Warning receipts owned by the engine. Direct/registered abilities keep the T01 contracts. */
public final class AbilityCasts {
    private AbilityCasts() {}
    private record Receipt(ActiveMob mob, double range, BooleanSupplier valid, Runnable started, Consumer<Boolean> cancelled) {}
    private static final Map<AbilityContext, Receipt> warnings = new WeakHashMap<>();
    private static final Set<AbilityContext> cancelledContexts = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<ActiveMob, Integer> counts = new WeakHashMap<>();

    static void watch(AbilityContext context, BooleanSupplier valid, Runnable started, Consumer<Boolean> cancelled) {
        watch(context,16,valid,started,cancelled);
    }
    static void watch(AbilityContext context,double range,BooleanSupplier valid,Runnable started,Consumer<Boolean> cancelled) {
        cancelledContexts.remove(context);
        counts.merge(context.caster(), 1, Integer::sum);
        warnings.put(context, new Receipt(context.caster(), range, valid, started, cancelled));
    }
    /** Actual selector range; direct invocations use the same default as the ability editor. */
    public static double range(AbilityContext context) {
        var receipt=warnings.get(context);return receipt==null?16:receipt.range();
    }
    public static boolean valid(AbilityContext context) {
        var receipt = warnings.get(context);
        return !cancelledContexts.contains(context) && (receipt == null || receipt.valid().getAsBoolean());
    }
    static int warningTicks(Ability ability,ParamValues params,int configured) {
        if(ability instanceof dev.dasan.customdungeons.ability.control.ControlAbility)return Math.max(20,configured);
        if(ability instanceof dev.dasan.customdungeons.ability.combat.CombatAbility)return dev.dasan.customdungeons.ability.combat.CombatService.warningTicks(ability.id(),params,configured);
        if(ability instanceof dev.dasan.customdungeons.ability.zone.ZoneAbility)return dev.dasan.customdungeons.ability.zone.ZoneService.warningTicks(ability.id(),params,configured);
        return Math.max(0,configured);
    }
    /** Last gate at the effect boundary, excluding only this cast's own lock/control. */
    public static boolean canBegin(AbilityContext context) {
        if (!valid(context)) {cancel(context,false);return false;}
        if (MobMovement.blocked(context)) {cancel(context,true);return false;}
        return true;
    }
    public static void started(AbilityContext context) {
        var receipt = remove(context);
        if (receipt != null) receipt.started().run();
    }
    /** Retry only temporary occupancy/pause; an escaped warning must be cancelled. */
    public static void cancel(AbilityContext context, boolean retry) {
        var receipt = remove(context);
        if (receipt != null) {cancelledContexts.add(context);receipt.cancelled().accept(retry);}
    }
    private static Receipt remove(AbilityContext context) {
        var receipt = warnings.remove(context);
        if (receipt != null) counts.computeIfPresent(receipt.mob(), (m, count) -> count > 1 ? count - 1 : null);
        return receipt;
    }
    static void cancelWarnings(ActiveMob mob) {
        var contexts=warnings.entrySet().stream().filter(e->e.getValue().mob()==mob).map(Map.Entry::getKey).toList();
        for(var context:contexts)cancel(context,false);
    }
    public static boolean warning(ActiveMob mob) {
        return counts.containsKey(mob);
    }
}
