package dev.dasan.customdungeons.ability;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AbilityRegistry {
    private final Map<String, Ability> abilities = new LinkedHashMap<>();

    public void register(Ability a) {
        if (!a.id().matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Invalid ability id: " + a.id());
        }
        if (abilities.putIfAbsent(a.id(), a) != null) {
            throw new IllegalArgumentException("Duplicate ability id: " + a.id());
        }
    }
    public Optional<Ability> get(String id) { return Optional.ofNullable(abilities.get(id)); }
    public Collection<Ability> all() { return Collections.unmodifiableCollection(abilities.values()); }
}
