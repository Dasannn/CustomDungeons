package dev.dasan.customdungeons.ability;
import java.util.List;
import org.bukkit.Material;
public interface Ability {
    String id();
    Material icon();
    List<ParamSpec> params();
    void execute(AbilityContext ctx);
}
