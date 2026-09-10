package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record ShadowBodySettings(
        int durationTicks,
        boolean breakOnAttack,
        boolean hideArmor,
        int cooldownTicks
) {

    public static ShadowBodySettings from(ConfigurationSection section, ShadowBodySettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new ShadowBodySettings(
                Math.max(1, section.getInt("duration-ticks", fallback.durationTicks())),
                section.getBoolean("break-on-attack", fallback.breakOnAttack()),
                section.getBoolean("hide-armor", fallback.hideArmor()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }
}
