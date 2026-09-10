package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ShadowBlinkSettings(
        double range,
        int cooldownTicks,
        int fallGraceTicks
) {

    public static ShadowBlinkSettings from(ConfigurationSection section, ShadowBlinkSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new ShadowBlinkSettings(
                Math.max(1.0, section.getDouble("range", fallback.range())),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                section.getInt("fall-grace-ticks", fallback.fallGraceTicks())
        );
    }
}
