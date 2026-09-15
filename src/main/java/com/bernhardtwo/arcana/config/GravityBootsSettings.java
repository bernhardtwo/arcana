package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Levitation Boots. The mana keys are deliberately not {@code mana-cost}: the
 * ladder limits itself, so the pool-size correction applied to cast costs
 * never applies here.
 */
public record GravityBootsSettings(
        double manaPerSecondBase,
        double manaPerSecondStep,
        int stepIntervalSeconds,
        int maxStep,
        int decayIntervalSeconds,
        int tickPeriod,
        float flySpeed,
        int graceSlowFallingSeconds,
        boolean respectClaims,
        boolean recipeEnabled
) {

    public static GravityBootsSettings from(ConfigurationSection section, GravityBootsSettings fallback) {
        if (section == null) {
            return fallback;
        }
        return new GravityBootsSettings(
                Math.max(0.0, section.getDouble("mana-per-second-base", fallback.manaPerSecondBase())),
                Math.max(0.0, section.getDouble("mana-per-second-step", fallback.manaPerSecondStep())),
                Math.max(1, section.getInt("step-interval-seconds", fallback.stepIntervalSeconds())),
                Math.max(0, section.getInt("max-step", fallback.maxStep())),
                Math.max(1, section.getInt("decay-interval-seconds", fallback.decayIntervalSeconds())),
                Math.max(1, section.getInt("tick-period", fallback.tickPeriod())),
                (float) Math.min(1.0, Math.max(0.0, section.getDouble("fly-speed", fallback.flySpeed()))),
                Math.max(0, section.getInt("grace-slow-falling-seconds", fallback.graceSlowFallingSeconds())),
                section.getBoolean("respect-claims", fallback.respectClaims()),
                section.getBoolean("recipe-enabled", fallback.recipeEnabled())
        );
    }

    /** Ladder step after {@code seconds} of accumulated flight, capped by max-step when it is not 0. */
    public int step(double seconds) {
        int step = (int) Math.floor(seconds / stepIntervalSeconds);
        return maxStep > 0 ? Math.min(maxStep, step) : step;
    }

    public double manaPerSecond(double seconds) {
        return manaPerSecondBase + manaPerSecondStep * step(seconds);
    }
}
