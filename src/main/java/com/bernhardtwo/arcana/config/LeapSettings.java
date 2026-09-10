package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record LeapSettings(
        double jumpPower,
        double forwardBoost,
        int cooldownTicks,
        int fallGraceTicks
) {

    public static LeapSettings from(ConfigurationSection section, LeapSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new LeapSettings(
                section.getDouble("jump-power", fallback.jumpPower()),
                section.getDouble("forward-boost", fallback.forwardBoost()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                section.getInt("fall-grace-ticks", fallback.fallGraceTicks())
        );
    }
}
