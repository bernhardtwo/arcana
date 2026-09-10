package com.bernhardtwo.arcana.ability.solar;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.PlayerStore;
import com.bernhardtwo.arcana.config.SolarBloomSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

/** Bone meal from a charge pool. A charge is spent only when the bone meal actually did something. */
public final class SolarBloomAbility implements Ability {

    private final ArcanaPlugin plugin;

    public SolarBloomAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "solar_bloom";
    }

    @Override
    public String displayName() {
        return "Solar: Bloom";
    }

    @Override
    public int cooldownTicks() {
        return 0;
    }

    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public boolean cast(Player caster) {
        return cast(caster, null);
    }

    @Override
    public boolean cast(Player caster, Block clicked) {
        if (clicked == null) {
            return false;
        }
        SolarBloomSettings settings = settings();
        PlayerStore store = plugin.store();
        PlayerStore.Charges charges = store.charges(caster, id(), settings.maxCharges(), settings.regenMillis());
        if (charges.count() <= 0) {
            caster.sendActionBar(status(charges));
            return false;
        }
        if (plugin.claims().isBlocked(caster, clicked.getLocation())) {
            caster.sendActionBar(Component.text("Solar: Bloom: you cannot build here", NamedTextColor.GRAY));
            return false;
        }
        if (!clicked.applyBoneMeal(BlockFace.UP)) {
            caster.sendActionBar(Component.text("Solar: Bloom: nothing to grow there", NamedTextColor.GRAY));
            return false;
        }
        store.consumeCharge(caster, id(), settings.maxCharges(), settings.regenMillis());
        caster.sendActionBar(status(store.charges(caster, id(), settings.maxCharges(), settings.regenMillis())));

        if (plugin.settings().effects()) {
            clicked.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, clicked.getLocation().toCenterLocation(),
                    15, 0.5, 0.5, 0.5, 0.0);
            clicked.getWorld().playSound(clicked.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.1f);
        }
        return true;
    }

    private Component status(PlayerStore.Charges charges) {
        String text = "Solar: Bloom: " + charges.count() + (charges.count() == 1 ? " charge" : " charges");
        if (charges.nextInMillis() > 0L) {
            long seconds = (charges.nextInMillis() + 999L) / 1000L;
            text += ", next in " + (seconds >= 60L ? seconds / 60L + "m " + seconds % 60L + "s" : seconds + "s");
        }
        return Component.text(text, charges.count() == 0 ? NamedTextColor.GRAY : NamedTextColor.GOLD);
    }

    private SolarBloomSettings settings() {
        return plugin.settings().solarBloom();
    }
}
