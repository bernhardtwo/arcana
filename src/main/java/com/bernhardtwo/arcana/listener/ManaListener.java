package com.bernhardtwo.arcana.listener;

import com.bernhardtwo.arcana.ArcanaPlugin;
import com.bernhardtwo.arcana.item.ManaPotion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class ManaListener implements Listener {

    private final ArcanaPlugin plugin;

    public ManaListener(ArcanaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Always cancelled for our potion: no vanilla effect, and the stack is consumed by hand only when mana was restored. */
    @EventHandler(ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        if (!ManaPotion.is(plugin, event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.mana().isActive()) {
            player.sendActionBar(warn("Mana Potion: this server has no mana"));
            return;
        }
        double max = plugin.mana().maxMana(player);
        if (max <= 0.0 || plugin.mana().mana(player) >= max) {
            player.sendActionBar(warn("Mana Potion: your mana is already full"));
            return;
        }
        double restored = plugin.mana().restore(player, plugin.settings().mana().potionRestore());

        EquipmentSlot hand = event.getHand();
        ItemStack held = player.getInventory().getItem(hand);
        if (ManaPotion.is(plugin, held)) {
            held.setAmount(held.getAmount() - 1);
        }
        player.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));

        player.sendActionBar(Component.text(String.format("Mana Potion: +%.0f mana (%.0f / %.0f)",
                restored, plugin.mana().mana(player), max), NamedTextColor.AQUA));
        if (plugin.settings().effects()) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 1.5f);
        }
        plugin.manaBar().refresh(player);
    }

    /** Both fire before the hands change, so the bar is refreshed one tick later. */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.manaBar().hide(event.getPlayer());
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.manaBar().hide(event.getPlayer());
    }

    private void refreshNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                plugin.manaBar().refresh(player);
            }
        });
    }

    private static Component warn(String text) {
        return Component.text(text, NamedTextColor.GRAY);
    }
}
