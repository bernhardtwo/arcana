package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.ice.Frost;
import com.bernhardtwo.arcana.config.IceSlashSettings;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public final class IceListener implements Listener {

    private final ArcanaPlugin plugin;

    public IceListener(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Vanilla snowballs deal no damage; setting it on the event keeps knockback and invulnerability frames vanilla. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSnowballHit(EntityDamageByEntityEvent event) {
        if (isIceSnowball(event.getDamager()) && event.getEntity() instanceof LivingEntity) {
            event.setDamage(plugin.settings().iceSlash().snowballDamage());
        }
    }

    /** Frost is applied only once the damage went through, so cancelled hits leave no trace. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageApplied(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (isIceSnowball(event.getDamager())) {
            IceSlashSettings settings = plugin.settings().iceSlash();
            Frost.apply(target, settings.freezeTicks(), settings.slowDurationTicks());
            return;
        }
        plugin.frost().applyIfPending(target);
    }

    private boolean isIceSnowball(Entity damager) {
        return damager instanceof Snowball snowball
                && snowball.getPersistentDataContainer().has(plugin.iceSnowballKey(), PersistentDataType.BYTE);
    }
}
