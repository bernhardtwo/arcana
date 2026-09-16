package com.bernhardtwo.arcana.ability.storm;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Targets;
import com.bernhardtwo.arcana.config.StormSettings;
import com.bernhardtwo.arcana.item.StormHammer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The hammer's melee hit, and lightning on whatever it connects with. The
 * click never reaches this through the dispatcher: a swing that misses does
 * nothing, and a swing that lands is seen by the storm listener from the
 * damage event at MONITOR, so only a hit that actually went through strikes.
 * The vanilla hit is never cancelled and never waits for the bolt: no mana,
 * no permission or a running cooldown only lose the lightning. The bolt is
 * {@code strikeLightningEffect} plus damage by hand, never a real strike,
 * which sets fire to the ground. Its damage grows with the fall distance of
 * the hit, the same number the mace itself reads for its own bonus.
 */
public final class StormSmashAbility implements Ability {

    private static final String NAME = "Storm: Smash";

    private final ArcanaPlugin plugin;

    public StormSmashAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "storm_smash";
    }

    @Override
    public String displayName() {
        return NAME;
    }

    @Override
    public String permission() {
        return StormHammer.PERMISSION;
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    @Override
    public String loreHint() {
        return "lightning on hit";
    }

    /** Refused silently: the swing itself is not a cast, only a hit that lands is. */
    @Override
    public boolean canCast(Player caster) {
        return false;
    }

    @Override
    public boolean cast(Player caster) {
        return false;
    }

    /**
     * A hit that went through, in the usual order: permission, cooldown,
     * mana, claims, then the strike one tick later so the two damages never
     * nest inside the same event. The fall distance is read now, before the
     * landing resets it.
     */
    public void onHit(Player caster, LivingEntity target) {
        if (!caster.hasPermission(permission()) || plugin.store().remainingMillis(caster, id()) > 0L) {
            return;
        }
        double cost = plugin.settings().manaCost(id());
        if (!plugin.mana().hasMana(caster, cost)) {
            caster.sendMessage(warn(String.format("%s needs %.0f mana, you have %.0f", NAME, cost, plugin.mana().mana(caster))));
            return;
        }
        if (!Targets.isTargetable(caster, target)) {
            return;
        }
        if (plugin.settings().targets().respectClaims() && plugin.claims().isBlocked(caster, target.getLocation())) {
            caster.sendMessage(warn(NAME + ": " + target.getName() + " is inside a claim you cannot build in"));
            return;
        }
        double blocks = caster.getFallDistance();
        double damage = settings().lightningDamage(blocks);
        plugin.mana().consume(caster, cost);
        plugin.store().startCooldownWithIndicator(caster, id(), cooldownTicks());
        Bukkit.getScheduler().runTask(plugin, () -> strike(caster, target, damage, blocks));
    }

    private void strike(Player caster, LivingEntity target, double damage, double blocks) {
        Location at = target.getLocation();
        // The flash and the thunder only: no fire, no damage of its own.
        at.getWorld().strikeLightningEffect(at);
        if (target.isValid() && !target.isDead()) {
            StormDamage.hit(target, caster, damage, DamageType.LIGHTNING_BOLT);
        }
        double radius = settings().lightningRadius();
        if (radius > 0.0) {
            for (Entity near : target.getNearbyEntities(radius, radius, radius)) {
                if (near instanceof LivingEntity living && !near.equals(target) && Targets.isValid(plugin, caster, near)) {
                    StormDamage.hit(living, caster, damage, DamageType.LIGHTNING_BOLT);
                }
            }
        }
        caster.sendMessage(Component.text(String.format("%s: %.1f lightning damage from %.1f blocks", NAME, damage, blocks),
                NamedTextColor.LIGHT_PURPLE));
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private StormSettings.Smash settings() {
        return plugin.settings().storm().smash();
    }
}
