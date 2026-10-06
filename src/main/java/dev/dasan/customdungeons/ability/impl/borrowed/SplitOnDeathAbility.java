package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import java.util.List;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

public final class SplitOnDeathAbility implements Ability {
    private static final NamespacedKey GENERATION = new NamespacedKey("customdungeons", "split_generation");
    public String id() { return "split_on_death"; }
    public Material icon() { return Material.SLIME_BALL; }
    public List<ParamSpec> params() { return List.of(
            new ParamSpec("count", ParamType.INT, 2, 1, 8),
            new ParamSpec("scaleFactor", ParamType.DOUBLE, 0.5, 0.1, 0.9),
            new ParamSpec("healthFactor", ParamType.DOUBLE, 0.5, 0.1, 0.9)); }
    public void execute(AbilityContext ctx) {
        var entity = ctx.caster().entity();
        if (!(ctx.cause() instanceof EntityDeathEvent death && death.getEntity().equals(entity))
                && !entity.isDead()) return;
        var data = entity.getPersistentDataContainer();
        if (data.has(GENERATION, PersistentDataType.BYTE)) return;
        data.set(GENERATION, PersistentDataType.BYTE, (byte) 0);
        var at = entity.getLocation();
        if (!BorrowedAbilitiesC.inRoom(ctx, at)) return;
        for (int i = 0; i < ctx.params().getInt("count"); i++) {
            var child = ctx.session().spawnMinion(ctx.caster().template().id(), at.clone(), ctx.caster());
            if (child == null) break; // The session enforces its live-mob cap.
            child.entity().getPersistentDataContainer().set(GENERATION, PersistentDataType.BYTE, (byte) 1);
            child.abilities().removeIf(a -> a.abilityId().equals(id()));
            child.combos().removeIf(c -> c.steps().stream().anyMatch(s -> s.abilityId().equals(id())));
            var health = child.entity().getAttribute(Attribute.MAX_HEALTH);
            if (health != null) {
                health.setBaseValue(Math.max(1, health.getValue() * ctx.params().getDouble("healthFactor")));
                child.entity().setHealth(Math.min(health.getValue(), health.getBaseValue()));
            }
            var scale = child.entity().getAttribute(Attribute.SCALE);
            if (scale != null) scale.setBaseValue(Math.max(0.0625,
                    scale.getValue() * ctx.params().getDouble("scaleFactor")));
        }
    }
}
