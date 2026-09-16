package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.storm.StormBeamAbility;
import com.bernhardtwo.arcana.ability.storm.StormChargeAbility;
import com.bernhardtwo.arcana.ability.storm.StormSmashAbility;
import com.bernhardtwo.arcana.item.StormHammer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/** Feeds the three storm abilities: the sneak release, the hit that landed, and every path that ends a channel or a beam. */
public final class StormListener implements Listener {

    private final ArcanaPlugin plugin;
    private final StormChargeAbility charge;
    private final StormBeamAbility beam;
    private final StormSmashAbility smash;

    public StormListener(ArcanaPlugin plugin, StormChargeAbility charge, StormBeamAbility beam, StormSmashAbility smash) {
        this.plugin = plugin;
        this.charge = charge;
        this.beam = beam;
        this.smash = smash;
    }

    /** Sneak released: the charge fires or fizzles. Pressing sneak does nothing here, the cast is the click. */
    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            charge.release(event.getPlayer());
        }
    }

    /**
     * Monitor and ignoreCancelled: a melee hit by the hammer that actually
     * went through. A left click that lands on an entity within reach never
     * fires PlayerInteractEvent, so this is the only signal for it. Melee
     * only: the beam's own magic damage carries the caster as damager too.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || !(event.getDamager() instanceof Player caster)
                || !(event.getEntity() instanceof LivingEntity target)
                || !StormHammer.isHolding(plugin, caster)) {
            return;
        }
        smash.onHit(caster, target);
    }

    /** Any damage the caster actually takes, environmental included, interrupts a charge. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            charge.onDamaged(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        charge.revokeStale(event.getPlayer());
    }

    /** Still online here, so the restored walk speed is what gets saved with the player. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        endBoth(event.getPlayer(), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        endBoth(event.getEntity(), null);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        endBoth(event.getPlayer(), "cancelled.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        endBoth(event.getPlayer(), "cancelled.");
    }

    /** The hand changes: hotbar scroll, hand swap, or the hammer dropped. Anything subtler is caught by the charge tick. */
    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        endBoth(event.getPlayer(), "cancelled: hammer put away.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        endBoth(event.getPlayer(), "cancelled: hammer put away.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (StormHammer.isHammer(plugin, event.getItemDrop().getItemStack())) {
            endBoth(event.getPlayer(), "cancelled: hammer put away.");
        }
    }

    private void endBoth(Player player, String suffix) {
        charge.cancel(player, suffix == null ? null : "Storm: Charge " + suffix);
        beam.end(player, suffix == null ? null : "Storm: Beam " + suffix);
    }
}
