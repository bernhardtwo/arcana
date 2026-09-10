package com.bernhardtwo.arcana.ability.gravity;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.GravitySettings;
import com.bernhardtwo.arcana.config.TargetRules;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class GravityAbility implements Ability {

    private static final double MIN_LENGTH = 1.0E-4;

    private final ArcanaPlugin plugin;
    private final GravityMode mode;

    public GravityAbility(ArcanaPlugin plugin, GravityMode mode) {
        this.plugin = plugin;
        this.mode = mode;
    }

    @Override
    public String id() {
        return mode == GravityMode.PUSH ? "gravity_push" : "gravity_pull";
    }

    @Override
    public String displayName() {
        return mode == GravityMode.PUSH ? "Gravity: Push" : "Gravity: Pull";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    @Override
    public void cast(Player caster) {
        GravitySettings settings = settings();
        TargetRules rules = plugin.settings().targets();
        Location center = caster.getLocation();
        World world = center.getWorld();
        double radius = settings.radius();

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius, this::isCandidate)) {
            if (entity.equals(caster) || !isAllowedTarget(entity, rules)) {
                continue;
            }
            if (rules.respectClaims() && plugin.claims().isBlocked(caster, entity.getLocation())) {
                continue;
            }
            Vector offset = entity.getLocation().toVector().subtract(center.toVector());
            double distance = offset.length();
            if (distance > radius) {
                continue;
            }
            if (distance < 0.1) {
                offset = caster.getLocation().getDirection().setY(0.0);
                if (offset.lengthSquared() < MIN_LENGTH) {
                    continue;
                }
                distance = 0.1;
            }
            applyImpulse(entity, offset, distance, radius, settings);
        }

        if (plugin.settings().effects()) {
            playEffects(caster, settings);
        }
    }

    private void applyImpulse(Entity entity, Vector offset, double distance, double radius, GravitySettings settings) {
        double scale = mode == GravityMode.PUSH
                ? Math.max(0.2, 1.0 - distance / radius)
                : 0.4 + 0.6 * (distance / radius);

        Vector impulse = offset.clone();
        if (impulse.lengthSquared() < MIN_LENGTH) {
            return;
        }
        impulse.normalize().multiply(settings.strength() * scale);
        if (mode == GravityMode.PULL) {
            impulse.multiply(-1.0);
            impulse.setY(settings.lift());
        } else {
            impulse.setY(Math.max(impulse.getY(), 0.0) + settings.lift());
        }

        Vector result = entity.getVelocity().add(impulse);
        clamp(result, settings.maxVelocity());
        entity.setVelocity(result);
        grantFallGrace(entity, settings);
    }

    private void grantFallGrace(Entity entity, GravitySettings settings) {
        boolean cancel = entity instanceof Player
                ? settings.cancelFallDamagePlayers()
                : settings.cancelFallDamageMobs();
        if (cancel) {
            plugin.fallGrace().grant(entity.getUniqueId(), settings.fallGraceTicks());
        }
    }

    private void clamp(Vector velocity, double max) {
        double length = velocity.length();
        if (length > max && length > MIN_LENGTH) {
            velocity.normalize().multiply(max);
        }
    }

    private boolean isCandidate(Entity entity) {
        return entity instanceof LivingEntity && !entity.hasMetadata("NPC");
    }

    private boolean isAllowedTarget(Entity entity, TargetRules rules) {
        if (entity instanceof Player player) {
            GameMode gameMode = player.getGameMode();
            if (gameMode == GameMode.SPECTATOR || gameMode == GameMode.CREATIVE) {
                return false;
            }
            return rules.players();
        }
        if (entity instanceof ArmorStand) {
            return rules.armorStands();
        }
        if (isHostile(entity)) {
            return rules.hostiles();
        }
        return rules.passives();
    }

    private boolean isHostile(Entity entity) {
        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Ghast
                || entity instanceof Phantom
                || entity instanceof Hoglin;
    }

    private void playEffects(Player caster, GravitySettings settings) {
        World world = caster.getWorld();
        Location origin = caster.getLocation().add(0.0, 1.0, 0.0);
        double outer = Math.min(settings.radius(), 8.0);

        world.playSound(origin, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, mode == GravityMode.PUSH ? 1.4f : 0.7f);
        world.playSound(origin, mode == GravityMode.PUSH ? Sound.ENTITY_GENERIC_EXPLODE : Sound.BLOCK_BEACON_POWER_SELECT,
                0.6f, mode == GravityMode.PUSH ? 1.7f : 0.8f);

        new BukkitRunnable() {
            private int step = 0;

            @Override
            public void run() {
                if (step >= 5 || !caster.isOnline()) {
                    cancel();
                    return;
                }
                double progress = step / 4.0;
                double ring = mode == GravityMode.PUSH
                        ? 0.8 + (outer - 0.8) * progress
                        : outer - (outer - 0.8) * progress;
                drawRing(world, origin, ring);
                step++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void drawRing(World world, Location origin, double radius) {
        int points = Math.max(16, (int) (radius * 10));
        Particle particle = mode == GravityMode.PUSH ? Particle.CLOUD : Particle.PORTAL;
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            Location at = origin.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
            world.spawnParticle(particle, at, 1, 0.05, 0.15, 0.05, 0.0);
        }
    }

    private GravitySettings settings() {
        return mode == GravityMode.PUSH ? plugin.settings().push() : plugin.settings().pull();
    }
}
