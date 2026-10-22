package dev.dasan.customdungeons.ability.impl.borrowed;

import dev.dasan.customdungeons.ability.*;
import dev.dasan.customdungeons.model.TargetMode;
import dev.dasan.customdungeons.model.MobTemplate;
import dev.dasan.customdungeons.runtime.ActiveMob;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;

public final class SummonVexesAbility implements Ability, Listener {
    private static final MobTemplate VEX = new MobTemplate(
            "__vex","VEX","",0,0,0,0,0,Map.of(),List.of(),List.of(),List.of(),false,"RED",null,List.of(),false);
    private final Map<Vex, ActiveMob> owners = new WeakHashMap<>();
    public String id() { return "summon_vexes"; }
    public Material icon() { return Material.VEX_SPAWN_EGG; }
    public List<ParamSpec> params() { return List.of(
        new ParamSpec("count", ParamType.INT, 3, 1, 16),
        new ParamSpec("lifetimeSeconds", ParamType.DOUBLE, 30.0, 0.05, 300)); }
    public void execute(AbilityContext ctx) {
        int lifetime = (int) Math.round(ctx.params().getDouble("lifetimeSeconds") * 20);
        for (int i = 0; i < ctx.params().getInt("count"); i++) {
            var at = ctx.caster().entity().getLocation().clone().add(0, 1, 0);
            if (!BorrowedAbilitiesA.inRoom(ctx.caster(), at)) continue;
            var minion = ctx.session().spawnMinionTemplate(VEX, at, ctx.caster());
            if(minion==null)break;
            if(!(minion.entity() instanceof Vex vex)) {
                dev.dasan.customdungeons.mob.MobHealth.terminate(minion.entity(),false);continue;
            }
            BorrowedAbilitiesA.mark(vex, ctx.caster(), "__vex");
            vex.setOwner(ctx.caster().entity());
            vex.setBound(at);
            vex.setLimitedLifetime(true);
            vex.setLimitedLifetimeTicks(lifetime);
            owners.put(vex, ctx.caster());
            follow(vex, ctx.caster(), lifetime);
        }
    }
    private void follow(Vex vex, ActiveMob owner, int remaining) {
        if (remaining <= 0 || !vex.isValid() || !owner.entity().isValid()
                || owner.entity().isDead() || owner.session().players().isEmpty()) {
            owners.remove(vex); dev.dasan.customdungeons.mob.MobHealth.terminate(vex,false); return;
        }
        if (!BorrowedAbilitiesA.inRoom(owner, vex.getLocation())) vex.teleport(owner.entity().getLocation());
        var targets = TargetSelector.select(owner, TargetMode.NEAREST, 64);
        vex.setTarget(targets.isEmpty() ? null : targets.getFirst());
        owner.session().scheduler().runLater(1, () -> follow(vex, owner, remaining - 1));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        var owner = owners.get(event.getDamager());
        if (owner != null && !BorrowedAbilitiesA.allowed(owner, event.getEntity())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void target(EntityTargetLivingEntityEvent event) {
        var owner = owners.get(event.getEntity());
        if (owner != null && event.getTarget() != null && !BorrowedAbilitiesA.allowed(owner, event.getTarget()))
            event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (owners.remove(event.getEntity()) != null) { event.getDrops().clear(); event.setDroppedExp(0); }
    }
}
