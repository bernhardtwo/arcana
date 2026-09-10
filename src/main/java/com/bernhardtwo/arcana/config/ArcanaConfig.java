package com.bernhardtwo.arcana.config;

import org.bukkit.configuration.file.FileConfiguration;

public final class ArcanaConfig {

    private static final GravitySettings PUSH_DEFAULTS =
            new GravitySettings(12.0, 2.6, 0.6, 4.0, 100, false, true, 120);
    private static final GravitySettings PULL_DEFAULTS =
            new GravitySettings(28.0, 2.2, 0.35, 3.5, 140, false, true, 120);

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
