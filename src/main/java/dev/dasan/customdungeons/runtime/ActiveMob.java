package dev.dasan.customdungeons.runtime;

import dev.dasan.customdungeons.mob.MobHost;
import dev.dasan.customdungeons.model.AbilityInstance;
import dev.dasan.customdungeons.model.ComboDef;
import dev.dasan.customdungeons.model.MobTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/** Mutable state owned by the session ticker and main-thread event handlers. */
public final class ActiveMob {
    private final Mob entity;
    private final MobTemplate template;
    private final MobHost session;
    private final List<AbilityInstance> abilities;
    private final List<ComboDef> combos;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final List<ItemStack> stolenItems = new ArrayList<>();
    private int phaseIndex = -1;
    private long invulnerableUntil;

    public ActiveMob(Mob entity, MobTemplate template, MobHost session) {
        this.entity = entity;
        this.template = template;
        this.session = session;
        abilities = new ArrayList<>(template.abilities());
        combos = new ArrayList<>(template.combos());
    }

    public Mob entity() { return entity; }
    public MobTemplate template() { return template; }
    public MobHost session() { return session; }
    public List<AbilityInstance> abilities() { return abilities; }
    public List<ComboDef> combos() { return combos; }
    public int phaseIndex() { return phaseIndex; }
    public void phaseIndex(int i) { phaseIndex = i; }
    public long invulnerableUntil() { return invulnerableUntil; }
    public void invulnerableUntil(long tick) { invulnerableUntil = tick; }
    public boolean ready(String key, long now) {
        Long readyAt = cooldowns.get(key);
        return readyAt == null || now >= readyAt;
    }
    public void cooldown(String key, long readyAt) { cooldowns.put(key, readyAt); }
    public List<ItemStack> stolenItems() { return stolenItems; }
}
