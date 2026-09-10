package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

public record SolarBloomSettings(
        int maxCharges,
        int chargeRegenMinutes
) {

    public long regenMillis() {
        return chargeRegenMinutes * 60_000L;
    }

    public static SolarBloomSettings from(ConfigurationSection section, SolarBloomSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new SolarBloomSettings(
                Math.max(1, section.getInt("max-charges", fallback.maxCharges())),
                Math.max(1, section.getInt("charge-regen-minutes", fallback.chargeRegenMinutes()))
        );
    }
}
