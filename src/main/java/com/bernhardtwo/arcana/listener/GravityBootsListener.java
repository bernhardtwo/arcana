package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.gravity.GravityBoots;
import com.bernhardtwo.arcana.item.LevitationBoots;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class GravityBootsListener implements Listener {

    private final ArcanaPlugin plugin;
    private final GravityBoots boots;

    public GravityBootsListener(ArcanaPlugin plugin, GravityBoots boots) {
        this.plugin = plugin;
        this.boots = boots;
    }

    /**
     * Sneak plus right click arms or disarms. Only with an empty main hand,
     * so nothing in hand is placed, eaten or fired at the same time, and only
     * on air or a block with no interaction of its own: a chest, an anvil or
     * a door opens as if the boots did not exist. Clicking a mob fires a
     * different event and never reaches here. {@code Material#isInteractable}
     * is deprecated without a replacement; it is still the list of blocks
     * with a use of their own, which is exactly the question here. Cancelled
     * events count too, like the staffs: spawn protection and claim plugins
     * cancel the click on a block they protect, and the click is only a
     * signal here, nothing in the world is touched.
     */
    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = false)
    public void onSneakClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() != null
                || (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || !LevitationBoots.isWearing(plugin, player)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable()) {
            return;
        }
        boots.toggleArmed(player);
    }

    /** The double tap. Only reaches the boots while they granted the allowFlight that let the client send it. */
    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!boots.isArmed(player)) {
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

    /** Boots off: disarmed at once, and a flight in the air ends with the grace. */
    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getSlot() == EquipmentSlot.FEET && !LevitationBoots.is(plugin, event.getNewItem())) {
            boots.disarm(event.getPlayer(), "Levitation Boots disarmed: boots removed.");
        }
    }

    /** Monitor and ignoreCancelled: only a hit that actually went through, melee or projectile, disarms. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player) {
            boots.onDamaged(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        boots.revokeStale(event.getPlayer());
    }

    /** Still online here, so the restored allowFlight is what gets saved with the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boots.disarm(event.getPlayer(), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        boots.disarm(event.getEntity(), null);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        boots.disarm(event.getPlayer(), "Levitation Boots disarmed.");
    }

    /** Fires before vanilla rewrites the abilities for the new mode, so the grant is restored first and creative gets its own flight untouched. */
    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        boots.disarm(event.getPlayer(), null);
    }
}
