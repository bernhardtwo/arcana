package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record SolarZenithSettings(
        int height,
        double radius,
        int burnTicks,
        double healAmount,
        int tickInterval,
        int markerInterval,
        int cooldownTicks
) {

    public static SolarZenithSettings from(ConfigurationSection section, SolarZenithSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new SolarZenithSettings(
                section.getInt("height", fallback.height()),
                section.getDouble("radius", fallback.radius()),
                section.getInt("burn-ticks", fallback.burnTicks()),
                section.getDouble("heal-amount", fallback.healAmount()),
                Math.max(1, section.getInt("tick-interval", fallback.tickInterval())),
                Math.max(1, section.getInt("marker-interval", fallback.markerInterval())),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
