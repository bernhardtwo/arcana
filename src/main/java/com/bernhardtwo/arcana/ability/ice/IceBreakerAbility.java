package com.bernhardtwo.arcana.ability.ice;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Targets;
import com.bernhardtwo.arcana.config.IceBreakerSettings;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Spiral ice beam. The hit is resolved instantly by ray trace; the spiral is only drawn afterwards. */
public final class IceBreakerAbility implements Ability {

    private static final double STEP = 0.5;
    private static final int MAX_STEPS = 80;
    private static final int ANIMATION_TICKS = 4;
    private static final double SPIRAL_RADIUS = 0.4;
    private static final double TURNS_PER_BLOCK = 2.5;
    private static final double RAY_SIZE = 0.4;

    private final ArcanaPlugin plugin;

    public IceBreakerAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "ice_breaker";
    }

    @Override
    public String displayName() {
        return "Ice: Breaker";
    }

    @Override
    public int cooldownTicks() {
        return plugin.settings().iceBreaker().cooldownTicks();
    }

    @Override
    public String lockoutGroup() {
        return "ice";
    }

    @Override
    public boolean cast(Player caster) {
        IceBreakerSettings settings = plugin.settings().iceBreaker();
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult hit = world.rayTrace(eye, direction, settings.range(), FluidCollisionMode.NEVER, true, RAY_SIZE,
                candidate -> Targets.isValid(plugin, caster, candidate));
        Vector impact = hit != null
                ? hit.getHitPosition()
                : eye.toVector().add(direction.clone().multiply(settings.range()));

        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            plugin.frost().hit(target, caster, settings.damage(), settings.freezeTicks(), settings.slowDurationTicks());
        }

        if (plugin.settings().effects()) {
            world.playSound(eye, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.3f);
            animate(world, eye.toVector(), direction, impact);
        }
        return true;
    }

    private void animate(World world, Vector start, Vector direction, Vector impact) {
        double length = impact.distance(start);
        int steps = (int) Math.min(Math.ceil(length / STEP), MAX_STEPS);
        double stepLength = steps == 0 ? 0.0 : length / steps;

        // Two cross products give a basis perpendicular to the beam at any angle, straight up included.
        Vector helper = Math.abs(direction.getY()) < 0.9 ? new Vector(0.0, 1.0, 0.0) : new Vector(1.0, 0.0, 0.0);
        Vector u = direction.clone().crossProduct(helper).normalize();
        Vector v = direction.clone().crossProduct(u).normalize();
        int perTick = Math.max(1, (int) Math.ceil(steps / (double) ANIMATION_TICKS));
        Location impactLocation = impact.toLocation(world);

        new BukkitRunnable() {
            private int done = 0;

            @Override
            public void run() {
                int end = Math.min(steps, done + perTick);
                for (int i = done; i < end; i++) {
                    double distance = i * stepLength;
                    double angle = distance * TURNS_PER_BLOCK;
                    Vector offset = u.clone().multiply(Math.cos(angle) * SPIRAL_RADIUS)
                            .add(v.clone().multiply(Math.sin(angle) * SPIRAL_RADIUS));
                    Vector along = start.clone().add(direction.clone().multiply(distance));
                    world.spawnParticle(Particle.SNOWFLAKE, along.clone().add(offset).toLocation(world), 1, 0.0, 0.0, 0.0, 0.0);
                    world.spawnParticle(Particle.SNOWFLAKE, along.subtract(offset).toLocation(world), 1, 0.0, 0.0, 0.0, 0.0);
                }
                done = end;
                if (done >= steps) {
                    world.spawnParticle(Particle.SNOWFLAKE, impactLocation, 30, 0.4, 0.4, 0.4, 0.05);
                    world.spawnParticle(Particle.ITEM_SNOWBALL, impactLocation, 12, 0.3, 0.3, 0.3, 0.0);
                    world.playSound(impactLocation, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.9f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
