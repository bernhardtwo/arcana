package com.bernhardtwo.arcana.ability.shadow;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.ShadowSwapSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Trades places with the first living entity on the aim line, after a channel.
 * The target is locked on cast, the caster slows down while the channel builds
 * up, and the target is validated again at the end: out of range, dead,
 * mounted or inside a claim the caster cannot build in makes it fizzle for a
 * short cooldown. Unlike Blink the trace crosses walls, so the claim check
 * always applies: swapping into someone's base would be a real bypass.
 * Every exit path goes through {@link #cancel(UUID, String)}, which restores
 * the walk speed.
 */
public final class ShadowSwapAbility implements Ability {

    private static final double RAY_SIZE = 0.4;
    private static final int BAR_SEGMENTS = 10;
    private static final double RING_RADIUS_START = 2.2;
    private static final double RING_RADIUS_END = 0.6;
    private static final int RING_POINTS_MAX = 8;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Channel> active = new HashMap<>();

    private static final class Channel {
        private LivingEntity target;
        private float walkSpeed;
        private BukkitTask task;
        private int elapsed;
    }

    public ShadowSwapAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "shadow_swap";
    }

    @Override
    public String displayName() {
        return "Shadow: Swap";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    /** The cooldown is charged when the channel resolves: full on a swap, short on a fizzle or a claim refusal, none otherwise. */
    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    /** A left click during the channel reaches the ability, skipping the cooldown check, and cancels it. */
    @Override
    public boolean replacesActiveCast(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    @Override
    public void cast(Player caster) {
        UUID id = caster.getUniqueId();
        if (active.containsKey(id)) {
            cancel(id, "Shadow: Swap cancelled.");
            return;
        }
        ShadowSwapSettings settings = settings();
        if (caster.isInsideVehicle() || !caster.getPassengers().isEmpty()) {
            caster.sendActionBar(warn("Shadow: Swap: dismount first"));
            return;
        }
        Location eye = caster.getEyeLocation();
        RayTraceResult hit = caster.getWorld().rayTraceEntities(eye, eye.getDirection().normalize(), settings.range(),
                RAY_SIZE, candidate -> isCandidate(caster, candidate, settings.allowPlayers()));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            caster.sendActionBar(warn("Shadow: Swap: nothing to swap with"));
            return;
        }
        if (plugin.claims().isBlocked(caster, target.getLocation())) {
            caster.sendActionBar(warn("Shadow: Swap: " + target.getName() + " is inside a claim you cannot build in"));
            plugin.store().startCooldownWithIndicator(caster, id(), settings.failedCooldownTicks());
            return;
        }

        Channel channel = new Channel();
        channel.target = target;
        channel.walkSpeed = caster.getWalkSpeed();
        channel.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(caster, channel), 1L, 1L);
        active.put(id, channel);

        if (settings.warnTarget() && target instanceof Player other) {
            other.sendMessage(Component.text(caster.getName() + " is channeling a swap on you, break line or run",
                    NamedTextColor.LIGHT_PURPLE));
        }
        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.4f, 1.4f);
        }
    }

    public boolean isChanneling(UUID player) {
        return active.containsKey(player);
    }

    /** Damage taken by the caster interrupts the channel when cancel-on-damage is on. */
    public void onDamaged(Player caster) {
        if (settings().cancelOnDamage() && active.containsKey(caster.getUniqueId())) {
            cancel(caster.getUniqueId(), "Shadow: Swap interrupted.");
        }
    }

    private void tick(Player caster, Channel channel) {
        ShadowSwapSettings settings = settings();
        channel.elapsed++;
        double progress = Math.min(1.0, channel.elapsed / (double) settings.channelTicks());

        double factor = 1.0 - progress * (1.0 - settings.channelMinSpeedFactor());
        caster.setWalkSpeed((float) (channel.walkSpeed * factor));
        caster.sendActionBar(progressBar(progress));
        if (plugin.settings().effects()) {
            converge(caster, progress);
            if (settings.warnTarget() && channel.target instanceof Player) {
                channel.target.getWorld().spawnParticle(Particle.WITCH, channel.target.getLocation().add(0.0, 1.0, 0.0),
                        3, 0.4, 0.6, 0.4, 0.0);
            }
        }

        if (channel.elapsed >= settings.channelTicks()) {
            complete(caster, channel, settings);
        }
    }

    private void complete(Player caster, Channel channel, ShadowSwapSettings settings) {
        LivingEntity target = channel.target;
        String reason = null;
        if (!target.isValid() || !isCandidate(caster, target, settings.allowPlayers())) {
            reason = target.getName() + " is gone";
        } else if (!target.getWorld().equals(caster.getWorld())
                || target.getLocation().distance(caster.getEyeLocation()) > settings.range()) {
            reason = target.getName() + " got out of range";
        } else if (plugin.claims().isBlocked(caster, target.getLocation())) {
            reason = target.getName() + " is inside a claim you cannot build in";
        } else if (caster.isInsideVehicle() || !caster.getPassengers().isEmpty()) {
            reason = "you are mounted";
        }
        if (reason != null) {
            cancel(caster.getUniqueId(), "Shadow: Swap fizzled: " + reason);
            plugin.store().startCooldownWithIndicator(caster, id(), settings.failedCooldownTicks());
            return;
        }

        cancel(caster.getUniqueId(), null);
        Location from = caster.getLocation();
        Location to = target.getLocation();
        Location casterDestination = to.clone();
        casterDestination.setYaw(from.getYaw());
        casterDestination.setPitch(from.getPitch());
        Location targetDestination = from.clone();
        targetDestination.setYaw(to.getYaw());
        targetDestination.setPitch(to.getPitch());

        plugin.fallGrace().grant(caster.getUniqueId(), settings.fallGraceTicks());
        plugin.fallGrace().grant(target.getUniqueId(), settings.fallGraceTicks());
        if (!caster.teleport(casterDestination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            caster.sendActionBar(warn("Shadow: Swap: something blocked the swap"));
            plugin.store().startCooldownWithIndicator(caster, id(), settings.failedCooldownTicks());
            return;
        }
        if (!target.teleport(targetDestination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            caster.teleport(from, PlayerTeleportEvent.TeleportCause.PLUGIN);
            caster.sendActionBar(warn("Shadow: Swap: something blocked the swap"));
            plugin.store().startCooldownWithIndicator(caster, id(), settings.failedCooldownTicks());
            return;
        }
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());

        caster.sendActionBar(Component.text("Shadow: Swap: traded places with " + target.getName(),
                NamedTextColor.LIGHT_PURPLE));
        if (target instanceof Player other) {
            other.sendMessage(Component.text("You were swapped by " + caster.getName(), NamedTextColor.LIGHT_PURPLE));
        }
        if (plugin.settings().effects()) {
            World world = caster.getWorld();
            puff(world, from);
            puff(world, to);
        }
    }

    /**
     * Ends the channel for any reason, restoring the walk speed stored at the
     * start. Charges nothing itself; the callers decide what the end costs.
     */
    public void cancel(UUID player, String message) {
        Channel channel = active.remove(player);
        if (channel == null) {
            return;
        }
        channel.task.cancel();
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        online.setWalkSpeed(channel.walkSpeed);
        if (message != null) {
            online.sendActionBar(warn(message));
        }
    }

    /** Plugin disable: every channel ends and every caster walks at their own speed again. */
    public void cancelAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            cancel(id, null);
        }
    }

    /** Living, not the caster, not an NPC, not riding or ridden, and players only when allowed and survival or adventure. */
    private static boolean isCandidate(Player caster, Entity entity, boolean allowPlayers) {
        if (!(entity instanceof LivingEntity) || entity.equals(caster) || entity.hasMetadata("NPC")) {
            return false;
        }
        if (entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) {
            return false;
        }
        if (entity instanceof Player player) {
            GameMode gameMode = player.getGameMode();
            return allowPlayers && gameMode != GameMode.CREATIVE && gameMode != GameMode.SPECTATOR;
        }
        return true;
    }

    /** Smoke drifting inward from a ring that tightens and thickens as the channel advances. */
    private static void converge(Player caster, double progress) {
        World world = caster.getWorld();
        Location center = caster.getLocation().add(0.0, 1.0, 0.0);
        double radius = RING_RADIUS_START - (RING_RADIUS_START - RING_RADIUS_END) * progress;
        int points = 1 + (int) Math.round(progress * (RING_POINTS_MAX - 1));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < points; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            Vector offset = new Vector(Math.cos(angle) * radius, random.nextDouble() * 1.2 - 0.6, Math.sin(angle) * radius);
            Vector inward = offset.clone().multiply(-1.0).normalize();
            world.spawnParticle(Particle.SMOKE, center.clone().add(offset), 0,
                    inward.getX(), inward.getY(), inward.getZ(), 0.12);
        }
    }

    private static Component progressBar(double progress) {
        int filled = (int) Math.round(progress * BAR_SEGMENTS);
        return Component.text("Shadow: Swap ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("|".repeat(filled), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text("|".repeat(BAR_SEGMENTS - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + (int) Math.round(progress * 100) + "%", NamedTextColor.GRAY));
    }

    private static void puff(World world, Location at) {
        Location center = at.clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 20, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.PORTAL, center, 30, 0.3, 0.5, 0.3, 0.5);
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f);
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private ShadowSwapSettings settings() {
        return plugin.settings().shadowSwap();
    }
}
