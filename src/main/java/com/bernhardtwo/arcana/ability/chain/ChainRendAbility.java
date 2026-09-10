package com.bernhardtwo.arcana.ability.chain;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.config.ChainRendSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Second stage: the claw spins at the anchor. On an entity that is damage
 * attributed to the caster. On a block it is a break, behind every guard at
 * once: build permission, a hardness ceiling that keeps obsidian and ancient
 * debris out of reach, a material blocklist, and an unconditional refusal
 * for anything with an inventory. The break is logged in CoreProtect so a
 * rollback can restore it.
 */
public final class ChainRendAbility implements Ability {

    private static final int SPIN_TICKS = 6;
    private static final int SPIN_POINTS = 4;
    private static final double SPIN_RADIUS = 0.8;
    private static final ItemStack BREAK_TOOL = new ItemStack(Material.DIAMOND_PICKAXE);

    private final ArcanaPlugin plugin;
    private final ChainHookAbility hooks;

    public ChainRendAbility(ArcanaPlugin plugin, ChainHookAbility hooks) {
        this.plugin = plugin;
        this.hooks = hooks;
    }

    @Override
    public String id() {
        return "chain_rend";
    }

    @Override
    public String displayName() {
        return "Chain: Rend";
    }

    @Override
    public int cooldownTicks() {
        return settings().cooldownTicks();
    }

    /** Nothing anchored, or a refused break, charges nothing and keeps the hook. */
    @Override
    public boolean startsCooldownOnCast() {
        return false;
    }

    @Override
    public void cast(Player caster) {
        ChainHookAbility.Hook hook = hooks.anchored(caster.getUniqueId());
        if (hook == null) {
            caster.sendActionBar(warn("Chain: Rend: no hook anchored"));
            return;
        }
        ChainRendSettings settings = settings();
        if (hook.entity() != null) {
            rendEntity(caster, hook.entity(), settings);
            return;
        }
        Block block = hook.block();
        String refusal = refusal(caster, block, settings);
        if (refusal != null) {
            caster.sendActionBar(warn("Chain: Rend: " + refusal));
            return;
        }
        rendBlock(caster, block, settings);
    }

    private void rendEntity(Player caster, LivingEntity target, ChainRendSettings settings) {
        target.damage(settings.entityDamage(), caster);
        if (plugin.settings().effects()) {
            Location center = target.getLocation().add(0.0, target.getHeight() / 2.0, 0.0);
            ChainHookAbility.playBoth(caster, center, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
            ChainHookAbility.playBoth(caster, center, Sound.BLOCK_CHAIN_BREAK, 0.8f, 1.3f);
            spin(center);
        }
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());
        hooks.release(caster.getUniqueId(), "Chain: Rend: tore into " + target.getName());
    }

    /** Logged before it is removed, so CoreProtect records the block that was actually there. */
    private void rendBlock(Player caster, Block block, ChainRendSettings settings) {
        String name = block.getType().name().toLowerCase().replace('_', ' ');
        boolean logged = plugin.coreProtect().logRemoval(caster.getName(), block);
        if (plugin.settings().effects()) {
            World world = block.getWorld();
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            world.spawnParticle(Particle.BLOCK, center, 30, 0.3, 0.3, 0.3, block.getBlockData());
            // The block's own break sound, so it sounds like what it actually broke.
            ChainHookAbility.playBoth(caster, center, block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
            ChainHookAbility.playBoth(caster, center, Sound.BLOCK_CHAIN_BREAK, 0.8f, 0.6f);
            spin(center);
        }
        boolean drops = ThreadLocalRandom.current().nextDouble() < settings.blockDropChance();
        if (drops) {
            block.breakNaturally(BREAK_TOOL, true, true);
        } else {
            block.setType(Material.AIR);
        }
        plugin.store().startCooldownWithIndicator(caster, id(), settings.cooldownTicks());
        hooks.release(caster.getUniqueId(), "Chain: Rend: broke " + name + (drops ? "" : ", nothing dropped")
                + (logged ? "" : " (not logged)"));
    }

    /** Every guard, in the order they are cheapest to explain. Null means the break may go ahead. */
    private String refusal(Player caster, Block block, ChainRendSettings settings) {
        Material type = block.getType();
        if (type.isAir()) {
            return "the anchor is gone";
        }
        if (plugin.claims().isBlocked(caster, block.getLocation())) {
            return "you cannot build there";
        }
        if (settings.blockedMaterials().contains(type) || block.getState() instanceof InventoryHolder) {
            return "that block is protected";
        }
        float hardness = type.getHardness();
        if (hardness < 0.0f || hardness > settings.maxBlockHardness()) {
            return "that block is too hard";
        }
        return null;
    }

    /** A few crit points circling the anchor for a handful of ticks. */
    private void spin(Location center) {
        World world = center.getWorld();
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick++ >= SPIN_TICKS) {
                    cancel();
                    return;
                }
                double base = tick * Math.PI / 3.0;
                for (int i = 0; i < SPIN_POINTS; i++) {
                    double angle = base + 2.0 * Math.PI * i / SPIN_POINTS;
                    Location at = center.clone().add(Math.cos(angle) * SPIN_RADIUS, 0.0, Math.sin(angle) * SPIN_RADIUS);
                    world.spawnParticle(Particle.CRIT, at, 2, 0.05, 0.05, 0.05, 0.0);
                }
                world.playSound(center, Sound.BLOCK_CHAIN_STEP, 0.6f, 1.6f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private ChainRendSettings settings() {
        return plugin.settings().chainRend();
    }
}
