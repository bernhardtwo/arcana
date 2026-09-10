package com.bernhardtwo.arcana;

import com.bernhardtwo.arcana.ability.AbilityRegistry;
import com.bernhardtwo.arcana.ability.FallGrace;
import com.bernhardtwo.arcana.ability.PlayerStore;
import com.bernhardtwo.arcana.ability.chain.ChainHookAbility;
import com.bernhardtwo.arcana.ability.chain.ChainReelAbility;
import com.bernhardtwo.arcana.ability.chain.ChainRendAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityLeapAbility;
import com.bernhardtwo.arcana.ability.gravity.GravityMode;
import com.bernhardtwo.arcana.ability.ice.Frost;
import com.bernhardtwo.arcana.ability.ice.IceArmorAbility;
import com.bernhardtwo.arcana.ability.ice.IceBreakerAbility;
import com.bernhardtwo.arcana.ability.ice.IceSlashAbility;
import com.bernhardtwo.arcana.ability.shadow.ShadowBlinkAbility;
import com.bernhardtwo.arcana.ability.shadow.ShadowBodyAbility;
import com.bernhardtwo.arcana.ability.shadow.ShadowSwapAbility;
import com.bernhardtwo.arcana.ability.solar.SolarBloomAbility;
import com.bernhardtwo.arcana.ability.solar.SolarLanternAbility;
import com.bernhardtwo.arcana.ability.solar.SolarZenithAbility;
import com.bernhardtwo.arcana.command.ArcanaCommand;
import com.bernhardtwo.arcana.config.ArcanaConfig;
import com.bernhardtwo.arcana.integration.ClaimGuard;
import com.bernhardtwo.arcana.integration.CoreProtectLog;
import com.bernhardtwo.arcana.integration.ManaBridge;
import com.bernhardtwo.arcana.item.ManaPotion;
import com.bernhardtwo.arcana.item.Wand;
import com.bernhardtwo.arcana.item.WandRegistry;
import com.bernhardtwo.arcana.listener.AbilityUseListener;
import com.bernhardtwo.arcana.listener.ChainListener;
import com.bernhardtwo.arcana.listener.DroppedItemListener;
import com.bernhardtwo.arcana.listener.FallDamageListener;
import com.bernhardtwo.arcana.listener.IceArmorListener;
import com.bernhardtwo.arcana.listener.IceListener;
import com.bernhardtwo.arcana.listener.ItemGuardListener;
import com.bernhardtwo.arcana.listener.ManaListener;
import com.bernhardtwo.arcana.mana.ManaBar;
import com.bernhardtwo.arcana.listener.ShadowListener;
import com.bernhardtwo.arcana.listener.SolarListener;
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
    private NamespacedKey displayKey;
    private NamespacedKey itemKey;
    private ArcanaConfig settings;
    private AbilityRegistry abilities;
    private WandRegistry wands;
    private PlayerStore store;
    private FallGrace fallGrace;
    private ClaimGuard claims;
    private CoreProtectLog coreProtect;
    private ManaBridge mana;
    private ManaBar manaBar;
    private Frost frost;
    private IceArmorAbility iceArmor;
    private SolarLanternAbility solarLantern;
    private SolarZenithAbility solarZenith;
    private ShadowBodyAbility shadowBody;
    private ShadowSwapAbility shadowSwap;
    private ChainHookAbility chainHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        wandKey = new NamespacedKey(this, "wand");
        iceSnowballKey = new NamespacedKey(this, "ice_snowball");
        displayKey = new NamespacedKey(this, "display");
        itemKey = new NamespacedKey(this, "item");
        settings = ArcanaConfig.load(getConfig(), getLogger());
        sweepOrphanedDisplays();
        store = new PlayerStore(this);
        fallGrace = new FallGrace();
        claims = new ClaimGuard(this);
        coreProtect = new CoreProtectLog(this);
        mana = new ManaBridge(this);
        manaBar = new ManaBar(this);
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
        solarLantern = new SolarLanternAbility(this);
        abilities.register(solarLantern);
        solarZenith = new SolarZenithAbility(this);
        abilities.register(solarZenith);
        abilities.register(new SolarBloomAbility(this));
        abilities.register(new ShadowBlinkAbility(this));
        shadowSwap = new ShadowSwapAbility(this);
        abilities.register(shadowSwap);
        shadowBody = new ShadowBodyAbility(this);
        abilities.register(shadowBody);
        chainHook = new ChainHookAbility(this);
        abilities.register(chainHook);
        abilities.register(new ChainReelAbility(this, chainHook));
        abilities.register(new ChainRendAbility(this, chainHook));
        solarZenith.removeOrphanLights();

        wands = new WandRegistry();
        wands.register(new Wand("gravity", "Gravity Staff", Material.BLAZE_ROD,
                "gravity_push", "gravity_pull", "gravity_leap"));
        wands.register(new Wand("ice", "Ice Staff", Material.END_ROD,
                "ice_breaker", "ice_armor", "ice_slash"));
        wands.register(new Wand("solar", "Solar Staff", Material.BREEZE_ROD,
                "solar_lantern", "solar_zenith", "solar_bloom"));
        wands.register(new Wand("shadow", "Shadow Staff", Material.ECHO_SHARD,
                "shadow_blink", "shadow_body", "shadow_swap"));
        wands.register(new Wand("chain", "Chainshot", Material.IRON_CHAIN,
                "chain_reel", "chain_rend", "chain_hook"));

        getServer().getPluginManager().registerEvents(new AbilityUseListener(this), this);
        getServer().getPluginManager().registerEvents(new ItemGuardListener(this), this);
        getServer().getPluginManager().registerEvents(new DroppedItemListener(this), this);
        getServer().getPluginManager().registerEvents(new ManaListener(this), this);
        getServer().getPluginManager().registerEvents(new FallDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new IceListener(this), this);
        getServer().getPluginManager().registerEvents(new IceArmorListener(iceArmor), this);
        getServer().getPluginManager().registerEvents(new SolarListener(solarLantern, solarZenith), this);
        getServer().getPluginManager().registerEvents(new ShadowListener(this, shadowBody, shadowSwap), this);
        getServer().getPluginManager().registerEvents(new ChainListener(chainHook), this);

        PluginCommand command = getCommand("arcana");
        if (command != null) {
            ArcanaCommand executor = new ArcanaCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getServer().getScheduler().runTaskTimer(this, fallGrace::purgeExpired, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, leap::tick, 5L, 5L);
        getServer().getScheduler().runTaskTimer(this, iceArmor::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, solarLantern::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, solarZenith::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, chainHook::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, manaBar::tick, 10L, 10L);
        ManaPotion.syncRecipe(this);
        getLogger().info("Arcana enabled with " + abilities.all().size() + " abilities.");
    }

    @Override
    public void onDisable() {
        if (iceArmor != null) {
            iceArmor.endAll();
        }
        if (solarLantern != null) {
            solarLantern.endAll();
        }
        if (solarZenith != null) {
            solarZenith.endAll();
        }
        if (shadowBody != null) {
            shadowBody.endAll();
        }
        if (shadowSwap != null) {
            shadowSwap.cancelAll();
        }
        if (chainHook != null) {
            chainHook.endAll();
        }
        if (manaBar != null) {
            manaBar.hideAll();
        }
    }

    public void reloadSettings() {
        reloadConfig();
        settings = ArcanaConfig.load(getConfig(), getLogger());
        ManaPotion.syncRecipe(this);
    }

    /** Defensive insurance: our displays are non-persistent, but sweep loaded worlds anyway. */
    private void sweepOrphanedDisplays() {
        int removed = 0;
        for (World world : getServer().getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (display.getPersistentDataContainer().has(displayKey, PersistentDataType.BYTE)) {
                    display.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            getLogger().warning("Removed " + removed + " orphaned display entity(ies).");
        }
    }

    public NamespacedKey wandKey() {
        return wandKey;
    }

    public NamespacedKey iceSnowballKey() {
        return iceSnowballKey;
    }

    public NamespacedKey displayKey() {
        return displayKey;
    }

    /** Tags Arcana items that are not wands, such as the mana potion. */
    public NamespacedKey itemKey() {
        return itemKey;
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

    public PlayerStore store() {
        return store;
    }

    public FallGrace fallGrace() {
        return fallGrace;
    }

    public ClaimGuard claims() {
        return claims;
    }

    public CoreProtectLog coreProtect() {
        return coreProtect;
    }

    public ManaBridge mana() {
        return mana;
    }

    public ManaBar manaBar() {
        return manaBar;
    }
}
