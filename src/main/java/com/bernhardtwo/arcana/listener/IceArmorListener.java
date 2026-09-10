package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ability.ice.IceArmorAbility;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class IceArmorListener implements Listener {

    private final IceArmorAbility armor;

    public IceArmorListener(IceArmorAbility armor) {
        this.armor = armor;
    }

    /** Only attacks consume a charge. A hit another plugin already cancelled never reaches here. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        LivingEntity attacker = attackerOf(event.getDamager());
        if (attacker != null && armor.absorb(victim, attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        armor.end(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        armor.end(event.getEntity().getUniqueId(), null);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        armor.end(event.getPlayer().getUniqueId(), "Ice: Armor faded.");
    }

    private static LivingEntity attackerOf(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return null;
    }
}
