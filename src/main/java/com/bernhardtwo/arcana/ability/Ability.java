package com.bernhardtwo.arcana.ability;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface Ability {

    String id();

    String displayName();

    int cooldownTicks();

    /**
     * Runs the ability. False means it refused and nothing happened, so the
     * caller starts no cooldown and spends no mana. A dismiss or a cancel of a
     * running instance is also false: ending something costs no mana.
     */
    boolean cast(Player caster);

    /** Cast with the block the player clicked, or null for a click in the air or on an entity. */
    default boolean cast(Player caster, Block clicked) {
        return cast(caster);
    }

    /** Short note appended to the wand lore, such as "toggle". Null for none. */
    default String loreHint() {
        return null;
    }

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

    /**
     * True while this ability is running for the player and a cast should reach
     * the ability anyway, to dismiss or replace the running instance. Such a
     * cast skips the lockout and cooldown checks; the ability itself decides
     * what ending the old instance costs.
     */
    default boolean replacesActiveCast(Player player) {
        return false;
    }

    /** True while this ability is running for the player and wants the rest of its group blocked meanwhile. */
    default boolean blocksGroupWhileActive(Player player) {
        return false;
    }
}
