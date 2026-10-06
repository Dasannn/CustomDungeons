package dev.dasan.customdungeons.ability;
import java.util.List;
import org.bukkit.Material;
/**
 * One ability with a unique snake_case id and declared, bounded parameters.
 * Parameter metadata drives the GUI; implementations must use ctx.params().
 * Targets are session participants selected by the engine. Use Effects for
 * attributed damage, nearby visuals and marked projectiles, and the session
 * scheduler for delays. Never create a per-mob task, break blocks, ignite terrain,
 * or perform disk/database I/O. Temporary blocks go through session.tempBlocks().
 * Implementations may be shared by multiple mobs; any mutable per-caster state
 * must be cleared when the caster dies or the owning session ends.
 */
public interface Ability {
    String id();
    Material icon();
    List<ParamSpec> params();
    void execute(AbilityContext ctx);
}
