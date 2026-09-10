package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record IceBreakerSettings(
        double range,
        double damage,
        int freezeTicks,
        int slowDurationTicks,
        int cooldownTicks
) {

    public static IceBreakerSettings from(ConfigurationSection section, IceBreakerSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new IceBreakerSettings(
                section.getDouble("range", fallback.range()),
                section.getDouble("damage", fallback.damage()),
                section.getInt("freeze-ticks", fallback.freezeTicks()),
                section.getInt("slow-duration-ticks", fallback.slowDurationTicks()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
