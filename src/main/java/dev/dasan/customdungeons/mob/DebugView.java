package dev.dasan.customdungeons.mob;

import dev.dasan.customdungeons.ability.AbilityCasts;
import dev.dasan.customdungeons.ability.AbilityEngine;
import dev.dasan.customdungeons.ability.combat.CombatService;
import dev.dasan.customdungeons.ability.control.ControlService;
import dev.dasan.customdungeons.intelligence.IntelligenceService;
import dev.dasan.customdungeons.runtime.ActiveMob;
import dev.dasan.customdungeons.text.Messages;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ravager;

/** Presentation data does not own mob state or an additional scheduler. */
public final class DebugView {
    // The sole global debug state. No player references, mob index or presentation history.
    private static final Set<UUID> users=new HashSet<>();
    private DebugView() {}
    public static boolean active() {return !users.isEmpty();}
    public static boolean enabled(UUID player) {return users.contains(player);}
    public static boolean toggle(Player player) {
        UUID id=player.getUniqueId();
        if(users.remove(id)) {player.sendActionBar(Component.empty());return false;}
        if(!player.hasPermission("customdungeons.admin.debug"))return false;
        users.add(id);return true;
    }
    public static void forget(UUID player) {users.remove(player);}
    public static void clear() {users.clear();}

    /** Called once by the host's engine, without looking up players unless debug is in use. */
    public static void tick(Collection<ActiveMob> mobs, AbilityEngine engine, Messages messages, long tick) {
        if(users.isEmpty() || Math.floorMod(tick,5)!=0)return;
        for(var iterator=users.iterator();iterator.hasNext();) {
            var player=Bukkit.getPlayer(iterator.next());
            if(player==null || !player.isOnline()) {iterator.remove();continue;}
            if(!player.hasPermission("customdungeons.admin.debug")) {
                iterator.remove();player.sendActionBar(Component.empty());continue;
            }
            // Paper traces collision boxes and stops at blocks; vanilla/other-host entities occlude.
            var looked=player.getTargetEntity(24,false);
            if(looked==null || !MobKeys.isDungeonMob(looked) || looked instanceof LivingEntity living && living.isDead()) {
                player.sendActionBar(Component.empty());continue;
            }
            for(var mob:mobs)if(mob.entity().equals(looked) && mob.entity().isValid() && !mob.entity().isDead()) {
                player.sendActionBar(format(messages,snapshot(mob,engine,messages,tick)));break;
            }
        }
    }

    public record Snapshot(Component name, Component target, boolean ai, String state, String cause,
                           Component causeAbility, Component discarded, Component reason, List<Component> adaptations) {}
    public static Component format(Messages messages, Snapshot snapshot) {
        Component adaptations=Component.empty();
        for(int i=0;i<snapshot.adaptations().size();i++) {
            if(i>0)adaptations=adaptations.append(messages.get("debug.separator"));
            adaptations=adaptations.append(snapshot.adaptations().get(i));
        }
        if(snapshot.adaptations().isEmpty())adaptations=messages.get("debug.none");
        return messages.get("debug.line",
                Placeholder.component("name",snapshot.name()),Placeholder.component("target",snapshot.target()),
                Placeholder.component("ai",messages.get(snapshot.ai()?"debug.yes":"debug.no")),
                Placeholder.component("state",messages.get("debug.state."+snapshot.state())),
                Placeholder.component("cause",messages.get("debug.cause."+snapshot.cause(),Placeholder.component("ability",snapshot.causeAbility()))),
                Placeholder.component("discarded",snapshot.discarded()),Placeholder.component("reason",snapshot.reason()),
                Placeholder.component("adaptations",adaptations));
    }
    private record Status(String state,String cause,String ability) {}
    private static Status status(ActiveMob mob,AbilityEngine engine,long tick) {
        var pause=CombatService.pauseInfo(mob);
        if(pause!=null && pause.stunned())return new Status("stunned",pause.abilityId().equals("charge")?"collision":"interrupted",pause.abilityId());
        if(mob.entity() instanceof Ravager ravager && ravager.getStunnedTicks()>0)return new Status("stunned","native-stun",null);
        if(AbilityCasts.warning(mob))return new Status("warning","ability",engine.castingAbility(mob));
        if(pause!=null)return new Status("paused","ability",pause.abilityId());
        var control=ControlService.controllingAbility(mob);
        if(control!=null)return new Status("controlling","ability",control);
        if(mob.entity() instanceof Ravager ravager && ravager.getRoarTicks()>0)return new Status("paused","native-roar",null);
        var brain=IntelligenceService.brain(mob);
        var adaptation=brain==null?null:brain.warning(tick);
        if(adaptation!=null)return new Status("warning","ability","adaptation:"+adaptation.rule().id());
        if(!mob.entity().hasAI())return new Status("paused","ai-disabled",null);
        return new Status("active","none",null);
    }
    public static Snapshot snapshot(ActiveMob mob,AbilityEngine engine,Messages messages,long tick) {
        var entity=mob.entity();var name=entity.customName();if(name==null)name=entity.name();
        var target=entity.getTarget();var targetName=target==null?messages.get("debug.none"):
                target instanceof Player p?Component.text(p.getName()):target.name();
        var status=status(mob,engine,tick);var discarded=engine.lastDiscarded(mob);
        var brain=IntelligenceService.brain(mob);
        var adaptations=brain==null?List.<Component>of():brain.active().stream().filter(a->a.begins()<=tick && tick<a.expires())
                .map(a->messages.get("intelligence.adaptation."+a.rule().id())).toList();
        return new Snapshot(name,targetName,entity.hasAI(),status.state(),status.cause(),abilityName(messages,status.ability()),
                abilityName(messages,discarded==null?null:discarded.abilityId()),
                messages.get(discarded==null?"debug.none":"debug.reason."+discarded.reason().key()),adaptations);
    }
    private static Component abilityName(Messages messages,String id) {
        if(id==null)return messages.get("debug.none");
        if(id.startsWith("combo:"))return messages.get("debug.combo",Placeholder.unparsed("id",id.substring(6)));
        if(id.startsWith("adaptation:"))return messages.get("intelligence.adaptation."+id.substring(11));
        return messages.get("ability."+id+".name");
    }
}
