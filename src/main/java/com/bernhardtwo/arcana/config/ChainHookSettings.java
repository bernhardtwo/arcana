package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ChainHookSettings(
        double range,
        double travelSpeed,
        int holdTicks,
        int cooldownTicks
) {

    public static ChainHookSettings from(ConfigurationSection section, ChainHookSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new ChainHookSettings(
                Math.max(1.0, section.getDouble("range", fallback.range())),
                Math.max(0.1, section.getDouble("travel-speed", fallback.travelSpeed())),
                Math.max(1, section.getInt("hold-ticks", fallback.holdTicks())),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
