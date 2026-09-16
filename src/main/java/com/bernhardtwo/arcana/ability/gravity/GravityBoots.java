package com.bernhardtwo.arcana.ability.gravity;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.PlayerStore;
import com.bernhardtwo.arcana.config.GravityBootsSettings;
import com.bernhardtwo.arcana.item.LevitationBoots;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sustained flight for mana with the Levitation Boots. Not a cast ability: it
 * is driven by a sneak click, the flight toggle and the armor slot. Three
 * states:
 *
 * <p><b>Worn</b>: the boots do nothing. allowFlight is whatever it was and
 * falls hurt like vanilla.
 *
 * <p><b>Armed</b>: sneak plus right click with an empty main hand while
 * wearing them. allowFlight goes on so the client sends the double tap, and
 * what the player had before (allowFlight and flySpeed, for an Essentials
 * /fly or an admin) is written to their container the moment it is granted
 * and restored from there on every disarm path, including a join after a
 * crash, so nobody keeps creative flight for free and nobody loses a /fly
 * they had. The grant in the container is the armed state.
 *
 * <p><b>Flying</b>: the player toggled flight on while armed. A per-flight
 * counter of accumulated seconds sets the ladder step, and mana is charged
 * every {@code tick-period} ticks. Every exit path goes through {@link #stop},
 * and every disarm path through {@link #disarm}, which stops first.
 */
public final class GravityBoots {

    public static final String PERMISSION = "arcana.use." + LevitationBoots.ID;
    private static final String NAME = "Levitation Boots";
    private static final float VANILLA_FLY_SPEED = 0.1f;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Flight> active = new HashMap<>();

    private static final class Flight {
        private double seconds;
        private int ticks;
        private int step;
    }

    public GravityBoots(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isFlying(UUID player) {
        return active.containsKey(player);
    }

    /** For the mana bar title while flying, null otherwise. */
    public String status(UUID player) {
        Flight flight = active.get(player);
        if (flight == null) {
            return null;
        }
        return String.format("%s step %d (%.0f/s)", NAME, flight.step, settings().manaPerSecond(flight.seconds));
    }

    // ----- armed -----

    public boolean isArmed(Player player) {
        return plugin.store().hasFlightGrant(player);
    }

    /** The sneak click: arms, or disarms when already armed. */
    public void toggleArmed(Player player) {
        if (isArmed(player)) {
            disarm(player, NAME + " disarmed.");
        } else {
            arm(player);
        }
    }

    /** Grants allowFlight, remembering what the player had. Refuses in creative or spectator, without the boots on, or without permission. */
    public boolean arm(Player player) {
        if (isArmed(player) || isCreativeLike(player) || !LevitationBoots.isWearing(plugin, player)) {
            return false;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendActionBar(warn("You do not have permission to use " + NAME + "."));
            return false;
        }
        plugin.store().grantFlight(player, new PlayerStore.FlightGrant(player.getAllowFlight(), player.getFlySpeed()));
        player.setAllowFlight(true);
        player.sendActionBar(Component.text(NAME + " armed: double tap jump to fly", NamedTextColor.LIGHT_PURPLE));
        if (plugin.settings().effects()) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
        }
        return true;
    }

    /**
     * Ends the armed state for any reason: a running flight ends first, with
     * the grace, then allowFlight and flySpeed go back to what they were
     * before the grant. Returns false when the player was not armed.
     */
    public boolean disarm(Player player, String message) {
        stop(player, null, true);
        PlayerStore.FlightGrant previous = plugin.store().revokeFlight(player);
        if (previous == null) {
            return false;
        }
        player.setAllowFlight(previous.allowFlight());
        player.setFlySpeed(previous.flySpeed());
        if (message != null && player.isOnline()) {
            player.sendActionBar(warn(message));
        }
        if (plugin.settings().effects() && player.isOnline()) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.6f);
        }
        return true;
    }

    /** Damage from an entity that actually went through: a flying escape is not free with disarm-on-damage on. */
    public void onDamaged(Player player) {
        if (settings().disarmOnDamage() && isArmed(player)) {
            disarm(player, NAME + " disarmed: you were hit.");
        }
    }

    /**
     * Join and startup: a grant still in the container means the last session
     * ended without a disarm, so the file carries our allowFlight. Restore the
     * previous values before anything else sees the player.
     */
    public boolean revokeStale(Player player) {
        if (!disarm(player, null)) {
            return false;
        }
        plugin.getLogger().info("Revoked flight left over from an unclean shutdown for " + player.getName() + ".");
        return true;
    }

    // ----- flying -----

    /** The double tap turning flight on. False refuses, and the caller cancels the toggle so the client lands again. */
    public boolean start(Player player) {
        UUID id = player.getUniqueId();
        if (active.containsKey(id)) {
            return true;
        }
        GravityBootsSettings settings = settings();
        if (!player.hasPermission(PERMISSION)) {
            player.sendActionBar(warn("You do not have permission to use " + NAME + "."));
            return false;
        }
        if (player.isGliding()) {
            player.sendActionBar(warn(NAME + ": not while gliding"));
            return false;
        }
        if (settings.respectClaims() && plugin.claims().isBlocked(player, player.getLocation())) {
            player.sendActionBar(warn(NAME + ": not inside a claim you cannot build in"));
            return false;
        }
        double seconds = plugin.store().flightSeconds(player, settings.stepIntervalSeconds(),
                settings.decayIntervalSeconds() * 1000L);
        double firstCharge = settings.manaPerSecond(seconds) * settings.tickPeriod() / 20.0;
        if (!plugin.mana().hasMana(player, firstCharge)) {
            player.sendActionBar(warn(String.format("%s need %.1f mana, you have %.0f",
                    NAME, firstCharge, plugin.mana().mana(player))));
            return false;
        }

        Flight flight = new Flight();
        flight.seconds = seconds;
        flight.step = settings.step(seconds);
        active.put(id, flight);
        plugin.store().writeFlightSeconds(player, seconds);
        player.setFlySpeed(settings.flySpeed());
        player.sendActionBar(Component.text(String.format("%s: flying, step %d (%.0f mana/s)",
                NAME, flight.step, settings.manaPerSecond(seconds)), NamedTextColor.LIGHT_PURPLE));
        plugin.manaBar().refresh(player);
        if (plugin.settings().effects()) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.4f);
        }
        return true;
    }

    /**
     * Ends the flight for any reason: writes the counter, lands the player,
     * restores the fly speed and, with {@code grace}, hands out Slow Falling so
     * a flight cut short in the air is survivable.
     */
    public void stop(Player player, String message, boolean grace) {
        Flight flight = active.remove(player.getUniqueId());
        if (flight == null) {
            return;
        }
        plugin.store().writeFlightSeconds(player, flight.seconds);
        if (!player.isOnline()) {
            return;
        }
        if (player.isFlying() && !isCreativeLike(player)) {
            player.setFlying(false);
        }
        PlayerStore.FlightGrant grant = plugin.store().flightGrant(player);
        player.setFlySpeed(grant == null ? VANILLA_FLY_SPEED : grant.flySpeed());
        int graceTicks = settings().graceSlowFallingSeconds() * 20;
        if (grace && graceTicks > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, graceTicks, 0, false, false, true));
        }
        if (message != null) {
            player.sendActionBar(warn(message));
        }
        plugin.manaBar().refresh(player);
        if (plugin.settings().effects()) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 0.8f);
        }
    }

    /** Runs every tick; charges every tick-period ticks. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        GravityBootsSettings settings = settings();
        double secondsPerCharge = settings.tickPeriod() / 20.0;
        for (Map.Entry<UUID, Flight> entry : new ArrayList<>(active.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Flight flight = entry.getValue();
            if (player == null) {
                active.remove(entry.getKey());
                continue;
            }
            // Backstop for a setFlying(false) from another plugin, which fires no toggle event.
            if (!player.isFlying() || isCreativeLike(player)) {
                stop(player, NAME + " off.", false);
                continue;
            }
            if (++flight.ticks % settings.tickPeriod() != 0) {
                continue;
            }
            // Only on charge ticks: reading the boots copies their meta, and the armor event already covers removal.
            if (!LevitationBoots.isWearing(plugin, player)) {
                stop(player, NAME + " off.", false);
                continue;
            }
            flight.seconds += secondsPerCharge;
            double cost = settings.manaPerSecond(flight.seconds) * secondsPerCharge;
            if (!plugin.mana().hasMana(player, cost)) {
                stop(player, NAME + ": out of mana, slow falling.", true);
                continue;
            }
            plugin.mana().consume(player, cost);
            plugin.store().writeFlightSeconds(player, flight.seconds);
            int step = settings.step(flight.seconds);
            if (step != flight.step) {
                flight.step = step;
                if (plugin.settings().effects()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f + 0.1f * Math.min(step, 8));
                }
            }
            if (plugin.settings().effects()) {
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 2, 0.2, 0.05, 0.2, 0.01);
            }
        }
    }

    /** Plugin disable: everyone is disarmed, which ends every flight with the grace and restores every grant. */
    public void endAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            disarm(player, NAME + " disarmed.");
        }
        active.clear();
    }

    /** Startup: any online player still carrying a grant (a reload of the plugin) gets it restored. */
    public void sweep() {
        int revoked = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (revokeStale(player)) {
                revoked++;
            }
        }
        if (revoked > 0) {
            plugin.getLogger().warning("Restored allowFlight for " + revoked + " player(s) left armed by an unclean shutdown.");
        }
    }

    private static boolean isCreativeLike(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private GravityBootsSettings settings() {
        return plugin.settings().gravityBoots();
    }
}
