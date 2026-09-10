package com.bernhardtwo.arcana.ability.ice;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.IceArmorSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orbiting ice crystals that absorb attacks. Stateful: every exit path
 * (last charge, idle timeout, quit, death, world change, plugin disable) must
 * go through {@link #end(UUID, String)} or {@link #endAll()} so no display
 * entity is left behind. The displays are also non-persistent, so a crash
 * cannot write them to disk.
 */
public final class IceArmorAbility implements Ability {

    private static final float CRYSTAL_SCALE = 0.3f;

    private final ArcanaPlugin plugin;
    private final Map<UUID, Armor> active = new HashMap<>();

    private static final class Armor {
        private final List<BlockDisplay> crystals = new ArrayList<>();
        private long lastHitMillis;
        private double angle;
    }

    public IceArmorAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "ice_armor";
    }

    @Override
    public String displayName() {
        return "Ice: Armor";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    @Override
    public String lockoutGroup() {
        return "ice";
    }

    @Override
    public boolean canCast(Player caster) {
        return !isActive(caster.getUniqueId());
    }

    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public boolean blocksGroupWhileActive(Player player) {
        return settings().blockAbilitiesWhileActive() && isActive(player.getUniqueId());
    }

    public boolean isActive(UUID player) {
        return active.containsKey(player);
    }

    @Override
    public void cast(Player caster) {
        IceArmorSettings settings = settings();
        World world = caster.getWorld();
        Armor armor = new Armor();
        armor.lastHitMillis = System.currentTimeMillis();
        Location center = orbitCenter(caster, settings);

        for (int i = 0; i < settings.charges(); i++) {
            Location at = orbitPoint(center, armor.angle, i, settings.charges(), settings.orbitRadius());
            armor.crystals.add(world.spawn(at, BlockDisplay.class, display -> {
                display.setBlock(Material.ICE.createBlockData());
                display.setTransformation(new Transformation(
                        new Vector3f(-CRYSTAL_SCALE / 2.0f), new AxisAngle4f(),
                        new Vector3f(CRYSTAL_SCALE), new AxisAngle4f()));
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTeleportDuration(1);
                // Never written to disk: a crash or a hard kill cannot leave orphans in the world.
                display.setPersistent(false);
                display.getPersistentDataContainer().set(plugin.iceCrystalKey(), PersistentDataType.BYTE, (byte) 1);
            }));
        }
        active.put(caster.getUniqueId(), armor);

        if (plugin.settings().effects()) {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.2f);
            world.spawnParticle(Particle.SNOWFLAKE, center, 24, 0.6, 0.5, 0.6, 0.02);
        }
    }

    /** Absorbs one attack. Returns false when the player has no armor up. */
    public boolean absorb(Player victim, LivingEntity attacker) {
        Armor armor = active.get(victim.getUniqueId());
        if (armor == null) {
            return false;
        }
        boolean strong = settings().strongAttackers().contains(attacker.getType());
        int shatter = strong ? armor.crystals.size() : 1;
        for (int i = 0; i < shatter && !armor.crystals.isEmpty(); i++) {
            BlockDisplay crystal = armor.crystals.remove(armor.crystals.size() - 1);
            shatterEffects(crystal.getLocation());
            crystal.remove();
        }
        armor.lastHitMillis = System.currentTimeMillis();

        if (armor.crystals.isEmpty()) {
            end(victim.getUniqueId(), "Ice: Armor shattered.");
        } else {
            int left = armor.crystals.size();
            victim.sendActionBar(Component.text("Ice: Armor: " + left + (left == 1 ? " charge" : " charges") + " left",
                    NamedTextColor.AQUA));
        }
        return true;
    }

    /** Runs every tick: orbits the crystals and expires idle armor. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        IceArmorSettings settings = settings();
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();

        for (Map.Entry<UUID, Armor> entry : active.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            Armor armor = entry.getValue();
            if (player == null || now - armor.lastHitMillis > settings.idleSeconds() * 1000L) {
                expired.add(entry.getKey());
                continue;
            }
            armor.angle += settings.orbitSpeed();
            Location center = orbitCenter(player, settings);
            int count = armor.crystals.size();
            for (int i = 0; i < count; i++) {
                armor.crystals.get(i).teleport(orbitPoint(center, armor.angle, i, count, settings.orbitRadius()));
            }
        }
        for (UUID id : expired) {
            end(id, "Ice: Armor faded.");
        }
    }

    /** Ends the armor for any reason, removes its displays and starts the cooldown. */
    public void end(UUID player, String message) {
        Armor armor = active.remove(player);
        if (armor == null) {
            return;
        }
        armor.crystals.forEach(BlockDisplay::remove);
        // Still online during PlayerQuitEvent, so the cooldown started on quit is saved with the player.
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        plugin.store().startCooldown(online, id(), cooldownTicks());
        if (message != null) {
            online.sendActionBar(Component.text(message, NamedTextColor.AQUA));
        }
    }

    /** Plugin disable: drop every crystal without touching cooldowns. */
    public void endAll() {
        for (Armor armor : active.values()) {
            armor.crystals.forEach(BlockDisplay::remove);
        }
        active.clear();
    }

    private void shatterEffects(Location at) {
        if (!plugin.settings().effects()) {
            return;
        }
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.3f);
        world.spawnParticle(Particle.BLOCK, at, 20, 0.2, 0.2, 0.2, Material.ICE.createBlockData());
    }

    private static Location orbitCenter(Player player, IceArmorSettings settings) {
        return player.getLocation().add(0.0, settings.orbitHeight(), 0.0);
    }

    private static Location orbitPoint(Location center, double angle, int index, int count, double radius) {
        double theta = angle + 2.0 * Math.PI * index / count;
        return center.clone().add(Math.cos(theta) * radius, 0.0, Math.sin(theta) * radius);
    }

    private IceArmorSettings settings() {
        return plugin.settings().iceArmor();
    }
}
