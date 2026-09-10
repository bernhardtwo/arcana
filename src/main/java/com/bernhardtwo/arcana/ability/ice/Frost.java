package com.bernhardtwo.arcana.ability.ice;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Damage plus the frost read (freeze ticks and Slowness). Damage always goes
 * through {@link LivingEntity#damage(double, org.bukkit.entity.Entity)} so the
 * kill is attributed and PvP or claim plugins can cancel it. The frost is only
 * applied once the damage event actually went through, which is why it is
 * parked here and picked up by the listener at MONITOR priority.
 */
public final class Frost {

    private final Map<UUID, int[]> pending = new HashMap<>();

    public void hit(LivingEntity target, Player caster, double damage, int freezeTicks, int slowTicks) {
        UUID id = target.getUniqueId();
        pending.put(id, new int[]{freezeTicks, slowTicks});
        target.damage(damage, caster);
        // Still present here means no damage event reached MONITOR: cancelled or skipped by invulnerability.
        pending.remove(id);
    }

    public void applyIfPending(LivingEntity target) {
        int[] frost = pending.remove(target.getUniqueId());
        if (frost != null) {
            apply(target, frost[0], frost[1]);
        }
    }

    public static void apply(LivingEntity target, int freezeTicks, int slowTicks) {
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), freezeTicks));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowTicks, 1, false, true, true));
    }
}
