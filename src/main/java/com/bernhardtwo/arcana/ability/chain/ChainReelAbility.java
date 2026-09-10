package com.bernhardtwo.arcana.ability.chain;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.gravity.GravityAbility;
import com.bernhardtwo.arcana.config.ChainReelSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Second stage: pulls along the chain. Anchored to a block, the caster is
 * reeled toward it over several ticks with a ramp, which reads as being
 * carried and stays controllable; the chain is doing the moving, so the
 * landing gets fall grace. Anchored to an entity, the entity is yanked toward
 * the caster with Gravity: Pull's impulse for one target. Both end the hook.
 */
public final class ChainReelAbility implements Ability {

    private static final int RAMP_TICKS = 8;
    private static final double ARRIVE_DISTANCE = 1.5;
    private static final double STALL_DISTANCE_SQUARED = 0.01;
    private static final double LIFT = 0.35;

    private final ArcanaPlugin plugin;
    private final ChainHookAbility hooks;

    public ChainReelAbility(ArcanaPlugin plugin, ChainHookAbility hooks) {
        this.plugin = plugin;
        this.hooks = hooks;
    }

    @Override
    public String id() {
        return "chain_reel";
    }

    @Override
    public String displayName() {
        return "Chain: Reel";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    /** Nothing anchored charges nothing. */
    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public boolean cast(Player caster) {
        ChainHookAbility.Hook hook = hooks.anchored(caster.getUniqueId());
        if (hook == null) {
            caster.sendActionBar(warn("Chain: Reel: no hook anchored"));
            return false;
        }
        if (hook.isReeling()) {
            caster.sendActionBar(warn("Chain: Reel: already reeling"));
            return false;
        }
        ChainReelSettings settings = settings();
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());
        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getEyeLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.5f);
        }
        if (hook.entity() != null) {
            pullEntity(caster, hook.entity(), settings);
        } else {
            pullSelf(caster, hook, settings);
        }
        return true;
    }

    private void pullEntity(Player caster, LivingEntity target, ChainReelSettings settings) {
        Vector offset = target.getLocation().toVector().subtract(caster.getLocation().toVector());
        if (offset.lengthSquared() > 1.0E-4) {
            Vector velocity = target.getVelocity().add(GravityAbility.pullImpulse(offset,
                    plugin.settings().chainHook().range(), settings.pullEntitySpeed(), LIFT));
            GravityAbility.clamp(velocity, settings.maxVelocity());
            target.setVelocity(velocity);
            if (!(target instanceof Player)) {
                plugin.fallGrace().grant(target.getUniqueId(), settings.fallGraceTicks());
            }
        }
        if (plugin.settings().effects()) {
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.1);
        }
        hooks.release(caster.getUniqueId(), "Chain: Reel: pulled " + target.getName() + " in");
    }

    /** Velocity toward the anchor every tick, ramped up over the first ticks and clamped, until arrival or a stall. */
    private void pullSelf(Player caster, ChainHookAbility.Hook hook, ChainReelSettings settings) {
        UUID id = caster.getUniqueId();
        Location anchor = hook.head();
        int maxTicks = RAMP_TICKS + (int) Math.ceil(2.0 * plugin.settings().chainHook().range() / settings.pullSelfSpeed());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticks;
            private Location last;

            @Override
            public void run() {
                if (!caster.isOnline()) {
                    hooks.release(id, null);
                    return;
                }
                ticks++;
                Location now = caster.getLocation();
                Vector toAnchor = anchor.toVector().subtract(caster.getEyeLocation().toVector());
                boolean stalled = last != null && ticks > RAMP_TICKS && now.distanceSquared(last) < STALL_DISTANCE_SQUARED;
                if (toAnchor.length() < ARRIVE_DISTANCE || stalled || ticks > maxTicks) {
                    hooks.release(id, "Chain: Reel: reeled in.");
                    return;
                }
                last = now;
                double speed = Math.min(settings.maxVelocity(), settings.pullSelfSpeed() * Math.min(1.0, ticks / (double) RAMP_TICKS));
                caster.setVelocity(toAnchor.normalize().multiply(speed));
                // Refreshed every tick, so the grace still covers the drop after the last pull.
                plugin.fallGrace().grant(id, settings.fallGraceTicks());
            }
        }, 0L, 1L);
        hook.reel(task);
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private ChainReelSettings settings() {
        return plugin.settings().chainReel();
    }
}
