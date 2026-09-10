package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record TargetRules(
        boolean players,
        boolean hostiles,
        boolean passives,
        boolean armorStands,
        boolean respectClaims
) {

    public static TargetRules from(ConfigurationSection section) {
        if (section == null) {
            return new TargetRules(true, true, true, false, true);
        }
        return new TargetRules(
                section.getBoolean("players", true),
                section.getBoolean("hostiles", true),
                section.getBoolean("passives", true),
                section.getBoolean("armor-stands", false),
                section.getBoolean("respect-claims", true)
        );
    }
}
