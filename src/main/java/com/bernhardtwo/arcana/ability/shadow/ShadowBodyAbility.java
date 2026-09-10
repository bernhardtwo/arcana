package com.bernhardtwo.arcana.ability.shadow;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.ShadowBodySettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Brief invisibility with the armor hidden client-side. Vanilla invisibility
 * still renders worn armor, so every viewer is sent AIR for the four armor
 * slots and both hands, and the real items when it ends. Every end path goes
 * through {@link #end(UUID, String)}: duration, attack, quit, death, world
 * change, effect removal (milk) and plugin disable.
 */
public final class ShadowBodyAbility implements Ability {

    private static final EquipmentSlot[] HIDDEN_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Map<EquipmentSlot, ItemStack> NOTHING = new EnumMap<>(EquipmentSlot.class);

    static {
        for (EquipmentSlot slot : HIDDEN_SLOTS) {
            NOTHING.put(slot, new ItemStack(Material.AIR));
        }
    }

    private final ArcanaPlugin plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public ShadowBodyAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "shadow_body";
    }

    @Override
    public String displayName() {
        return "Shadow: Body";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    @Override
    public void cast(Player caster) {
        ShadowBodySettings settings = settings();
        UUID id = caster.getUniqueId();
        BukkitTask previous = active.remove(id);
        if (previous != null) {
            previous.cancel();
        }

        caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, settings.durationTicks(), 0,
                false, false, true));
        active.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> end(id, "Shadow: Body faded."),
                settings.durationTicks()));
        for (Player viewer : caster.getTrackedBy()) {
            hideFor(viewer, caster);
        }
        caster.sendActionBar(Component.text("Shadow: Body: unseen for " + settings.durationTicks() / 20 + "s",
                NamedTextColor.LIGHT_PURPLE));

        if (plugin.settings().effects()) {
            caster.getWorld().spawnParticle(Particle.LARGE_SMOKE, caster.getLocation().add(0.0, 1.0, 0.0),
                    25, 0.3, 0.6, 0.3, 0.02);
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 0.7f);
        }
    }

    public boolean isActive(UUID player) {
        return active.containsKey(player);
    }

    /** Sends AIR for the hidden slots to one viewer. Safe to call for a viewer who just started tracking the caster. */
    public void hideFor(Player viewer, Player caster) {
        if (!settings().hideArmor() || !isActive(caster.getUniqueId()) || viewer.equals(caster) || !viewer.canSee(caster)) {
            return;
        }
        viewer.sendEquipmentChange(caster, NOTHING);
    }

    /** Dealing damage ends the body when break-on-attack is on. */
    public void onAttack(Player attacker) {
        if (settings().breakOnAttack() && isActive(attacker.getUniqueId())) {
            end(attacker.getUniqueId(), "Shadow: Body broken, you attacked.");
        }
    }

    /** Ends the body for any reason and restores the real equipment to every viewer. */
    public void end(UUID player, String message) {
        BukkitTask task = active.remove(player);
        if (task == null) {
            return;
        }
        task.cancel();
        Player online = Bukkit.getPlayer(player);
        if (online == null) {
            return;
        }
        online.removePotionEffect(PotionEffectType.INVISIBILITY);
        EntityEquipment equipment = online.getEquipment();
        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : HIDDEN_SLOTS) {
            real.put(slot, equipment.getItem(slot));
        }
        for (Player viewer : online.getTrackedBy()) {
            if (!viewer.equals(online)) {
                viewer.sendEquipmentChange(online, real);
            }
        }
        if (message != null) {
            online.sendActionBar(Component.text(message, NamedTextColor.GRAY));
        }
    }

    /** Plugin disable: every body ends and every viewer gets the real items back. */
    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            end(id, null);
        }
    }

    private ShadowBodySettings settings() {
        return plugin.settings().shadowBody();
    }
}
