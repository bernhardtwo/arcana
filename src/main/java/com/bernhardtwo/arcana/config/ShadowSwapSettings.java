package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ShadowSwapSettings(
        double range,
        boolean allowPlayers,
        int cooldownTicks,
        int fallGraceTicks
) {

    public static ShadowSwapSettings from(ConfigurationSection section, ShadowSwapSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new ShadowSwapSettings(
                Math.max(1.0, section.getDouble("range", fallback.range())),
                section.getBoolean("allow-players", fallback.allowPlayers()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                section.getInt("fall-grace-ticks", fallback.fallGraceTicks())
        );
    }
}
