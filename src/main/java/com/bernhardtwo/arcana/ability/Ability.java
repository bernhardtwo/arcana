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

    /**
     * Abilities sharing a non-null group cannot be cast while any other member
     * of that group is on cooldown. Null means no cross blocking.
     */
    default String lockoutGroup() {
        return null;
    }

    /**
     * Precondition checked before cooldowns. Returning false refuses the cast
     * silently, without a message and without starting a cooldown.
     */
    default boolean canCast(Player caster) {
        return true;
    }
}
