package com.bernhardtwo.arcana.ability.shadow;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.ShadowBlinkSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Short teleport along the line of sight. The destination is always a point on
 * the unobstructed ray from the eye, walked back from the first solid block
 * until a standing player fits there, so it can never end up inside a block or
 * past a wall. No claim check: every destination is somewhere the caster could
 * see and walk to. No fall grace either: a blink over a drop hurts.
 */
public final class ShadowBlinkAbility implements Ability {

    private static final double STEP = 0.25;
    private static final double MIN_DISTANCE = 1.0;
    private static final double HALF_WIDTH = 0.3;
    private static final double HALF_HEIGHT = 0.9;

    private final ArcanaPlugin plugin;

    public ShadowBlinkAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "shadow_blink";
    }

    @Override
    public String displayName() {
        return "Shadow: Blink";
    }

    @Override
    public int cooldownTicks() {
        return plugin.settings().shadowBlink().cooldownTicks();
    }

    /** A refused blink, no room, charges nothing. */
    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public void cast(Player caster) {
        ShadowBlinkSettings settings = plugin.settings().shadowBlink();
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection().normalize();

        RayTraceResult hit = world.rayTraceBlocks(eye, direction, settings.range(), FluidCollisionMode.NEVER, true);
        double reach = hit != null ? hit.getHitPosition().distance(eye.toVector()) : settings.range();

        Location origin = caster.getLocation();
        Location destination = null;
        for (double distance = reach; distance >= MIN_DISTANCE; distance -= STEP) {
            Vector feet = eye.toVector().add(direction.clone().multiply(distance));
            if (fits(world, feet)) {
                destination = feet.toLocation(world, origin.getYaw(), origin.getPitch());
                break;
            }
        }
        if (destination == null) {
            caster.sendActionBar(Component.text("Shadow: Blink: no room to blink there", NamedTextColor.GRAY));
            return;
        }

        caster.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());

        if (plugin.settings().effects()) {
            puff(world, origin);
            puff(world, destination);
        }
    }

    /** A standing player box (0.6 wide, 1.8 tall) at these feet touches no block, hard entity or border, and is above the void. */
    private static boolean fits(World world, Vector feet) {
        if (feet.getY() < world.getMinHeight() || !world.getWorldBorder().isInside(feet.toLocation(world))) {
            return false;
        }
        BoundingBox body = BoundingBox.of(feet.clone().add(new Vector(0.0, HALF_HEIGHT, 0.0)),
                HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH);
        return !world.hasCollisionsIn(body);
    }

    /** The origin puff is deliberate: it is what tells other players where the caster went. */
    private static void puff(World world, Location at) {
        Location center = at.clone().add(0.0, 1.0, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 20, 0.3, 0.5, 0.3, 0.02);
        world.spawnParticle(Particle.PORTAL, center, 30, 0.3, 0.5, 0.3, 0.5);
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f);
    }
}
