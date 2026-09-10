package com.bernhardtwo.arcana.ability.solar;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Displays;
import com.bernhardtwo.arcana.config.SolarZenithSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * A static sun above the cast point. Burns hostiles and unclaimed players,
 * heals the caster and the peaceful, until the caster walks out of the
 * radius. Every exit path goes through {@link #end(UUID, String)}, which also
 * removes the one real light block the sun placed; the light positions are
 * written to {@code lights.yml} so a crash can be cleaned up on the next start.
 * The sun display is tagged and non-persistent, so the startup sweep covers it.
 */
public final class SolarZenithAbility implements Ability {

    private static final float SUN_SCALE = 3.0f;
    // Hard cap on particles per refresh, whatever the config asks for.
    private static final int MAX_MARKER_POINTS = 400;
    private static final Particle.DustOptions HEAL_MARK = new Particle.DustOptions(Color.YELLOW, 1.0f);

    private final ArcanaPlugin plugin;
    private final File lightsFile;
    private final Map<UUID, Sun> active = new HashMap<>();

    private static final class Sun {
        private BlockDisplay display;
        private Location center;
        private Location light;
        private int groundY;
        private int ticks;
        private double gradientPhase;
    }

    public SolarZenithAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
        this.lightsFile = new File(plugin.getDataFolder(), "lights.yml");
    }

    @Override
    public String id() {
        return "solar_zenith";
    }

    @Override
    public String displayName() {
        return "Solar: Zenith";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    @Override
    public boolean canCast(Player caster) {
        return !active.containsKey(caster.getUniqueId());
    }

    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public boolean cast(Player caster) {
        SolarZenithSettings settings = settings();
        Sun sun = new Sun();
        sun.groundY = caster.getLocation().getBlockY();
        sun.center = caster.getLocation().add(0.0, settings.height(), 0.0).toCenterLocation();
        sun.display = Displays.spawn(plugin, sun.center, Material.GLOWSTONE, SUN_SCALE);
        sun.display.setGlowing(true);

        Block block = sun.center.getBlock();
        if (block.getType().isAir() && !plugin.claims().isBlocked(caster, block.getLocation())) {
            block.setType(Material.LIGHT, false);
            sun.light = block.getLocation();
        }
        active.put(caster.getUniqueId(), sun);
        saveLights();

        if (plugin.settings().effects()) {
            World world = caster.getWorld();
            world.playSound(sun.center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 1.4f);
            world.spawnParticle(Particle.END_ROD, sun.center, 60, 1.2, 1.2, 1.2, 0.05);
        }
        return true;
    }

    /** Runs every tick; the effects and the ring follow their own intervals. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        SolarZenithSettings settings = settings();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Sun> entry : active.entrySet()) {
            Player caster = Bukkit.getPlayer(entry.getKey());
            Sun sun = entry.getValue();
            if (caster == null || caster.getWorld() != sun.center.getWorld()
                    || caster.getLocation().distance(sun.center) > settings.radius()) {
                expired.add(entry.getKey());
                continue;
            }
            sun.ticks++;
            if (sun.ticks % settings.tickInterval() == 0) {
                affect(caster, sun, settings);
            }
            if (sun.ticks % settings.markerInterval() == 0) {
                drawCylinder(sun, settings);
            }
        }
        for (UUID id : expired) {
            end(id, "Solar: Zenith set.");
        }
    }

    private void affect(Player caster, Sun sun, SolarZenithSettings settings) {
        for (LivingEntity entity : sun.center.getWorld().getNearbyLivingEntities(sun.center, settings.radius())) {
            if (entity.hasMetadata("NPC")) {
                continue;
            }
            if (entity.equals(caster)) {
                heal(entity, settings.healAmount());
            } else if (entity instanceof Player player) {
                // Server rule: PvP is free only outside claims, so inside any claim nobody burns.
                GameMode mode = player.getGameMode();
                if (mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR
                        && !plugin.claims().hasClaimAt(player.getLocation())) {
                    burn(player, settings.burnTicks());
                }
            } else if (entity instanceof Enemy) {
                burn(entity, settings.burnTicks());
            } else if (entity instanceof Mob) {
                // Villagers, tamed animals and every other mob that is not an enemy.
                heal(entity, settings.healAmount());
            }
        }
    }

    private static void burn(LivingEntity entity, int ticks) {
        entity.setFireTicks(Math.max(entity.getFireTicks(), ticks));
    }

    private void heal(LivingEntity entity, double amount) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 20.0 : attribute.getValue();
        if (entity.getHealth() >= max) {
            return;
        }
        entity.setHealth(Math.min(max, entity.getHealth() + amount));
        if (plugin.settings().effects()) {
            Location at = entity.getLocation().add(0.0, entity.getHeight() + 0.3, 0.0);
            entity.getWorld().spawnParticle(Particle.HEART, at, 3, 0.3, 0.2, 0.3, 0.0);
            entity.getWorld().spawnParticle(Particle.DUST, at, 4, 0.3, 0.2, 0.3, 0.0, HEAL_MARK);
        }
    }

    /**
     * Wall of dust particles at the radius: {@code marker-rings} rings spread
     * over {@code marker-height} blocks centered on the cast height, forced so
     * they render at long range regardless of the client's particle setting.
     * Each point's color is a triangle-wave blend of the two marker colors,
     * keyed on its angle, its ring and a phase that advances every refresh, so
     * bands of color travel around and along the wall with no seam. The total
     * per refresh is capped; points inside solid blocks are skipped because
     * they would not render anyway.
     */
    private void drawCylinder(Sun sun, SolarZenithSettings settings) {
        World world = sun.center.getWorld();
        int rings = settings.markerRings();
        int points = Math.max(4, Math.min(settings.markerPointsPerRing(), MAX_MARKER_POINTS / rings));
        boolean force = settings.markerForceRender();
        double bottom = sun.groundY + 0.5 - settings.markerHeight() / 2.0;
        double step = rings == 1 ? 0.0 : settings.markerHeight() / (rings - 1);
        sun.gradientPhase += settings.markerGradientSpeed();
        for (int ring = 0; ring < rings; ring++) {
            double y = bottom + ring * step;
            // Alternate rings are offset by half a step so the wall has no empty vertical columns.
            double offset = ring % 2 == 0 ? 0.0 : Math.PI / points;
            // Half a band across the full height tilts the bands so they flow along the wall, not only around it.
            double ringShift = rings == 1 ? 0.0 : 0.5 * ring / (rings - 1);
            for (int i = 0; i < points; i++) {
                double theta = offset + 2.0 * Math.PI * i / points;
                double x = sun.center.getX() + Math.cos(theta) * settings.radius();
                double z = sun.center.getZ() + Math.sin(theta) * settings.radius();
                if (!world.getBlockAt((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isPassable()) {
                    continue;
                }
                double phase = settings.markerGradientCycles() * theta / (2.0 * Math.PI) + ringShift + sun.gradientPhase;
                Particle.DustOptions dust = new Particle.DustOptions(
                        blend(settings.markerColorA(), settings.markerColorB(), triangle(phase)),
                        settings.markerParticleSize());
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust, force);
            }
        }
    }

    /** 0 at whole phases, 1 at half phases, linear in between: A to B and back with no seam. */
    private static double triangle(double phase) {
        double t = phase - Math.floor(phase);
        return 1.0 - Math.abs(2.0 * t - 1.0);
    }

    private static Color blend(Color a, Color b, double t) {
        return Color.fromRGB(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Ends the sun for any reason: removes the display and the light block, then starts the full cooldown. */
    public void end(UUID player, String message) {
        Sun sun = active.remove(player);
        if (sun == null) {
            return;
        }
        sun.display.remove();
        removeLight(sun.light);
        saveLights();
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        plugin.store().startCooldown(online, id(), cooldownTicks());
        if (message != null) {
            online.sendActionBar(Component.text(message, NamedTextColor.GOLD));
        }
    }

    /** Plugin disable: every sun ends and its cooldown starts, which persists with the player. */
    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            end(id, null);
        }
    }

    /** Startup: removes any light block a previous run recorded and did not get to clean up. */
    public void removeOrphanLights() {
        if (!lightsFile.exists()) {
            return;
        }
        int removed = 0;
        for (String entry : YamlConfiguration.loadConfiguration(lightsFile).getStringList("lights")) {
            String[] parts = entry.split(",");
            World world = parts.length == 4 ? Bukkit.getWorld(parts[0]) : null;
            if (world == null) {
                plugin.getLogger().warning("Skipping unreadable light entry: " + entry);
                continue;
            }
            Block block = world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.AIR, false);
                removed++;
            }
        }
        saveLights();
        if (removed > 0) {
            plugin.getLogger().warning("Removed " + removed + " orphaned zenith light block(s).");
        }
    }

    private static void removeLight(Location light) {
        if (light != null && light.getBlock().getType() == Material.LIGHT) {
            light.getBlock().setType(Material.AIR, false);
        }
    }

    /** Write-through: the file always lists exactly the light blocks currently in the world. */
    private void saveLights() {
        List<String> entries = new ArrayList<>();
        for (Sun sun : active.values()) {
            if (sun.light != null) {
                entries.add(sun.light.getWorld().getName() + "," + sun.light.getBlockX() + ","
                        + sun.light.getBlockY() + "," + sun.light.getBlockZ());
            }
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("lights", entries);
        try {
            yaml.save(lightsFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + lightsFile.getName(), ex);
        }
    }

    private SolarZenithSettings settings() {
        return plugin.settings().solarZenith();
    }
}
