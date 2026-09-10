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

    /**
     * False for abilities with a duration, which start their own cooldown when
     * they end instead of when they are cast.
     */
    default boolean startsCooldownOnCast() {
        return true;
    }

    /** True while this ability is running for the player and wants the rest of its group blocked meanwhile. */
    default boolean blocksGroupWhileActive(Player player) {
        return false;
    }
}
