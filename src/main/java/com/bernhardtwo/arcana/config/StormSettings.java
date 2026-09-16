package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

/**
 * Thor's Hammer: its three abilities and the item. Charge and Smash have a
 * {@code mana-cost} like every cast, read by {@link ArcanaConfig}. Beam
 * charges a share of the caster's maximum mana per second instead, so it
 * has no {@code mana-cost} and the pool-size correction never applies to
 * it: a percentage is already calibrated against any pool.
 */
public record StormSettings(Charge charge, Smash smash, Beam beam, int windBurstLevel, boolean recipeEnabled) {

    public record Charge(
            int minChargeTicks,
            int maxChargeTicks,
            double dashPowerMin,
            double dashPowerMax,
            int secondDashDelayTicks,
            double secondDashPowerMultiplier,
            boolean rootWhileCharging,
            boolean cancelOnDamage,
            int cooldownSeconds
    ) {

        static Charge from(ConfigurationSection section, Charge fallback) {
            if (section == null) {
                return fallback;
            }
            int min = Math.max(1, section.getInt("min-charge-ticks", fallback.minChargeTicks()));
            return new Charge(
                    min,
                    Math.max(min, section.getInt("max-charge-ticks", fallback.maxChargeTicks())),
                    Math.max(0.0, section.getDouble("dash-power-min", fallback.dashPowerMin())),
                    Math.max(0.0, section.getDouble("dash-power-max", fallback.dashPowerMax())),
                    Math.max(1, section.getInt("second-dash-delay-ticks", fallback.secondDashDelayTicks())),
                    Math.max(0.0, section.getDouble("second-dash-power-multiplier", fallback.secondDashPowerMultiplier())),
                    section.getBoolean("root-while-charging", fallback.rootWhileCharging()),
                    section.getBoolean("cancel-on-damage", fallback.cancelOnDamage()),
                    Math.max(0, section.getInt("cooldown-seconds", fallback.cooldownSeconds()))
            );
        }

        public int cooldownTicks() {
            return cooldownSeconds * 20;
        }

        /** Dash power for a channel held {@code ticks}: the minimum at min-charge-ticks, the maximum at max-charge-ticks, linear between. */
        public double dashPower(int ticks) {
            double progress = maxChargeTicks <= minChargeTicks ? 1.0
                    : Math.min(1.0, Math.max(0.0, (ticks - minChargeTicks) / (double) (maxChargeTicks - minChargeTicks)));
            return dashPowerMin + (dashPowerMax - dashPowerMin) * progress;
        }
    }

    public record Smash(
            double lightningBaseDamage,
            double lightningDamagePerBlock,
            double lightningMaxDamage,
            double lightningRadius,
            int cooldownSeconds
    ) {

        static Smash from(ConfigurationSection section, Smash fallback, Logger logger) {
            if (section == null) {
                return fallback;
            }
            String source = section.getString("height-source", "fall-distance");
            if (!"fall-distance".equals(source)) {
                logger.warning("storm_smash.height-source '" + source + "' is not supported, using fall-distance.");
            }
            return new Smash(
                    Math.max(0.0, section.getDouble("lightning-base-damage", fallback.lightningBaseDamage())),
                    Math.max(0.0, section.getDouble("lightning-damage-per-block", fallback.lightningDamagePerBlock())),
                    Math.max(0.0, section.getDouble("lightning-max-damage", fallback.lightningMaxDamage())),
                    Math.max(0.0, section.getDouble("lightning-radius", fallback.lightningRadius())),
                    Math.max(0, section.getInt("cooldown-seconds", fallback.cooldownSeconds()))
            );
        }

        public int cooldownTicks() {
            return cooldownSeconds * 20;
        }

        /** Base plus per-block times the blocks fallen, capped at the maximum. */
        public double lightningDamage(double blocks) {
            return Math.min(lightningMaxDamage, lightningBaseDamage + lightningDamagePerBlock * Math.max(0.0, blocks));
        }
    }

    public record Beam(
            double damagePercentPerSecond,
            double manaPercentPerSecond,
            double maxDamagePerSecond,
            double range,
            int tickPeriod,
            boolean affectPlayers,
            double loseTargetGraceSeconds
    ) {

        static Beam from(ConfigurationSection section, Beam fallback) {
            if (section == null) {
                return fallback;
            }
            return new Beam(
                    Math.max(0.0, section.getDouble("damage-percent-per-second", fallback.damagePercentPerSecond())),
                    Math.max(0.0, section.getDouble("mana-percent-per-second", fallback.manaPercentPerSecond())),
                    Math.max(0.0, section.getDouble("max-damage-per-second", fallback.maxDamagePerSecond())),
                    Math.max(1.0, section.getDouble("range", fallback.range())),
                    Math.max(1, section.getInt("tick-period", fallback.tickPeriod())),
                    section.getBoolean("affect-players", fallback.affectPlayers()),
                    Math.max(0.0, section.getDouble("lose-target-grace-seconds", fallback.loseTargetGraceSeconds()))
            );
        }

        /** One charge tick's share of the target's maximum health, capped by max-damage-per-second when it is not 0. */
        public double damagePerCharge(double maxHealth) {
            double seconds = tickPeriod / 20.0;
            double damage = maxHealth * damagePercentPerSecond / 100.0 * seconds;
            return maxDamagePerSecond > 0.0 ? Math.min(damage, maxDamagePerSecond * seconds) : damage;
        }

        /** One charge tick's share of the caster's maximum mana. */
        public double manaPerCharge(double maxMana) {
            return maxMana * manaPercentPerSecond / 100.0 * tickPeriod / 20.0;
        }

        public int graceTicks() {
            return (int) Math.round(loseTargetGraceSeconds * 20.0);
        }
    }

    /** Reads the four storm blocks under {@code abilities}. */
    public static StormSettings from(ConfigurationSection abilities, StormSettings fallback, Logger logger) {
        if (abilities == null) {
            return fallback;
        }
        ConfigurationSection hammer = abilities.getConfigurationSection("storm_hammer");
        return new StormSettings(
                Charge.from(abilities.getConfigurationSection("storm_charge"), fallback.charge()),
                Smash.from(abilities.getConfigurationSection("storm_smash"), fallback.smash(), logger),
                Beam.from(abilities.getConfigurationSection("storm_beam"), fallback.beam()),
                hammer == null ? fallback.windBurstLevel()
                        : Math.max(0, hammer.getInt("wind-burst-level", fallback.windBurstLevel())),
                hammer == null ? fallback.recipeEnabled()
                        : hammer.getBoolean("recipe-enabled", fallback.recipeEnabled())
        );
    }
}
