package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.gravity.GravityBoots;
import com.bernhardtwo.arcana.item.LevitationBoots;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GravityBootsListener implements Listener {

    private final ArcanaPlugin plugin;
    private final GravityBoots boots;

    public GravityBootsListener(ArcanaPlugin plugin, GravityBoots boots) {
        this.plugin = plugin;
        this.boots = boots;
    }

    /** The double tap. Only reaches the boots while they granted the allowFlight that let the client send it. */
    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!plugin.store().hasFlightGrant(player)) {
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return;
        }
        if (event.isFlying()) {
            if (!boots.start(player)) {
                event.setCancelled(true);
            }
        } else {
            // Landing or a second double tap: vanilla already stopped the flight, this just settles the books.
            boots.stop(player, "Levitation Boots: landed.", false);
        }
    }

    /** Boots off in the air: flight ends at once with the grace, then the grant is restored. */
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.FEET) {
            return;
        }
        Player player = event.getPlayer();
        if (!LevitationBoots.is(plugin, event.getNewItem())) {
            boots.stop(player, "Levitation Boots removed, slow falling.", true);
        }
        syncNextTick(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        boots.revokeStale(event.getPlayer());
        syncNextTick(event.getPlayer());
    }

    /** Still online here, so the restored allowFlight is what gets saved with the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boots.stop(event.getPlayer(), null, false);
        boots.disarm(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        boots.stop(event.getEntity(), null, false);
        boots.disarm(event.getEntity());
    }

    /** With keepInventory the boots are still on after the respawn and nothing else re-arms them. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        syncNextTick(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        boots.stop(event.getPlayer(), "Levitation Boots off.", true);
    }

    /**
     * Fires before vanilla rewrites the abilities for the new mode, so the
     * grant is restored first and creative gets its own flight untouched. The
     * next tick re-arms if the new mode is survival or adventure.
     */
    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        boots.stop(event.getPlayer(), null, false);
        boots.disarm(event.getPlayer());
        syncNextTick(event.getPlayer());
    }

    private void syncNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                boots.sync(player);
            }
        });
    }
}
