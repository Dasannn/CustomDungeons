package dev.dasan.customdungeons.ability;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.jspecify.annotations.Nullable;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.runtime.SessionContext;
public record AbilityContext(ActiveMob caster, List<LivingEntity> targets, ParamValues params,
                             SessionContext session, @Nullable Event cause) {
    public AbilityContext { targets = List.copyOf(targets); }
}
