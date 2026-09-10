package com.bernhardtwo.arcana.item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class WandRegistry {

    private final Map<String, Wand> wands = new LinkedHashMap<>();

    public void register(Wand wand) {
        wands.put(wand.id(), wand);
    }

    public Optional<Wand> find(String id) {
        return Optional.ofNullable(wands.get(id));
    }

    public Collection<Wand> all() {
        return wands.values();
    }
}
