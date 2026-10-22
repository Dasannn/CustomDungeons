package dev.dasan.customdungeons.mob;

import java.util.*;
import org.jspecify.annotations.Nullable;

/** Main-thread capacity shared by tracked creatures, in-flight spawns and auxiliary creatures. */
public final class MobCapacity {
    private final Set<Reservation> reservations=new HashSet<>();
    public @Nullable Reservation reserve(int occupied,int maximum) {
        if((long)occupied+reservations.size()>=maximum)return null;
        var slot=new Reservation();reservations.add(slot);return slot;
    }
    /** Spawn initializers bind before events; adoption must not reserve the same creature twice. */
    public boolean owns(UUID entity) { return reservations.stream().anyMatch(slot->entity.equals(slot.entity)); }
    /** Transfers a bound reservation to the caller's tracked-creature count. */
    public void commit(UUID entity) { reservations.removeIf(slot->entity.equals(slot.entity)); }
    public final class Reservation implements AutoCloseable {
        private UUID entity;
        private Reservation() {}
        public void bind(UUID entity) { this.entity=Objects.requireNonNull(entity); }
        @Override public void close() { reservations.remove(this); }
    }
}
