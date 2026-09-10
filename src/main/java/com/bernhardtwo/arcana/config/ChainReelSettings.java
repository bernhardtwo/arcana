package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ChainReelSettings(
        double pullSelfSpeed,
        double pullEntitySpeed,
        double maxVelocity,
        int fallGraceTicks,
        int cooldownTicks
) {

    public static ChainReelSettings from(ConfigurationSection section, ChainReelSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new ChainReelSettings(
                Math.max(0.1, section.getDouble("pull-self-speed", fallback.pullSelfSpeed())),
                Math.max(0.0, section.getDouble("pull-entity-speed", fallback.pullEntitySpeed())),
                Math.max(0.1, section.getDouble("max-velocity", fallback.maxVelocity())),
                Math.max(0, section.getInt("fall-grace-ticks", fallback.fallGraceTicks())),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
