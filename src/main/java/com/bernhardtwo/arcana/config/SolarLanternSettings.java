package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record SolarLanternSettings(
        int durationTicks,
        int cooldownTicks,
        double orbitRadius
) {

    public static SolarLanternSettings from(ConfigurationSection section, SolarLanternSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new SolarLanternSettings(
                Math.max(1, section.getInt("duration-ticks", fallback.durationTicks())),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                section.getDouble("orbit-radius", fallback.orbitRadius())
        );
    }
}
