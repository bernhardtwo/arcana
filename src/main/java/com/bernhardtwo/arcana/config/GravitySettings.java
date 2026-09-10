package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record GravitySettings(
        double radius,
        double strength,
        double lift,
        double maxVelocity,
        int cooldownTicks,
        boolean cancelFallDamagePlayers,
        boolean cancelFallDamageMobs,
        int fallGraceTicks
) {

    public static GravitySettings from(ConfigurationSection section, GravitySettings fallback) {
        if (section == null) {
            return fallback;
        }
        ConfigurationSection fall = section.getConfigurationSection("cancel-fall-damage");
        return new GravitySettings(
                section.getDouble("radius", fallback.radius()),
                section.getDouble("strength", fallback.strength()),
                section.getDouble("lift", fallback.lift()),
                section.getDouble("max-velocity", fallback.maxVelocity()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                fall == null ? fallback.cancelFallDamagePlayers() : fall.getBoolean("players", fallback.cancelFallDamagePlayers()),
                fall == null ? fallback.cancelFallDamageMobs() : fall.getBoolean("mobs", fallback.cancelFallDamageMobs()),
                section.getInt("fall-grace-ticks", fallback.fallGraceTicks())
        );
    }
}
