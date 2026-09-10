package com.bernhardtwo.arcana.config;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public record SolarZenithSettings(
        int height,
        double radius,
        int burnTicks,
        double healAmount,
        int tickInterval,
        int markerInterval,
        double markerHeight,
        int markerRings,
        int markerPointsPerRing,
        float markerParticleSize,
        Color markerColorA,
        Color markerColorB,
        boolean markerForceRender,
        double markerGradientCycles,
        double markerGradientSpeed,
        int cooldownTicks
) {

    public static SolarZenithSettings from(ConfigurationSection section, SolarZenithSettings fallback, Logger logger) {
        if (section == null) {
            return fallback;
        }
        return new SolarZenithSettings(
                section.getInt("height", fallback.height()),
                section.getDouble("radius", fallback.radius()),
                section.getInt("burn-ticks", fallback.burnTicks()),
                section.getDouble("heal-amount", fallback.healAmount()),
                Math.max(1, section.getInt("tick-interval", fallback.tickInterval())),
                Math.max(1, section.getInt("marker-interval", fallback.markerInterval())),
                Math.max(0.0, section.getDouble("marker-height", fallback.markerHeight())),
                Math.max(1, section.getInt("marker-rings", fallback.markerRings())),
                Math.max(1, section.getInt("marker-points-per-ring", fallback.markerPointsPerRing())),
                (float) Math.max(0.1, section.getDouble("marker-particle-size", fallback.markerParticleSize())),
                color(section, "marker-color-a", fallback.markerColorA(), logger),
                color(section, "marker-color-b", fallback.markerColorB(), logger),
                section.getBoolean("marker-force-render", fallback.markerForceRender()),
                Math.max(0.0, section.getDouble("marker-gradient-cycles", fallback.markerGradientCycles())),
                section.getDouble("marker-gradient-speed", fallback.markerGradientSpeed()),
                section.getInt("cooldown-ticks", fallback.cooldownTicks())
        );
    }

    /** Parses an "RRGGBB" hex string, with or without a leading '#'. Invalid values warn and keep the fallback. */
    private static Color color(ConfigurationSection section, String key, Color fallback, Logger logger) {
        String raw = section.getString(key);
        if (raw == null) {
            return fallback;
        }
        String hex = raw.trim().startsWith("#") ? raw.trim().substring(1) : raw.trim();
        if (hex.length() == 6) {
            try {
                return Color.fromRGB(Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
                // Falls through to the warning below.
            }
        }
        logger.warning("Invalid color in solar_zenith." + key + ": \"" + raw + "\", expected RRGGBB hex. Using default.");
        return fallback;
    }
}
