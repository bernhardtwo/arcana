package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ShadowSwapSettings(
        double range,
        boolean allowPlayers,
        int channelTicks,
        double channelMinSpeedFactor,
        boolean cancelOnDamage,
        boolean warnTarget,
        int failedCooldownTicks,
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
                Math.max(1, section.getInt("channel-ticks", fallback.channelTicks())),
                Math.min(1.0, Math.max(0.0, section.getDouble("channel-min-speed-factor", fallback.channelMinSpeedFactor()))),
                section.getBoolean("cancel-on-damage", fallback.cancelOnDamage()),
                section.getBoolean("warn-target", fallback.warnTarget()),
                Math.max(0, section.getInt("failed-cooldown-ticks", fallback.failedCooldownTicks())),
                section.getInt("cooldown-ticks", fallback.cooldownTicks()),
                section.getInt("fall-grace-ticks", fallback.fallGraceTicks())
        );
    }
}
