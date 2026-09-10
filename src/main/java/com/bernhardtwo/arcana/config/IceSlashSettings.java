package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record IceSlashSettings(
        double range,
        double arcDegrees,
        double slashDamage,
        double snowballDamage,
        double snowballSpeed,
        int freezeTicks,
        int slowDurationTicks,
        int cooldownTicks
) {

    public static IceSlashSettings from(ConfigurationSection section, IceSlashSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new IceSlashSettings(
                section.getDouble("range", fallback.range()),
                section.getDouble("arc-degrees", fallback.arcDegrees()),
                section.getDouble("slash-damage", fallback.slashDamage()),
                section.getDouble("snowball-damage", fallback.snowballDamage()),
                section.getDouble("snowball-speed", fallback.snowballSpeed()),
                section.getInt("freeze-ticks", fallback.freezeTicks()),
                section.getInt("slow-duration-ticks", fallback.slowDurationTicks()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
