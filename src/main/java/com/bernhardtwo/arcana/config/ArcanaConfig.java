package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class ArcanaConfig {

    private static final GravitySettings PUSH_DEFAULTS =
            new GravitySettings(6.0, 1.3, 0.45, 2.2, 100, false, true, 120);
    private static final GravitySettings PULL_DEFAULTS =
            new GravitySettings(14.0, 1.1, 0.25, 1.8, 140, false, true, 120);

    private final boolean effects;
    private final TargetRules targets;
    private final GravitySettings push;
    private final GravitySettings pull;

    private ArcanaConfig(boolean effects, TargetRules targets, GravitySettings push, GravitySettings pull) {
        this.effects = effects;
        this.targets = targets;
        this.push = push;
        this.pull = pull;
    }

    public static ArcanaConfig load(FileConfiguration config) {
        return new ArcanaConfig(
                config.getBoolean("effects", true),
                TargetRules.from(config.getConfigurationSection("targets")),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_push"), PUSH_DEFAULTS),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_pull"), PULL_DEFAULTS)
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
}
