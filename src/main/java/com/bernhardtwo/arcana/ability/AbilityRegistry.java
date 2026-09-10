package com.bernhardtwo.arcana.ability;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class AbilityRegistry {

    private final Map<String, Ability> abilities = new LinkedHashMap<>();

    public void register(Ability ability) {
        abilities.put(ability.id(), ability);
    }

    public Optional<Ability> find(String id) {
        return Optional.ofNullable(abilities.get(id));
    }

    public Collection<Ability> all() {
        return abilities.values();
    }
}
