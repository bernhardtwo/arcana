package com.bernhardtwo.arcana.ability.storm;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Damage through the vanilla pipeline, with the caster as both causing and
 * direct entity: {@code EntityDamageByEntityEvent} fires with the caster as
 * damager, so the PvP flag, claim plugins, god modes, totems, absorption and
 * kill credit all apply exactly as for a sword. What it does not do is touch
 * the target's invulnerability window. Vanilla keeps every hit landing
 * within ten ticks of the last one only for the amount above it, and every
 * hit that lands refreshes that window: a beam ticking twice a second would
 * leave its target immune to everyone else's hits half the time. So the
 * window is cleared before the hit and put back afterwards, together with
 * the last damage amount it compares against, and a sword landing in the
 * same second sees the target exactly as if the beam were not there.
 */
public final class StormDamage {

    private StormDamage() {
    }

    public static void hit(LivingEntity target, Player caster, double amount, DamageType type) {
        if (amount <= 0.0) {
            return;
        }
        int window = target.getNoDamageTicks();
        double last = target.getLastDamage();
        target.setNoDamageTicks(0);
        target.damage(amount, DamageSource.builder(type).withCausingEntity(caster).withDirectEntity(caster).build());
        target.setNoDamageTicks(window);
        target.setLastDamage(last);
    }
}
