package com.bernhardtwo.arcana.ability.solar;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Displays;
import com.bernhardtwo.arcana.config.SolarLanternSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Night vision plus one orbiting light. Right click toggles it: lighting one
 * checks the cooldown, dismissing never does. The lantern ends on dismiss,
 * on the light running out, quit, death, world change and plugin disable,
 * always through {@link #end(UUID, String)}, which charges a cooldown
 * proportional to the time it was lit.
 */
public final class SolarLanternAbility implements Ability {

    private static final float ORB_SCALE = 0.35f;
    private static final double ORBIT_HEIGHT = 1.4;
    private static final double ORBIT_SPEED = 0.08;
    private static final long WARN_BEFORE_MILLIS = 60_000L;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Lantern> active = new HashMap<>();

    private static final class Lantern {
        private BlockDisplay orb;
        private long litSinceMillis;
        private int durationTicks;
        private double angle;
        private boolean warned;
    }

    public SolarLanternAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "solar_lantern";
    }

    @Override
    public String displayName() {
        return "Solar: Lantern";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public String loreHint() {
        return "toggle";
    }

    /** Dismissing is always allowed: the listener skips the cooldown check while a lantern is lit. */
    @Override
    public boolean replacesActiveCast(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    @Override
    public boolean cast(Player caster) {
        if (active.containsKey(caster.getUniqueId())) {
            int cooldown = end(caster.getUniqueId(), null);
            caster.sendActionBar(Component.text("Solar: Lantern dismissed, cooldown " + format(cooldown),
                    NamedTextColor.GOLD));
            return false;
        }

        SolarLanternSettings settings = settings();
        Lantern lantern = new Lantern();
        lantern.litSinceMillis = System.currentTimeMillis();
        lantern.durationTicks = settings.durationTicks();
        lantern.orb = Displays.spawn(plugin, orbitPoint(caster, lantern, settings), Material.LANTERN, ORB_SCALE);
        active.put(caster.getUniqueId(), lantern);

        caster.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, settings.durationTicks(), 0,
                false, false, true));
        caster.sendActionBar(Component.text("Solar: Lantern lit, right click again to dismiss", NamedTextColor.GOLD));

        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.6f);
            caster.getWorld().spawnParticle(Particle.END_ROD, caster.getEyeLocation(), 12, 0.4, 0.3, 0.4, 0.02);
        }
        return true;
    }

    /** Runs every tick: orbits the orb, warns once near the end and expires finished lanterns. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        SolarLanternSettings settings = settings();
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Lantern> entry : active.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Lantern lantern = entry.getValue();
            long remaining = lantern.durationTicks * 50L - (now - lantern.litSinceMillis);
            if (player == null || remaining <= 0L) {
                expired.add(entry.getKey());
                continue;
            }
            if (!lantern.warned && remaining <= WARN_BEFORE_MILLIS) {
                lantern.warned = true;
                player.sendActionBar(Component.text("Solar: Lantern: under a minute of light left", NamedTextColor.GOLD));
            }
            lantern.angle += ORBIT_SPEED;
            lantern.orb.teleport(orbitPoint(player, lantern, settings));
        }
        for (UUID id : expired) {
            end(id, "Solar: Lantern faded.");
        }
    }

    /**
     * Ends the lantern for any reason and returns the cooldown charged:
     * cooldown-ticks scaled by the fraction of the duration it was lit.
     */
    public int end(UUID player, String message) {
        Lantern lantern = active.remove(player);
        if (lantern == null) {
            return 0;
        }
        lantern.orb.remove();
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return 0;
        }
        online.removePotionEffect(PotionEffectType.NIGHT_VISION);
        double lit = Math.min(1.0, (System.currentTimeMillis() - lantern.litSinceMillis) / (lantern.durationTicks * 50.0));
        int cooldown = (int) Math.round(cooldownTicks() * lit);
        if (cooldown > 0) {
            plugin.store().startCooldown(online, id(), cooldown);
        }
        if (message != null) {
            online.sendActionBar(Component.text(message, NamedTextColor.GOLD));
        }
        return cooldown;
    }

    /** Plugin disable: ends every lantern, charging the proportional cooldown since it now persists. */
    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            end(id, null);
        }
    }

    private static String format(int ticks) {
        long seconds = ticks / 20L;
        return seconds >= 60L ? seconds / 60L + "m " + seconds % 60L + "s" : seconds + "s";
    }

    private static Location orbitPoint(Player player, Lantern lantern, SolarLanternSettings settings) {
        Location center = player.getLocation().add(0.0, ORBIT_HEIGHT, 0.0);
        return Displays.orbitPoint(center, lantern.angle, 0, 1, settings.orbitRadius());
    }

    private SolarLanternSettings settings() {
        return plugin.settings().solarLantern();
    }
}
