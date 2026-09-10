package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ItemSettings(
        boolean protectFromCrafting,
        boolean indestructibleWhenDropped
) {

    public static ItemSettings from(ConfigurationSection section) {
        if (section == null) {
            return new ItemSettings(true, true);
        }
        return new ItemSettings(
                section.getBoolean("protect-from-crafting", true),
                section.getBoolean("indestructible-when-dropped", true)
        );
    }
}
