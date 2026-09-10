package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.ability.Ability;
import com.bernhardtwo.arcana.item.AbilityItems;
import com.bernhardtwo.arcana.item.Wand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class AbilityUseListener implements Listener {

    private final ArcanaPlugin plugin;

    public AbilityUseListener(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = event.getItem();
        Optional<Wand> wand = AbilityItems.wandOf(plugin, held);
        if (wand.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        String abilityId = player.isSneaking() ? wand.get().secondaryAbility() : wand.get().primaryAbility();
        Optional<Ability> ability = plugin.abilities().find(abilityId);
        if (ability.isEmpty()) {
            player.sendMessage(warn("That ability no longer exists on this server."));
            return;
        }

        Ability selected = ability.get();
        if (!player.hasPermission(selected.permission())) {
            player.sendMessage(warn("You do not have permission to use " + selected.displayName() + "."));
            return;
        }

        long remaining = plugin.cooldowns().remainingMillis(player.getUniqueId(), selected.id());
        if (remaining > 0L) {
            player.sendActionBar(warn(selected.displayName() + " ready in " + format(remaining)));
            return;
        }

        selected.cast(player);
        plugin.cooldowns().start(player.getUniqueId(), selected.id(), selected.cooldownTicks());
        if (held != null) {
            player.setCooldown(held.getType(), selected.cooldownTicks());
        }
        player.sendActionBar(Component.text(selected.displayName(), NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldowns().forget(event.getPlayer().getUniqueId());
    }

    private Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }

    private String format(long millis) {
        double seconds = millis / 1000.0;
        return String.format("%.1fs", seconds);
    }
}
