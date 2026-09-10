package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.logging.Logger;

public final class ArcanaConfig {

    private static final GravitySettings PUSH_DEFAULTS =
            new GravitySettings(12.0, 2.6, 0.6, 4.0, 100, false, true, 120);
    private static final GravitySettings PULL_DEFAULTS =
            new GravitySettings(28.0, 2.2, 0.35, 3.5, 140, false, true, 120);
    private static final LeapSettings LEAP_DEFAULTS =
            new LeapSettings(0.9, 0.35, 20, 100);
    private static final IceSlashSettings SLASH_DEFAULTS =
            new IceSlashSettings(3.5, 70.0, 4.0, 3.0, 1.8, 60, 40, 30);
    private static final IceBreakerSettings BREAKER_DEFAULTS =
            new IceBreakerSettings(24.0, 7.0, 100, 60, 120);
    private static final IceArmorSettings ARMOR_DEFAULTS =
            new IceArmorSettings(3, 1.2, 1.0, 0.12, 15, 1200, false,
                    EnumSet.of(EntityType.WARDEN, EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.GHAST));
    private static final SolarLanternSettings LANTERN_DEFAULTS = new SolarLanternSettings(24000, 12000, 0.9);
    private static final SolarZenithSettings ZENITH_DEFAULTS = new SolarZenithSettings(6, 20.0, 60, 1.0, 20, 10, 12.0, 5, 18000);
    private static final SolarBloomSettings BLOOM_DEFAULTS = new SolarBloomSettings(5, 12);

    private final boolean effects;
    private final TargetRules targets;
    private final GravitySettings push;
    private final GravitySettings pull;
    private final LeapSettings leap;
    private final IceSlashSettings iceSlash;
    private final IceBreakerSettings iceBreaker;
    private final IceArmorSettings iceArmor;
    private final SolarLanternSettings solarLantern;
    private final SolarZenithSettings solarZenith;
    private final SolarBloomSettings solarBloom;

    private ArcanaConfig(boolean effects, TargetRules targets, GravitySettings push, GravitySettings pull,
                         LeapSettings leap, IceSlashSettings iceSlash, IceBreakerSettings iceBreaker,
                         IceArmorSettings iceArmor, SolarLanternSettings solarLantern,
                         SolarZenithSettings solarZenith, SolarBloomSettings solarBloom) {
        this.effects = effects;
        this.targets = targets;
        this.push = push;
        this.pull = pull;
        this.leap = leap;
        this.iceSlash = iceSlash;
        this.iceBreaker = iceBreaker;
        this.iceArmor = iceArmor;
        this.solarLantern = solarLantern;
        this.solarZenith = solarZenith;
        this.solarBloom = solarBloom;
    }

    public static ArcanaConfig load(FileConfiguration config, Logger logger) {
        return new ArcanaConfig(
                config.getBoolean("effects", true),
                TargetRules.from(config.getConfigurationSection("targets")),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_push"), PUSH_DEFAULTS),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_pull"), PULL_DEFAULTS),
                LeapSettings.from(config.getConfigurationSection("abilities.gravity_leap"), LEAP_DEFAULTS),
                IceSlashSettings.from(config.getConfigurationSection("abilities.ice_slash"), SLASH_DEFAULTS),
                IceBreakerSettings.from(config.getConfigurationSection("abilities.ice_breaker"), BREAKER_DEFAULTS),
                IceArmorSettings.from(config.getConfigurationSection("abilities.ice_armor"), ARMOR_DEFAULTS, logger),
                SolarLanternSettings.from(config.getConfigurationSection("abilities.solar_lantern"), LANTERN_DEFAULTS),
                SolarZenithSettings.from(config.getConfigurationSection("abilities.solar_zenith"), ZENITH_DEFAULTS),
                SolarBloomSettings.from(config.getConfigurationSection("abilities.solar_bloom"), BLOOM_DEFAULTS)
        );
    }

    public boolean effects() {
        return effects;
    }

    public TargetRules targets() {
        return targets;
    }

    public GravitySettings push() {
        return push;
    }

    public GravitySettings pull() {
        return pull;
    }

    public LeapSettings leap() {
        return leap;
    }

    public IceSlashSettings iceSlash() {
        return iceSlash;
    }

    public IceBreakerSettings iceBreaker() {
        return iceBreaker;
    }

    public IceArmorSettings iceArmor() {
        return iceArmor;
    }

    public SolarLanternSettings solarLantern() {
        return solarLantern;
    }

    public SolarZenithSettings solarZenith() {
        return solarZenith;
    }

    public SolarBloomSettings solarBloom() {
        return solarBloom;
    }
}
