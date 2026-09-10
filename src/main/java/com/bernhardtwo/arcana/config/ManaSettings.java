package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ManaSettings(
        boolean bar,
        double potionRestore,
        boolean recipeEnabled
) {

    public static ManaSettings from(ConfigurationSection section, ManaSettings fallback) {
        if (section == null) {
            return fallback;
        }
        ConfigurationSection potion = section.getConfigurationSection("potion");
        return new ManaSettings(
                section.getBoolean("bar", fallback.bar()),
                potion == null ? fallback.potionRestore() : Math.max(0.0, potion.getDouble("restore", fallback.potionRestore())),
                potion == null ? fallback.recipeEnabled() : potion.getBoolean("recipe-enabled", fallback.recipeEnabled())
        );
    }
}
