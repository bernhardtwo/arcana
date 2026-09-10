package com.bernhardtwo.arcana.ability.shadow;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.ShadowSwapSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Trades places with the first living entity on the aim line. Unlike Blink
 * this can cross walls, so it always refuses a target standing where the
 * caster has no build permission: swapping into someone's base would be a
 * real protection bypass.
 */
public final class ShadowSwapAbility implements Ability {

    private static final double RAY_SIZE = 0.4;

    private final ArcanaPlugin plugin;

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
        return plugin.settings().shadowSwap().cooldownTicks();
    }

    @Override
    public void cast(Player caster) {
        ShadowSwapSettings settings = plugin.settings().shadowSwap();
        if (caster.isInsideVehicle() || !caster.getPassengers().isEmpty()) {
            caster.sendActionBar(warn("Shadow: Swap: dismount first"));
            return;
        }
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult hit = world.rayTraceEntities(eye, direction, settings.range(), RAY_SIZE,
                candidate -> isCandidate(caster, candidate, settings.allowPlayers()));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            caster.sendActionBar(warn("Shadow: Swap: nothing to swap with"));
            return;
        }
        if (plugin.claims().isBlocked(caster, target.getLocation())) {
            caster.sendActionBar(warn("Shadow: Swap: " + target.getName() + " is inside a claim you cannot build in"));
            return;
        }

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
            return;
        }
        if (!target.teleport(targetDestination, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            caster.teleport(from, PlayerTeleportEvent.TeleportCause.PLUGIN);
            caster.sendActionBar(warn("Shadow: Swap: something blocked the swap"));
            return;
        }

        caster.sendActionBar(Component.text("Shadow: Swap: traded places with " + target.getName(),
                NamedTextColor.LIGHT_PURPLE));
        if (target instanceof Player other) {
            other.sendMessage(Component.text("You were swapped by " + caster.getName(), NamedTextColor.LIGHT_PURPLE));
        }

        if (plugin.settings().effects()) {
            puff(world, from);
            puff(world, to);
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

    private static void puff(World world, Location at) {
        Location center = at.clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 20, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.PORTAL, center, 30, 0.3, 0.5, 0.3, 0.5);
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f);
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
