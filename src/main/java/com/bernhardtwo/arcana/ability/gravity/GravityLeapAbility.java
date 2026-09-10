package com.bernhardtwo.arcana.ability.gravity;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.LeapSettings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Second jump while airborne, once per airborne period. The per-jump flag is
 * cleared by {@link #tick()}, which the plugin runs every 5 ticks.
 */
public final class GravityLeapAbility implements Ability {

    private static final double MIN_LENGTH = 1.0E-4;

    private final ArcanaPlugin plugin;
    private final Set<UUID> spent = new HashSet<>();

    public GravityLeapAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "gravity_leap";
    }

    @Override
    public String displayName() {
        return "Gravity: Leap";
    }

    @Override
    public int cooldownTicks() {
        return plugin.settings().leap().cooldownTicks();
    }

    @Override
    public boolean canCast(Player caster) {
        return !caster.isOnGround() && !spent.contains(caster.getUniqueId());
    }

    @Override
    public void cast(Player caster) {
        LeapSettings settings = plugin.settings().leap();
        spent.add(caster.getUniqueId());

        Vector forward = caster.getLocation().getDirection().setY(0.0);
        if (forward.lengthSquared() > MIN_LENGTH) {
            forward.normalize().multiply(settings.forwardBoost());
        } else {
            forward.zero();
        }
        Vector velocity = caster.getVelocity();
        velocity.setY(settings.jumpPower());
        velocity.add(forward);
        caster.setVelocity(velocity);

        plugin.fallGrace().grant(caster.getUniqueId(), settings.fallGraceTicks());

        if (plugin.settings().effects()) {
            World world = caster.getWorld();
            Location feet = caster.getLocation();
            world.spawnParticle(Particle.CLOUD, feet, 12, 0.3, 0.05, 0.3, 0.02);
            world.playSound(feet, Sound.ENTITY_BREEZE_JUMP, 0.8f, 1.2f);
        }
    }

    // ponytail: 5-tick sampling can miss a landing shorter than 5 ticks; use PlayerMoveEvent if that matters.
    public void tick() {
        if (spent.isEmpty()) {
            return;
        }
        spent.removeIf(id -> {
            Player player = Bukkit.getPlayer(id);
            return player == null || player.isOnGround();
        });
    }
}
