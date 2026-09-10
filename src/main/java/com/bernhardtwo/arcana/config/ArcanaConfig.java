package com.bernhardtwo.arcana.config;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
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
    private static final SolarZenithSettings ZENITH_DEFAULTS = new SolarZenithSettings(6, 20.0, 60, 1.0, 20, 10, 12.0, 5, 32, 1.6f,
            Color.fromRGB(0xFFFFFF), Color.fromRGB(0xFF6A00), true, 2.0, 0.02, 18000);
    private static final SolarBloomSettings BLOOM_DEFAULTS = new SolarBloomSettings(5, 12);
    private static final ShadowBlinkSettings BLINK_DEFAULTS = new ShadowBlinkSettings(14.0, 60);
    private static final ShadowSwapSettings SWAP_DEFAULTS = new ShadowSwapSettings(20.0, true, 60, 0.15, true, true, 40, 200, 100);
    private static final ShadowBodySettings BODY_DEFAULTS = new ShadowBodySettings(100, true, true, 700);
    private static final ManaSettings MANA_DEFAULTS = new ManaSettings(true, 20.0, false);
    /** Tuned against a pool of 40 to 60. The chain is free on purpose: a tool, not magic. */
    private static final Map<String, Double> MANA_COST_DEFAULTS = Map.ofEntries(
            Map.entry("gravity_push", 10.0), Map.entry("gravity_pull", 10.0), Map.entry("gravity_leap", 5.0),
            Map.entry("ice_slash", 6.0), Map.entry("ice_breaker", 12.0), Map.entry("ice_armor", 20.0),
            Map.entry("solar_lantern", 15.0), Map.entry("solar_zenith", 30.0), Map.entry("solar_bloom", 4.0),
            Map.entry("shadow_blink", 8.0), Map.entry("shadow_swap", 12.0), Map.entry("shadow_body", 15.0),
            Map.entry("chain_hook", 0.0), Map.entry("chain_reel", 0.0), Map.entry("chain_rend", 0.0));
    private static final ChainHookSettings HOOK_DEFAULTS = new ChainHookSettings(24.0, 1.6, 200, 40);
    private static final ChainReelSettings REEL_DEFAULTS = new ChainReelSettings(1.2, 1.1, 2.5, 100, 60);
    private static final ChainRendSettings REND_DEFAULTS = new ChainRendSettings(6.0, 0.5, 5.0,
            EnumSet.of(Material.BEDROCK, Material.BARRIER, Material.SPAWNER, Material.END_PORTAL_FRAME,
                    Material.REINFORCED_DEEPSLATE), 100);

    private final boolean effects;
    private final TargetRules targets;
    private final ItemSettings items;
    private final ManaSettings mana;
    private final Map<String, Double> manaCosts;
    private final GravitySettings push;
    private final GravitySettings pull;
    private final LeapSettings leap;
    private final IceSlashSettings iceSlash;
    private final IceBreakerSettings iceBreaker;
    private final IceArmorSettings iceArmor;
    private final SolarLanternSettings solarLantern;
    private final SolarZenithSettings solarZenith;
    private final SolarBloomSettings solarBloom;
    private final ShadowBlinkSettings shadowBlink;
    private final ShadowSwapSettings shadowSwap;
    private final ShadowBodySettings shadowBody;
    private final ChainHookSettings chainHook;
    private final ChainReelSettings chainReel;
    private final ChainRendSettings chainRend;

    private ArcanaConfig(boolean effects, TargetRules targets, ItemSettings items, ManaSettings mana,
                         Map<String, Double> manaCosts, GravitySettings push, GravitySettings pull,
                         LeapSettings leap, IceSlashSettings iceSlash, IceBreakerSettings iceBreaker,
                         IceArmorSettings iceArmor, SolarLanternSettings solarLantern,
                         SolarZenithSettings solarZenith, SolarBloomSettings solarBloom,
                         ShadowBlinkSettings shadowBlink, ShadowSwapSettings shadowSwap,
                         ShadowBodySettings shadowBody, ChainHookSettings chainHook,
                         ChainReelSettings chainReel, ChainRendSettings chainRend) {
        this.effects = effects;
        this.targets = targets;
        this.items = items;
        this.mana = mana;
        this.manaCosts = manaCosts;
        this.push = push;
        this.pull = pull;
        this.leap = leap;
        this.iceSlash = iceSlash;
        this.iceBreaker = iceBreaker;
        this.iceArmor = iceArmor;
        this.solarLantern = solarLantern;
        this.solarZenith = solarZenith;
        this.solarBloom = solarBloom;
        this.shadowBlink = shadowBlink;
        this.shadowSwap = shadowSwap;
        this.shadowBody = shadowBody;
        this.chainHook = chainHook;
        this.chainReel = chainReel;
        this.chainRend = chainRend;
    }

    public static ArcanaConfig load(FileConfiguration config, Logger logger) {
        Map<String, Double> manaCosts = new HashMap<>();
        MANA_COST_DEFAULTS.forEach((id, cost) ->
                manaCosts.put(id, Math.max(0.0, config.getDouble("abilities." + id + ".mana-cost", cost))));
        return new ArcanaConfig(
                config.getBoolean("effects", true),
                TargetRules.from(config.getConfigurationSection("targets")),
                ItemSettings.from(config.getConfigurationSection("items")),
                ManaSettings.from(config.getConfigurationSection("mana"), MANA_DEFAULTS),
                manaCosts,
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_push"), PUSH_DEFAULTS),
                GravitySettings.from(config.getConfigurationSection("abilities.gravity_pull"), PULL_DEFAULTS),
                LeapSettings.from(config.getConfigurationSection("abilities.gravity_leap"), LEAP_DEFAULTS),
                IceSlashSettings.from(config.getConfigurationSection("abilities.ice_slash"), SLASH_DEFAULTS),
                IceBreakerSettings.from(config.getConfigurationSection("abilities.ice_breaker"), BREAKER_DEFAULTS),
                IceArmorSettings.from(config.getConfigurationSection("abilities.ice_armor"), ARMOR_DEFAULTS, logger),
                SolarLanternSettings.from(config.getConfigurationSection("abilities.solar_lantern"), LANTERN_DEFAULTS),
                SolarZenithSettings.from(config.getConfigurationSection("abilities.solar_zenith"), ZENITH_DEFAULTS, logger),
                SolarBloomSettings.from(config.getConfigurationSection("abilities.solar_bloom"), BLOOM_DEFAULTS),
                ShadowBlinkSettings.from(config.getConfigurationSection("abilities.shadow_blink"), BLINK_DEFAULTS),
                ShadowSwapSettings.from(config.getConfigurationSection("abilities.shadow_swap"), SWAP_DEFAULTS),
                ShadowBodySettings.from(config.getConfigurationSection("abilities.shadow_body"), BODY_DEFAULTS),
                ChainHookSettings.from(config.getConfigurationSection("abilities.chain_hook"), HOOK_DEFAULTS),
                ChainReelSettings.from(config.getConfigurationSection("abilities.chain_reel"), REEL_DEFAULTS),
                ChainRendSettings.from(config.getConfigurationSection("abilities.chain_rend"), REND_DEFAULTS, logger)
        );
    }

    public boolean effects() {
        return effects;
    }

    public TargetRules targets() {
        return targets;
    }

    public ItemSettings items() {
        return items;
    }

    public ManaSettings mana() {
        return mana;
    }

    /** Mana an ability costs to cast; 0 for an ability with no configured cost. */
    public double manaCost(String abilityId) {
        return manaCosts.getOrDefault(abilityId, 0.0);
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

    public ShadowBlinkSettings shadowBlink() {
        return shadowBlink;
    }

    public ShadowSwapSettings shadowSwap() {
        return shadowSwap;
    }

    public ShadowBodySettings shadowBody() {
        return shadowBody;
    }

    public ChainHookSettings chainHook() {
        return chainHook;
    }

    public ChainReelSettings chainReel() {
        return chainReel;
    }

    public ChainRendSettings chainRend() {
        return chainRend;
    }
}
