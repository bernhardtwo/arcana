package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.shadow.ShadowBodyAbility;
import com.bernhardtwo.arcana.ability.shadow.ShadowSwapAbility;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public final class ShadowListener implements Listener {

    private final ArcanaPlugin plugin;
    private final ShadowBodyAbility body;
    private final ShadowSwapAbility swap;

    public ShadowListener(ArcanaPlugin plugin, ShadowBodyAbility body, ShadowSwapAbility swap) {
        this.plugin = plugin;
        this.body = body;
        this.swap = swap;
    }

    /** Monitor and ignoreCancelled: only damage that actually went through counts as an attack. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker != null) {
            body.onAttack(attacker);
        }
    }

    /** Any damage the caster actually takes, environmental included, interrupts a swap channel. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            swap.onDamaged(player);
        }
    }

    /**
     * A viewer starting to track the caster (joining, coming into range) gets
     * the real equipment with the spawn packets, sent right after this event,
     * so the override goes out one tick later.
     */
    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (!(event.getEntity() instanceof Player caster) || !body.isActive(caster.getUniqueId())) {
            return;
        }
        Player viewer = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline() && caster.isOnline()) {
                body.hideFor(viewer, caster);
            }
        });
    }

    /** Invisibility removed by anything else, milk included: the armor must come back with it. */
    @EventHandler(ignoreCancelled = true)
    public void onEffectRemoved(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || !PotionEffectType.INVISIBILITY.equals(event.getModifiedType())) {
            return;
        }
        EntityPotionEffectEvent.Action action = event.getAction();
        if (action == EntityPotionEffectEvent.Action.REMOVED || action == EntityPotionEffectEvent.Action.CLEARED) {
            body.end(player.getUniqueId(), "Shadow: Body ended.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endBoth(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        endBoth(event.getEntity().getUniqueId(), null);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        endBoth(event.getPlayer().getUniqueId(), "faded.");
    }

    private void endBoth(UUID player, String suffix) {
        body.end(player, suffix == null ? null : "Shadow: Body " + suffix);
        swap.cancel(player, suffix == null ? null : "Shadow: Swap " + suffix);
    }
}
