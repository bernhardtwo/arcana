package com.bernhardtwo.arcana;

import com.bernhardtwo.arcana.ability.AbilityRegistry;
import com.bernhardtwo.arcana.ability.CooldownTracker;
import com.bernhardtwo.arcana.ability.FallGrace;
import com.bernhardtwo.arcana.ability.gravity.GravityAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityLeapAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityMode;
import com.bernhardtwo.arcana.ability.ice.Frost;
import com.bernhardtwo.arcana.ability.ice.IceArmorAbility;
import com.bernhardtwo.arcana.ability.ice.IceBreakerAbility;
import com.bernhardtwo.arcana.ability.ice.IceSlashAbility;
import com.bernhardtwo.arcana.command.ArcanaCommand;
import com.bernhardtwo.arcana.config.ArcanaConfig;
import com.bernhardtwo.arcana.integration.ClaimGuard;
import com.bernhardtwo.arcana.item.Wand;
import com.bernhardtwo.arcana.item.WandRegistry;
import com.bernhardtwo.arcana.listener.AbilityUseListener;
import com.bernhardtwo.arcana.listener.FallDamageListener;
import com.bernhardtwo.arcana.listener.IceArmorListener;
import com.bernhardtwo.arcana.listener.IceListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArcanaPlugin extends JavaPlugin {

    private NamespacedKey wandKey;
    private NamespacedKey iceSnowballKey;
    private NamespacedKey iceCrystalKey;
    private ArcanaConfig settings;
    private AbilityRegistry abilities;
    private WandRegistry wands;
    private CooldownTracker cooldowns;
    private FallGrace fallGrace;
    private ClaimGuard claims;
    private Frost frost;
    private IceArmorAbility iceArmor;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "wand");
        iceSnowballKey = new NamespacedKey(this, "ice_snowball");
        iceCrystalKey = new NamespacedKey(this, "ice_crystal");
        settings = ArcanaConfig.load(getConfig(), getLogger());
        sweepOrphanedCrystals();
        cooldowns = new CooldownTracker();
        fallGrace = new FallGrace();
        claims = new ClaimGuard(this);
        frost = new Frost();

        abilities = new AbilityRegistry();
        abilities.register(new GravityAbility(this, GravityMode.PUSH));
        abilities.register(new GravityAbility(this, GravityMode.PULL));
        GravityLeapAbility leap = new GravityLeapAbility(this);
        abilities.register(leap);
        abilities.register(new IceSlashAbility(this));
        abilities.register(new IceBreakerAbility(this));
        iceArmor = new IceArmorAbility(this);
        abilities.register(iceArmor);

        wands = new WandRegistry();
        wands.register(new Wand("gravity", "Gravity Staff", Material.BLAZE_ROD,
                "gravity_push", "gravity_pull", "gravity_leap"));
        wands.register(new Wand("ice", "Ice Staff", Material.END_ROD,
                "ice_breaker", "ice_armor", "ice_slash"));

        getServer().getPluginManager().registerEvents(new AbilityUseListener(this), this);
        getServer().getPluginManager().registerEvents(new FallDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new IceListener(this), this);
        getServer().getPluginManager().registerEvents(new IceArmorListener(iceArmor), this);

        PluginCommand command = getCommand("arcana");
        if (command != null) {
            ArcanaCommand executor = new ArcanaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getScheduler().runTaskTimer(this, fallGrace::purgeExpired, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, leap::tick, 5L, 5L);
        getServer().getScheduler().runTaskTimer(this, iceArmor::tick, 1L, 1L);
        getLogger().info("Arcana enabled with " + abilities.all().size() + " abilities.");
    }

    @Override
    public void onDisable() {
        if (iceArmor != null) {
            iceArmor.endAll();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = ArcanaConfig.load(getConfig(), getLogger());
    }

    /** Defensive insurance: crystals are non-persistent, but sweep loaded worlds anyway. */
    private void sweepOrphanedCrystals() {
        int removed = 0;
        for (World world : getServer().getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (display.getPersistentDataContainer().has(iceCrystalKey, PersistentDataType.BYTE)) {
                    display.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            getLogger().warning("Removed " + removed + " orphaned ice crystal(s).");
        }
    }

    public NamespacedKey wandKey() {
        return wandKey;
    }

    public NamespacedKey iceSnowballKey() {
        return iceSnowballKey;
    }

    public NamespacedKey iceCrystalKey() {
        return iceCrystalKey;
    }

    public Frost frost() {
        return frost;
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
