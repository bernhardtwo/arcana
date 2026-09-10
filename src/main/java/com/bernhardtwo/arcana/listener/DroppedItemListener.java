package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.item.AbilityItems;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

/**
 * A tagged item on the ground neither burns nor despawns. ItemSpawnEvent
 * fires for every item entity however it got there: dropped by hand, on
 * death, or out of a broken block. The void still deletes it, nothing can be
 * done there. Merging and pickup are untouched. Gated by
 * {@code items.indestructible-when-dropped}, checked when the item spawns.
 */
public final class DroppedItemListener implements Listener {

    private final ArcanaPlugin plugin;

    public DroppedItemListener(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (isProtected(item)) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
        }
    }

    /** Backstop for any damage source invulnerability does not cover. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && isProtected(item)) {
            event.setCancelled(true);
        }
    }

    private boolean isProtected(Item item) {
        return plugin.settings().items().indestructibleWhenDropped() && AbilityItems.isArcana(plugin, item.getItemStack());
    }
}
