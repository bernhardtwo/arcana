package com.bernhardtwo.arcana.item;

import org.bukkit.Material;

public record Wand(
        String id,
        String displayName,
        Material material,
        String primaryAbility,
        String secondaryAbility
) {
}
