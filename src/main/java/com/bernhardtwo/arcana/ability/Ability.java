package com.bernhardtwo.arcana.ability;

import org.bukkit.entity.Player;

public interface Ability {

    String id();

    String displayName();

    int cooldownTicks();

    void cast(Player caster);

    default String permission() {
        return "arcana.use." + id();
    }
}
