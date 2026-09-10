package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.file.FileConfiguration;

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

    private final boolean effects;
    private final TargetRules targets;
    private final GravitySettings push;
    private final GravitySettings pull;
    private final LeapSettings leap;
    private final IceSlashSettings iceSlash;
    private final IceBreakerSettings iceBreaker;

    private ArcanaConfig(boolean effects, TargetRules targets, GravitySettings push, GravitySettings pull,
                         LeapSettings leap, IceSlashSettings iceSlash, IceBreakerSettings iceBreaker) {
        this.effects = effects;
        this.targets = targets;
        this.push = push;
        this.pull = pull;
        this.leap = leap;
        this.iceSlash = iceSlash;
        this.iceBreaker = iceBreaker;
    }

    public static ArcanaConfig load(FileConfiguration config) {
        return new ArcanaConfig(
                config.getBoolean("effects", true),
                TargetRules.from(config.getConfigurationSection("targets")),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_push"), PUSH_DEFAULTS),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_pull"), PULL_DEFAULTS),
                LeapSettings.from(config.getConfigurationSection("abilities.gravity_leap"), LEAP_DEFAULTS),
                IceSlashSettings.from(config.getConfigurationSection("abilities.ice_slash"), SLASH_DEFAULTS),
                IceBreakerSettings.from(config.getConfigurationSection("abilities.ice_breaker"), BREAKER_DEFAULTS)
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
}
