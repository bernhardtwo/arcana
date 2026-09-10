package com.bernhardtwo.arcana;

import com.bernhardtwo.arcana.ability.AbilityRegistry;
import com.bernhardtwo.arcana.ability.CooldownTracker;
import com.bernhardtwo.arcana.ability.FallGrace;
import com.bernhardtwo.arcana.ability.gravity.GravityAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityLeapAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityMode;
import com.bernhardtwo.arcana.command.ArcanaCommand;
import com.bernhardtwo.arcana.config.ArcanaConfig;
import com.bernhardtwo.arcana.integration.ClaimGuard;
import com.bernhardtwo.arcana.item.Wand;
import com.bernhardtwo.arcana.item.WandRegistry;
import com.bernhardtwo.arcana.listener.AbilityUseListener;
import com.bernhardtwo.arcana.listener.FallDamageListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArcanaPlugin extends JavaPlugin {

    private NamespacedKey wandKey;
    private ArcanaConfig settings;
    private AbilityRegistry abilities;
    private WandRegistry wands;
    private CooldownTracker cooldowns;
    private FallGrace fallGrace;
    private ClaimGuard claims;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "wand");
        settings = ArcanaConfig.load(getConfig());
        cooldowns = new CooldownTracker();
        fallGrace = new FallGrace();
        claims = new ClaimGuard(this);

        abilities = new AbilityRegistry();
        abilities.register(new GravityAbility(this, GravityMode.PUSH));
        abilities.register(new GravityAbility(this, GravityMode.PULL));
        GravityLeapAbility leap = new GravityLeapAbility(this);
        abilities.register(leap);

        wands = new WandRegistry();
        wands.register(new Wand("gravity", "Gravity Staff", Material.BLAZE_ROD,
                "gravity_push", "gravity_pull", "gravity_leap"));

        getServer().getPluginManager().registerEvents(new AbilityUseListener(this), this);
        getServer().getPluginManager().registerEvents(new FallDamageListener(this), this);

        PluginCommand command = getCommand("arcana");
        if (command != null) {
            ArcanaCommand executor = new ArcanaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getScheduler().runTaskTimer(this, fallGrace::purgeExpired, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, leap::tick, 5L, 5L);
        getLogger().info("Arcana enabled with " + abilities.all().size() + " abilities.");
    }

    public void reloadSettings() {
        reloadConfig();
        settings = ArcanaConfig.load(getConfig());
    }

    public NamespacedKey wandKey() {
        return wandKey;
    }

    public ArcanaConfig settings() {
        return settings;
    }

    public AbilityRegistry abilities() {
        return abilities;
    }

    public WandRegistry wands() {
        return wands;
    }

    public CooldownTracker cooldowns() {
        return cooldowns;
    }

    public FallGrace fallGrace() {
        return fallGrace;
    }

    public ClaimGuard claims() {
        return claims;
    }
}
