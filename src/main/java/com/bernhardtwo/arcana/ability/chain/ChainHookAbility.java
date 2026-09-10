package com.bernhardtwo.arcana.ability.chain;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.ability.Displays;
import com.bernhardtwo.arcana.ability.Targets;
import com.bernhardtwo.arcana.config.ChainHookSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The first stage of the chain staff: a claw that flies along the look
 * direction and latches onto the first solid block or living entity, then
 * stays anchored for Reel and Rend to act on. One hook per player. Stateful
 * like Ice Armor: every exit path goes through {@link #release(UUID, String)}
 * or {@link #endAll()} so the claw display never outlives the hook, and the
 * display is non-persistent so a crash cannot write it to disk.
 */
public final class ChainHookAbility implements Ability {

    private static final float CLAW_SCALE = 0.45f;
    private static final double RAY_SIZE = 0.3;
    private static final double LINK_SPACING = 0.4;
    private static final int TRAVEL_SOUND_INTERVAL = 3;
    private static final int HOLD_SOUND_INTERVAL = 12;
    private static final Vector UP = new Vector(0.0, 1.0, 0.0);
    private static final Particle.DustOptions LINK = new Particle.DustOptions(Color.fromRGB(0x74736F), 0.55f);

    private final ArcanaPlugin plugin;
    private final Map<UUID, Hook> active = new HashMap<>();

    /** Travelling while neither block nor entity is set; anchored afterwards. */
    public static final class Hook {
        private World world;
        private Vector direction;
        private Vector head;
        private BlockDisplay claw;
        private double travelled;
        private Block block;
        private LivingEntity entity;
        private int holdLeft;
        private int age;
        private BukkitTask reel;

        public boolean isAnchored() {
            return block != null || entity != null;
        }

        /** The block the claw is latched onto, null for an entity hook. */
        public Block block() {
            return block;
        }

        /** The entity the claw is latched onto, null for a block hook. */
        public LivingEntity entity() {
            return entity;
        }

        /** Where the claw is right now. */
        public Location head() {
            return head.toLocation(world);
        }

        public boolean isReeling() {
            return reel != null;
        }

        /** A pull in progress, cancelled with the hook on every release path. */
        public void reel(BukkitTask task) {
            reel = task;
        }
    }

    public ChainHookAbility(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "chain_hook";
    }

    @Override
    public String displayName() {
        return "Chain: Hook";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    /** Charged when the claw latches. A miss retracts for free. */
    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    /** Firing while a hook is out reaches the ability, which releases the old hook and fires again. */
    @Override
    public boolean replacesActiveCast(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    @Override
    public void cast(Player caster) {
        UUID id = caster.getUniqueId();
        release(id, null);

        Location eye = caster.getEyeLocation();
        Hook hook = new Hook();
        hook.world = caster.getWorld();
        hook.direction = eye.getDirection().normalize();
        hook.head = eye.toVector();
        hook.claw = Displays.spawn(plugin, eye, Material.IRON_CHAIN, CLAW_SCALE);
        active.put(id, hook);

        if (plugin.settings().effects()) {
            hook.world.playSound(eye, Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f, 0.6f);
        }
    }

    /** The anchored hook of a player, or null while the claw is still flying or there is none. */
    public Hook anchored(UUID player) {
        Hook hook = active.get(player);
        return hook != null && hook.isAnchored() ? hook : null;
    }

    /** Runs every tick: flies the claw, watches the anchor, and draws the chain. */
    public void tick() {
        if (active.isEmpty()) {
            return;
        }
        ChainHookSettings settings = settings();
        for (Map.Entry<UUID, Hook> entry : new ArrayList<>(active.entrySet())) {
            Player caster = Bukkit.getPlayer(entry.getKey());
            Hook hook = entry.getValue();
            if (caster == null || !caster.getWorld().equals(hook.world)) {
                release(entry.getKey(), null);
                continue;
            }
            hook.age++;
            if (hook.isAnchored()) {
                hold(caster, hook, settings);
            } else {
                travel(caster, hook, settings);
            }
            if (active.containsKey(entry.getKey()) && plugin.settings().effects()) {
                drawChain(caster, hook);
                rattle(caster, hook);
            }
        }
    }

    /** Advances the head one step and latches onto the first solid block or valid entity on that step. */
    private void travel(Player caster, Hook hook, ChainHookSettings settings) {
        double step = Math.min(settings.travelSpeed(), settings.range() - hook.travelled);
        RayTraceResult hit = hook.world.rayTrace(hook.head(), hook.direction, step, FluidCollisionMode.NEVER, true,
                RAY_SIZE, candidate -> Targets.isTargetable(caster, candidate));
        if (hit == null) {
            hook.head.add(hook.direction.clone().multiply(step));
            hook.travelled += step;
            hook.claw.teleport(hook.head());
            if (hook.travelled >= settings.range()) {
                retract(caster, "Chain: Hook: nothing in reach");
            }
            return;
        }

        String name;
        if (hit.getHitEntity() instanceof LivingEntity target) {
            if (plugin.claims().isBlocked(caster, target.getLocation())) {
                retract(caster, "Chain: Hook: " + target.getName() + " is inside a claim you cannot build in");
                return;
            }
            hook.entity = target;
            name = target.getName();
        } else {
            hook.block = hit.getHitBlock();
            name = hook.block.getType().name().toLowerCase().replace('_', ' ');
        }
        hook.head = hit.getHitPosition();
        hook.holdLeft = settings.holdTicks();
        hook.claw.teleport(hook.head());
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());
        caster.sendActionBar(Component.text("Chain: Hook: latched onto " + name, NamedTextColor.LIGHT_PURPLE));
        if (plugin.settings().effects()) {
            playBoth(caster, hook.head(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.7f);
        }
    }

    /**
     * Chain links clinking every few ticks while the claw flies, softer and
     * sparser once it holds. Played at the head for bystanders and to the
     * caster directly, since positional audio alone never reaches them at range.
     */
    private static void rattle(Player caster, Hook hook) {
        if (hook.isAnchored()) {
            if (hook.age % HOLD_SOUND_INTERVAL == 0) {
                playBoth(caster, hook.head(), Sound.BLOCK_CHAIN_HIT, 0.35f, 0.8f);
            }
        } else if (hook.age % TRAVEL_SOUND_INTERVAL == 0) {
            playBoth(caster, hook.head(), Sound.BLOCK_CHAIN_STEP, 0.9f, 1.2f);
        }
    }

    /** In the world at {@code at}, and again for the caster at their own ears. */
    static void playBoth(Player caster, Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, volume, pitch);
        caster.playSound(caster.getLocation(), sound, volume, pitch);
    }

    /** Follows an entity anchor, and lets go on expiry, a dead or missing anchor, or too much distance. */
    private void hold(Player caster, Hook hook, ChainHookSettings settings) {
        UUID id = caster.getUniqueId();
        if (--hook.holdLeft <= 0) {
            release(id, "Chain: Hook let go.");
            return;
        }
        if (hook.entity != null) {
            if (!hook.entity.isValid() || hook.entity.isDead() || !hook.entity.getWorld().equals(hook.world)) {
                release(id, "Chain: Hook: " + hook.entity.getName() + " is gone");
                return;
            }
            hook.head = hook.entity.getLocation().toVector().add(new Vector(0.0, hook.entity.getHeight() / 2.0, 0.0));
            hook.claw.teleport(hook.head());
        } else if (hook.block.getType().isAir()) {
            release(id, "Chain: Hook: the anchor is gone");
            return;
        }
        if (caster.getEyeLocation().toVector().distance(hook.head) > settings.range()) {
            release(id, "Chain: Hook snapped.");
        }
    }

    private void retract(Player caster, String message) {
        if (plugin.settings().effects()) {
            caster.getWorld().playSound(caster.getEyeLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.7f);
        }
        release(caster.getUniqueId(), message);
    }

    /** Ends the hook for any reason: cancels a pull in progress and removes the claw. Charges nothing. */
    public void release(UUID player, String message) {
        Hook hook = active.remove(player);
        if (hook == null) {
            return;
        }
        if (hook.reel != null) {
            hook.reel.cancel();
        }
        hook.claw.remove();
        Player online = Bukkit.getPlayer(player);
        if (online != null && message != null) {
            online.sendActionBar(Component.text(message, NamedTextColor.GRAY));
        }
    }

    /** Plugin disable: every claw goes, no messages. */
    public void endAll() {
        for (UUID id : new ArrayList<>(active.keySet())) {
            release(id, null);
        }
    }

    /** Dust links from the caster's hand to the claw head. This is what sells it. */
    private static void drawChain(Player caster, Hook hook) {
        Vector from = handOf(caster);
        Vector span = hook.head.clone().subtract(from);
        int links = (int) (span.length() / LINK_SPACING);
        for (int i = 1; i <= links; i++) {
            Location at = from.clone().add(span.clone().multiply(i / (double) links)).toLocation(hook.world);
            hook.world.spawnParticle(Particle.DUST, at, 1, 0.0, 0.0, 0.0, 0.0, LINK);
        }
    }

    /** A point just right of and below the eyes, where the staff hand is. */
    private static Vector handOf(Player caster) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();
        Vector right = direction.clone().crossProduct(UP);
        if (right.lengthSquared() > 1.0E-4) {
            right.normalize().multiply(0.4);
        }
        return eye.toVector().add(right).add(direction.multiply(0.3)).add(new Vector(0.0, -0.35, 0.0));
    }

    private ChainHookSettings settings() {
        return plugin.settings().chainHook();
    }
}
