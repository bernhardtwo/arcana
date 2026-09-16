package com.bernhardtwo.arcana.ability.storm;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Targets;
import com.bernhardtwo.arcana.config.StormSettings;
import com.bernhardtwo.arcana.item.StormHammer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A continuous bolt to whatever the caster aims at while the right mouse
 * button is held. The client repeats a held right click every four ticks,
 * so the beam lives on those clicks: the first one starts it, every one
 * after refreshes it, and {@value #HOLD_TIMEOUT_TICKS} ticks without one
 * ends it. Every tick-period ticks with a target in the line of fire it
 * charges a share of the caster's maximum mana and deals the same share of
 * the target's maximum health as magic damage through {@link StormDamage}.
 * With nothing in the line of fire it charges nothing, and after
 * lose-target-grace-seconds of that it ends. Out of mana ends it with a
 * message. The hand is only read on charge ticks: reading an item's
 * container copies its meta.
 */
public final class StormBeamAbility implements Ability {

    private static final String NAME = "Storm: Beam";
    /** Two missed repeats of the client's four tick cadence mean the button is up. */
    private static final int HOLD_TIMEOUT_TICKS = 8;
    private static final double RAY_SIZE = 0.3;
    private static final double BEAM_STEP = 0.6;
    private static final int BEAM_POINTS_MAX = 48;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Beam> active = new HashMap<>();

    private static final class Beam {
        private int lastClickTick;
        private int ticks;
        private int ticksWithoutTarget;
        private LivingEntity target;
        private boolean warnedClaim;
        private BukkitTask task;
    }

    public StormBeamAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "storm_beam";
    }

    @Override
    public String displayName() {
        return NAME;
    }

    @Override
    public String permission() {
        return StormHammer.PERMISSION;
    }

    /** No cooldown and no cast cost: the beam charges by the second. */
    @Override
    public int cooldownTicks() {
        return 0;
    }

    @Override
    public String loreHint() {
        return "hold";
    }

    /** Never during a charge: one storm at a time. */
    @Override
    public boolean canCast(Player caster) {
        return !plugin.stormCharge().isChanneling(caster.getUniqueId());
    }

    /** The repeated clicks of a held button reach the running beam, skipping the checks the first click passed. */
    @Override
    public boolean replacesActiveCast(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    @Override
    public boolean cast(Player caster) {
        UUID id = caster.getUniqueId();
        int now = Bukkit.getCurrentTick();
        Beam running = active.get(id);
        if (running != null) {
            running.lastClickTick = now;
            return false;
        }
        double firstCharge = settings().manaPerCharge(plugin.mana().maxMana(caster));
        if (!plugin.mana().hasMana(caster, firstCharge)) {
            caster.sendMessage(warn(String.format("%s needs %.1f mana, you have %.0f",
                    NAME, firstCharge, plugin.mana().mana(caster))));
            return false;
        }
        Beam beam = new Beam();
        beam.lastClickTick = now;
        beam.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(caster, beam), 1L, 1L);
        active.put(id, beam);
        plugin.manaBar().refresh(caster);
        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 0.8f, 1.4f);
        }
        return true;
    }

    public boolean isActive(UUID player) {
        return active.containsKey(player);
    }

    /** For the mana bar: the beam and its target, or null when off. */
    public String status(Player player) {
        Beam beam = active.get(player.getUniqueId());
        if (beam == null) {
            return null;
        }
        return beam.target == null ? NAME + ", no target" : NAME + " on " + beam.target.getName();
    }

    /** Ends the beam for any reason. Nothing to restore: the beam changes nothing on the caster. */
    public void end(Player caster, String message) {
        Beam beam = active.remove(caster.getUniqueId());
        if (beam == null) {
            return;
        }
        beam.task.cancel();
        if (!caster.isOnline()) {
            return;
        }
        if (message != null) {
            caster.sendMessage(warn(message));
        }
        plugin.manaBar().refresh(caster);
        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.6f);
        }
    }

    /** Plugin disable: every beam goes. */
    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                end(player, NAME + " off.");
            } else {
                active.remove(id).task.cancel();
            }
        }
    }

    private void tick(Player caster, Beam beam) {
        if (Bukkit.getCurrentTick() - beam.lastClickTick > HOLD_TIMEOUT_TICKS) {
            end(caster, null);
            return;
        }
        beam.ticks++;
        StormSettings.Beam settings = settings();
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTrace(eye, eye.getDirection(), settings.range(),
                FluidCollisionMode.NEVER, true, RAY_SIZE, candidate -> isCandidate(caster, candidate, settings.affectPlayers()));
        LivingEntity target = hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
        if (target != null && plugin.settings().targets().respectClaims()
                && plugin.claims().isBlocked(caster, target.getLocation())) {
            if (!beam.warnedClaim) {
                beam.warnedClaim = true;
                caster.sendMessage(warn(NAME + ": " + target.getName() + " is inside a claim you cannot build in"));
            }
            target = null;
        }
        beam.target = target;
        if (target == null) {
            if (++beam.ticksWithoutTarget > settings.graceTicks()) {
                end(caster, NAME + " lost its target.");
                return;
            }
        } else {
            beam.ticksWithoutTarget = 0;
        }
        if (plugin.settings().effects()) {
            Vector endPoint = hit != null ? hit.getHitPosition()
                    : eye.toVector().add(eye.getDirection().multiply(settings.range()));
            draw(caster.getWorld(), eye, endPoint);
        }
        if (beam.ticks % settings.tickPeriod() != 0) {
            return;
        }
        // The charge branch: the only place the hand is read.
        if (!StormHammer.isHolding(plugin, caster)) {
            end(caster, null);
            return;
        }
        if (target == null) {
            return;
        }
        double mana = settings.manaPerCharge(plugin.mana().maxMana(caster));
        if (!plugin.mana().hasMana(caster, mana)) {
            end(caster, NAME + ": out of mana.");
            return;
        }
        AttributeInstance maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
        double damage = settings.damagePerCharge(maxHealth == null ? target.getHealth() : maxHealth.getValue());
        plugin.mana().consume(caster, mana);
        StormDamage.hit(target, caster, damage, DamageType.MAGIC);
        if (plugin.settings().effects()) {
            Location center = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
            World world = target.getWorld();
            world.spawnParticle(Particle.ELECTRIC_SPARK, center, 12, 0.3, 0.4, 0.3, 0.1);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.25f, 1.8f);
        }
    }

    /** Living, not the caster, not an NPC, not creative or spectator, and players only when affect-players is on. Claims are checked on the one hit, not here. */
    private static boolean isCandidate(Player caster, Entity entity, boolean affectPlayers) {
        return Targets.isTargetable(caster, entity) && (affectPlayers || !(entity instanceof Player));
    }

    private static void draw(World world, Location eye, Vector endPoint) {
        Vector start = eye.toVector().add(new Vector(0.0, -0.3, 0.0));
        Vector line = endPoint.clone().subtract(start);
        double length = line.length();
        if (length < BEAM_STEP) {
            return;
        }
        Vector step = line.multiply(BEAM_STEP / length);
        int points = Math.min(BEAM_POINTS_MAX, (int) (length / BEAM_STEP));
        Location point = start.toLocation(world);
        for (int i = 0; i < points; i++) {
            point.add(step);
            world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private StormSettings.Beam settings() {
        return plugin.settings().storm().beam();
    }
}
