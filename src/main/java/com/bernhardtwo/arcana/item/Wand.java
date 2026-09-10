package com.bernhardtwo.arcana.item;

import org.bukkit.Material;

/**
 * Maps an item to up to three abilities. {@code leftClickAbility} may be null,
 * in which case left click does nothing for this wand.
 */
public record Wand(
        String id,
        String displayName,
        Material material,
        String rightClickAbility,
        String sneakRightClickAbility,
        String leftClickAbility
) {
}
