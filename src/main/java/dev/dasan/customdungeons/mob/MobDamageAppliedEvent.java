package dev.dasan.customdungeons.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;

/** Immutable receipt of one hit, before the bounded native mirror changes its amount. */
public final class MobDamageAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final LivingEntity entity;
    private final EntityDamageEvent damage;
    private final double amount, remaining;

    public MobDamageAppliedEvent(LivingEntity entity, EntityDamageEvent damage, double amount, double remaining) {
        this.entity = entity; this.damage = damage; this.amount = amount; this.remaining = remaining;
    }
    public LivingEntity entity() { return entity; }
    public EntityDamageEvent damage() { return damage; }
    public double amount() { return amount; }
    public double remaining() { return remaining; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
