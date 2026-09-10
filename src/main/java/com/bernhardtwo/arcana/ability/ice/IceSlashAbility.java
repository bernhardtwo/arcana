package com.bernhardtwo.arcana.ability.ice;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Targets;
import com.bernhardtwo.arcana.config.IceSlashSettings;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/** Melee arc in front of the caster plus a tagged snowball. Damage lands through the vanilla damage pipeline. */
public final class IceSlashAbility implements Ability {

    private static final double MIN_LENGTH = 1.0E-4;
    private static final int ARC_POINTS = 14;

    private final ArcanaPlugin plugin;

    public IceSlashAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "ice_slash";
    }

    @Override
    public String displayName() {
        return "Ice: Slash";
    }

    @Override
    public int cooldownTicks() {
        return plugin.settings().iceSlash().cooldownTicks();
    }

    @Override
    public String lockoutGroup() {
        return "ice";
    }

    @Override
    public boolean cast(Player caster) {
        IceSlashSettings settings = plugin.settings().iceSlash();
        Location origin = caster.getLocation();
        Vector look = origin.getDirection().setY(0.0);
        boolean hasLook = look.lengthSquared() > MIN_LENGTH;
        if (hasLook) {
            look.normalize();
        }
        double range = settings.range();
        double minDot = Math.cos(Math.toRadians(settings.arcDegrees()));

        for (Entity entity : caster.getWorld().getNearbyEntities(origin, range, range, range,
                candidate -> Targets.isValid(plugin, caster, candidate))) {
            Vector offset = entity.getLocation().toVector().subtract(origin.toVector());
            if (offset.lengthSquared() > range * range) {
                continue;
            }
            offset.setY(0.0);
            // Standing on top of the caster counts as in front; otherwise compare on the horizontal plane.
            if (offset.lengthSquared() > MIN_LENGTH && (!hasLook || offset.normalize().dot(look) < minDot)) {
                continue;
            }
            plugin.frost().hit((LivingEntity) entity, caster, settings.slashDamage(),
                    settings.freezeTicks(), settings.slowDurationTicks());
        }

        Vector velocity = caster.getEyeLocation().getDirection().multiply(settings.snowballSpeed());
        Snowball snowball = caster.launchProjectile(Snowball.class, velocity);
        snowball.getPersistentDataContainer().set(plugin.iceSnowballKey(), PersistentDataType.BYTE, (byte) 1);

        if (plugin.settings().effects() && hasLook) {
            playEffects(caster, look, settings.arcDegrees());
        }
        return true;
    }

    private void playEffects(Player caster, Vector look, double arcDegrees) {
        World world = caster.getWorld();
        Location center = caster.getEyeLocation().subtract(0.0, 0.4, 0.0);
        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        world.playSound(center, Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 1.5f);

        double half = Math.toRadians(arcDegrees);
        for (int i = 0; i < ARC_POINTS; i++) {
            double angle = -half + 2.0 * half * i / (ARC_POINTS - 1);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vector rotated = new Vector(
                    look.getX() * cos - look.getZ() * sin,
                    0.0,
                    look.getX() * sin + look.getZ() * cos
            ).multiply(1.8);
            world.spawnParticle(Particle.SNOWFLAKE, center.clone().add(rotated), 2, 0.05, 0.1, 0.05, 0.0);
        }
    }
}
