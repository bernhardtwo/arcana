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
 * Night vision plus one orbiting light. A recast refreshes the light to the
 * full duration but keeps the session's accumulated lit time, so continuous
 * light is possible and the bill only grows. The session ends on the light
 * running out, quit, death, world change and plugin disable, always through
 * {@link #end(UUID, String)}, which charges a cooldown proportional to the
 * total lit time, capped at the full cooldown.
 */
public final class SolarLanternAbility implements Ability {

    private static final float ORB_SCALE = 0.35f;
    private static final double ORBIT_HEIGHT = 1.4;
    private static final double ORBIT_SPEED = 0.08;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Lantern> active = new HashMap<>();

    private static final class Lantern {
        private BlockDisplay orb;
        private long litSinceMillis;
        private long consumedMillis;
        private int durationTicks;
        private double angle;

        private long totalMillis(long now) {
            return consumedMillis + (now - litSinceMillis);
        }
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
    public boolean replacesActiveCast(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    @Override
    public void cast(Player caster) {
        SolarLanternSettings settings = settings();
        long now = System.currentTimeMillis();
        Lantern lantern = active.get(caster.getUniqueId());
        if (lantern != null) {
            // Recast: bank the time lit so far, restart the light, and show the bill so it never surprises.
            lantern.consumedMillis += now - lantern.litSinceMillis;
            lantern.litSinceMillis = now;
            lantern.durationTicks = settings.durationTicks();
        } else {
            lantern = new Lantern();
            lantern.litSinceMillis = now;
            lantern.durationTicks = settings.durationTicks();
            lantern.orb = Displays.spawn(plugin, orbitPoint(caster, lantern, settings), Material.LANTERN, ORB_SCALE);
            active.put(caster.getUniqueId(), lantern);
        }

        caster.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, settings.durationTicks(), 0,
                false, false, true));
        if (lantern.consumedMillis > 0L) {
            caster.sendActionBar(Component.text("Solar: Lantern refreshed, cooldown now "
                    + format(cooldownFor(lantern, now)), NamedTextColor.GOLD));
        }

        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, 1.6f);
            caster.getWorld().spawnParticle(Particle.END_ROD, caster.getEyeLocation(), 12, 0.4, 0.3, 0.4, 0.02);
        }
    }

    /** Runs every tick: orbits the orb and expires finished lanterns. */
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
            if (player == null || now - lantern.litSinceMillis >= lantern.durationTicks * 50L) {
                expired.add(entry.getKey());
                continue;
            }
            lantern.angle += ORBIT_SPEED;
            lantern.orb.teleport(orbitPoint(player, lantern, settings));
        }
        for (UUID id : expired) {
            end(id, "Solar: Lantern faded.");
        }
    }

    /** Ends the session for any reason. The cooldown is cooldown-ticks scaled by the total lit time over the duration. */
    public void end(UUID player, String message) {
        Lantern lantern = active.remove(player);
        if (lantern == null) {
            return;
        }
        lantern.orb.remove();
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        online.removePotionEffect(PotionEffectType.NIGHT_VISION);
        int cooldown = cooldownFor(lantern, System.currentTimeMillis());
        if (cooldown > 0) {
            plugin.store().startCooldown(online, id(), cooldown);
        }
        if (message != null) {
            online.sendActionBar(Component.text(message, NamedTextColor.GOLD));
        }
    }

    /** Plugin disable: ends every lantern, charging the proportional cooldown since it now persists. */
    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            end(id, null);
        }
    }

    private int cooldownFor(Lantern lantern, long now) {
        double consumed = Math.min(1.0, lantern.totalMillis(now) / (lantern.durationTicks * 50.0));
        return (int) Math.round(cooldownTicks() * consumed);
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
