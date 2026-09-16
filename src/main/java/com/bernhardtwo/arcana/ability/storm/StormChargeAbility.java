package com.bernhardtwo.arcana.ability.storm;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.StormSettings;
import com.bernhardtwo.arcana.item.StormHammer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sneak plus right click starts a channel. Releasing sneak fires a dash along
 * the look direction whose power grows with the time held, and a second,
 * weaker dash toward wherever the caster looks by then. Released before
 * min-charge-ticks it fires nothing and charges nothing. The mana is paid on
 * cast, like Shadow: Swap, and comes back on every cancel, since nothing
 * happened; the cooldown starts only at a release that fired. While charging
 * the caster is rooted through walkSpeed, and the speed they had is written
 * to their container like the boots' flight grant, so a crash mid-channel is
 * repaired on the next join. Every exit path goes through {@link #cancel} or
 * {@link #release}, both of which restore the walk speed.
 */
public final class StormChargeAbility implements Ability {

    private static final String NAME = "Storm: Charge";
    private static final double MIN_LENGTH = 1.0E-4;
    private static final double RING_RADIUS_START = 1.6;
    private static final double RING_RADIUS_END = 0.6;
    private static final int RING_POINTS_MAX = 8;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Channel> active = new HashMap<>();

    private static final class Channel {
        private int startTick;
        private BukkitTask task;
    }

    public StormChargeAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "storm_charge";
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
        return "hold sneak, release to dash";
    }

    /** Charged at a release that fired, never on a fizzle. */
    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    /** A repeated sneak click during the channel reaches the ability and is ignored, instead of failing the mana check on a pool the cast already paid from. */
    @Override
    public boolean replacesActiveCast(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    @Override
    public boolean cast(Player caster) {
        UUID id = caster.getUniqueId();
        if (active.containsKey(id) || !caster.isSneaking()) {
            return false;
        }
        // One storm at a time.
        plugin.stormBeam().end(caster, null);
        Channel channel = new Channel();
        channel.startTick = Bukkit.getCurrentTick();
        if (settings().rootWhileCharging()) {
            plugin.store().grantRoot(caster, caster.getWalkSpeed());
            caster.setWalkSpeed(0.0f);
        }
        channel.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(caster, channel), 1L, 1L);
        active.put(id, channel);
        plugin.manaBar().refresh(caster);
        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BREEZE_INHALE, 0.8f, 0.7f);
        }
        return true;
    }

    public boolean isChanneling(UUID player) {
        return active.containsKey(player);
    }

    /** For the mana bar: the charge so far, or null when not channeling. */
    public String status(Player player) {
        Channel channel = active.get(player.getUniqueId());
        return channel == null ? null : NAME + " " + percent(charged(channel)) + "%";
    }

    /** Sneak released: fires when held long enough, fizzles for free otherwise. */
    public void release(Player caster) {
        Channel channel = active.get(caster.getUniqueId());
        if (channel == null) {
            return;
        }
        StormSettings.Charge settings = settings();
        int ticks = charged(channel);
        if (ticks < settings.minChargeTicks()) {
            cancel(caster, null);
            return;
        }
        if (!StormHammer.isHolding(plugin, caster)) {
            cancel(caster, NAME + " cancelled: hammer put away.");
            return;
        }
        end(caster, channel);
        double power = settings.dashPower(ticks);
        dash(caster, power);
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());
        caster.sendMessage(Component.text(String.format("%s: released at %d%%", NAME, percent(ticks)), NamedTextColor.LIGHT_PURPLE));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (caster.isOnline() && !caster.isDead()) {
                dash(caster, power * settings.secondDashPowerMultiplier());
            }
        }, settings.secondDashDelayTicks());
        plugin.manaBar().refresh(caster);
    }

    /** Damage the caster actually took interrupts the channel when cancel-on-damage is on. */
    public void onDamaged(Player caster) {
        if (settings().cancelOnDamage() && active.containsKey(caster.getUniqueId())) {
            cancel(caster, NAME + " interrupted.");
        }
    }

    /** Ends the channel for any reason with nothing fired: the walk speed comes back, and so does the mana paid on cast. */
    public void cancel(Player caster, String message) {
        Channel channel = active.get(caster.getUniqueId());
        if (channel == null) {
            return;
        }
        end(caster, channel);
        plugin.mana().restore(caster, plugin.settings().manaCost(id()));
        if (!caster.isOnline()) {
            return;
        }
        if (message != null) {
            caster.sendMessage(warn(message));
        }
        plugin.manaBar().refresh(caster);
    }

    /** Plugin disable: every channel ends and every caster walks at their own speed again. */
    public void cancelAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                cancel(player, NAME + " cancelled.");
            } else {
                active.remove(id).task.cancel();
            }
        }
    }

    /** Join and startup: a walk speed still in the container means the last session ended mid-channel, so the file carries our 0. */
    public boolean revokeStale(Player player) {
        if (active.containsKey(player.getUniqueId())) {
            return false;
        }
        Float walkSpeed = plugin.store().revokeRoot(player);
        if (walkSpeed == null) {
            return false;
        }
        player.setWalkSpeed(walkSpeed);
        plugin.getLogger().info("Restored the walk speed left at 0 by an unclean shutdown for " + player.getName() + ".");
        return true;
    }

    /** Startup: any online player still carrying a root (a reload of the plugin) gets their walk speed back. */
    public void sweep() {
        int restored = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (revokeStale(player)) {
                restored++;
            }
        }
        if (restored > 0) {
            plugin.getLogger().warning("Restored the walk speed of " + restored + " player(s) left rooted by an unclean shutdown.");
        }
    }

    private void end(Player caster, Channel channel) {
        active.remove(caster.getUniqueId());
        channel.task.cancel();
        Float walkSpeed = plugin.store().revokeRoot(caster);
        if (walkSpeed != null) {
            caster.setWalkSpeed(walkSpeed);
        }
    }

    private void tick(Player caster, Channel channel) {
        if (!plugin.settings().effects()) {
            return;
        }
        int ticks = charged(channel);
        int max = settings().maxChargeTicks();
        if (ticks == max) {
            caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f);
        }
        // Sparks on a ring that tightens and thickens as the charge builds.
        double progress = Math.min(1.0, ticks / (double) max);
        World world = caster.getWorld();
        Location center = caster.getLocation().add(0.0, 0.2, 0.0);
        double radius = RING_RADIUS_START - (RING_RADIUS_START - RING_RADIUS_END) * progress;
        int points = 1 + (int) Math.round(progress * (RING_POINTS_MAX - 1));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < points; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            world.spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(Math.cos(angle) * radius, random.nextDouble() * 1.6, Math.sin(angle) * radius),
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void dash(Player caster, double power) {
        Vector direction = caster.getLocation().getDirection();
        if (direction.lengthSquared() < MIN_LENGTH || power <= 0.0) {
            return;
        }
        caster.setVelocity(direction.normalize().multiply(power));
        if (plugin.settings().effects()) {
            World world = caster.getWorld();
            world.spawnParticle(Particle.GUST, caster.getLocation(), 1, 0.0, 0.0, 0.0, 0.0);
            world.playSound(caster.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 0.8f);
        }
    }

    private int charged(Channel channel) {
        return Bukkit.getCurrentTick() - channel.startTick;
    }

    private int percent(int ticks) {
        return (int) Math.min(100L, Math.round(100.0 * ticks / settings().maxChargeTicks()));
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private StormSettings.Charge settings() {
        return plugin.settings().storm().charge();
    }
}
